package com.platinum20.dashboard.capture

/**
 * Il dito sul vocale (1.635, Antonio: come Claude): trascinato a sinistra oltre [cancelPx] lasciando si butta,
 * a destra oltre [sendPx] si invia. Pura, provata nei test.
 */
object VoiceSlide {
    /** -1 = butta, 1 = invia, 0 = niente (resta acceso a mani libere). */
    fun armed(dx: Float, cancelPx: Float, sendPx: Float): Int = when {
        dx <= -cancelPx -> -1
        dx >= sendPx -> 1
        else -> 0
    }
}
