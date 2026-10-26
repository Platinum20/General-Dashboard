package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Una tacca della linea dei giorni: la riga della lista dove comincia quel giorno e come si chiama ("Mer 28"). */
data class DayMark(val index: Int, val label: String)

/** Logica pura della linea dei giorni, per i test. */
object DayScroll {
    /** Dove sta la tacca, da 0 (in cima) a 1 (in fondo): in proporzione alla lista, come in Google Foto. */
    fun fraction(index: Int, total: Int): Float = if (total <= 1) 0f else (index.toFloat() / (total - 1)).coerceIn(0f, 1f)

    /** La tacca più vicina al dito, che sta a [fraction] dell'altezza. */
    fun nearest(marks: List<DayMark>, total: Int, fraction: Float): DayMark? =
        marks.minByOrNull { kotlin.math.abs(fraction(it.index, total) - fraction) }

    /** Il giorno che si sta guardando: l'ultima tacca già passata. */
    fun current(marks: List<DayMark>, firstVisible: Int): DayMark? = marks.lastOrNull { it.index <= firstVisible } ?: marks.firstOrNull()
}

/**
 * La linea dei giorni a destra di una lista lunga ordinata per data: una tacca per giorno, la si trascina
 * (o la si tocca) e si salta a quel giorno, con l'etichetta "Mer 28" accanto al dito. Pensata per
 * Note › Prossimi giorni, riusabile per Iscrizioni di YouTube e News. Va in un Box sopra la lista, a destra.
 */
@Composable
fun DayScrollbar(listState: LazyListState, marks: List<DayMark>, modifier: Modifier = Modifier) {
    if (marks.size < 3) return
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val currentMarks by rememberUpdatedState(marks)
    var dragging by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<DayMark?>(null) }
    val total by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val shown by remember { derivedStateOf { DayScroll.current(currentMarks, listState.firstVisibleItemIndex) } }
    val active = if (dragging) picked ?: shown else shown
    val primary = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val alpha by animateFloatAsState(if (dragging) 1f else 0.7f, label = "linea dei giorni")

    BoxWithConstraints(modifier.fillMaxHeight().width(28.dp)) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        fun jump(y: Float) {
            val mark = DayScroll.nearest(currentMarks, total, (y / height).coerceIn(0f, 1f)) ?: return
            if (mark != picked) {
                picked = mark
                haptic(Haptic.TICK)
                scope.launch { listState.scrollToItem(mark.index) }
            }
        }
        Canvas(
            Modifier
                .matchParentSize()
                .pointerInput(height) {
                    // Un tocco salta al giorno, trascinando si scorre di giorno in giorno.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        jump(down.position.y)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            jump(change.position.y)
                        }
                        dragging = false
                        picked = null
                    }
                },
        ) {
            val x = size.width - 6.dp.toPx()
            drawLine(track.copy(alpha = 0.5f * alpha), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
            currentMarks.forEach { m ->
                val y = DayScroll.fraction(m.index, total) * size.height
                val on = m == active
                drawLine(
                    if (on) primary else tick.copy(alpha = alpha),
                    Offset(x - (if (on) 10.dp else 5.dp).toPx(), y),
                    Offset(x + 1.dp.toPx(), y),
                    (if (on) 3.dp else 1.5.dp).toPx(),
                    StrokeCap.Round,
                )
            }
        }
        // L'etichetta del giorno accanto al dito, solo mentre si trascina.
        AnimatedVisibility(
            visible = dragging && active != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).wrapContentSize(Alignment.TopEnd, unbounded = true),
        ) {
            val y = active?.let { DayScroll.fraction(it.index, total) * height } ?: 0f
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.offset {
                    IntOffset(-with(density) { 34.dp.roundToPx() }, (y - with(density) { 16.dp.toPx() }).roundToInt().coerceAtLeast(0))
                },
            ) {
                Text(
                    active?.label.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
