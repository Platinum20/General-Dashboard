package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Regole della barra del tempo: un tocco non sposta mai il video (troppi salti per sbaglio),
 * solo un trascinamento soprattutto orizzontale. Pure, così si testano.
 */
object SeekGesture {
    /** Il trascinamento è un seek solo se va più di lato che in verticale (su/giù resta allo schermo intero). */
    fun isSeek(dx: Float, dy: Float): Boolean = abs(dx) > abs(dy)

    /** Punto della barra (0..1) sotto al dito. */
    fun fraction(x: Float, width: Int): Float = if (width <= 0) 0f else (x / width).coerceIn(0f, 1f)
}

/**
 * La barra del tempo dei player, al posto dello Slider di Material che salta al primo tocco.
 * Si sposta solo trascinando di lato oltre la soglia del tocco; [onSeeking] segue il dito,
 * [onSeekEnd] dice se applicare (false = gesto annullato). Il tocco semplice passa al video.
 */
@Composable
internal fun SeekBar(
    value: Float,
    enabled: Boolean,
    onSeeking: (Float) -> Unit,
    onSeekEnd: (commit: Boolean) -> Unit,
    thumb: @Composable () -> Unit,
    track: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seeking by rememberUpdatedState(onSeeking)
    val end by rememberUpdatedState(onSeekEnd)
    Layout(
        content = {
            track()
            thumb()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_HEIGHT)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { seeking(it.coerceIn(0f, 1f)); end(true); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Finché il dito non si muove di lato oltre la soglia non succede nulla: niente salti al tocco.
                    val start = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (SeekGesture.isSeek(over.x, over.y)) change.consume()
                    } ?: return@awaitEachGesture
                    seeking(SeekGesture.fraction(start.position.x, size.width))
                    val done = horizontalDrag(start.id) { change ->
                        change.consume()
                        seeking(SeekGesture.fraction(change.position.x, size.width))
                    }
                    end(done)
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trackP = measurables[0].measure(loose.copy(minWidth = width, maxWidth = width))
        val thumbP = measurables[1].measure(loose)
        val height = maxOf(trackP.height, thumbP.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            trackP.place(0, (height - trackP.height) / 2)
            val x = (value.coerceIn(0f, 1f) * width - thumbP.width / 2f).roundToInt()
                .coerceIn(0, (width - thumbP.width).coerceAtLeast(0))
            thumbP.place(x, (height - thumbP.height) / 2)
        }
    }
}

/** Il doppio tocco ripetuto si somma: +10, +20, +30… finché si resta dallo stesso lato. */
object SeekStack {
    const val STEP_S = 10

    fun next(shownS: Int, forward: Boolean): Int = when {
        forward && shownS > 0 -> shownS + STEP_S
        !forward && shownS < 0 -> shownS - STEP_S
        forward -> STEP_S
        else -> -STEP_S
    }
}
