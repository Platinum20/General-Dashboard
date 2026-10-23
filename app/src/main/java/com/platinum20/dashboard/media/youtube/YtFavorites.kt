package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * I "Mi piace" messi dal player dell'app: restano sul telefono (il collegamento con YouTube è in
 * sola lettura) e compaiono in cima alla scheda Mi piace, insieme a quelli del tuo account.
 */
object YtFavorites {
    private const val PREFS = "yt_favorites"
    private const val KEY = "liked"
    private const val MAX = 500

    private var prefs: SharedPreferences? = null
    private val _items = MutableStateFlow<List<YtVideo>>(emptyList())
    /** Dal più recente. */
    val items: StateFlow<List<YtVideo>> = _items.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _items.value = runCatching {
            val array = JSONArray(p.getString(KEY, "[]"))
            (0 until array.length()).map { array.getJSONObject(it) }.map { o ->
                YtVideo(
                    id = o.getString("id"), title = o.optString("title"), channel = o.optString("channel"),
                    channelId = "", published = null, durationSec = o.optInt("dur"), channelAvatar = o.optString("avatar"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun isLiked(id: String): Boolean = _items.value.any { it.id == id }

    /** Mette o toglie il Mi piace; restituisce true se ora piace. */
    fun toggle(context: Context, video: YtVideo): Boolean {
        init(context)
        val liked = !isLiked(video.id)
        _items.value = toggled(_items.value, video).take(MAX)
        persist()
        return liked
    }

    /** La lista dopo il tocco su Mi piace: funzione pura, provata nei test. */
    internal fun toggled(list: List<YtVideo>, video: YtVideo): List<YtVideo> =
        if (list.any { it.id == video.id }) list.filterNot { it.id == video.id } else listOf(video) + list

    private fun persist() {
        val p = prefs ?: return
        val array = JSONArray()
        _items.value.forEach {
            array.put(
                JSONObject().put("id", it.id).put("title", it.title).put("channel", it.channel)
                    .put("dur", it.durationSec).put("avatar", it.channelAvatar)
            )
        }
        p.edit().putString(KEY, array.toString()).apply()
    }
}
