package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp

/**
 * Il passaggio dal video grande al mini player: chiudendo il player il video rimpicciolisce e vola
 * esattamente sulla miniatura del mini player (in alto o in basso, dove sta davvero), poi il mini
 * player lo prende in mano senza comparire di colpo. Qui la geometria (pura, provata nei test)
 * e il poco stato condiviso tra la pagina del video e il mini player.
 */
object MiniDock {
    /** Quanto dura il volo verso il mini player. */
    const val FLY_MS = Motion.ENTER_MS

    /** Lo spazio del mini player sullo schermo (coordinate della finestra), misurato anche quando è nascosto. */
    var frame: Rect? = null

    /** Altezza della barra del mini player, appena la si è vista; prima una stima. */
    var barHeight: Float = 0f

    /** In alto o in basso: resta dove l'hai lasciato anche dopo aver aperto il player grande. */
    var atTop: Boolean = false

    /** Il video sta volando verso il mini player: intanto il mini player resta invisibile. */
    var docking by mutableStateOf(false)

    /**
     * La pagina che si chiude volando resta ferma e visibile per tutto il volo (sopra quella che torna),
     * poi sparisce di colpo: a quel punto di lei si vede solo il video, già dentro il mini player.
     */
    val holdExit: ExitTransition get() = fadeOut(tween(1, delayMillis = FLY_MS + 80))

    /** Le misure della barra in dp, come in [FloatingPlayer]: margine 8, video 84×48, barra stimata 70. */
    private const val THUMB_W = 84f
    private const val THUMB_H = 48f
    private const val THUMB_INSET = 8f
    private const val BAR_GUESS = 70f

    /** Dove sta la miniatura del video nel mini player, in px. */
    fun thumb(frame: Rect, barHeight: Float, atTop: Boolean, density: Float): Rect {
        val bar = if (barHeight > 0f) barHeight else BAR_GUESS * density
        val top = if (atTop) frame.top else frame.bottom - bar
        val left = frame.left + THUMB_INSET * density
        val y = top + THUMB_INSET * density
        return Rect(left, y, left + THUMB_W * density, y + THUMB_H * density)
    }

    /** La miniatura adesso, o null se il mini player non è mai stato misurato. */
    fun target(density: Float): Rect? = frame?.let { thumb(it, barHeight, atTop, density) }

    /**
     * Il video mentre lo trascini giù: segue il dito in verticale e intanto si stringe verso la misura
     * (e il lato) della miniatura, tutto rimpicciolito a metà schermo di corsa.
     */
    fun dragged(start: Rect, target: Rect, dragged: Float, screenHeight: Float): Rect {
        val p = shrink(dragged, screenHeight)
        val width = start.width + (target.width - start.width) * p
        val height = start.height + (target.height - start.height) * p
        val left = start.left + (target.left - start.left) * p
        val top = start.top + dragged.coerceAtLeast(0f)
        return Rect(left, top, left + width, top + height)
    }

    /** Quanto è già rimpicciolito (0..1) dopo [dragged] px di trascinamento. */
    fun shrink(dragged: Float, screenHeight: Float): Float =
        (dragged / (screenHeight * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)

    /** Il volo: da dove l'hai lasciato alla miniatura. */
    fun fly(from: Rect, to: Rect, t: Float): Rect = lerp(from, to, t.coerceIn(0f, 1f))

    /** Scala e spostamento (origine in alto a sinistra) che portano [start] a stare in [now]. */
    fun layer(start: Rect, now: Rect): Layer {
        if (start.width <= 0f || start.height <= 0f) return Layer(1f, 1f, 0f, 0f)
        return Layer(now.width / start.width, now.height / start.height, now.left - start.left, now.top - start.top)
    }

    data class Layer(val scaleX: Float, val scaleY: Float, val dx: Float, val dy: Float)
}
