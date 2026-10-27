package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureFeedback
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
import com.platinum20.dashboard.today.Upcoming
import com.platinum20.dashboard.today.UpcomingItem
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.captureTarget
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.platinum20.dashboard.autofill.AutoFill
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.AutoAwesome
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

/** Niente più inviti per il calendario: si può sempre consentire da Info app. */
internal fun hideAgenda(context: Context) {
    CalendarAsk.setOff(context)
    CaptureFeedback.tell(context, "Ok, niente calendario: lo puoi consentire da Info app", actionLabel = "Info app", action = { openAppInfo(context) })
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
                    "Niente in programma nei prossimi giorni.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            // La linea del tempo: i nomi a sinistra, la linea con i giorni a destra; fra due giorni lontani
            // la linea si allunga. Uguale in Note › Prossimi giorni, dove c'è tutto.
            shown.forEachIndexed { index, item ->
                val first = index == 0 || shown[index - 1].day != item.day
                TimelineRow(
                    TimelineEntry(
                        key = "$index",
                        kind = item.kind,
                        text = (item.time?.let { "$it  " } ?: "") + item.text,
                        dayLabel = if (first) Upcoming.dayLabel(item.day, today) else null,
                        gapDp = if (first && index > 0) Upcoming.gapDp(shown[index - 1].day, item.day) else 0f,
                        // Il segno del mese quando si passa al mese dopo (anche in cima, se si comincia già dal prossimo).
                        month = if (first) Upcoming.monthBreak(if (index == 0) today else shown[index - 1].day, item.day, today) else null,
                        color = item.event?.color ?: 0,
                    ),
                    top = index > 0,
                    bottom = index < shown.lastIndex,
                    onClick = onOpenAll,
                )
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

/**
 * Spese in Oggi: le ultime [Upcoming.MAX] spese sulla stessa linea del tempo di "Prossimi giorni" (Antonio),
 * con icona e colore della categoria. Sotto, il totale del mese; si tocca per la pagina intera.
 */
@Composable
fun MoneyCard(data: NotesData, today: LocalDate, onOpen: () -> Unit) {
    val month = YearMonth.from(today)
    val total = Expenses.total(data.expenses, month)
    val entries = expenseEntries(data.expenses, today).take(Upcoming.MAX)
    Section(title = "Spese", modifier = Modifier.captureTarget(CaptureMode.EXPENSE)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (entries.isEmpty()) {
                Text(
                    "Nessuna spesa ancora.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            entries.forEachIndexed { index, (entry, _) ->
                TimelineRow(entry, top = index > 0, bottom = index < entries.lastIndex, onClick = onOpen)
            }
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tutte le spese · " + month.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN) + " " + Expenses.format(total),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** Si mostra la card Spese solo con qualcosa dentro: una spesa o una fissa. */
val NotesData.hasMoney: Boolean get() = expenses.isNotEmpty() || fixedExpenses.isNotEmpty()
