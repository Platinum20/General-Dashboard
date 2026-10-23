package com.platinum20.dashboard.news

import com.platinum20.dashboard.net.DiskCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Notizie condivise tra la sezione News e il recap della Dashboard, per non scaricarle due volte.
 * Le ultime scaricate restano su file: all'avvio si vedono subito, poi arrivano quelle nuove.
 */
object NewsStore {
    private const val MAX_AGE_MS = 10 * 60_000L
    private const val CACHE_FILE = "notizie.json"
    /** Oltre due giorni le notizie salvate sono troppo vecchie per mostrarle. */
    private const val MAX_SAVED_AGE_MS = 48 * 3_600_000L

    private val repository = NewsRepository()
    private val mutex = Mutex()
    private val restoreMutex = Mutex()
    private var restored = false
    private val _result = MutableStateFlow<NewsResult?>(null)
    val result: StateFlow<NewsResult?> = _result.asStateFlow()
    @Volatile
    private var fetchedAt = 0L

    /** Rimette in memoria le notizie salvate all'ultimo aggiornamento (una volta sola). */
    suspend fun restore() = restoreMutex.withLock {
        if (restored) return@withLock
        restored = true
        if (_result.value != null) return@withLock
        val saved = DiskCache.read(CACHE_FILE)?.let { runCatching { decode(it) }.getOrNull() } ?: return@withLock
        if (System.currentTimeMillis() - saved.second > MAX_SAVED_AGE_MS || saved.first.isEmpty()) return@withLock
        if (_result.value == null) {
            _result.value = NewsResult(saved.first, emptyList())
            fetchedAt = saved.second
        }
    }

    suspend fun load(force: Boolean = false): NewsResult {
        restore()
        mutex.withLock {
            val cached = _result.value
            if (!force && cached != null && System.currentTimeMillis() - fetchedAt < MAX_AGE_MS) return cached
            val fresh = repository.fetchAll(onProgress = { partial ->
                // Al primo avvio (niente da mostrare) le notizie compaiono man mano che arrivano le fonti.
                if (_result.value == null || fetchedAt == 0L) _result.value = NewsResult(partial, emptyList())
            })
            _result.value = fresh
            fetchedAt = System.currentTimeMillis()
            DiskCache.write(CACHE_FILE, encode(fresh.items, fetchedAt))
            return fresh
        }
    }

    internal fun encode(items: List<NewsItem>, savedAt: Long): String {
        val array = JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("t", item.title)
                    .put("l", item.link)
                    .put("s", item.summary)
                    .put("i", item.imageUrl ?: "")
                    .put("p", item.publishedAt?.toEpochMilli() ?: 0L)
                    .put("f", item.source.id)
            )
        }
        return JSONObject().put("savedAt", savedAt).put("items", array).toString()
    }

    /** Le notizie salvate e quando; quelle di fonti non più nell'elenco vengono scartate. */
    internal fun decode(text: String, sources: List<NewsSource> = NewsSources.all): Pair<List<NewsItem>, Long> {
        val root = JSONObject(text)
        val byId = sources.associateBy { it.id }
        val array = root.getJSONArray("items")
        val items = (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            val source = byId[o.optString("f")] ?: return@mapNotNull null
            NewsItem(
                title = o.getString("t"),
                link = o.getString("l"),
                summary = o.optString("s"),
                imageUrl = o.optString("i").ifBlank { null },
                publishedAt = o.optLong("p").takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
                source = source,
            )
        }
        return items to root.optLong("savedAt")
    }
}
