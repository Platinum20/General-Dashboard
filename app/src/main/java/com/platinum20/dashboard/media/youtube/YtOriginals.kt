package com.platinum20.dashboard.media.youtube

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

/**
 * I titoli dei video nella lingua in cui li ha scritti l'autore (Antonio, 1.571: "ogni parte deve essere quella
 * originale"). YouTube traduce da solo i titoli nella lingua di chi guarda; nelle liste (canali, Home, Iscrizioni,
 * Cronologia, ricerca) si sostituiscono con quelli veri: con l'API collegata 50 alla volta, se no l'oEmbed pubblico,
 * che non è mai tradotto. Si ricordano in un file, così ogni video si chiede una volta sola.
 */
object YtOriginals {
    private const val FILE = "yt-titoli-originali.json"
    /** Quanti titoli si tengono nel file: i più vecchi escono. */
    private const val KEEP = 4_000
    /** Quanti titoli si chiedono per ogni lista arrivata: il resto alla prossima. */
    private const val PER_ROUND = 60
    private const val PARALLEL = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val asked = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    @Volatile private var app: Context? = null
    @Volatile private var loaded = false

    private val _titles = MutableStateFlow<Map<String, String>>(emptyMap())
    /** id del video → titolo originale. */
    val titles: StateFlow<Map<String, String>> = _titles.asStateFlow()

    fun init(context: Context) {
        if (app == null) app = context.applicationContext
    }

    /** Le liste con i titoli veri al posto di quelli tradotti; il resto del video resta com'è. */
    fun apply(videos: List<YtVideo>, titles: Map<String, String>): List<YtVideo> {
        if (titles.isEmpty()) return videos
        var changed = false
        val out = videos.map { v ->
            val real = titles[v.id]
            if (real != null && real != v.title) { changed = true; v.copy(title = real) } else v
        }
        return if (changed) out else videos
    }

    /** I video della lista di cui non si sa ancora il titolo vero e che non si sono già chiesti, al massimo [max]. */
    internal fun missing(videos: List<YtVideo>, known: Map<String, String>, asked: Set<String>, max: Int = PER_ROUND): List<String> =
        videos.asSequence().map { it.id }.filter { it.isNotBlank() && it !in known && it !in asked }.distinct().take(max).toList()

    /** Da mettere nel file: gli ultimi [KEEP] (le mappe tengono l'ordine d'arrivo). */
    internal fun trimmed(titles: Map<String, String>, keep: Int = KEEP): Map<String, String> =
        if (titles.size <= keep) titles else titles.entries.drop(titles.size - keep).associate { it.key to it.value }

    /** Chiede i titoli veri dei video di [videos] che mancano; arrivano in [titles]. */
    fun want(videos: List<YtVideo>?) {
        if (videos.isNullOrEmpty()) return
        val context = app ?: return
        scope.launch {
            restore(context)
            val ids = missing(videos, _titles.value, asked)
            if (ids.isEmpty()) return@launch
            asked.addAll(ids)
            val found = fetch(context, ids)
            if (found.isEmpty()) return@launch
            _titles.update { trimmed(it + found) }
            save(context)
        }
    }

    private suspend fun fetch(context: Context, ids: List<String>): Map<String, String> {
        YouTubeAuth.init(context)
        val token = if (YouTubeAuth.connected.value) runCatching { YouTubeAuth.token(context) }.getOrNull() else null
        if (token != null) {
            val api = runCatching { ids.chunked(50).flatMap { YouTubeApi.originalTitles(token, it).entries }.associate { it.key to it.value } }
                .getOrNull()
            if (api != null) return api
        }
        return ids.chunked(PARALLEL).flatMap { chunk ->
            kotlinx.coroutines.coroutineScope {
                chunk.map { id -> async { YouTubeApi.oembedTitle(id)?.let { id to it } } }.awaitAll().filterNotNull()
            }
        }.toMap()
    }

    private suspend fun restore(context: Context) = lock.withLock {
        if (loaded) return@withLock
        loaded = true
        val saved = runCatching {
            val o = JSONObject(File(context.filesDir, FILE).readText())
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrNull().orEmpty()
        if (saved.isNotEmpty()) _titles.update { saved + it }
    }

    private suspend fun save(context: Context) = lock.withLock {
        runCatching { File(context.filesDir, FILE).writeText(JSONObject(_titles.value).toString()) }
    }
}
