package com.platinum20.dashboard.ui.screens

import android.content.Context

/**
 * I gesti di Canvas non hanno pulsanti (trascina, porta di lato): ognuno si spiega una volta sola, la
 * prime volte che serve, con una riga piccola. Poi non ricompare più.
 */
object GestureHints {
    const val SWIPE_BUBBLE = "swipe_bubble"
    /** Il "quando" delle schede si tocca per cambiarlo (punto 26). */
    const val DETAIL_WHEN = "detail_when"

    private fun prefs(context: Context) = context.getSharedPreferences("gesture_hints", Context.MODE_PRIVATE)

    /** Quante volte si ripete un suggerimento (1.402: il fumetto lo diceva una volta sola, troppo poco per impararlo). */
    const val TIMES = 3

    fun seen(context: Context, hint: String): Boolean = !stillShow(context, hint, times = 1)

    /** Vero finché il suggerimento è comparso meno di [times] volte (il vecchio "visto" conta come tutte). */
    fun stillShow(context: Context, hint: String, times: Int = TIMES): Boolean =
        shouldShow(prefs(context).getInt("$hint.n", 0), prefs(context).getBoolean(hint, false), times)

    fun shouldShow(count: Int, legacySeen: Boolean, times: Int = TIMES): Boolean = !legacySeen && count < times

    fun mark(context: Context, hint: String) {
        val p = prefs(context)
        p.edit().putInt("$hint.n", p.getInt("$hint.n", 0) + 1).apply()
    }
}
