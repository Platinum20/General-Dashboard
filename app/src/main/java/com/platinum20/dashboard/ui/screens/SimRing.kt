package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.sim.GigaVerdict
import com.platinum20.dashboard.sim.SimStatus
import java.util.Locale

/**
 * L'eroe della pagina SIM (1.528, Antonio scelta B): a sinistra l'anello dei giga rimasti con il numero dentro,
 * a destra la risposta "Ti bastano fino al rinnovo" col suo perché. Con i giga illimitati niente anello.
 */
@Composable
internal fun SimRingHero(s: SimStatus, verdict: GigaVerdict?) {
    val content = LocalContentColor.current
    val soft = content.copy(alpha = 0.7f)
    val data = s.data
    Text(
        (s.offer ?: "WindTre").uppercase(Locale.ITALIAN),
        style = MaterialTheme.typography.labelMedium,
        color = soft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (data != null && !data.unlimited) {
            val fraction = data.fraction ?: 0f
            Box(
                Modifier.size(112.dp).semantics(mergeDescendants = true) {
                    contentDescription = gbLabel(data.left) + " rimasti" + (data.total?.let { " di " + gbLabel(it) } ?: "")
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(112.dp)) {
                    val stroke = 10.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(content.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    drawArc(content, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RollingText(gbLabel(data.left), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("rimasti", style = MaterialTheme.typography.labelSmall, color = soft)
                }
            }
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            if (data == null || data.unlimited) {
                Text(if (data?.unlimited == true) "Giga illimitati" else "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
            }
            val text = verdict?.let { gigaVerdictText(it) }
            if (text != null) {
                Text(text.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(text.second, style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else if (data?.total != null && !data.unlimited) {
                Text("di " + gbLabel(data.total), style = MaterialTheme.typography.bodyMedium, color = soft)
            }
        }
    }
}

/** L'icona di ogni riga della pagina SIM, dal suo nome. */
internal fun simRowIcon(name: String): ImageVector = when (name) {
    "Minuti" -> Icons.Outlined.Call
    "SMS" -> Icons.Outlined.Sms
    "Roaming UE" -> Icons.Outlined.Public
    "Credito" -> Icons.Outlined.Euro
    "Rinnovo" -> Icons.Outlined.Autorenew
    else -> Icons.Outlined.SimCard
}

/** Il consumo degli ultimi giorni, una barra per giorno (1.528): si vede subito un giorno strano. */
@Composable
internal fun SimUsageChart(days: List<Double>) {
    if (days.size < 2) return
    val shown = days.takeLast(30)
    val max = shown.maxOrNull()?.takeIf { it > 0 } ?: return
    val color = MaterialTheme.colorScheme.primary
    CanvasCard {
        CardTitle("Ultimi ${shown.size} giorni") {
            Text(
                "fino a " + gbLabel(max) + " al giorno",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                .semantics { contentDescription = "Consumo dei giga negli ultimi ${shown.size} giorni" },
        ) {
            val slot = size.width / shown.size
            val w = slot * 0.62f
            shown.forEachIndexed { i, gb ->
                val h = (size.height * (gb / max).toFloat()).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color.copy(alpha = if (i == shown.lastIndex) 1f else 0.6f),
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

