package com.platinum20.dashboard.media.youtube

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non mi piace e iscrizioni messi dal player: come il cuore dei Mi piace, vanno subito anche sul tuo
 * YouTube tramite [YtSync]. YouTube non dà la lista dei Non mi piace, quindi qui si ricordano quelli messi in Canvas.
 */
object YtRatings {
    private const val PREFS = "yt_ratings"
    private const val KEY_DISLIKES = "dislikes"
    private const val MAX = 500

    private var prefs: SharedPreferences? = null
    private val _dislikes = MutableStateFlow<List<String>>(emptyList())
    /** Dal più recente. */
    val dislikes: StateFlow<List<String>> = _dislikes.asStateFlow()

    private val _subscribed = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** Iscrizioni cambiate ora dal player, finché YouTube non rimanda la lista dei canali. */
    val subscribed: StateFlow<Map<String, Boolean>> = _subscribed.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _dislikes.value = p.getString(KEY_DISLIKES, "").orEmpty().split(',').filter { it.isNotBlank() }
    }

    fun isDisliked(id: String): Boolean = id in _dislikes.value

    /** Mette o toglie il Non mi piace; restituisce true se ora c'è. Su YouTube il Non mi piace toglie il Mi piace. */
    fun toggleDislike(context: Context, id: String): Boolean {
        init(context)
        val disliked = !isDisliked(id)
        set(toggled(_dislikes.value, id))
        if (disliked && YtFavorites.isLiked(id)) YtFavorites.replace(context, YtFavorites.items.value.filterNot { it.id == id })
        YtSync.dislike(context, id, disliked)
        return disliked
    }

    /** Messo il Mi piace: il Non mi piace sparisce, come su YouTube. */
    internal fun clearDislike(context: Context, id: String) {
        init(context)
        if (isDisliked(id)) set(_dislikes.value - id)
    }

    /**
     * Iscrive o disiscrive dal canale; false se non hai fatto l'accesso a YouTube nell'app.
     * Lo stato cambia subito e torna indietro se YouTube rifiuta ([onFail]).
     */
    fun toggleSubscribe(context: Context, channelId: String, now: Boolean, onFail: () -> Unit): Boolean {
        val next = !now
        if (!YtSync.subscribe(context, channelId, next) { ok ->
                if (!ok) {
                    _subscribed.value = _subscribed.value - channelId
                    onFail()
                }
            }
        ) return false
        _subscribed.value = _subscribed.value + (channelId to next)
        return true
    }

    /** Iscritto o no: prima quello toccato ora, poi la lista dei canali di YouTube. Funzione pura, provata nei test. */
    internal fun isSubscribed(id: String, changed: Map<String, Boolean>, channels: List<YtChannel>?): Boolean =
        changed[id] ?: channels.orEmpty().any { it.id == id }

    /** La lista dopo il tocco su Non mi piace: funzione pura, provata nei test. */
    internal fun toggled(list: List<String>, id: String): List<String> =
        if (id in list) list - id else listOf(id) + list

    private fun set(list: List<String>) {
        _dislikes.value = list.take(MAX)
        prefs?.edit()?.putString(KEY_DISLIKES, _dislikes.value.joinToString(","))?.apply()
    }
}
