package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Un giorno della linea a destra: l'item della lista dove comincia, la chiave della sua riga (per accenderla),
 * il mese (per il colpo al cambio) e dove sta il pallino del giorno dentro l'item, in dp dall'alto.
 */
data class ScrubDay(val item: Int, val key: String, val month: String? = null, val dotDp: Float = DayScrub.DAY_DOT_DP)

/**
 * Logica pura della linea a destra del Calendario come barra di scorrimento (Antonio, 1.446): la linea è
 * tutto il calendario, in cima il primo giorno e in fondo l'ultimo. Dove metti il dito, lì sei: la riga di
 * quel giorno va sotto il dito, attaccata alla linea col suo filo, e si accende. Lasciando non si apre niente.
 */
object DayScrub {
    /** Il pallino della riga del giorno (26 dp) sta a metà. */
    const val DAY_DOT_DP = 13f

    /** Sopra la riga del giorno c'è il segno del mese (30 dp). */
    const val MONTH_DP = 30f

    /** Il pallino di OGGI (32 dp) sta a metà. */
    const val NOW_DOT_DP = 16f

    /** Il giorno sotto il dito: [fraction] dell'altezza della linea, da 0 (in cima) a 1 (in fondo). */
    fun at(fraction: Float, count: Int): Int =
        if (count <= 1) 0 else (fraction.coerceIn(0f, 1f) * (count - 1)).roundToInt()

    /**
     * I giorni della linea del tempo: OGGI e ogni riga che apre un giorno. [first] è l'item della prima riga;
     * la riga delle abitudini sta prima della riga [habitsAt] e occupa [habitRows] item.
     */
    fun days(entries: List<TimelineEntry>, first: Int, habitsAt: Int = entries.size, habitRows: Int = 0): List<ScrubDay> {
        var month: String? = null
        return entries.mapIndexedNotNull { i, e ->
            e.month?.let { month = it }
            val above = if (e.month != null) MONTH_DP else 0f
            val dot = when {
                e.todayHeader -> above + NOW_DOT_DP
                e.dayLabel != null -> above + DAY_DOT_DP
                else -> return@mapIndexedNotNull null
            }
            ScrubDay(first + i + if (i >= habitsAt) habitRows else 0, e.key, month, dot)
        }
    }
}

/** Lo stato del gesto: quale giorno è sotto il dito (la sua riga si accende) e dove va messo. */
@Stable
class DayScrubState internal constructor(internal val list: LazyListState) {
    /** La chiave della riga del giorno sotto il dito, null quando non si trascina. */
    var hot by mutableStateOf<String?>(null)
        private set
    /** Dove portare la lista: l'item del giorno e il punto (px dall'alto) dove deve cadere il suo pallino. */
    internal var target by mutableStateOf<Pair<ScrubDay, Float>?>(null)
    internal var days: List<ScrubDay> = emptyList()
    internal var haptic: (Haptic) -> Unit = {}

    private var held: List<ScrubDay> = emptyList()
    private var current = -1

    internal fun begin() {
        // Durante il gesto i giorni restano quelli di partenza: se il calendario cresce, sotto il dito non cambia niente.
        held = days
        current = -1
        haptic(Haptic.GRAB)
    }

    internal fun move(y: Float, height: Float) {
        if (held.isEmpty()) return
        val next = DayScrub.at(y / height.coerceAtLeast(1f), held.size)
        val day = held[next]
        if (next != current) {
            // Ogni giorno un tic leggero; entrando in un altro mese un colpo più netto.
            if (current >= 0) haptic(if (day.month != held[current].month) Haptic.SNAP else Haptic.TICK)
            current = next
            hot = day.key
        }
        target = day to y
    }

    internal fun end() {
        hot = null
        current = -1
    }
}

/** Lo stato della linea come barra di scorrimento per [list]; [days] vengono da [DayScrub.days]. */
@Composable
fun rememberDayScrub(list: LazyListState, days: List<ScrubDay>): DayScrubState {
    val haptic = rememberHaptic()
    val state = remember(list) { DayScrubState(list) }
    state.days = days
    state.haptic = haptic
    // Il pallino del giorno va esattamente sotto il dito: la riga è già lì, attaccata alla linea.
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(state) {
        snapshotFlow { state.target }.collectLatest { target ->
            val (day, y) = target ?: return@collectLatest
            list.scrollToItem(day.item, -(y - day.dotDp * density).roundToInt())
        }
    }
    return state
}

/**
 * Sulla lista: chi tocca la striscia larga [strip] sul bordo destro (dove passa la linea) e si muove in
 * verticale tiene la linea come una barra di scorrimento. Il gesto si prende prima delle righe, così
 * lasciando il dito non si apre l'evento sotto. Un tocco fermo resta alla riga; un gesto in orizzontale pure.
 */
fun Modifier.dayScrub(state: DayScrubState, strip: Dp = SCRUB_STRIP): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < size.width - strip.toPx() || state.days.size < 2) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop / 2
        var moved = Offset.Zero
        var y = down.position.y
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed) return@awaitEachGesture
            moved += change.positionChange()
            y = change.position.y
            if (moved.getDistance() > slop) {
                if (abs(moved.y) <= abs(moved.x)) return@awaitEachGesture
                event.changes.forEach { it.consume() }
                break
            }
        }
        val height = size.height.toFloat()
        state.begin()
        state.move(y, height)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            event.changes.forEach { it.consume() }
            if (change == null || !change.pressed) break
            state.move(change.position.y, height)
        }
        state.end()
    }
}

/** La striscia a destra della lista che risponde al gesto: la linea e un dito di margine attorno. */
val SCRUB_STRIP = 56.dp
