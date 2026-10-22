package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un video guardato nell'app: dove eri arrivato e quando. */
data class YtWatched(
    val id: String,
    val title: String,
    val channel: String,
    val channelAvatar: String,
    val positionMs: Long,
    val durationMs: Long,
    val watchedAt: Long,
) {
    /** Quanto ne hai visto, da 0 a 1. */
    val progress: Float get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/**
 * Cronologia dei video guardati nell'app con il punto in cui eri: si riprende da lì e le anteprime
 * mostrano la barra rossa di quanto hai visto, come nell'app di YouTube.
 */
object YtHistory {
    private const val PREFS = "yt_history"
    private const val KEY = "watched"
    private const val MAX = 300

    private var prefs: SharedPreferences? = null
    private val _items = MutableStateFlow<Map<String, YtWatched>>(emptyMap())
    val items: StateFlow<Map<String, YtWatched>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching {
            val array = JSONArray(p.getString(KEY, "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.associate { o ->
                o.getString("id") to YtWatched(
                    o.getString("id"), o.optString("title"), o.optString("channel"), o.optString("avatar"),
                    o.optLong("pos"), o.optLong("dur"), o.optLong("at"),
                )
            }
        }.getOrDefault(emptyMap())
    }

    /** Dal più recente. */
    fun recent(): List<YtWatched> = _items.value.values.sortedByDescending { it.watchedAt }

    /** Punto da cui riprendere: solo se eri a metà (né all'inizio né agli ultimi secondi). */
    fun resumeMs(id: String): Long {
        val item = _items.value[id] ?: return 0L
        return if (item.positionMs > 10_000 && (item.durationMs <= 0 || item.positionMs < item.durationMs - 15_000)) item.positionMs else 0L
    }

    fun save(context: Context, item: YtWatched) {
        init(context)
        // Visto fino alla fine: la barra resta piena ma la prossima volta riparte dall'inizio.
        val done = item.durationMs > 0 && item.positionMs >= item.durationMs - 15_000
        val entry = if (done) item.copy(positionMs = item.durationMs) else item
        _items.value = (_items.value + (item.id to entry)).values
            .sortedByDescending { it.watchedAt }.take(MAX).associateBy { it.id }
        persist()
    }

    fun remove(id: String) {
        _items.value = _items.value - id
        persist()
    }

    /** Svuota tutto e restituisce quello che c'era, per poterlo rimettere con [restore]. */
    fun clear(): Map<String, YtWatched> {
        val previous = _items.value
        _items.value = emptyMap()
        persist()
        return previous
    }

    fun restore(previous: Map<String, YtWatched>) {
        _items.value = previous + _items.value
        persist()
    }

    /** Dati finti per le foto automatiche (solo test). */
    internal fun preview(items: List<YtWatched>) {
        _items.value = items.associateBy { it.id }
    }

    private fun persist() {
        val array = JSONArray()
        _items.value.values.forEach { w ->
            array.put(
                JSONObject().put("id", w.id).put("title", w.title).put("channel", w.channel).put("avatar", w.channelAvatar)
                    .put("pos", w.positionMs).put("dur", w.durationMs).put("at", w.watchedAt)
            )
        }
        prefs?.edit()?.putString(KEY, array.toString())?.apply()
    }
}
