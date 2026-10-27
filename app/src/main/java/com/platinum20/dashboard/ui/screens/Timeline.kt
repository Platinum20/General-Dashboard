package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.platinum20.dashboard.notes.ExpenseCategory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.today.BriefKind

/**
 * Una riga della linea del tempo, uguale nella card "Prossimi giorni" di Oggi e in Note › Prossimi giorni:
 * a sinistra l'icona e il nome, a destra il giorno e poi la linea con il pallino, sotto il +.
 */
data class TimelineEntry(
    val key: String,
    val kind: BriefKind,
    val text: String,
    /** Il giorno scritto a destra, solo sulla prima riga di quel giorno. */
    val dayLabel: String?,
    /** Spazio in più sopra la riga (dp): fra due giorni lontani la linea si allunga. */
    val gapDp: Float = 0f,
    /** Colore del calendario per gli eventi, 0 = quello normale. */
    val color: Int = 0,
    /** "Scaduti": il giorno in rosso. */
    val late: Boolean = false,
    /** Già passato: tutto più tenue. */
    val past: Boolean = false,
    /** L'icona a sinistra, se non è quella del tipo (le note). */
    val icon: ImageVector? = null,
    /** Sempre visibile dopo il nome, che si accorcia lui (l'importo delle spese). */
    val trailing: String? = null,
    /** Il mese che comincia con questa riga ("NOVEMBRE"): sopra si disegna il segno del mese. */
    val month: String? = null,
)

/** L'icona di una categoria di spesa: la spesa si riconosce a colpo d'occhio sulla linea. */
fun ExpenseCategory.icon(): ImageVector = when (this) {
    ExpenseCategory.FOOD -> Icons.Outlined.ShoppingCart
    ExpenseCategory.EATING_OUT -> Icons.Outlined.Restaurant
    ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsCar
    ExpenseCategory.HOME -> Icons.Outlined.Home
    ExpenseCategory.HEALTH -> Icons.Outlined.LocalHospital
    ExpenseCategory.FUN -> Icons.Outlined.SportsEsports
    ExpenseCategory.OTHER -> Icons.AutoMirrored.Outlined.ReceiptLong
}

/** Il colore di una categoria (ARGB), lo stesso per l'icona sulla linea e per la barra del riepilogo. */
fun ExpenseCategory.color(): Int = when (this) {
    ExpenseCategory.FOOD -> 0xFF43A047.toInt()
    ExpenseCategory.EATING_OUT -> 0xFFFB8C00.toInt()
    ExpenseCategory.TRANSPORT -> 0xFF1E88E5.toInt()
    ExpenseCategory.HOME -> 0xFF8E24AA.toInt()
    ExpenseCategory.HEALTH -> 0xFFE53935.toInt()
    ExpenseCategory.FUN -> 0xFF00ACC1.toInt()
    ExpenseCategory.OTHER -> 0xFF8D8D8D.toInt()
}

/** L'icona piccola di una riga: evento, promemoria, data, pacco o addebito. */
fun BriefKind.timelineIcon(): ImageVector = when (this) {
    BriefKind.EVENT -> Icons.Outlined.Event
    BriefKind.TASK -> Icons.Outlined.Checklist
    BriefKind.DATE -> Icons.Outlined.Celebration
    BriefKind.PARCEL -> Icons.Outlined.LocalShipping
    BriefKind.MONEY -> Icons.AutoMirrored.Outlined.ReceiptLong
    else -> Icons.Outlined.Event
}

/**
 * Una riga della linea del tempo. [top]/[bottom]: se la linea continua sopra e sotto il pallino.
 * [onIcon]: per i promemoria in Note, toccando il cerchio a sinistra si segna fatto.
 */
@Composable
fun TimelineRow(
    entry: TimelineEntry,
    top: Boolean,
    bottom: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onIcon: (() -> Unit)? = null,
    /** 16 dp dentro una card (Oggi); 0 nelle pagine, dove la linea sta sullo sfondo senza card. */
    start: Dp = 16.dp,
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    if (entry.gapDp > 0f) TimelineGap(entry.gapDp.dp, line, top = top)
    entry.month?.let { TimelineMonthMarker(it, start = start, top = top) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(start = start, end = LINE_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = entry.color.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
        if (onIcon != null) {
            Icon(
                Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "Fatto",
                tint = tint,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onIcon),
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Icon(entry.icon ?: entry.kind.timelineIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 7.dp),
        )
        entry.trailing?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.width(8.dp))
        val first = entry.dayLabel != null
        Text(
            entry.dayLabel.orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                entry.late -> MaterialTheme.colorScheme.error
                entry.past -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.primary
            },
            maxLines = 1,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(4.dp))
        TimelineGutter(
            Modifier.fillMaxHeight(),
            line,
            dot = when {
                first && entry.past -> MaterialTheme.colorScheme.outline to 7.dp
                first -> (if (entry.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) to 7.dp
                else -> MaterialTheme.colorScheme.outline to 4.dp
            },
            top = top,
            bottom = bottom,
        )
    }
}

private val GUTTER = 22.dp

/**
 * Il margine destro dopo la linea: con la card a 16 dp dal bordo, il centro della linea cade a 46 dp
 * dal bordo dello schermo, cioè sul centro del + rotondo in basso a destra (60 dp, a 16 dp dal bordo).
 */
private val LINE_END = 19.dp

/** Lo spazio vuoto fra due giorni lontani: solo la linea, allineata a quella delle righe. */
@Composable
private fun TimelineGap(height: Dp, line: Color, top: Boolean) {
    Row(Modifier.fillMaxWidth().height(height).padding(end = LINE_END)) {
        Spacer(Modifier.weight(1f))
        TimelineGutter(Modifier.fillMaxHeight(), line, dot = null, top = top, bottom = top)
    }
}

/** Il pezzo di linea di una riga: sopra e/o sotto il centro, con il pallino (colore e grandezza) se c'è. */
@Composable
private fun TimelineGutter(modifier: Modifier, line: Color, dot: Pair<Color, Dp>?, top: Boolean, bottom: Boolean) {
    Box(
        modifier.width(GUTTER).drawBehind {
            val x = size.width / 2
            val mid = size.height / 2
            val stroke = 2.dp.toPx()
            if (top) drawLine(line, Offset(x, 0f), Offset(x, mid), stroke)
            if (bottom) drawLine(line, Offset(x, mid), Offset(x, size.height), stroke)
            dot?.let { (color, r) -> drawCircle(color, r.toPx() / 2, Offset(x, mid)) }
        },
    )
}

/**
 * Il confine fra passato e adesso nelle pagine con il passato sopra: "Oggi" ben visibile e una riga che
 * arriva fino alla linea, con il pallino grande. Lì la lista fa un po' di resistenza (vedi [PastBarrier]).
 */
@Composable
fun TimelineNowMarker(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().height(32.dp).padding(end = LINE_END),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("OGGI", style = MaterialTheme.typography.labelMedium, color = primary)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(primary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
            },
        )
        Box(
            Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                drawLine(primary, Offset(0f, size.height / 2), Offset(size.width / 2, size.height / 2), 2.dp.toPx())
                drawCircle(primary, 6.dp.toPx(), Offset(size.width / 2, size.height / 2))
            },
        )
    }
}

/**
 * Il cambio di mese, come "OGGI" ma più tenue: il nome del mese, una riga fino alla linea e un pallino.
 * Scorrendoci sopra vibra appena (Antonio), così si sente quando si passa da un mese all'altro.
 */
@Composable
fun TimelineMonthMarker(label: String, start: Dp = 16.dp, top: Boolean = true) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().height(30.dp).padding(start = start, end = LINE_END).monthTick(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(2.dp).drawBehind {
                drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5.dp.toPx())
            },
        )
        Box(
            Modifier.width(GUTTER).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val mid = size.height / 2
                if (top) drawLine(line, Offset(x, 0f), Offset(x, mid), 2.dp.toPx())
                drawLine(line, Offset(x, mid), Offset(x, size.height), 2.dp.toPx())
                drawLine(line, Offset(0f, mid), Offset(x, mid), 1.5.dp.toPx())
                drawCircle(color, 4.5.dp.toPx(), Offset(x, mid))
            },
        )
    }
}

/** Quando un segno del mese attraversa la riga immaginaria a [MonthCross.LINE] dello schermo. Logica pura. */
object MonthCross {
    /** Dove sta la riga: un terzo dall'alto, sotto la barra del titolo, dove l'occhio legge. */
    const val LINE = 0.3f

    /** [previous] null = appena comparso: niente vibrazione all'apertura. */
    fun crossed(previous: Float?, now: Float, line: Float): Boolean =
        previous != null && (previous < line) != (now < line)
}

/** Una vibrazione leggera quando questo segno passa la riga di [MonthCross], in su o in giù. */
@Composable
private fun Modifier.monthTick(): Modifier {
    val haptic = rememberHaptic()
    val last = androidx.compose.runtime.remember { floatArrayOf(Float.NaN) }
    return onGloballyPositioned { c ->
        val y = c.positionInWindow().y + c.size.height / 2f
        val line = c.findRootCoordinates().size.height * MonthCross.LINE
        if (MonthCross.crossed(last[0].takeIf { !it.isNaN() }, y, line)) haptic(Haptic.TICK)
        last[0] = y
    }
}

/**
 * La piccola resistenza fra adesso e passato (Antonio): scorrendo verso il passato la lista si ferma sul
 * confine "Oggi" con una vibrazione leggera; se il dito insiste per [threshold] px si passa, con un'altra
 * vibrazione. Un lancio (fling) si ferma sul confine. Logica pura, per i test.
 */
object PastBarrier {
    /** [consumed] = quanto scorrimento trattiene la barriera; [bump]/[through] = quando vibrare. */
    data class Step(val consumed: Float, val pull: Float, val unlocked: Boolean, val bump: Boolean = false, val through: Boolean = false)

    /**
     * [boundary]: dove sta il confine rispetto al bordo alto della lista (px; 0 = proprio in cima, negativo = già
     * passato sopra, positivo = si sta guardando il passato). [delta] > 0 = si scorre verso il passato.
     */
    fun step(boundary: Float?, delta: Float, pull: Float, unlocked: Boolean, threshold: Float, fling: Boolean): Step {
        if (boundary == null) return Step(0f, pull, unlocked)
        // Già nel passato: niente resistenza finché non si torna ad adesso.
        if (boundary > 1f) return Step(0f, 0f, true)
        // Tornati ben dentro adesso: la resistenza si riarma.
        val armed = if (boundary < -threshold) false else unlocked
        if (delta <= 0f || armed) return Step(0f, if (delta <= 0f) 0f else pull, armed)
        val room = -boundary
        if (delta <= room) return Step(0f, pull, false)
        val excess = delta - room
        if (fling) return Step(excess, pull, false)
        val total = pull + excess
        if (total >= threshold) return Step(0f, 0f, true, through = true)
        return Step(excess, total, false, bump = pull == 0f)
    }
}

/** La [PastBarrier] collegata a una lista: [itemIndex] è l'item con la linea, [offset] dove sta "Oggi" dentro l'item. */
@Composable
fun rememberPastBarrier(listState: androidx.compose.foundation.lazy.LazyListState, itemIndex: Int, offset: Int?): androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
    val haptic = rememberHaptic()
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
    val index by androidx.compose.runtime.rememberUpdatedState(itemIndex)
    val at by androidx.compose.runtime.rememberUpdatedState(offset)
    return androidx.compose.runtime.remember(listState, haptic, threshold) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            var pull = 0f
            var unlocked = false
            fun boundary(): Float? {
                val o = at ?: return null
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                return when {
                    item != null -> (item.offset + o).toFloat()
                    listState.firstVisibleItemIndex > index -> -1e9f
                    else -> 1e9f
                }
            }
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                val s = PastBarrier.step(
                    boundary(), available.y, pull, unlocked, threshold,
                    fling = source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput,
                )
                pull = s.pull
                unlocked = s.unlocked
                if (s.bump) haptic(Haptic.TICK)
                if (s.through) haptic(Haptic.THRESHOLD)
                return Offset(0f, s.consumed)
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                pull = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }
}
