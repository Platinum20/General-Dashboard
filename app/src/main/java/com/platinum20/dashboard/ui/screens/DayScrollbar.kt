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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Una tacca della linea del tempo: la riga della lista dove comincia quel giorno, il nome per il fumetto
 * mentre si trascina ("Mer 28"), il giorno (in giorni dal 1970, anche con la virgola) e l'etichetta corta
 * scritta accanto alla linea ("28", "3 nov").
 */
data class DayMark(val index: Int, val label: String, val at: Double = index.toDouble(), val tick: String = label)

/** Logica pura della linea del tempo, per i test. */
object DayScroll {
    /** Fino a due settimane lo spazio è proporzionale al tempo; i salti più lunghi si accorciano. */
    const val LINEAR_DAYS = 14.0

    /** Quanto spazio prende un salto di [days] giorni: uguale fino a [LINEAR_DAYS], poi cresce piano. */
    fun span(days: Double): Double {
        val d = kotlin.math.abs(days)
        return if (d <= LINEAR_DAYS) d else LINEAR_DAYS + 2 * kotlin.math.sqrt(d - LINEAR_DAYS)
    }

    /** Dove sta ogni tacca, da 0 (in cima) a 1 (in fondo): distanziate secondo il tempo fra l'una e l'altra. */
    fun fractions(marks: List<DayMark>): List<Float> {
        if (marks.size <= 1) return marks.map { 0f }
        val steps = marks.zipWithNext { a, b -> span(b.at - a.at) }
        val total = steps.sum()
        // Tutte nello stesso giorno: in fila come prima, in proporzione alla lista.
        if (total <= 0.0) return marks.indices.map { it.toFloat() / (marks.size - 1) }
        var acc = 0.0
        return listOf(0f) + steps.map { acc += it; (acc / total).toFloat().coerceIn(0f, 1f) }
    }

    /** La tacca più vicina al dito, che sta a [fraction] dell'altezza. */
    fun nearest(marks: List<DayMark>, fraction: Float): DayMark? {
        val at = fractions(marks)
        return marks.indices.minByOrNull { kotlin.math.abs(at[it] - fraction) }?.let { marks[it] }
    }

    /** Il giorno che si sta guardando: l'ultima tacca già passata. */
    fun current(marks: List<DayMark>, firstVisible: Int): DayMark? = marks.lastOrNull { it.index <= firstVisible } ?: marks.firstOrNull()

    /**
     * Quali etichette scrivere senza che si accavallino: [gap] è lo spazio minimo fra due, in frazione
     * dell'altezza. Quella accesa ([keep]) c'è sempre, le altre solo se c'è posto.
     */
    fun visibleTicks(fractions: List<Float>, gap: Float, keep: Int = -1): Set<Int> {
        val shown = sortedSetOf<Int>()
        if (keep in fractions.indices) shown += keep
        fractions.indices.forEach { i ->
            if (shown.none { kotlin.math.abs(fractions[it] - fractions[i]) < gap }) shown += i
        }
        return shown
    }

    /**
     * L'etichetta corta di un giorno: "Oggi", "Dom" (domani), "Ieri", poi solo il numero; il mese quando
     * cambia rispetto alla tacca prima ([previous]), l'anno se non è quello di [today].
     */
    fun tick(day: java.time.LocalDate, today: java.time.LocalDate, previous: java.time.LocalDate?): String = when {
        day == today -> "Oggi"
        day == today.plusDays(1) -> "Dom"
        day == today.minusDays(1) -> "Ieri"
        day.year != today.year && day.year != previous?.year -> day.year.toString()
        previous == null || previous.month != day.month || previous.year != day.year ->
            "${day.dayOfMonth} " + day.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ITALIAN)
        else -> day.dayOfMonth.toString()
    }
}

/**
 * La linea del tempo a destra di una lista lunga ordinata per data: una tacca per giorno, distanziate
 * secondo il tempo che passa fra l'una e l'altra, con il giorno scritto accanto quando c'è posto. La si
 * trascina (o la si tocca) e si salta a quel giorno, con l'etichetta "Mer 28" accanto al dito. In Note ›
 * Prossimi giorni e in Note (per data di inserimento), riusabile per Iscrizioni di YouTube e News.
 * Va in un Box sopra la lista, a destra.
 */
@Composable
fun DayScrollbar(listState: LazyListState, marks: List<DayMark>, modifier: Modifier = Modifier) {
    if (marks.size < 2) return
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val currentMarks by rememberUpdatedState(marks)
    val fractions = remember(marks) { DayScroll.fractions(marks) }
    var dragging by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<DayMark?>(null) }
    val shown by remember { derivedStateOf { DayScroll.current(currentMarks, listState.firstVisibleItemIndex) } }
    val active = if (dragging) picked ?: shown else shown
    val primary = MaterialTheme.colorScheme.primary
    val tick = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val alpha by animateFloatAsState(if (dragging) 1f else 0.8f, label = "linea del tempo")

    BoxWithConstraints(modifier.fillMaxHeight().width(WIDTH)) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        fun jump(y: Float) {
            val mark = DayScroll.nearest(currentMarks, (y / height).coerceIn(0f, 1f)) ?: return
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
            drawLine(track.copy(alpha = 0.6f * alpha), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
            val activeIndex = currentMarks.indexOf(active)
            val lineHeight = 14.sp.toPx()
            val labels = DayScroll.visibleTicks(fractions, (lineHeight + 4.dp.toPx()) / size.height, activeIndex)
            currentMarks.forEachIndexed { i, m ->
                val y = fractions.getOrElse(i) { 0f } * size.height
                val on = i == activeIndex
                drawLine(
                    if (on) primary else tick.copy(alpha = alpha),
                    Offset(x - (if (on) 8.dp else 4.dp).toPx(), y),
                    Offset(x + 1.dp.toPx(), y),
                    (if (on) 3.dp else 1.5.dp).toPx(),
                    StrokeCap.Round,
                )
                if (i in labels) {
                    val text = measurer.measure(
                        m.tick,
                        tickStyle.copy(color = if (on) primary else labelColor.copy(alpha = alpha), fontWeight = if (on) FontWeight.SemiBold else null),
                        maxLines = 1,
                    )
                    val top = (y - text.size.height / 2f).coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f))
                    drawText(text, topLeft = Offset((x - 11.dp.toPx() - text.size.width).coerceAtLeast(0f), top))
                }
            }
        }
        // L'etichetta del giorno accanto al dito, solo mentre si trascina.
        AnimatedVisibility(
            visible = dragging && active != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).wrapContentSize(Alignment.TopEnd, unbounded = true),
        ) {
            val y = active?.let { fractions.getOrElse(currentMarks.indexOf(it)) { 0f } * height } ?: 0f
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.offset {
                    IntOffset(-with(density) { (WIDTH + 6.dp).roundToPx() }, (y - with(density) { 16.dp.toPx() }).roundToInt().coerceAtLeast(0))
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

/** Quanto è larga la linea con le etichette: la lista accanto lascia questo spazio a destra. */
private val WIDTH = 46.dp

/** Il margine destro della lista quando c'è la linea del tempo. */
val DayScrollbarGutter = WIDTH + 12.dp
