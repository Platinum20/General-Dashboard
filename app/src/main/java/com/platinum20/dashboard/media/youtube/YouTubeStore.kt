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
    private const val MAX_AGE = 15 * 60_000L

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

    fun refreshSubscriptions(context: Context, force: Boolean = false) =
        load(context, _subscriptions, force, FeedCache.videos("yt-iscrizioni.json"), web = { YouTubeApi.uploads(YouTubeWeb.subscriptions(it)).take(300) }) { token ->
            YouTubeApi.withDetails(token, YouTubeApi.uploads(YouTubeApi.subscriptions(token)).take(300))
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
        load(context, _home, force, FeedCache.videos("yt-home.json"), web = { YouTubeWeb.home(it) }, api = null)

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

    private fun fresh(state: Loadable<*>) = state.data != null && System.currentTimeMillis() - state.loadedAt < MAX_AGE

    private fun <T> load(
        context: Context,
        flow: MutableStateFlow<Loadable<T>>,
        force: Boolean,
        codec: FeedCodec<T>,
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
            val result = call(context, web, api)
            flow.value = if (result.data == null) result.copy(data = previous) else result
            result.data?.let { FeedCache.save(codec, it, result.loadedAt) }
        }
    }

    /**
     * Con l'API ufficiale collegata usa un token valido (se è scaduto lo rinnova una volta e riprova),
     * altrimenti legge il sito con l'accesso fatto nell'app.
     */
    private suspend fun <T> call(context: Context, web: suspend (Context) -> T, api: (suspend (String) -> T)?): Loadable<T> {
        val app = context.applicationContext
        return try {
            val token = if (api != null && YouTubeAuth.connected.value) YouTubeAuth.token(app) else null
            val data = when {
                // Senza API ufficiale si legge il sito: mai più di un minuto, poi un errore chiaro.
                api == null || token == null -> withTimeoutOrNull(60_000) { web(app) }
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

/** Aggiunge in fondo una pagina di video del canale senza doppioni (YouTube a volte ne ripete uno a cavallo). */
internal fun mergeChannelPage(old: List<YtVideo>, page: List<YtVideo>): List<YtVideo> = (old + page).distinctBy { it.id }
