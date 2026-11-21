package com.platinum20.dashboard.media.youtube

import android.content.Context
import com.platinum20.dashboard.media.FeedCache
import com.platinum20.dashboard.media.FeedCodec
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.net.userMessage
import com.platinum20.dashboard.media.youtube.player.YtChannelPage
import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Feed di YouTube dell'utente, tenuti in memoria finché l'app è aperta. */
object YouTubeStore {
    private val scope = MainScope()
    @Volatile private var appContext: Context? = null

    /** Per i titoli e i nomi originali anche delle pagine che non passano un Context (canali). */
    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        YtOriginals.init(context)
        watchOriginals()
    }
    private const val MAX_AGE = 15 * 60_000L
    /** Tempo per i feed RSS dei canali e per tutta la lettura delle iscrizioni (due pagine del sito + i feed). */
    private const val RSS_BUDGET = 30_000L
    private const val SUBS_TIMEOUT = 90_000L

    private val _subscriptions = MutableStateFlow(Loadable<List<YtVideo>>())
    /** Nuovi video dei canali seguiti, i più recenti prima. */
    val subscriptions: StateFlow<Loadable<List<YtVideo>>> = _subscriptions.asStateFlow()

    private val _playlists = MutableStateFlow(Loadable<List<YtPlaylist>>())
    val playlists: StateFlow<Loadable<List<YtPlaylist>>> = _playlists.asStateFlow()

    private val _liked = MutableStateFlow(Loadable<List<YtVideo>>())
    val liked: StateFlow<Loadable<List<YtVideo>>> = _liked.asStateFlow()

    private val _playlistItems = MutableStateFlow<Map<String, Loadable<List<YtVideo>>>>(emptyMap())
    val playlistItems: StateFlow<Map<String, Loadable<List<YtVideo>>>> = _playlistItems.asStateFlow()

    private val _search = MutableStateFlow(Loadable<List<YtVideo>>())
    val search: StateFlow<Loadable<List<YtVideo>>> = _search.asStateFlow()

    private val _watchLater = MutableStateFlow(Loadable<List<YtVideo>>())
    /** "Guarda più tardi": c'è solo leggendo il sito, l'API ufficiale non lo espone. */
    val watchLater: StateFlow<Loadable<List<YtVideo>>> = _watchLater.asStateFlow()

    /**
     * Durata e foto del canale dalla pagina Iscrizioni (1.486, Antonio: "manca la durata su alcuni"): i video letti dai
     * feed RSS non le hanno. Si tiene quello che c'è già, si riempie solo il vuoto.
     */
    internal fun withFeedDetails(videos: List<YtVideo>, feed: List<YtVideo>): List<YtVideo> {
        if (feed.isEmpty()) return videos
        val byId = feed.associateBy { it.id }
        val avatars = feed.filter { it.channelId.isNotBlank() && it.channelAvatar.isNotBlank() }.associate { it.channelId to it.channelAvatar }
        return videos.map { v ->
            val f = byId[v.id]
            val uploaded = v.uploadedText.ifBlank { f?.uploadedText.orEmpty() }
            v.copy(
                durationSec = v.durationSec.takeIf { it > 0 } ?: f?.durationSec ?: 0,
                channelAvatar = v.channelAvatar.ifBlank { f?.channelAvatar?.takeIf { it.isNotBlank() } ?: avatars[v.channelId].orEmpty() },
                uploadedText = uploaded,
                // L'RSS non sa delle dirette: "Diretta terminata" viene dalla pagina Iscrizioni ("Trasmesso … fa", 1.542).
                liveEnded = v.liveEnded || (!v.live && (f?.liveEnded == true || YtLive.ended(uploaded))),
                live = v.live || f?.live == true,
            )
        }
    }

    fun refreshSubscriptions(context: Context, force: Boolean = false) =
        load(context, _subscriptions, force, FeedCache.videos("yt-iscrizioni.json"), timeoutMs = SUBS_TIMEOUT, web = {
            val channels = YouTubeWeb.subscriptions(it)
            val feed = YouTubeWeb.lastFeed
            // Prima i feed RSS (con i video di prima per i canali che non hanno risposto), poi i nuovi della pagina Iscrizioni.
            val rss = keepMissingChannels(YouTubeApi.uploads(channels, RSS_BUDGET), _subscriptions.value.data.orEmpty(), channels)
            withFeedDetails(YtFeedTime.withFeedVideos(rss, feed, java.time.Instant.now()).take(300), feed)
        }) { token ->
            val channels = YouTubeApi.subscriptions(token)
            YouTubeApi.withDetails(token, keepMissingChannels(YouTubeApi.uploads(channels, RSS_BUDGET), _subscriptions.value.data.orEmpty(), channels).take(300))
        }

    private val _subsMore = MutableStateFlow<List<YtVideo>>(emptyList())
    /** I video più vecchi delle iscrizioni caricati arrivando in fondo a "Tutte le novità" (1.488). */
    val subscriptionsMore: StateFlow<List<YtVideo>> = _subsMore.asStateFlow()
    private val _subsEnded = MutableStateFlow(false)
    /** Non ci sono altri video delle iscrizioni da caricare: in fondo niente più caricamento. */
    val subscriptionsEnded: StateFlow<Boolean> = _subsEnded.asStateFlow()
    private var subsNext: String? = null
    private var subsStarted = false
    private var loadingSubs = false

    /**
     * Altri video delle iscrizioni in fondo a "Tutte le novità", come scorrendo le Iscrizioni di YouTube (1.488,
     * Antonio: "non è infinito"): si continua dalla pagina Iscrizioni letta dal sito, due pagine per volta.
     */
    fun loadMoreSubscriptions(context: Context) {
        if (loadingSubs || _subsEnded.value) return
        if (!subsStarted) {
            subsStarted = true
            subsNext = YouTubeWeb.lastFeedNext
        }
        val token = subsNext ?: run { _subsEnded.value = true; return }
        loadingSubs = true
        val app = context.applicationContext
        scope.launch {
            try {
                val page = withTimeoutOrNull(90_000) { YouTubeWeb.homeMore(app, token) } ?: return@launch
                subsNext = page.next
                val known = (_subscriptions.value.data.orEmpty() + _subsMore.value).map { it.id }.toSet()
                val fresh = page.videos.filter { it.id !in known }
                _subsMore.update { it + fresh }
                if (page.next == null || fresh.isEmpty()) _subsEnded.value = page.next == null
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Fine silenziosa: al prossimo arrivo in fondo si riprova.
            } finally {
                loadingSubs = false
            }
        }
    }

    private val _channels = MutableStateFlow(Loadable<List<YtChannel>>())
    /** I canali a cui sei iscritto (scheda Canali di Video). */
    val channels: StateFlow<Loadable<List<YtChannel>>> = _channels.asStateFlow()

    fun refreshChannels(context: Context, force: Boolean = false) =
        load(context, _channels, force, FeedCache.channels, web = { YouTubeWeb.subscriptions(it) }) { token -> YouTubeApi.subscriptions(token) }

    private val _channelVideos = MutableStateFlow<Map<String, Loadable<List<YtVideo>>>>(emptyMap())
    /** Tutti i video di ogni canale aperto (scheda Canali o tocco sul canale nella pagina del video). */
    val channelVideos: StateFlow<Map<String, Loadable<List<YtVideo>>>> = _channelVideos.asStateFlow()

    private val _channelMore = MutableStateFlow<Map<String, YtChannelPage>>(emptyMap())
    private val _channelHasMore = MutableStateFlow<Set<String>>(emptySet())
    /** Canali con altri video ancora da caricare (pagina successiva di YouTube). */
    val channelHasMore: StateFlow<Set<String>> = _channelHasMore.asStateFlow()

    fun loadChannel(channel: YtChannel, force: Boolean = false) {
        val state = _channelVideos.value[channel.id]
        if (!force && state != null && (state.loading || fresh(state))) return
        _channelVideos.update { it + (channel.id to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = try {
                val page = YtResolver.channelVideos(channel.id)
                keepPage(channel.id, page)
                Loadable(data = withAvatar(page.videos, channel), loadedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Se l'estrazione non va restano almeno gli ultimi video del feed RSS.
                try {
                    keepPage(channel.id, null)
                    Loadable(data = YouTubeApi.channelVideos(channel), loadedAt = System.currentTimeMillis())
                } catch (e2: Exception) {
                    if (e2 is kotlinx.coroutines.CancellationException) throw e2
                    (state ?: Loadable()).copy(loading = false, error = e.userMessage("I video del canale non arrivano, riprova tra poco."))
                }
            }
            _channelVideos.update { it + (channel.id to result) }
        }
    }

    private val _channelInfo = MutableStateFlow<Map<String, Loadable<com.platinum20.dashboard.media.youtube.player.YtChannelInfo>>>(emptyMap())
    /** La testata dei canali aperti (copertina, iscritti, schede), per id. */
    val channelInfo: StateFlow<Map<String, Loadable<com.platinum20.dashboard.media.youtube.player.YtChannelInfo>>> = _channelInfo.asStateFlow()

    fun loadChannelInfo(channel: YtChannel) {
        val state = _channelInfo.value[channel.id]
        if (state != null && (state.loading || state.data != null)) return
        _channelInfo.update { it + (channel.id to Loadable(loading = true)) }
        scope.launch {
            val result = try {
                Loadable(data = originalChannel(YtResolver.channelInfo(channel.id)), loadedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Loadable(error = e.userMessage("Il canale non risponde, riprova tra poco."))
            }
            _channelInfo.update { it + (channel.id to result) }
        }
    }

    /**
     * Nome e descrizione del canale come li ha scritti l'autore (1.571): con l'API collegata si prendono da `snippet`,
     * che non è mai tradotto; senza, restano quelli di YouTube in italiano.
     */
    private suspend fun originalChannel(info: com.platinum20.dashboard.media.youtube.player.YtChannelInfo): com.platinum20.dashboard.media.youtube.player.YtChannelInfo {
        val context = appContext ?: return info
        YouTubeAuth.init(context)
        if (!YouTubeAuth.connected.value) return info
        val token = runCatching { YouTubeAuth.token(context) }.getOrNull() ?: return info
        val (name, description) = runCatching { YouTubeApi.originalChannel(token, info.id) }.getOrNull() ?: return info
        return com.platinum20.dashboard.media.youtube.player.YtChannelInfo(
            id = info.id, name = name ?: info.name, avatar = info.avatar, banner = info.banner, subscribers = info.subscribers,
            description = description ?: info.description, verified = info.verified, tabs = info.tabs,
        )
    }

    private val _channelTabs = MutableStateFlow<Map<String, Loadable<com.platinum20.dashboard.media.youtube.player.YtChannelTabItems>>>(emptyMap())
    /** Shorts, Live e Playlist dei canali aperti, per "id/scheda". */
    val channelTabs: StateFlow<Map<String, Loadable<com.platinum20.dashboard.media.youtube.player.YtChannelTabItems>>> = _channelTabs.asStateFlow()

    fun loadChannelTab(channel: YtChannel, filter: String) {
        val key = channel.id + "/" + filter
        val state = _channelTabs.value[key]
        if (state != null && (state.loading || state.data != null)) return
        val tab = _channelInfo.value[channel.id]?.data?.tabs?.get(filter) ?: return
        _channelTabs.update { it + (key to Loadable(loading = true)) }
        scope.launch {
            val result = try {
                Loadable(data = YtResolver.channelTab(channel.id, tab), loadedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Loadable(error = e.userMessage("Questa scheda del canale non arriva, riprova tra poco."))
            }
            _channelTabs.update { it + (key to result) }
        }
    }

    private var loadingMore: String? = null

    /** La pagina dopo dei video del canale: si chiama arrivando in fondo alla lista. */
    fun loadMoreChannel(channel: YtChannel) {
        val page = _channelMore.value[channel.id] ?: return
        if (loadingMore == channel.id) return
        loadingMore = channel.id
        scope.launch {
            try {
                val next = YtResolver.channelVideos(channel.id, page)
                keepPage(channel.id, next)
                _channelVideos.update { all ->
                    val old = all[channel.id] ?: return@update all
                    all + (channel.id to old.copy(data = mergeChannelPage(old.data.orEmpty(), withAvatar(next.videos, channel))))
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Fine silenziosa: i video già caricati restano, al prossimo arrivo in fondo si riprova.
            } finally {
                loadingMore = null
            }
        }
    }

    private fun keepPage(id: String, page: YtChannelPage?) {
        _channelMore.update { if (page?.next != null) it + (id to page) else it - id }
        _channelHasMore.update { if (page?.next != null) it + id else it - id }
    }

    private fun withAvatar(videos: List<YtVideo>, channel: YtChannel) =
        videos.map { if (it.channelAvatar.isBlank()) it.copy(channelAvatar = channel.thumbnail, channel = it.channel.ifBlank { channel.title }) else it }

    private val _home = MutableStateFlow(Loadable<List<YtVideo>>())
    /** La Home di YouTube (video consigliati): c'è solo con l'accesso a YouTube fatto nell'app. */
    val home: StateFlow<Loadable<List<YtVideo>>> = _home.asStateFlow()

    private val _history = MutableStateFlow(Loadable<List<YtVideo>>())
    /** Cronologia di YouTube del tuo account (con l'accesso nell'app); quella dell'app è in [YtHistory]. */
    val history: StateFlow<Loadable<List<YtVideo>>> = _history.asStateFlow()

    fun refreshHistory(context: Context, force: Boolean = false) =
        load(context, _history, force, FeedCache.videos("yt-cronologia.json"), web = { YouTubeWeb.history(it) }, api = null)

    fun refreshHome(context: Context, force: Boolean = false) =
        load(context, _home, force, FeedCache.videos("yt-home.json"), web = { ctx ->
            YouTubeWeb.home(ctx).also { homeNext = it.next; _homeEnded.value = false }.videos
        }, api = null)

    /** Segno di YouTube per i consigliati dopo; null se la Home è venuta dalla copia salvata. */
    private var homeNext: String? = null
    private var loadingHome = false
    private val _homeEnded = MutableStateFlow(false)
    /** YouTube non ha più consigliati da dare (succede di rado): in fondo niente più caricamento. */
    val homeEnded: StateFlow<Boolean> = _homeEnded.asStateFlow()

    /**
     * Altri consigliati in fondo alla Home, come scorrendo l'app di YouTube. Senza segno (Home presa dalla
     * copia salvata) si rilegge la Home e si aggiungono quelli nuovi: anche così il segno arriva.
     */
    fun loadMoreHome(context: Context) {
        val old = _home.value
        if (loadingHome || old.loading || old.data == null || _homeEnded.value) return
        loadingHome = true
        val app = context.applicationContext
        scope.launch {
            try {
                val token = homeNext
                val page = withTimeoutOrNull(90_000) {
                    if (token != null) YouTubeWeb.homeMore(app, token) else YouTubeWeb.home(app)
                } ?: return@launch
                homeNext = page.next
                val before = _home.value.data.orEmpty()
                val merged = mergeChannelPage(before, page.videos)
                _homeEnded.value = endOfHome(page.next, before.size, merged.size)
                _home.update { it.copy(data = merged) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Fine silenziosa: i video restano, al prossimo arrivo in fondo si riprova.
            } finally {
                loadingHome = false
            }
        }
    }

    fun refreshPlaylists(context: Context, force: Boolean = false) =
        load(context, _playlists, force, FeedCache.playlists, web = { YouTubeWeb.playlists(it) }) { token -> YouTubeApi.playlists(token) }

    fun refreshLiked(context: Context, force: Boolean = false) =
        load(context, _liked, force, FeedCache.videos("yt-mi-piace.json"), web = { YouTubeWeb.liked(it) }) { token -> YouTubeApi.liked(token) }

    fun refreshWatchLater(context: Context, force: Boolean = false) =
        load(context, _watchLater, force, FeedCache.videos("yt-dopo.json"), web = { YouTubeWeb.watchLater(it) }, api = null)

    fun loadPlaylist(context: Context, playlistId: String, force: Boolean = false) {
        val state = _playlistItems.value[playlistId]
        if (!force && state != null && (state.loading || fresh(state))) return
        _playlistItems.update { it + (playlistId to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = call(context, web = { YouTubeWeb.playlistItems(it, playlistId) }) { token ->
                YouTubeApi.playlistItems(token, playlistId)
            }
            _playlistItems.update { it + (playlistId to result) }
        }
    }

    fun search(context: Context, query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch {
            // Prima la ricerca nativa (niente quota dell'API); se YouTube la blocca, la strada di prima.
            val native = runCatching { YtResolver.search(query.trim()) }.getOrNull()?.takeIf { it.isNotEmpty() }
            _search.value = if (native != null) {
                Loadable(data = native, loadedAt = System.currentTimeMillis())
            } else {
                call(context, web = { YouTubeWeb.search(it, query.trim()) }) { token -> YouTubeApi.search(token, query.trim()) }
            }
        }
    }

    fun clear() {
        FeedCache.clear("yt-")
        _subscriptions.value = Loadable()
        _playlists.value = Loadable()
        _liked.value = Loadable()
        _watchLater.value = Loadable()
        _playlistItems.value = emptyMap()
        _search.value = Loadable()
        _home.value = Loadable()
        homeNext = null
        _homeEnded.value = false
        _history.value = Loadable()
        _channels.value = Loadable()
        _channelVideos.value = emptyMap()
        _channelMore.value = emptyMap()
        _channelHasMore.value = emptySet()
    }

    /** Dati finti per le foto automatiche delle schermate (solo test). */
    internal fun preview(home: List<YtVideo>, subscriptions: List<YtVideo>) {
        val now = System.currentTimeMillis()
        _home.value = Loadable(data = home, loadedAt = now)
        _subscriptions.value = Loadable(data = subscriptions, loadedAt = now)
    }

    /**
     * Titoli originali (1.571): ogni lista letta dal sito o da NewPipe chiede a [YtOriginals] i titoli veri e li mette
     * al posto di quelli tradotti da YouTube, anche quando arrivano dopo. Le Iscrizioni no: vengono dai feed RSS, che
     * hanno già i titoli originali. Parte con [init], non alla prima lettura dell'oggetto (i test JVM non hanno Main).
     */
    private fun watchOriginals() {
        val lists = listOf(_liked, _search, _watchLater, _home, _history)
        lists.forEach { flow -> scope.launch { flow.collect { YtOriginals.want(it.data); relabel(flow, YtOriginals.titles.value) } } }
        scope.launch { _subsMore.collect { YtOriginals.want(it); relabelList(_subsMore, YtOriginals.titles.value) } }
        listOf(_channelVideos, _playlistItems).forEach { flow ->
            scope.launch { flow.collect { all -> all.values.forEach { YtOriginals.want(it.data) }; relabelMap(flow, YtOriginals.titles.value) } }
        }
        scope.launch { _channelTabs.collect { all -> all.values.forEach { YtOriginals.want(it.data?.videos) }; relabelTabs(YtOriginals.titles.value) } }
        scope.launch {
            YtOriginals.titles.collect { titles ->
                lists.forEach { relabel(it, titles) }
                relabelList(_subsMore, titles)
                relabelMap(_channelVideos, titles)
                relabelMap(_playlistItems, titles)
                relabelTabs(titles)
            }
        }
    }

    private fun relabel(flow: MutableStateFlow<Loadable<List<YtVideo>>>, titles: Map<String, String>) {
        if (titles.isEmpty()) return
        flow.update { l -> l.data?.let { d -> YtOriginals.apply(d, titles).takeIf { it !== d } }?.let { l.copy(data = it) } ?: l }
    }

    private fun relabelList(flow: MutableStateFlow<List<YtVideo>>, titles: Map<String, String>) {
        if (titles.isNotEmpty()) flow.update { YtOriginals.apply(it, titles) }
    }

    private fun relabelMap(flow: MutableStateFlow<Map<String, Loadable<List<YtVideo>>>>, titles: Map<String, String>) {
        if (titles.isEmpty()) return
        flow.update { all ->
            var changed = false
            val out = all.mapValues { (_, l) ->
                val d = l.data ?: return@mapValues l
                val fixed = YtOriginals.apply(d, titles)
                if (fixed !== d) { changed = true; l.copy(data = fixed) } else l
            }
            if (changed) out else all
        }
    }

    private fun relabelTabs(titles: Map<String, String>) {
        if (titles.isEmpty()) return
        _channelTabs.update { all ->
            var changed = false
            val out = all.mapValues { (_, l) ->
                val d = l.data ?: return@mapValues l
                val fixed = YtOriginals.apply(d.videos, titles)
                if (fixed !== d.videos) { changed = true; l.copy(data = d.copy(videos = fixed)) } else l
            }
            if (changed) out else all
        }
    }

    private fun fresh(state: Loadable<*>) = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE

    private fun <T> load(
        context: Context,
        flow: MutableStateFlow<Loadable<T>>,
        force: Boolean,
        codec: FeedCodec<T>,
        timeoutMs: Long = 60_000,
        web: suspend (Context) -> T,
        api: (suspend (String) -> T)?,
    ) {
        val state = flow.value
        if (state.loading || (!force && fresh(state))) return
        flow.update { it.copy(loading = true, error = null) }
        scope.launch {
            var previous = state.data
            // All'avvio si vede subito l'ultimo feed salvato, intanto si scarica quello nuovo.
            if (previous == null) {
                FeedCache.restore(codec)?.let { saved ->
                    previous = saved.data
                    flow.update { it.copy(data = saved.data, loadedAt = saved.loadedAt) }
                    if (!force && fresh(saved)) {
                        flow.update { it.copy(loading = false) }
                        return@launch
                    }
                }
            }
            val result = call(context, web, timeoutMs, api)
            flow.value = if (result.data == null) result.copy(data = previous) else result
            result.data?.let { FeedCache.save(codec, it, result.loadedAt) }
        }
    }

    /**
     * Con l'API ufficiale collegata usa un token valido (se è scaduto lo rinnova una volta e riprova),
     * altrimenti legge il sito con l'accesso fatto nell'app.
     */
    private suspend fun <T> call(context: Context, web: suspend (Context) -> T, timeoutMs: Long = 60_000, api: (suspend (String) -> T)?): Loadable<T> {
        val app = context.applicationContext
        init(app)
        return try {
            val token = if (api != null && YouTubeAuth.connected.value) YouTubeAuth.token(app) else null
            val data = when {
                // Senza API ufficiale si legge il sito: mai oltre [timeoutMs], poi un errore chiaro.
                api == null || token == null -> withTimeoutOrNull(timeoutMs) { web(app) }
                    ?: throw java.io.IOException(
                        "YouTube non risponde. Tocca la chiave in alto e collega l'API ufficiale di Google: è più veloce e affidabile."
                    )
                else -> try {
                    api(token)
                } catch (e: YouTubeAuthException) {
                    api(YouTubeAuth.token(app, forceRefresh = true) ?: throw e)
                }
            }
            Loadable(data = data, loadedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            Loadable(error = e.userMessage("YouTube non è raggiungibile, riprova tra poco."))
        }
    }
}

/** La Home è finita solo se YouTube non dà più il segno per continuare e quest'ultima pagina non ha portato niente di nuovo. */
internal fun endOfHome(next: String?, before: Int, after: Int): Boolean = next == null && after <= before

/** Aggiunge in fondo una pagina di video del canale senza doppioni (YouTube a volte ne ripete uno a cavallo). */
internal fun mergeChannelPage(old: List<YtVideo>, page: List<YtVideo>): List<YtVideo> = (old + page).distinctBy { it.id }

/**
 * I canali seguiti il cui feed RSS stavolta non è arrivato tengono i video di prima: altrimenti, quando
 * YouTube rifiuta molti feed di fila, Iscrizioni restava con i video di un canale solo.
 * I canali non più seguiti spariscono; il risultato è sempre dal più recente.
 */
internal fun keepMissingChannels(fresh: List<YtVideo>, previous: List<YtVideo>, channels: List<YtChannel>): List<YtVideo> {
    val followed = channels.map { it.id }.toSet()
    val arrived = fresh.map { it.channelId }.toSet()
    val kept = previous.filter { it.channelId in followed && it.channelId !in arrived }
    return (fresh + kept).distinctBy { it.id }.sortedByDescending { it.published }
}
