package com.platinum20.dashboard.ui.screens

import kotlin.math.roundToInt

/**
 * Logica pura dei gesti sul video, separata per poterla provare: su/giù a schermo intero
 * regola il volume, in verticale uno swipe su apre lo schermo intero.
 */
object VideoSwipe {
    /** Un dito che percorre tutta l'altezza dello schermo porta il volume da zero al massimo. */
    fun volumeAfter(startLevel: Int, maxLevel: Int, draggedUpPx: Float, heightPx: Float): Int {
        if (maxLevel <= 0 || heightPx <= 0f) return startLevel.coerceAtLeast(0)
        val delta = (draggedUpPx / heightPx * maxLevel).roundToInt()
        return (startLevel + delta).coerceIn(0, maxLevel)
    }

    /** Swipe su sul video in verticale: basta un po' di strada verso l'alto, o uno scatto. */
    fun opensFullscreen(travelledPx: Float, velocityY: Float, thresholdPx: Float): Boolean =
        travelledPx < 0f && (-travelledPx > thresholdPx || velocityY < -1_000f)
}
