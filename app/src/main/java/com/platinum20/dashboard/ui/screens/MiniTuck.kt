package com.platinum20.dashboard.ui.screens

/**
 * Le regole del mini player a linguetta (1.508, Antonio): lanciato a destra o a sinistra si ritira (solo a mano:
 * mai da solo scorrendo, 1.521) in una linguetta tonda sul bordo, così non copre la pagina; si chiude solo
 * portandolo giù, sotto lo schermo, dove una calamita lo prende con una vibrazione. Pure, provate nei test.
 */
object MiniTuck {
    /** Oltre quanti dp sotto il suo posto in basso la calamita lo prende (e lasciandolo si chiude). */
    const val MAGNET_DP = 72f

    /** Quanto scatta giù la calamita quando lo prende, in dp: si sente che è "attaccato". */
    const val PULL_DP = 28f

    enum class Release { CLOSE, TUCK_LEFT, TUCK_RIGHT, STAY }

    /** Sotto questa soglia (dp) la calamita non scende mai, anche con poco spazio: un piccolo spostamento non chiude. */
    const val MIN_MAGNET_DP = 24f

    /**
     * Quanto va portato giù perché la calamita lo prenda: [MAGNET_DP], ma senza barra delle sezioni sotto il dito
     * arriva presto al bordo dello schermo, quindi al massimo metà dello spazio che ha davanti (1.521: prima
     * nelle pagine senza barra non si chiudeva). [roomPx] = dal centro della barra al fondo dello schermo.
     */
    fun threshold(roomPx: Float, density: Float): Float =
        minOf(MAGNET_DP * density, maxOf(MIN_MAGNET_DP * density, roomPx * 0.5f))

    /** La calamita lo tiene: è sceso di almeno [thresholdPx] sotto il suo posto in basso. */
    fun magnet(belowPx: Float, thresholdPx: Float): Boolean = belowPx > thresholdPx

    /**
     * Cosa succede lasciandolo: preso dalla calamita = si chiude; portato oltre metà di lato o lanciato
     * di lato (la stessa regola di [SwipeAway]) = linguetta da quel lato; altrimenti torna in alto o in basso.
     */
    fun release(magnet: Boolean, dx: Float, vx: Float, vy: Float, width: Float): Release = when {
        magnet -> Release.CLOSE
        SwipeAway.closes(dx, vx, vy, width) -> if (dx >= 0f) Release.TUCK_RIGHT else Release.TUCK_LEFT
        else -> Release.STAY
    }

    /**
     * Su quale bordo va la linguetta lasciandola (1.521): lanciata forte va dal lato del lancio,
     * altrimenti dal lato della metà dello schermo dove l'hai lasciata.
     */
    fun tabSide(centerX: Float, vx: Float, width: Float): Boolean = when {
        vx > 1_500f -> true
        vx < -1_500f -> false
        else -> centerX > width / 2f
    }

    /**
     * Dove si aggancia la linguetta lungo il bordo: solo in alto (0) o in basso (1), come la barra (1.522).
     * Uno scatto su o giù decide; altrimenti la metà dello schermo dove l'hai lasciata.
     */
    fun tabCorner(frac: Float, vFracPerSec: Float): Float = when {
        vFracPerSec < -0.6f -> 0f
        vFracPerSec > 0.6f -> 1f
        frac < 0.5f -> 0f
        else -> 1f
    }
}
