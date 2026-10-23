package com.platinum20.dashboard.media.podcast

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.NowPlaying
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.radio.RadioService
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** L'episodio in ascolto. */
data class PodcastNow(
    val episode: Episode,
    val playing: Boolean,
    val buffering: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val error: String?,
)

/**
 * Podcast: ricerca (iTunes Search, gratis e senza chiavi), quelli seguiti, episodi dai feed RSS
 * e ascolto nello stesso player della radio, con il punto in cui eri salvato per ogni episodio.
 */
object PodcastStore {
    private const val PREFS = "podcast"
    private const val KEY_FOLLOWED = "followed"
    private const val KEY_POSITIONS = "positions"
    private const val MAX_POSITIONS = 200
    private const val KEY_SPOTIFY_FEEDS = "spotify_feeds"
    private const val ITUNES = "https://itunes.apple.com/search?media=podcast&country=it&limit=30&term="

    private val scope = MainScope()
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: Triple<Episode, Long, Boolean>? = null
    private var current: Episode? = null
    private var ticker: Job? = null
    /** L'episodio appena chiesto al player, finché il player non lo carica. */
    private var awaiting: String? = null

    private val _search = MutableStateFlow(Loadable<List<Podcast>>())
    val search: StateFlow<Loadable<List<Podcast>>> = _search.asStateFlow()

    private val _followed = MutableStateFlow<List<Podcast>>(emptyList())
    val followed: StateFlow<List<Podcast>> = _followed.asStateFlow()

    private val _spotify = MutableStateFlow<List<Podcast>>(emptyList())
    /**
     * I podcast salvati su Spotify, messi insieme agli altri: se lo stesso podcast ha un feed RSS
     * pubblico si ascolta da lì (nel player dell'app, con il punto salvato), se no con Spotify.
     */
    val spotify: StateFlow<List<Podcast>> = _spotify.asStateFlow()

    /** Tutti i podcast che segui, qui e su Spotify, senza doppioni. */
    fun allFollowed(): List<Podcast> = merge(_followed.value, _spotify.value)

    /** Seguiti nell'app prima, poi quelli di Spotify che non ci sono già (stesso titolo o stesso feed). */
    fun merge(followed: List<Podcast>, spotify: List<Podcast>): List<Podcast> {
        val titles = followed.map { PodcastFeed.normalizeTitle(it.title) }.toSet()
        val feeds = followed.map { it.feedUrl }.toSet()
        return followed + spotify.filter { PodcastFeed.normalizeTitle(it.title) !in titles && it.feedUrl !in feeds }
    }

    private var spotifyLoadedAt = 0L

    /** Legge i podcast salvati su Spotify e cerca per ognuno il feed RSS (una volta sola, poi ricordato). */
    private suspend fun loadSpotify() {
        if (System.currentTimeMillis() - spotifyLoadedAt < 30 * 60_000L) return
        SpotifyAuth.init(appContext)
        val token = runCatching { SpotifyAuth.token() }.getOrNull() ?: return
        val shows = runCatching { SpotifyApi.savedShows(token) }.getOrNull() ?: return
        val known = runCatching { JSONObject(prefs.getString(KEY_SPOTIFY_FEEDS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val podcasts = shows.map { show ->
            scope.async {
                val feed = if (known.has(show.id)) known.optString(show.id) else runCatching {
                    PodcastFeed.matchFeed(
                        show.title,
                        PodcastFeed.parseSearch(get(ITUNES + URLEncoder.encode(show.title, "UTF-8"))),
                    )?.feedUrl.orEmpty()
                }.getOrNull()?.also { known.put(show.id, it) }
                Podcast(
                    id = show.id,
                    title = show.title,
                    author = show.subtitle,
                    feedUrl = feed?.ifBlank { null } ?: show.uri,
                    artwork = show.image,
                    fromSpotify = true,
                )
            }
        }.awaitAll()
        prefs.edit().putString(KEY_SPOTIFY_FEEDS, known.toString()).apply()
        _spotify.value = podcasts
        spotifyLoadedAt = System.currentTimeMillis()
    }

    private val _episodes = MutableStateFlow<Map<String, Loadable<List<Episode>>>>(emptyMap())
    /** Gli episodi di ogni podcast aperto, per indirizzo del feed. */
    val episodes: StateFlow<Map<String, Loadable<List<Episode>>>> = _episodes.asStateFlow()

    private val _latest = MutableStateFlow(Loadable<List<Episode>>())
    /** Gli ultimi episodi dei podcast seguiti, tutti insieme dal più nuovo. */
    val latest: StateFlow<Loadable<List<Episode>>> = _latest.asStateFlow()

    private val _positions = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Dove eri rimasto in ogni episodio (millisecondi), per id dell'episodio. */
    val positions: StateFlow<Map<String, Long>> = _positions.asStateFlow()

    private val _now = MutableStateFlow<PodcastNow?>(null)
    val now: StateFlow<PodcastNow?> = _now.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _followed.value = runCatching {
            val array = JSONArray(prefs.getString(KEY_FOLLOWED, "[]"))
            (0 until array.length()).map { Podcast.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        _positions.value = runCatching {
            val o = JSONObject(prefs.getString(KEY_POSITIONS, "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.optLong(it) }
        }.getOrDefault(emptyMap())
    }

    fun search(query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch {
            _search.value = runCatching {
                PodcastFeed.parseSearch(
                    get(ITUNES + URLEncoder.encode(query.trim(), "UTF-8"))
                )
            }.fold({ Loadable(data = it, loadedAt = System.currentTimeMillis()) }, { Loadable(error = "Ricerca non riuscita, riprova") })
        }
    }

    fun clearSearch() {
        _search.value = Loadable()
    }

    fun isFollowed(podcast: Podcast) = _followed.value.any { it.feedUrl == podcast.feedUrl }

    /** Seguito qui o salvato su Spotify. */
    fun isInLibrary(podcast: Podcast) = allFollowed().any {
        it.feedUrl == podcast.feedUrl || PodcastFeed.normalizeTitle(it.title) == PodcastFeed.normalizeTitle(podcast.title)
    }

    fun toggleFollow(podcast: Podcast) {
        val list = if (isFollowed(podcast)) _followed.value.filter { it.feedUrl != podcast.feedUrl } else _followed.value + podcast
        _followed.value = list
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FOLLOWED, array.toString()).apply()
        loadLatest(force = true)
    }

    fun loadEpisodes(podcast: Podcast, force: Boolean = false) {
        val state = _episodes.value[podcast.feedUrl]
        if (state?.loading == true || (!force && state?.data != null)) return
        _episodes.update { it + (podcast.feedUrl to (state ?: Loadable()).copy(loading = true, error = null)) }
        scope.launch {
            val result = runCatching { fetchEpisodes(podcast) }
                .fold({ Loadable(data = it, loadedAt = System.currentTimeMillis()) }, { Loadable(data = state?.data, error = "Feed del podcast non raggiungibile") })
            _episodes.update { it + (podcast.feedUrl to result) }
        }
    }

    /** Gli ultimi episodi dei podcast seguiti (i feed si scaricano insieme; uno che non risponde non ferma gli altri). */
    fun loadLatest(force: Boolean = false) {
        val state = _latest.value
        if (state.loading || (!force && state.data != null)) return
        _latest.update { it.copy(loading = true, error = null) }
        scope.launch {
            loadSpotify()
            val follow = allFollowed()
            if (follow.isEmpty()) {
                _latest.value = Loadable(data = emptyList(), loadedAt = System.currentTimeMillis())
                return@launch
            }
            val lists = follow.map { podcast -> async { runCatching { fetchEpisodes(podcast) }.getOrNull() } }.awaitAll()
            val episodes = latestOf(lists.filterNotNull())
            _latest.value = Loadable(
                data = episodes,
                loadedAt = System.currentTimeMillis(),
                error = if (lists.all { it == null }) "Feed dei podcast non raggiungibili" else null,
            )
        }
    }

    /** I più nuovi tra gli episodi di tutti i podcast, al massimo [perPodcast] per podcast e [max] in tutto. */
    fun latestOf(lists: List<List<Episode>>, perPodcast: Int = 5, max: Int = 30): List<Episode> =
        lists.flatMap { it.sortedByDescending(Episode::publishedAt).take(perPodcast) }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAt }
            .take(max)

    private suspend fun fetchEpisodes(podcast: Podcast): List<Episode> {
        if (podcast.feedUrl.startsWith("spotify:show:")) {
            val token = SpotifyAuth.token() ?: throw IOException("Spotify non collegato")
            return SpotifyApi.showEpisodes(token, podcast.feedUrl.substringAfterLast(':')).map { PodcastFeed.fromSpotify(it, podcast) }
        }
        val list = PodcastFeed.parse(get(podcast.feedUrl), podcast.feedUrl)
        // Nel feed il titolo è a volte diverso da quello della ricerca: teniamo quello che l'utente ha visto.
        return list.map { if (podcast.title.isNotBlank()) it.copy(podcastTitle = podcast.title) else it }
            .map { if (it.image.isBlank()) it.copy(image = podcast.artwork) else it }
    }

    /** Con [autoplay] falso l'episodio si carica fermo e aspetta il play (es. aperto da una nota). */
    fun play(episode: Episode, autoplay: Boolean = true) {
        // Episodio che c'è solo su Spotify: suona nel riproduttore di Spotify (protetto), sempre nell'app.
        if (episode.audioUrl.startsWith("spotify:")) {
            if (current != null) stop()
            SpotifyPlayer.playLink(appContext, episode.audioUrl, episode.title, episode.podcastTitle, episode.image)
            return
        }
        // Un solo player alla volta: si fermano YouTube, Spotify e la radio (che lascia il player al podcast).
        NowPlaying.pauseOthers(null, podcast = false)
        RadioStore.yieldPlayer()
        com.platinum20.dashboard.media.Playback.starting(com.platinum20.dashboard.media.PlaySource.PODCAST)
        saveCurrentPosition()
        current = episode
        val startMs = PodcastProgress.resumeAt(_positions.value[episode.id] ?: 0, episode.durationSec * 1000)
        _now.value = PodcastNow(episode, playing = false, buffering = true, positionMs = startMs, durationMs = episode.durationSec * 1000, error = null)
        // Subito in "Continua" di Media, senza aspettare la prima pausa.
        savePosition(episode.id, startMs)
        val ready = controller
        if (ready != null) start(ready, episode, startMs, autoplay) else {
            pending = Triple(episode, startMs, autoplay)
            connect()
        }
    }

    fun togglePause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            NowPlaying.pauseOthers(null)
            c.play()
        }
    }

    /** Salta avanti o indietro di [deltaMs] (i classici 30 e 15 secondi). */
    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        if (current == null) return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
    }

    fun pause() {
        if (current == null) return
        controller?.takeIf { it.isPlaying }?.pause()
    }

    fun stop() {
        saveCurrentPosition()
        controller?.stop()
        controller?.clearMediaItems()
        current = null
        _now.value = null
    }

    /** La radio prende il player: salviamo il punto e smettiamo di seguirlo, senza comandarlo. */
    fun yieldPlayer() {
        saveCurrentPosition()
        current = null
        _now.value = null
    }

    private fun start(controller: MediaController, episode: Episode, startMs: Long, autoplay: Boolean) {
        awaiting = episode.id
        val item = MediaItem.Builder()
            .setMediaId(episode.id)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(episode.audioUrl)).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(episode.podcastTitle)
                    .apply { if (episode.image.isNotBlank()) setArtworkUri(Uri.parse(episode.image)) }
                    .build()
            )
            .build()
        controller.setMediaItem(item, startMs)
        controller.prepare()
        if (autoplay) controller.play() else controller.pause()
    }

    private fun connect() {
        if (connecting) return
        connecting = true
        val token = SessionToken(appContext, ComponentName(appContext, RadioService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish(player)
            })
            pending?.let { (episode, startMs, autoplay) -> start(c, episode, startMs, autoplay) }
            pending = null
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun publish(player: Player) {
        val episode = current ?: return
        // La radio ha messo la sua stazione: l'episodio non è più nel player.
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId == episode.id) awaiting = null
        // Il player non ha ancora caricato l'episodio appena scelto: aspettiamo.
        if (awaiting == episode.id || (mediaId == null && pending != null)) return
        if (mediaId != null && mediaId != episode.id) {
            current = null
            _now.value = null
            return
        }
        val duration = player.duration.takeIf { it > 0 } ?: (episode.durationSec * 1000)
        val ended = player.playbackState == Player.STATE_ENDED
        _now.value = PodcastNow(
            episode = episode,
            playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = if (ended) duration else player.currentPosition,
            durationMs = duration,
            error = player.playerError?.let { "Episodio non raggiungibile" },
        )
        if (ended) savePosition(episode.id, duration) else if (!player.isPlaying) savePosition(episode.id, player.currentPosition)
        if (player.isPlaying) startTicker() else ticker?.cancel()
    }

    // Mentre suona: barra del tempo che scorre e punto salvato ogni 10 secondi.
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var beats = 0
            while (isActive) {
                delay(1_000)
                val c = controller ?: break
                val episode = current ?: break
                _now.update { it?.copy(positionMs = c.currentPosition) }
                if (++beats % 10 == 0) savePosition(episode.id, c.currentPosition)
            }
        }
    }

    private fun saveCurrentPosition() {
        val episode = current ?: return
        val c = controller ?: return
        if (c.currentMediaItem?.mediaId == episode.id) savePosition(episode.id, c.currentPosition)
    }

    private fun savePosition(id: String, positionMs: Long) {
        if (!::prefs.isInitialized) return
        // Anche in "Continua" di Media, insieme a video, musica e radio.
        current?.takeIf { it.id == id }?.let { episode ->
            MediaHistory.init(appContext)
            MediaHistory.record(
                ResumeItem(
                    kind = ResumeKind.PODCAST,
                    key = episode.id,
                    title = episode.title,
                    subtitle = episode.podcastTitle,
                    image = episode.image,
                    positionMs = positionMs,
                    durationMs = (_now.value?.durationMs ?: 0).takeIf { it > 0 } ?: (episode.durationSec * 1000),
                    at = System.currentTimeMillis(),
                    link = episode.audioUrl,
                    feed = episode.feedUrl,
                )
            )
        }
        val map = (_positions.value - id + (id to positionMs)).entries.toList().takeLast(MAX_POSITIONS).associate { it.key to it.value }
        _positions.value = map
        prefs.edit().putString(KEY_POSITIONS, JSONObject(map).toString()).apply()
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        var address = url
        // Molti feed passano da redirect (anche tra http e https), che HttpURLConnection non segue da solo.
        repeat(6) {
            val conn = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Canvas/${BuildConfig.VERSION_NAME}")
            }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: throw IOException("Redirect senza indirizzo")
                    address = PodcastFeed.secure(URL(URL(address), next).toString())
                    return@repeat
                }
                if (code !in 200..299) throw IOException("HTTP $code")
                return@withContext conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Troppi redirect")
    }
}
