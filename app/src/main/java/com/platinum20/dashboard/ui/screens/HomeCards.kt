package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.calendar.CalendarAsk
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Upcoming
import com.platinum20.dashboard.today.UpcomingItem
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.captureTarget
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LocalShipping
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.material.icons.outlined.Celebration
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.platinum20.dashboard.autofill.AutoFill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.PaddingValues

/** Gli impegni di oggi e di domani, condivisi tra "Adesso" e l'agenda. */
class AgendaState(
    val granted: Boolean,
    /** null finché non sono stati letti (o manca il permesso). */
    val today: List<CalendarEvent>?,
    val tomorrow: List<CalendarEvent>?,
    val requestPermission: () -> Unit,
) {
    /**
     * Gli impegni di oggi; vuoto se non c'è niente da dire. La sera, finiti tutti, l'agenda sparisce:
     * domani e i giorni dopo li elenca "Prossimi giorni".
     */
    fun shown(now: LocalDateTime = LocalDateTime.now()): List<CalendarEvent> {
        val list = today.orEmpty()
        if (now.hour < Briefing.EVENING_HOUR) return list
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return if (list.none { it.allDay || it.end > nowMs }) emptyList() else list
    }

    /** Con il permesso e nessun impegno la card sparisce: non serve leggere "nessun impegno". */
    val hasContent: Boolean get() = !granted || shown().isNotEmpty()
}

@Composable
fun rememberAgenda(refreshKey: Int = 0): AgendaState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var today by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var tomorrow by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        reload++
        if (!it && CalendarAsk.shouldHide(CalendarAsk.recordDenial(context))) hideAgenda(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = CalendarRepository.hasPermission(context)
        reload++
    }
    LaunchedEffect(granted, reload, refreshKey) {
        if (granted) {
            val day = LocalDate.now()
            today = CalendarRepository.eventsOn(context, day)
            tomorrow = CalendarRepository.eventsOn(context, day.plusDays(1))
        }
    }
    return AgendaState(granted, today, tomorrow) { launcher.launch(Manifest.permission.READ_CALENDAR) }
}

/** Niente più inviti per il calendario: si può sempre consentire dal Calendario. */
internal fun hideAgenda(context: Context) {
    CalendarAsk.setOff(context)
    Toast.makeText(context, "Ok, niente agenda: la puoi consentire dal Calendario", Toast.LENGTH_LONG).show()
}

/** Scadenza di un promemoria, in rosso se già passata. */
@Composable
fun DueText(dueAt: Long, repeat: Repeat? = null) {
    val overdue = dueAt < System.currentTimeMillis()
    Text(
        (if (overdue) "Scaduto · " else "") + dueLabel(dueAt) + (repeat?.let { " · ${it.label}" } ?: ""),
        style = MaterialTheme.typography.labelMedium,
        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

/**
 * "Prossimi giorni": i prossimi giorni in una card sola, divisi per giorno. Le stesse cose stanno,
 * senza limite di giorni, in Note > Prossimi giorni; oggi sta in "Adesso". Il giorno si scrive solo alla
 * sua prima riga, così la settimana si legge a colpo d'occhio.
 */
@Composable
fun NextThingsCard(items: List<UpcomingItem>, today: LocalDate, onOpenAll: () -> Unit) {
    val shown = items.take(Upcoming.MAX)
    Section(title = "Prossimi giorni") {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (shown.isEmpty()) {
                Text(
                    "Niente nei prossimi ${Upcoming.DAYS} giorni.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            shown.forEachIndexed { index, item ->
                val first = index == 0 || shown[index - 1].day != item.day
                if (first && index > 0) RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable(onClick = onOpenAll)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (first) Upcoming.dayLabel(item.day, today) else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.width(72.dp),
                    )
                    Icon(
                        item.kind.icon(),
                        contentDescription = null,
                        tint = item.event?.color?.takeIf { it != 0 }?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (item.time?.let { "$it  " } ?: "") + item.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Toccando la card si apre tutto quello che arriva, senza limite di giorni.
            val more = items.size - shown.size
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAll)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (more > 0) "Tutto quello che arriva · altre $more" else "Tutto quello che arriva",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** L'icona piccola di una riga di "Prossimi giorni": evento, promemoria, data, pacco o addebito. */
private fun BriefKind.icon(): ImageVector = when (this) {
    BriefKind.EVENT -> Icons.Outlined.Event
    BriefKind.TASK -> Icons.Outlined.Checklist
    BriefKind.DATE -> Icons.Outlined.Celebration
    BriefKind.PARCEL -> Icons.Outlined.LocalShipping
    BriefKind.MONEY -> Icons.AutoMirrored.Outlined.ReceiptLong
    else -> Icons.Outlined.Event
}

/**
 * Spese, in Oggi dalla 1.186: il totale del mese, il budget e il prossimo addebito. Si tocca per la pagina
 * intera (mesi, categorie, fisse, scontrino). Compare solo quando c'è qualcosa da dire.
 */
@Composable
fun MoneyCard(data: NotesData, today: LocalDate, onOpen: () -> Unit) {
    val month = YearMonth.from(today)
    val total = Expenses.total(data.expenses, month)
    val budget = data.budgetCents
    val next = data.fixedExpenses.minByOrNull { it.nextDate(today) }
    Section(title = "Spese", modifier = Modifier.captureTarget(CaptureMode.EXPENSE)) {
        Row(
            Modifier
                .fillMaxWidth()
                .bouncyPress(0.98f)
                .clickable(onClick = onOpen)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(Expenses.format(total), style = MaterialTheme.typography.headlineSmall)
                if (budget != null) {
                    val over = total > budget
                    Text(
                        if (over) "Budget superato di ${Expenses.format(total - budget)}"
                        else "Restano ${Expenses.format(budget - total)} del budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                next?.let {
                    val day = it.nextDate(today)
                    Text(
                        "Prossimo addebito: ${it.text.ifEmpty { it.category.label }} ${Expenses.format(it.cents)} · " +
                            if (day == today) "oggi" else Upcoming.dayLabel(day, today).lowercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Si mostra la card Spese solo con qualcosa dentro: una spesa, una fissa o un budget. */
val NotesData.hasMoney: Boolean get() = expenses.isNotEmpty() || fixedExpenses.isNotEmpty() || budgetCents != null
