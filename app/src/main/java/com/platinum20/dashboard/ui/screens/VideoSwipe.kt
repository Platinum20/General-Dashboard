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

/**
 * Tasti del volume col video a schermo intero: li prende l'Activity, cambia il volume senza
 * la barra di sistema e avvisa il player, che mostra la sua barra a sinistra.
 */
object FullscreenVolume {
    /** Vero solo mentre un video di YouTube è a schermo intero. */
    @Volatile
    var active = false

    private val _changes = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val changes: kotlinx.coroutines.flow.SharedFlow<Unit> = _changes

    /** true = tasto consumato (volume su/giù a schermo intero), false = lo gestisce il sistema. */
    fun onKey(context: android.content.Context, keyCode: Int): Boolean {
        if (!active) return false
        val direction = when (keyCode) {
            android.view.KeyEvent.KEYCODE_VOLUME_UP -> android.media.AudioManager.ADJUST_RAISE
            android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> android.media.AudioManager.ADJUST_LOWER
            else -> return false
        }
        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, 0)
        _changes.tryEmit(Unit)
        return true
    }

    fun isVolumeKey(keyCode: Int) =
        keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
}
