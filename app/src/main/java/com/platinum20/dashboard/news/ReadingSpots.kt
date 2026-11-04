package com.platinum20.dashboard.news

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Dove eri arrivato in un articolo: uscendo a metà, la volta dopo il lettore riparte da lì.
 * Solo gli ultimi [MAX] articoli; finito o appena iniziato non si ricorda niente.
 */
object ReadingSpots {
    private const val PREFS = "reading_spots"
    private const val KEY = "spots"
    const val MAX = 60

    private var prefs: SharedPreferences? = null
    private var spots = LinkedHashMap<String, Int>()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        spots = decode(prefs?.getString(KEY, null))
    }

    fun get(url: String): Int? = spots[url]

    /** [scroll] e [max] in pixel: si salva solo se sei davvero a metà. */
    fun put(url: String, scroll: Int, max: Int) {
        spots = remember(spots, url, scroll, max)
        prefs?.edit()?.putString(KEY, encode(spots))?.apply()
    }

    /** Vero se a questo punto vale la pena di riprendere: dopo il primo 5% e prima della fine. */
    internal fun worthKeeping(scroll: Int, max: Int): Boolean =
        max > 0 && scroll > max * 0.05f && scroll < max * 0.92f

    internal fun remember(current: Map<String, Int>, url: String, scroll: Int, max: Int): LinkedHashMap<String, Int> {
        val next = LinkedHashMap(current)
        next.remove(url)
        if (worthKeeping(scroll, max)) next[url] = scroll
        while (next.size > MAX) next.remove(next.keys.first())
        return next
    }

    internal fun encode(map: Map<String, Int>): String = JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()

    internal fun decode(text: String?): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        if (text.isNullOrBlank()) return out
        runCatching {
            val o = JSONObject(text)
            o.keys().forEach { k -> out[k] = o.getInt(k) }
        }
        return out
    }
}
