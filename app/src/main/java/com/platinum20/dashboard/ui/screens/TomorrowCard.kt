package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.today.TomorrowKind
import com.platinum20.dashboard.today.TomorrowPlan
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.asTemp
import java.time.format.DateTimeFormatter
import java.util.Locale

/** La sera: com'è domani, in una card sola. Ogni riga porta alla sua schermata. */
@Composable
fun TomorrowCard(
    plan: TomorrowPlan,
    onOpenWeather: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenNotes: () -> Unit,
) {
    val dayName = plan.day.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))
    Section(title = "${plan.label} · $dayName") {
        Column(Modifier.padding(vertical = 4.dp)) {
            plan.headline?.let { headline ->
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            plan.weather?.let { w ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable(onClick = onOpenWeather)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(weatherIcon(w.code), TileAccent.BLUE, size = 36.dp, iconSize = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(WeatherCodes.description(w.code), style = MaterialTheme.typography.bodyLarge)
                        val rain = when {
                            w.rainFrom != null -> "Pioggia ${w.rainChance}% ${w.rainFrom}"
                            w.rainChance >= 30 -> "Pioggia ${w.rainChance}%"
                            else -> null
                        }
                        if (rain != null) {
                            Text(rain, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "${w.max.asTemp()} / ${w.min.asTemp()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            plan.lines.forEachIndexed { index, line ->
                if (index > 0 || plan.weather != null) RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable(onClick = if (line.kind == TomorrowKind.EVENT) onOpenCalendar else onOpenNotes)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        when (line.kind) {
                            TomorrowKind.EVENT -> Icons.Outlined.Event
                            TomorrowKind.TASK -> Icons.Outlined.TaskAlt
                            TomorrowKind.DATE -> Icons.Outlined.Celebration
                            TomorrowKind.MONEY -> Icons.Outlined.Euro
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
