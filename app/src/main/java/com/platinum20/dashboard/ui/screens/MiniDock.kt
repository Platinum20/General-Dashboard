package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.media.Playback

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

    /** A destra o a sinistra (i quattro angoli del riquadro, 1.545): a destra all'inizio. */
    var atRight: Boolean = true

    /** Ritirato nella linguetta sul bordo ([MiniTuck]): allora non copre la pagina e non serve spazio in fondo. */
    var tucked by mutableStateOf(false)

    /** Da che lato sta la linguetta: a destra all'inizio, poi dove l'hai lanciato. */
    var tuckRight by mutableStateOf(true)

    /** Dove sta la linguetta lungo il bordo: 0 = in alto, 1 = in basso (la si trascina su e giù, 1.521). */
    var tabFrac by androidx.compose.runtime.mutableFloatStateOf(1f)

    /** Il video sta volando verso il mini player: intanto il mini player resta invisibile. */
    var docking by mutableStateOf(false)

    /**
     * La pagina che si chiude volando resta ferma e visibile per tutto il volo (sopra quella che torna),
     * poi sparisce di colpo: a quel punto di lei si vede solo il video, già dentro il mini player.
     */
    val holdExit: ExitTransition get() = fadeOut(tween(1, delayMillis = FLY_MS + 80))

    /** Il riquadro in dp ([MiniTuck.BOX_W] × [MiniTuck.BOX_H]): il video lo riempie tutto. */
    private const val BAR_GUESS = MiniTuck.BOX_H

    /**
     * Quanto spazio lasciare in fondo, in dp, a una pagina senza barra delle sezioni (editor delle note)
     * perché il mini player in basso non copra i suoi tasti: niente se non suona nulla o se sta in alto.
     */
    fun bottomRoom(playing: Boolean, atTop: Boolean, barHeight: Float, density: Float, tucked: Boolean = false): Float =
        if (!playing || atTop || tucked) 0f else (if (barHeight > 0f) barHeight / density else BAR_GUESS) + 6f

    /** Dove sta il video nel mini player, in px: tutto il riquadro, nell'angolo dove sta davvero. */
    fun thumb(frame: Rect, atTop: Boolean, atRight: Boolean, density: Float): Rect {
        val w = MiniTuck.BOX_W * density
        val h = MiniTuck.BOX_H * density
        val gap = MiniTuck.TOP_GAP * density
        val top = if (atTop) frame.top + minOf(gap, (frame.height - h).coerceAtLeast(0f)) else frame.bottom - h
        val left = if (atRight) frame.right - w else frame.left
        return Rect(left, top, left + w, top + h)
    }

    /**
     * Quanto lasciare libero in fondo a una barra fissa (es. "Controlla e aggiorna") perché il mini
     * player in basso non la copra: la barra stimata più il suo margine, in dp.
     */
    fun bottomReserveDp(playing: Boolean, atTop: Boolean, tucked: Boolean = false): Float = if (playing && !atTop && !tucked) BAR_GUESS + 6f else 0f

    /** La miniatura adesso, o null se il mini player non è mai stato misurato. */
    fun target(density: Float): Rect? = frame?.let { thumb(it, atTop, atRight, density) }

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

/**
 * Lo spazio in fondo per il mini player nelle pagine senza barra delle sezioni (Impostazioni, Meteo…):
 * senza, l'ultima riga resta sotto il mini player anche a fine lista.
 */
@Composable
fun miniPlayerRoom(): Dp {
    val playing by Playback.now.collectAsStateWithLifecycle()
    return MiniDock.bottomRoom(playing != null, MiniDock.atTop, MiniDock.barHeight, LocalDensity.current.density, MiniDock.tucked).dp
}
