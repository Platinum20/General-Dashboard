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
    /** Il canale, per le storie in cima a Media (1.482: chi hai guardato per ultimo); vuoto nei vecchi salvataggi. */
    val channelId: String = "",
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
                    o.optLong("pos"), o.optLong("dur"), o.optLong("at"), o.optString("cid"),
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

    /** Aggiunge quello che hai visto su YouTube (dal PC): vedi [mergedWith]. */
    fun mergeRemote(context: Context, videos: List<YtVideo>) {
        init(context)
        val merged = mergedWith(_items.value, videos, System.currentTimeMillis())
        if (merged == _items.value) return
        _items.value = merged.values.sortedByDescending { it.watchedAt }.take(MAX).associateBy { it.id }
        persist()
    }

    /**
     * La cronologia di Canvas più quella di YouTube ([remote], dal più recente): i video nuovi entrano
     * con quanto ne hai visto, a quelli già presenti si aggiorna la barra solo se su YouTube sei andato
     * più avanti. Funzione pura, provata nei test.
     */
    internal fun mergedWith(local: Map<String, YtWatched>, remote: List<YtVideo>, now: Long): Map<String, YtWatched> {
        val out = LinkedHashMap(local)
        remote.forEachIndexed { index, v ->
            if (v.isShort || v.live) return@forEachIndexed
            val known = out[v.id]
            val durationMs = known?.durationMs?.takeIf { it > 0 } ?: (v.durationSec * 1000L)
            val positionMs = if (durationMs > 0) durationMs * v.watchedPercent.coerceIn(0, 100) / 100 else 0L
            if (known == null) {
                out[v.id] = YtWatched(v.id, v.title, v.channel, v.channelAvatar, positionMs, durationMs, now - index * 1_000L, v.channelId)
            } else if (positionMs > known.positionMs) {
                out[v.id] = known.copy(positionMs = positionMs, durationMs = durationMs)
            }
        }
        return out
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
        val p = prefs ?: return
        val array = JSONArray()
        _items.value.values.forEach { w ->
            array.put(
                JSONObject().put("id", w.id).put("title", w.title).put("channel", w.channel).put("avatar", w.channelAvatar)
                    .put("pos", w.positionMs).put("dur", w.durationMs).put("at", w.watchedAt).put("cid", w.channelId)
            )
        }
        p.edit().putString(KEY, array.toString()).apply()
    }
}
