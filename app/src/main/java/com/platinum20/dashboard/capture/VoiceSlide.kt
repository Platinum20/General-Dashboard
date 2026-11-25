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
}
