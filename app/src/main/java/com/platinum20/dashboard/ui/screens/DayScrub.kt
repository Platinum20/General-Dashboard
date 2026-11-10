package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/** Un giorno della linea a destra: l'item della lista dove comincia, il nome ("Domani") e il mese ("Novembre"). */
data class ScrubDay(val item: Int, val label: String, val month: String? = null)

/**
 * Logica pura dello scorrimento rapido sulla linea a destra del Calendario (Antonio): trascinando sulla
 * linea si passa di giorno in giorno, piano all'inizio e sempre più svelti allungando il gesto. Lasciando
 * il dito non si apre niente: serve solo a muoversi.
 */
object DayScrub {
    /** Un giorno ogni tanti dp di dito, all'inizio: abbastanza per fermarsi sul giorno voluto. */
    const val STEP_DP = 26f

    /** Oltre questa distanza il gesto accelera: con un pollice si attraversano settimane. */
    const val ACCEL_DP = 170f

    /** Quanto si allunga al massimo il cursore oltre il primo o l'ultimo giorno: un elastico, poi torna. */
    const val PULL_DP = 44f

    /** Di quanti giorni ci si sposta per [dyDp] di dito (in giù = avanti nel tempo). */
    fun shift(dyDp: Float): Int {
        val d = abs(dyDp)
        val steps = d / STEP_DP + (d / ACCEL_DP).let { it * it }
        return (sign(dyDp) * floor(steps)).toInt()
    }

    /** Il giorno da cui si parte: quello che si sta guardando in cima alla lista. */
    fun from(days: List<ScrubDay>, firstVisible: Int): Int = days.indexOfLast { it.item <= firstVisible }.coerceAtLeast(0)

    /** Il giorno sotto il dito, senza uscire dalla linea. */
    fun target(start: Int, dyDp: Float, count: Int): Int = (start + shift(dyDp)).coerceIn(0, (count - 1).coerceAtLeast(0))

    /** Oltre la fine il cursore segue il dito sempre meno, fino a [PULL_DP]: si sente il bordo. */
    fun rubber(overDp: Float): Float = sign(overDp) * PULL_DP * (1f - exp(-abs(overDp) / PULL_DP))

    /**
     * I giorni della linea del tempo: OGGI, ogni riga che apre un giorno e i segni del mese. [first] è l'item
     * della prima riga; la riga delle abitudini sta prima della riga [habitsAt] e occupa [habitRows] item.
     */
    fun days(entries: List<TimelineEntry>, first: Int, habitsAt: Int = entries.size, habitRows: Int = 0): List<ScrubDay> {
        var month: String? = null
        return entries.mapIndexedNotNull { i, e ->
            e.month?.let { month = monthName(it) }
            val label = when {
                e.todayHeader -> "Oggi"
                e.dayLabel != null -> e.dayLabel
                e.month != null -> month
                else -> null
            } ?: return@mapIndexedNotNull null
            ScrubDay(first + i + if (i >= habitsAt) habitRows else 0, label, month.takeIf { it != label })
        }
    }

    /** "NOVEMBRE" → "Novembre". */
    fun monthName(marker: String): String = marker.lowercase().replaceFirstChar { it.titlecase() }
}

/** Lo stato del gesto: dove sta il dito e su che giorno, per disegnare il cursore e il fumetto. */
@Stable
class DayScrubState internal constructor(internal val list: LazyListState) {
    var dragging by mutableStateOf(false)
        private set
    /** Dove disegnare il cursore, in px dall'alto della lista (con l'elastico ai bordi). */
    var thumbY by mutableFloatStateOf(0f)
        private set
    var day by mutableStateOf<ScrubDay?>(null)
        private set
    internal var target by mutableStateOf<Int?>(null)
    internal var days: List<ScrubDay> = emptyList()
    internal var haptic: (Haptic) -> Unit = {}

    private var held: List<ScrubDay> = emptyList()
    private var start = 0
    private var current = 0
    private var startY = 0f
    private var edgeY: Float? = null

    internal fun begin(y: Float) {
        // Durante il gesto la lista dei giorni resta quella di partenza: se il calendario cresce non si salta.
        held = days
        start = DayScrub.from(held, list.firstVisibleItemIndex)
        current = start
        startY = y
        edgeY = null
        thumbY = y
        day = held.getOrNull(start)
        target = null
        dragging = true
        haptic(Haptic.GRAB)
    }

    internal fun move(y: Float, density: Float) {
        if (held.isEmpty()) return
        val dyDp = (y - startY) / density
        val raw = start + DayScrub.shift(dyDp)
        val next = DayScrub.target(start, dyDp, held.size)
        if (next != current) {
            val before = held[current]
            current = next
            val now = held[next]
            day = now
            target = now.item
            // Ogni giorno un tic leggero; entrando in un altro mese un colpo più netto.
            haptic(if (now.month != before.month && now.month != null) Haptic.SNAP else Haptic.TICK)
        }
        if (raw != next) {
            val edge = edgeY ?: y.also { edgeY = it; haptic(Haptic.THRESHOLD) }
            thumbY = edge + DayScrub.rubber((y - edge) / density) * density
        } else {
            edgeY = null
            thumbY = y
        }
    }

    internal fun end() {
        // Rilasciando oltre il bordo il cursore torna sull'ultimo giorno con una molla, poi sparisce.
        edgeY?.let { thumbY = it }
        edgeY = null
        dragging = false
    }
}

/** Lo stato dello scorrimento rapido per [list]: [days] sono i giorni (vedi [DayScrub.days]). */
@Composable
fun rememberDayScrub(list: LazyListState, days: List<ScrubDay>): DayScrubState {
    val haptic = rememberHaptic()
    val state = remember(list) { DayScrubState(list) }
    state.days = days
    state.haptic = haptic
    // La lista insegue il giorno sotto il dito con la molla senza rimbalzo: se il dito va avanti, si riparte da dove è.
    LaunchedEffect(state) {
        snapshotFlow { state.target }.collectLatest { item ->
            if (item == null) return@collectLatest
            val seen = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == item }
            if (seen != null) list.animateScrollBy(seen.offset.toFloat(), Motion.settle())
            else list.scrollToItem(item)
        }
    }
    return state
}

/**
 * Sulla lista: chi tocca la striscia larga [strip] sul bordo destro (dove passa la linea) e trascina in
 * verticale scorre di giorno in giorno. Il gesto si prende prima delle righe, così lasciando il dito non si
 * apre l'evento sotto. Un tocco senza trascinare resta alla riga; un gesto in orizzontale pure.
 */
fun Modifier.dayScrub(state: DayScrubState, strip: Dp = SCRUB_STRIP): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < size.width - strip.toPx() || state.days.size < 2) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        var moved = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            moved += change.positionChange()
            if (moved.getDistance() > slop) {
                if (abs(moved.y) <= abs(moved.x)) return@awaitEachGesture
                event.changes.forEach { it.consume() }
                break
            }
        }
        state.begin(down.position.y)
        state.move(down.position.y + moved.y, density)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (change == null || !change.pressed) break
            state.move(change.position.y, density)
        }
        state.end()
    }
}

/**
 * Il cursore blu sulla linea e il fumetto col giorno accanto, solo mentre si trascina. [lineFromEnd] è la
 * distanza del centro della linea dal bordo destro della lista. Va in un Box sopra la lista, grande uguale.
 */
@Composable
fun DayScrubThumb(state: DayScrubState, lineFromEnd: Dp, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(if (state.dragging) 1f else 0f, Motion.lively(), label = "cursore giorni")
    val y = remember(state) { Animatable(state.thumbY) }
    LaunchedEffect(state.thumbY, state.dragging) {
        // Sotto il dito il cursore lo segue esatto; lasciato oltre il bordo torna con la molla del gesto.
        if (state.dragging) y.snapTo(state.thumbY) else y.animateTo(state.thumbY, Motion.gesture())
    }
    if (shown <= 0.01f && !state.dragging) return
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .placeAt({ y.value }, lineFromEnd, centerX = true)
                .graphicsLayer { alpha = shown; scaleY = 0.4f + 0.6f * shown }
                .size(6.dp, 34.dp)
                .background(primary, CircleShape),
        )
        val day = state.day ?: return@Box
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 2.dp,
            modifier = Modifier
                .placeAt({ y.value }, lineFromEnd + 18.dp, centerX = false)
                .graphicsLayer {
                    alpha = shown
                    val s = 0.85f + 0.15f * shown
                    scaleX = s; scaleY = s
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(day.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                day.month?.let { Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            }
        }
    }
}

/**
 * Mette il figlio con il centro verticale a [y] px e il bordo destro (o il centro, [centerX]) a [fromEnd] dal
 * bordo destro, senza uscire dall'altezza della lista.
 */
private fun Modifier.placeAt(y: () -> Float, fromEnd: Dp, centerX: Boolean): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    layout(w, h) {
        val right = w - fromEnd.roundToPx() + if (centerX) p.width / 2 else 0
        val top = (y() - p.height / 2f).roundToInt().coerceIn(0, (h - p.height).coerceAtLeast(0))
        p.place(right - p.width, top)
    }
}

/** La striscia a destra della lista che risponde al gesto: la linea e un dito di margine attorno. */
val SCRUB_STRIP = 56.dp
