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

    /** Accanto alla ✕/graffetta, sempre (1.637): l'onda che entra da destra la copre quando si è parlato abbastanza. */
    const val HELD_TIP = "↑ blocca  ·  ← annulla"
    const val LOCKED_TIP = "↑ nel testo  ·  ✕ annulla"

    /** Dove comincia l'onda (da destra, [count] barrette): a sinistra di lì la scritta si vede ancora. */
    fun waveStart(width: Float, count: Int, barPx: Float, gapPx: Float): Float =
        if (count <= 0) width else (width - count * (barPx + gapPx) + gapPx).coerceIn(0f, width)
}
