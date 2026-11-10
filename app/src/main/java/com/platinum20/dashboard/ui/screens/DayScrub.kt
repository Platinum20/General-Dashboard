package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    fun at(fraction: Float, count: Int): Int = position(fraction, count).toInt()

    /**
     * Dove si è nella linea, con la virgola: 2.4 = fra il terzo e il quarto giorno, un po' oltre il terzo.
     * La parte dopo la virgola fa scorrere la lista con continuità da un giorno al successivo.
     */
    fun position(fraction: Float, count: Int): Float =
        if (count <= 1) 0f else fraction.coerceIn(0f, 1f) * (count - 1)

    /**
     * Di quanto la cima della lista sta sotto la cima dell'item del giorno: il suo pallino ([dotPx] dentro
     * l'item) va sotto il dito ([fingerPx]), spostato di [t] del tratto [gapPx] fino al pallino del giorno dopo.
     * A t = 1 il giorno dopo è esattamente sotto il dito: niente scatti al passaggio.
     */
    fun offset(dotPx: Float, gapPx: Float, t: Float, fingerPx: Float): Int = (dotPx + t * gapPx - fingerPx).roundToInt()

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

/** Lo stato del gesto: quale giorno è sotto il dito (la sua riga si accende) e dove sta la lista. */
@Stable
class DayScrubState internal constructor(internal val list: LazyListState) {
    /** La chiave della riga del giorno sotto il dito, null quando non si trascina. */
    var hot by mutableStateOf<String?>(null)
        private set
    /** Mentre il dito tiene la linea: il calendario non carica altri mesi, così le righe non cambiano sotto il dito. */
    var active by mutableStateOf(false)
        private set
    internal var days: List<ScrubDay> = emptyList()
    internal var haptic: (Haptic) -> Unit = {}
    internal var density = 1f
    internal var scope: kotlinx.coroutines.CoroutineScope? = null

    /** I giorni all'inizio del gesto: la scala della linea non cambia sotto il dito anche se il calendario cresce. */
    private var held: List<ScrubDay> = emptyList()
    private var current = -1
    /** Il tratto fra un giorno e il successivo, misurato una volta per gesto: la lista scorre con passo costante. */
    private val gaps = HashMap<String, Float>()

    internal fun begin() {
        held = days
        current = -1
        gaps.clear()
        active = true
        // Se la lista stava ancora scorrendo per un lancio, si ferma: ora comanda il dito.
        scope?.launch { list.stopScroll() }
        haptic(Haptic.GRAB)
    }

    internal fun move(y: Float, height: Float) {
        if (held.isEmpty()) return
        val p = DayScrub.position(y / height.coerceAtLeast(1f), held.size)
        val i = p.toInt().coerceAtMost(held.lastIndex)
        val t = (p - i).coerceIn(0f, 1f)
        val day = held[i]
        if (i != current) {
            // Ogni giorno un tic leggero; entrando in un altro mese un colpo più netto.
            if (current >= 0) haptic(if (day.month != held[current].month) Haptic.SNAP else Haptic.TICK)
            current = i
            hot = day.key
        }
        // L'item si cerca per chiave nei giorni di adesso: se sopra arrivano righe nuove del passato, i numeri cambiano.
        val live = days.firstOrNull { it.key == day.key } ?: return
        val next = held.getOrNull(i + 1)?.let { n -> days.firstOrNull { it.key == n.key } }
        val gap = if (next == null || t == 0f) 0f else gaps.getOrPut(day.key) { gap(live, next) }
        list.requestScrollToItem(live.item, DayScrub.offset(live.dotDp * density, gap, t, y))
    }

    /** Dal pallino di [from] a quello di [to] in px: misurato se si vedono tutti e due, altrimenti stimato. */
    private fun gap(from: ScrubDay, to: ScrubDay): Float {
        val seen = list.layoutInfo.visibleItemsInfo
        val a = seen.firstOrNull { it.index == from.item }
        val b = seen.firstOrNull { it.index == to.item }
        if (a != null && b != null) return (b.offset + to.dotDp * density) - (a.offset + from.dotDp * density)
        val each = if (seen.isEmpty()) 48f * density else seen.sumOf { it.size }.toFloat() / seen.size
        return (to.item - from.item) * each + (to.dotDp - from.dotDp) * density
    }

    internal fun end() {
        active = false
        hot = null
        current = -1
    }
}

/** Lo stato della linea come barra di scorrimento per [list]; [days] vengono da [DayScrub.days]. */
@Composable
fun rememberDayScrub(list: LazyListState, days: List<ScrubDay>): DayScrubState {
    val haptic = rememberHaptic()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val state = remember(list) { DayScrubState(list) }
    state.days = days
    state.haptic = haptic
    state.scope = scope
    state.density = androidx.compose.ui.platform.LocalDensity.current.density
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
