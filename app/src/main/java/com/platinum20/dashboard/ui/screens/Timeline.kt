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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
)

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
) {
    val line = MaterialTheme.colorScheme.outlineVariant
    if (entry.gapDp > 0f) TimelineGap(entry.gapDp.dp, line, top = top)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = LINE_END),
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
            modifier = Modifier.width(TIMELINE_LABEL),
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

private val TIMELINE_LABEL = 76.dp
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
