package com.platinum20.dashboard.ui.screens

/**
 * Le regole del mini player a linguetta (1.508, Antonio): lanciato a destra o a sinistra si ritira (solo a mano:
 * mai da solo scorrendo, 1.518) in una linguetta tonda sul bordo, così non copre la pagina; si chiude solo
 * portandolo giù, sotto lo schermo, dove una calamita lo prende con una vibrazione. Pure, provate nei test.
 */
object MiniTuck {
    /** Oltre quanti dp sotto il suo posto in basso la calamita lo prende (e lasciandolo si chiude). */
    const val MAGNET_DP = 72f

    /** Quanto scatta giù la calamita quando lo prende, in dp: si sente che è "attaccato". */
    const val PULL_DP = 28f

    /** Tirando la linguetta verso il centro di almeno tanto (dp) il mini player si riapre. */
    const val PULL_OUT_DP = 24f

    enum class Release { CLOSE, TUCK_LEFT, TUCK_RIGHT, STAY }

    /** La calamita lo tiene: è sceso di almeno [MAGNET_DP] sotto il suo posto in basso. */
    fun magnet(belowPx: Float, density: Float): Boolean = belowPx > MAGNET_DP * density

    /**
     * Cosa succede lasciandolo: preso dalla calamita = si chiude; portato oltre metà di lato o lanciato
     * di lato (la stessa regola di [SwipeAway]) = linguetta da quel lato; altrimenti torna in alto o in basso.
     */
    fun release(magnet: Boolean, dx: Float, vx: Float, vy: Float, width: Float): Release = when {
        magnet -> Release.CLOSE
        SwipeAway.closes(dx, vx, vy, width) -> if (dx >= 0f) Release.TUCK_RIGHT else Release.TUCK_LEFT
        else -> Release.STAY
    }

    /** La linguetta tirata verso il centro dello schermo (a destra si tira a sinistra e viceversa). */
    fun pulledOut(dx: Float, right: Boolean, density: Float): Boolean =
        (if (right) -dx else dx) > PULL_OUT_DP * density
}
