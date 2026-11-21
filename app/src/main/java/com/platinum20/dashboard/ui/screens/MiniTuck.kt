package com.platinum20.dashboard.ui.screens

/**
 * Le regole del mini player (1.508, Antonio; riquadro dal 1.546): un riquadro con video o copertina e i tasti
 * dentro, agganciato a uno dei quattro angoli; lanciato di lato (solo a mano) si ritira in una linguetta alta
 * quanto lui sul bordo, che si tira fuori con una vibrazione; si chiude solo portandolo giù, sotto lo schermo,
 * dove una calamita lo prende con una vibrazione. Pure, provate nei test.
 */
object MiniTuck {
    /**
     * La riga sotto il titolo nel riquadro: solo il sottotitolo, perché la fonte la dice già l'icona della copertina
     * ("Radio · Collegamento…" finiva coi puntini nei 200 dp del riquadro). Senza sottotitolo, la fonte.
     */
    fun line(source: String, subtitle: String): String = subtitle.ifBlank { source }

    /** Oltre quanti dp sotto il suo posto in basso la calamita lo prende (e lasciandolo si chiude). */
    const val MAGNET_DP = 72f

    /** Quanto scatta giù la calamita quando lo prende, in dp: si sente che è "attaccato". */
    const val PULL_DP = 28f

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

    /** Il riquadro del mini player (1.546, Antonio): circa il doppio del video di prima (84 × 48), 16:9. */
    const val BOX_W = 200f
    const val BOX_H = 113f

    /** In alto il riquadro sta sotto il titolo grande delle pagine, non sopra (dp dal bordo alto). */
    const val TOP_GAP = 96f

    /** La linguetta: sottile, alta quanto il riquadro, attaccata al bordo dello schermo (dp). */
    const val TAB_W = 24f

    /** Quanto si tira la linguetta verso il centro perché il riquadro esca (con la vibrazione), in dp. */
    const val PULL_OUT_DP = 40f

    /** Dopo quanto spariscono i tasti sopra il video. */
    const val CONTROLS_MS = 3_000L

    /** Un lancio di lato: oltre questa velocità (px/s) conta la direzione del dito, non dove l'hai lasciato. */
    const val FLING = 1_500f

    /** Dove va il riquadro lasciandolo: chiuso, nella linguetta da un lato, o nell'angolo a sinistra/destra. */
    enum class Drop { CLOSE, TUCK_LEFT, TUCK_RIGHT, LEFT, RIGHT }

    /** Portato oltre il bordo per più di un terzo: -1 a sinistra, 1 a destra, 0 dentro. [left] = bordo sinistro in px. */
    fun offEdge(left: Float, boxW: Float, maxW: Float): Int = when {
        left < -boxW * 0.35f -> -1
        left + boxW > maxW + boxW * 0.35f -> 1
        else -> 0
    }

    /** Quanto del riquadro è fuori dallo schermo (0..1): sbiadisce man mano. */
    fun offscreen(left: Float, boxW: Float, maxW: Float): Float =
        (maxOf(-left, left + boxW - maxW).coerceAtLeast(0f) / boxW.coerceAtLeast(1f)).coerceIn(0f, 1f)

    /**
     * Cosa succede lasciandolo (1.546): preso dalla calamita = si chiude; portato oltre il bordo = linguetta da
     * quel lato; lanciato verso il bordo del suo angolo ([fromRight] = da dove è partito) = linguetta, lanciato
     * verso l'altro lato = angolo di là; altrimenti l'angolo della metà dove l'hai lasciato. In alto o in basso
     * lo decide la parte verticale.
     */
    fun drop(magnet: Boolean, left: Float, boxW: Float, maxW: Float, vx: Float, fromRight: Boolean): Drop {
        val center = left + boxW / 2f
        val rightHalf = center > maxW / 2f
        return when {
            magnet -> Drop.CLOSE
            offEdge(left, boxW, maxW) > 0 -> Drop.TUCK_RIGHT
            offEdge(left, boxW, maxW) < 0 -> Drop.TUCK_LEFT
            vx > FLING -> if (fromRight) Drop.TUCK_RIGHT else Drop.RIGHT
            vx < -FLING -> if (!fromRight) Drop.TUCK_LEFT else Drop.LEFT
            rightHalf -> Drop.RIGHT
            else -> Drop.LEFT
        }
    }

    /** La linguetta tirata verso il centro di almeno [PULL_OUT_DP]: il riquadro esce. */
    fun pulledOut(inwardPx: Float, density: Float): Boolean = inwardPx > PULL_OUT_DP * density

    /** I tasti restano sempre in vista con musica e radio (sotto non c'è niente da guardare) e col video in pausa. */
    fun controlsAlways(isVideo: Boolean, playing: Boolean): Boolean = !isVideo || !playing

    enum class Tap { SHOW_CONTROLS, EXPAND }

    /** Un tocco sul riquadro: sul video senza tasti li fa comparire, altrimenti apre il player intero. */
    fun tap(isVideo: Boolean, controlsShown: Boolean): Tap =
        if (isVideo && !controlsShown) Tap.SHOW_CONTROLS else Tap.EXPAND

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
