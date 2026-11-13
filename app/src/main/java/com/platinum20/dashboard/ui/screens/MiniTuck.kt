package com.platinum20.dashboard.ui.screens

/**
 * Le regole del mini player a linguetta (1.503, Antonio): lanciato a destra o a sinistra (o appena una
 * pagina scorre) si ritira in una linguetta tonda sul bordo, così non copre la pagina; si chiude solo
 * portandolo giù, sotto lo schermo, dove una calamita lo prende con una vibrazione. Pure, provate nei test.
 */
object MiniTuck {
    /** Oltre quanti dp sotto il suo posto in basso la calamita lo prende (e lasciandolo si chiude). */
    const val MAGNET_DP = 72f

    /** Quanto scatta giù la calamita quando lo prende, in dp: si sente che è "attaccato". */
    const val PULL_DP = 28f

    /** Scorrendo una pagina verso il basso di almeno tanto (dp) il mini player si ritira da solo. */
    const val SCROLL_DP = 48f

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

    /** Scorrimento della pagina, accumulato verso il basso: tornando su si riparte da zero. */
    fun scrolled(total: Float, delta: Float): Float = if (delta < 0f) total - delta else 0f

    fun scrollTucks(scrolledPx: Float, density: Float): Boolean = scrolledPx > SCROLL_DP * density

    /** La linguetta tirata verso il centro dello schermo (a destra si tira a sinistra e viceversa). */
    fun pulledOut(dx: Float, right: Boolean, density: Float): Boolean =
        (if (right) -dx else dx) > PULL_OUT_DP * density
}

/**
 * Scorrendo una pagina verso il basso il mini player si ritira nella linguetta, così non copre quello
 * che stai leggendo. Guarda soltanto: non consuma niente dello scorrimento.
 */
@androidx.compose.runtime.Composable
fun rememberMiniTuckOnScroll(): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    return androidx.compose.runtime.remember(density) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var total = 0f
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || consumed.y == 0f) return androidx.compose.ui.geometry.Offset.Zero
                total = MiniTuck.scrolled(total, consumed.y)
                if (!MiniDock.tucked && com.platinum20.dashboard.media.Playback.now.value != null && MiniTuck.scrollTucks(total, density)) {
                    total = 0f
                    MiniDock.tucked = true
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
}
