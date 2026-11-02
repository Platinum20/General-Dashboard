package com.platinum20.dashboard.media.podcast

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    /** L'episodio nel player unico, se è un podcast a suonare. */
    val now: StateFlow<PodcastNow?> = CanvasPlayer.now.map { n ->
        n?.takeIf { it.item.kind == ResumeKind.PODCAST }?.let {
            val item = it.item
            PodcastNow(
                Episode(
                    id = item.key, title = item.title, audioUrl = item.link, publishedAt = 0,
                    durationSec = item.durationMs / 1000, description = "", image = item.image,
                    podcastTitle = item.subtitle, feedUrl = item.feed,
                ),
                it.playing, it.loading, it.positionMs, it.durationMs, it.error,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        CanvasPlayer.init(appContext)
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
        // Episodio che c'è solo su Spotify: suona nel riproduttore di Spotify (protetto), sempre nella coda unica.
        val item = if (episode.audioUrl.startsWith("spotify:")) {
            ResumeItem(ResumeKind.SPOTIFY, episode.audioUrl, episode.title, episode.podcastTitle, episode.image, at = System.currentTimeMillis())
        } else episode.asItem()
        CanvasPlayer.play(appContext, item, autoplay)
    }

    fun Episode.asItem() = ResumeItem(
        ResumeKind.PODCAST, id, title, podcastTitle, image,
        durationMs = durationSec * 1000, at = System.currentTimeMillis(), link = audioUrl, feed = feedUrl,
    )

    private fun isCurrent() = CanvasPlayer.now.value?.item?.kind == ResumeKind.PODCAST

    fun togglePause() {
        if (isCurrent()) CanvasPlayer.toggle()
    }

    /** Salta avanti o indietro di [deltaMs] (i classici 30 e 15 secondi). */
    fun seekBy(deltaMs: Long) {
        if (isCurrent()) CanvasPlayer.seekBy(deltaMs)
    }

    fun pause() {
        if (isCurrent()) CanvasPlayer.pause()
    }

    fun stop() {
        if (isCurrent()) CanvasPlayer.stop()
    }

    /** Da dove riprendere l'episodio [id] (0 se appena iniziato o quasi finito). */
    internal fun resumeMs(id: String, durationMs: Long): Long = PodcastProgress.resumeAt(_positions.value[id] ?: 0, durationMs)

    /** Il player unico segna dove si è arrivati nell'episodio. */
    internal fun saveProgress(item: ResumeItem, positionMs: Long, durationMs: Long, at: Long = System.currentTimeMillis()) {
        if (!::prefs.isInitialized) return
        // Anche in "Continua" di Media, insieme a video, musica e radio.
        MediaHistory.init(appContext)
        MediaHistory.record(item.copy(positionMs = positionMs, durationMs = durationMs.takeIf { it > 0 } ?: item.durationMs, at = at))
        val id = item.key
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
