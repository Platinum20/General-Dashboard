package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.today.ComingKind
import com.platinum20.dashboard.today.WeekReviewData
import java.time.LocalDate

/** Ricorda per quale settimana la card "La tua settimana" è già stata chiusa. */
object WeekReviewSeen {
    private const val PREFS = "week_review"
    private const val KEY = "seen_monday"

    fun isSeen(context: Context, monday: LocalDate): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) == monday.toString()

    fun markSeen(context: Context, monday: LocalDate) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, monday.toString()).apply()
    }
}

/** La domenica sera: com'è andata la settimana e cosa arriva nella prossima. */
@Composable
fun WeekReviewCard(
    review: WeekReviewData,
    onOpenNotes: () -> Unit,
    onOpenCalendar: () -> Unit,
    onDismiss: () -> Unit,
) {
    Section(title = "La tua settimana", action = "Fatto", onAction = onDismiss) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                review.headline,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            val past = buildList {
                if (review.tasksDone > 0) add(Icons.Outlined.TaskAlt to if (review.tasksDone == 1) "1 promemoria chiuso" else "${review.tasksDone} promemoria chiusi")
                review.habits.forEach { add(Icons.Outlined.Repeat to it.text + if (it.reached) " ✓" else "") }
                review.spend?.let { s ->
                    val change = s.changePercent?.let { if (it >= 0) " (+$it% sulla media)" else " ($it% sulla media)" }.orEmpty()
                    add(Icons.Outlined.Euro to "Spese ${Expenses.format(s.cents)}$change")
                    s.biggestRise?.let { (cat, pct) -> add(Icons.Outlined.Euro to "${cat.label}: +$pct% del solito") }
                }
                if (review.parcelsArrived > 0) add(Icons.Outlined.LocalShipping to if (review.parcelsArrived == 1) "1 pacco arrivato" else "${review.parcelsArrived} pacchi arrivati")
            }
            if (past.isNotEmpty()) {
                SectionHeader("Com'è andata", modifier = Modifier.padding(horizontal = 12.dp))
                past.forEachIndexed { i, (icon, text) ->
                    if (i > 0) RowDivider()
                    WeekRow(icon, text, onOpenNotes)
                }
            }
            if (review.coming.isNotEmpty()) {
                SectionHeader("Cosa arriva", modifier = Modifier.padding(horizontal = 12.dp))
                review.coming.forEachIndexed { i, line ->
                    if (i > 0) RowDivider()
                    when (line.kind) {
                        ComingKind.EVENT -> WeekRow(Icons.Outlined.Event, line.text, onOpenCalendar)
                        ComingKind.DATE -> WeekRow(Icons.Outlined.Celebration, line.text, onOpenNotes)
                        ComingKind.MONEY -> WeekRow(Icons.Outlined.Euro, line.text, onOpenNotes)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
