package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * La regola unica del "portalo di lato per chiudere", la stessa del mini player: si chiude
 * lasciandolo oltre metà larghezza, o lanciandolo forte di lato quando è già ben avviato.
 */
internal object SwipeAway {
    const val CLOSE_AT = 0.5f

    /** Oltre questa distanza lasciando si chiude (la X diventa rossa). */
    fun armed(dx: Float, width: Float): Boolean = abs(dx) > width * CLOSE_AT

    /** Lancio deciso di lato, nella stessa direzione in cui si sta già spostando. */
    fun fling(dx: Float, vx: Float, vy: Float, width: Float): Boolean =
        abs(vx) > 2_500f && abs(vx) > 1.5f * abs(vy) && sign(vx) == sign(dx) && abs(dx) > width * 0.2f

    fun closes(dx: Float, vx: Float, vy: Float, width: Float): Boolean = armed(dx, width) || fling(dx, vx, vy, width)
}

/** Stato di un elemento che si chiude portandolo di lato: spostamento, larghezza e soglia passata. */
@Stable
internal class SwipeAwayState {
    val x = Animatable(0f)
    var width by mutableFloatStateOf(1f)
    var armed by mutableStateOf(false)

    /** Da 0 a 1 man mano che ci si avvicina alla soglia: fa crescere la X. */
    val reveal: Float get() = (abs(x.value) / (width * SwipeAway.CLOSE_AT)).coerceIn(0f, 1f)

    suspend fun reset() {
        armed = false
        x.snapTo(0f)
    }
}

@Composable
internal fun rememberSwipeAwayState(): SwipeAwayState = remember { SwipeAwayState() }

/**
 * Trascinando di lato l'elemento lo segue e sbiadisce; lasciato oltre la soglia (o lanciato)
 * esce dallo schermo e chiama [onDismiss], altrimenti torna al suo posto. Solo in orizzontale,
 * così i tocchi sui pulsanti dentro restano validi.
 */
internal fun Modifier.swipeAway(state: SwipeAwayState, onDismiss: () -> Unit): Modifier = composed {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // pointerInput sta fuori dall'offset: il dito si misura su un riquadro che non si muove con lui.
    this
        .onSizeChanged { state.width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(state) {
            val tracker = VelocityTracker()
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    view.haptic(Haptic.GRAB)
                    tracker.resetTracking()
                    dx = state.x.value
                },
                onDragCancel = {
                    state.armed = false
                    scope.launch { state.x.animateTo(0f, Springs.bouncy()) }
                },
                onDragEnd = {
                    val v = tracker.calculateVelocity()
                    if (SwipeAway.closes(dx, v.x, v.y, state.width)) {
                        state.armed = false
                        view.haptic(Haptic.CONFIRM)
                        val dir = if (dx >= 0) 1f else -1f
                        scope.launch {
                            state.x.animateTo(dir * state.width * 1.2f, tween(200), initialVelocity = v.x)
                            onDismiss()
                        }
                    } else {
                        state.armed = false
                        scope.launch { state.x.animateTo(0f, Springs.bouncy(), initialVelocity = v.x) }
                    }
                },
            ) { change, amount ->
                change.consume()
                dx += amount
                tracker.addPosition(change.uptimeMillis, Offset(dx, 0f))
                val armed = SwipeAway.armed(dx, state.width)
                if (armed != state.armed) {
                    state.armed = armed
                    view.haptic(if (armed) Haptic.THRESHOLD else Haptic.TICK)
                }
                scope.launch { state.x.snapTo(dx) }
            }
        }
        .offset { IntOffset(state.x.value.roundToInt(), 0) }
        .graphicsLayer { alpha = 1f - 0.6f * (abs(state.x.value) / state.width).coerceIn(0f, 1f) }
}

/** La X che compare dietro, dal lato che si scopre: cresce e diventa rossa quando lasciando si chiude. */
@Composable
internal fun BoxScope.SwipeAwayMark(state: SwipeAwayState) {
    val reveal = state.reveal
    if (reveal <= 0f) return
    Box(
        Modifier
            .align(if (state.x.value > 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 12.dp)
            .size(44.dp)
            .graphicsLayer {
                val sc = 0.6f + 0.4f * reveal
                scaleX = sc
                scaleY = sc
                alpha = reveal
            }
            .clip(CircleShape)
            .background(if (state.armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = if (state.armed) "Lascia per annullare" else null,
            tint = if (state.armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = 90f * (1f - reveal) },
        )
    }
}
