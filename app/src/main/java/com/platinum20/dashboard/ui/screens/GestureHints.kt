package com.platinum20.dashboard.ui.screens

import android.content.Context

/**
 * I gesti di Canvas non hanno pulsanti (trascina, porta di lato): ognuno si spiega una volta sola, la
 * prima volta che serve, con una riga piccola. Poi non ricompare più.
 */
object GestureHints {
    const val SWIPE_BUBBLE = "swipe_bubble"
    /** Il "quando" delle schede si tocca per cambiarlo (punto 26). */
    const val DETAIL_WHEN = "detail_when"

    private fun prefs(context: Context) = context.getSharedPreferences("gesture_hints", Context.MODE_PRIVATE)

    fun seen(context: Context, hint: String): Boolean = prefs(context).getBoolean(hint, false)

    fun mark(context: Context, hint: String) {
        prefs(context).edit().putBoolean(hint, true).apply()
    }
}
