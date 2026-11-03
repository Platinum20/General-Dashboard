package com.platinum20.dashboard.ui.screens

import android.os.SystemClock
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.util.WeakHashMap
import kotlinx.coroutines.delay

/**
 * Il movimento di Canvas, in un posto solo: le stesse curve e gli stessi tempi ovunque,
 * così ogni pagina si muove allo stesso modo. Curve "enfatizzate" di Material 3:
 * partono svelte e si posano piano, niente rimbalzi sulle pagine intere.
 */
object Motion {
    /** Entrata: parte veloce, arriva morbida. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Uscita: si stacca piano, poi va via svelta. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Movimenti che vanno e vengono (pannelli, indicatori). */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val ENTER_MS = 340
    const val EXIT_MS = 160

    /** Fra le sezioni della barra: più svelto di un dettaglio, così non si somma alla cascata delle card. */
    const val TAB_MS = 280

    /** Di quanto scivola una pagina: un accenno, non tutta la larghezza. */
    private const val SHIFT = 10

    /**
     * Tra le sezioni della barra la pagina nuova arriva dal lato della sua scheda
     * (Note sta a destra di Home: entra da destra), quella vecchia scivola dall'altra parte.
     */
    fun tabEnter(forward: Boolean): EnterTransition =
        slideInHorizontally(tween(TAB_MS, easing = Decelerate)) { w -> (if (forward) w else -w) / SHIFT } +
            fadeIn(tween(TAB_MS - 60, delayMillis = 40, easing = Decelerate))

    fun tabExit(forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(TAB_MS, easing = Decelerate)) { w -> (if (forward) -w else w) / SHIFT } +
            fadeOut(tween(EXIT_MS - 20, easing = Accelerate))

    /** Pagina di dettaglio: arriva da destra e si ingrandisce appena; sotto, la sezione arretra. */
    val pushEnter: EnterTransition =
        slideInHorizontally(tween(ENTER_MS, easing = Decelerate)) { it / SHIFT } +
            fadeIn(tween(ENTER_MS - 80, delayMillis = 40, easing = Decelerate)) +
            scaleIn(tween(ENTER_MS, easing = Decelerate), initialScale = 0.98f)

    val pushExit: ExitTransition =
        slideOutHorizontally(tween(ENTER_MS, easing = Decelerate)) { -it / (SHIFT * 2) } +
            fadeOut(tween(EXIT_MS + 60, easing = Accelerate)) +
            scaleOut(tween(ENTER_MS, easing = Decelerate), targetScale = 0.98f)

    /** Indietro: lo stesso movimento al contrario, così si capisce da dove si torna (anche col gesto). */
    val popEnter: EnterTransition =
        slideInHorizontally(tween(ENTER_MS, easing = Decelerate)) { -it / (SHIFT * 2) } +
            fadeIn(tween(ENTER_MS - 80, delayMillis = 40, easing = Decelerate)) +
            scaleIn(tween(ENTER_MS, easing = Decelerate), initialScale = 0.98f)

    val popExit: ExitTransition =
        slideOutHorizontally(tween(ENTER_MS, easing = Decelerate)) { it / SHIFT } +
            fadeOut(tween(EXIT_MS + 60, easing = Accelerate)) +
            scaleOut(tween(ENTER_MS, easing = Decelerate), targetScale = 0.98f)

    /*
     * Le tre molle di Canvas. Tutto quello che si muove usa una di queste (la pillola della barra
     * resta com'era nella 1.140, a parte).
     */

    /** Posa: pagine, contenuti, righe che cambiano posto. Arriva e si ferma, senza rimbalzo. */
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Viva: pressioni, comparse di fumetti e pallini. Un accenno di vita, mai un'oscillazione. */
    fun <T> lively() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    /**
     * Gesto: quello che il dito lascia andare (mini player, fumetti, player). Si usa con la velocità
     * del lancio, così un lancio veloce esce veloce e uno lento esce piano.
     */
    fun <T> gesture() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /**
     * Quanto dura l'uscita di una cosa lanciata col dito: lo spazio che manca diviso la velocità del
     * lancio, così continua alla stessa andatura del dito. Fra 120 e 280 ms: mai di colpo, mai lenta.
     */
    fun flingMs(distancePx: Float, velocityPxPerSec: Float): Int {
        val v = kotlin.math.abs(velocityPxPerSec)
        if (v < 1f) return FLING_MAX_MS
        return (kotlin.math.abs(distancePx) / v * 1000f).toInt().coerceIn(FLING_MIN_MS, FLING_MAX_MS)
    }

    /** L'uscita di un lancio: [flingMs] con una curva che parte alla velocità del dito e non frena. */
    fun <T> fling(distancePx: Float, velocityPxPerSec: Float) =
        tween<T>(flingMs(distancePx, velocityPxPerSec), easing = androidx.compose.animation.core.LinearOutSlowInEasing)

    const val FLING_MIN_MS = 120
    const val FLING_MAX_MS = 280
}

/**
 * Ingresso a cascata: appena si apre una pagina le card salgono e compaiono una dopo l'altra,
 * dall'alto in basso. Solo nei primi istanti della pagina: scorrendo, le card che arrivano
 * sono già lì, senza animazioni che rallentano. Il turno di ognuna è l'ordine in cui compare.
 */
fun Modifier.entrance(): Modifier = composed {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val turn = remember(owner) { Entrance.nextTurn(owner) }
    val progress = remember { Animatable(if (turn == null) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (turn == null) return@LaunchedEffect
        delay(turn * Entrance.STAGGER_MS)
        progress.animateTo(1f, tween(Entrance.DURATION_MS, easing = Motion.Decelerate))
    }
    // Un accenno: la pagina scivola già di lato, le card salgono appena (prima 18 dp, due movimenti forti insieme).
    val rise = with(LocalDensity.current) { 10.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise
    }
}

internal object Entrance {
    const val WINDOW_MS = 450L
    const val STAGGER_MS = 35L
    const val DURATION_MS = 300
    private const val MAX_TURNS = 7

    private class Page(val start: Long) { var count = 0 }

    // Una pagina per ogni voce della navigazione (o per l'activity fuori dal NavHost).
    private val pages = WeakHashMap<Any, Page>()

    /** Il turno di chi compare adesso, oppure null se la pagina è aperta da un po' (niente animazione). */
    fun nextTurn(owner: Any): Int? = synchronized(pages) {
        val now = SystemClock.uptimeMillis()
        val page = pages.getOrPut(owner) { Page(now) }
        if (now - page.start > WINDOW_MS) return null
        (page.count++).coerceAtMost(MAX_TURNS)
    }
}
