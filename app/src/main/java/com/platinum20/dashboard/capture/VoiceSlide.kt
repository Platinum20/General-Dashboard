package com.platinum20.dashboard.capture

/**
 * Il dito sulla freccia mentre si detta (1.636, Antonio: come Claude): a sinistra oltre la soglia lasciando si butta
 * il vocale, in su oltre la soglia si blocca a mani libere. Pura, provata nei test.
 */
object VoiceSlide {
    /** Trascinato abbastanza a sinistra: lasciando si butta. */
    fun cancels(dx: Float, cancelPx: Float): Boolean = dx <= -cancelPx

    /** Tirato abbastanza in su: resta acceso senza dito. */
    fun locks(dy: Float, lockPx: Float): Boolean = dy <= -lockPx

    /**
     * Le tre ‹ accanto alla ✕ (1.637): si accendono una dopo l'altra da destra a sinistra, come "scorri per annullare".
     * [i] = 0 la più a sinistra; [phase] da 0 a 1 gira di continuo.
     */
    fun chevronAlpha(i: Int, phase: Float): Float {
        val step = (phase * 3).toInt().coerceIn(0, 2)
        return if (i == 2 - step) 1f else 0.3f
    }

    /** Dove comincia l'onda (da destra, [count] barrette): a sinistra di lì la scritta si vede ancora. */
    fun waveStart(width: Float, count: Int, barPx: Float, gapPx: Float): Float =
        if (count <= 0) width else (width - count * (barPx + gapPx) + gapPx).coerceIn(0f, width)
}
