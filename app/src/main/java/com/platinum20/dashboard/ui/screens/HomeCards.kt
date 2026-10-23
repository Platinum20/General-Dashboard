package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import com.platinum20.dashboard.today.TodayTaskPicker
import com.platinum20.dashboard.today.DayItem
import com.platinum20.dashboard.today.DayTimeline
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
     * domani lo racconta la card "Domani", non serve una seconda lista.
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

private val hourFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun EventRow(event: CalendarEvent, past: Boolean, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val time = if (event.allDay) "Tutto il giorno" else
        Instant.ofEpochMilli(event.begin).atZone(zone).format(hourFormat) + " – " +
            Instant.ofEpochMilli(event.end).atZone(zone).format(hourFormat)
    val alpha = if (past) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (event.color != 0) Color(event.color).copy(alpha = alpha) else MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (event.location.isNotBlank()) "$time · ${event.location}" else time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

/** Per quanto resta visibile "Annulla" dopo una spunta. */
private const val UNDO_MS = 5_000L

/** In Oggi solo le date vicine: le altre restano in Note > Date. */
const val SOON_DAYS = 7L

val NotesData.soon: List<Countdown> get() = upcoming.filter { it.daysLeft() <= SOON_DAYS }

@Composable
fun DayCard(agenda: AgendaState, onOpenNotes: () -> Unit, tomorrowCard: Boolean = false) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val open = data.openTasks
    // Dei promemoria solo quello che conta adesso: il resto è a un tocco, in Note.
    val tasks = remember(open, tomorrowCard) { TodayTaskPicker.pick(open, LocalDateTime.now(), tomorrowCard = tomorrowCard) }
    val events = if (agenda.granted && agenda.today != null) agenda.shown() else emptyList()
    val soon = data.soon
    val items = remember(events, tasks, soon) { DayTimeline.build(events, tasks.shown, soon, LocalDate.now()) }
    // L'ultimo promemoria spuntato da qui: per qualche secondo si può annullare.
    var lastDone by remember { mutableStateOf<TaskItem?>(null) }
    LaunchedEffect(lastDone) {
        if (lastDone != null) {
            delay(UNDO_MS)
            lastDone = null
        }
    }
    var editing by remember { mutableStateOf<Countdown?>(null) }

    Section(
        title = "La giornata",
        action = if (agenda.granted) "Calendario" else "Note",
        onAction = { if (agenda.granted) inApp?.openCalendar?.invoke() else onOpenNotes() },
    ) {
        Column(Modifier.animateContentSize(Springs.snappy())) {
            if (items.isEmpty() && lastDone == null) {
                Text(
                    if (open.isEmpty()) "Niente in programma. Tocca + in basso per aggiungere."
                    else "Niente in scadenza oggi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            val now = System.currentTimeMillis()
            items.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                when (item) {
                    is DayItem.Event -> EventRow(item.event, past = !item.event.allDay && item.event.end < now) {
                        inApp?.openEvent?.invoke(item.event)
                    }
                    is DayItem.Task -> TaskRow(item.task) {
                        haptic(Haptic.CONFIRM)
                        lastDone = item.task
                        NotesStore.toggleTask(item.task.id)
                    }
                    is DayItem.Date -> DateRow(item.countdown, item.daysLeft) { editing = item.countdown }
                }
            }
            if (tasks.more > 0) {
                RowDivider()
                Text(
                    "+ altri ${tasks.more} promemoria in Note",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenNotes)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            lastDone?.let { undone ->
                RowDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (undone.repeat != null) "Spostato alla prossima volta: ${undone.text}" else "Fatto: ${undone.text}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            haptic(Haptic.TICK)
                            NotesStore.restoreTask(undone)
                            lastDone = null
                        },
                        modifier = Modifier.bouncyPress(),
                    ) { Text("Annulla") }
                }
            }
        }
    }
    editing?.let { CountdownDialog(countdown = it, onDismiss = { editing = null }) }
}

@Composable
private fun TaskRow(task: TaskItem, onDone: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onDone)
            .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onDone() })
        Column {
            Text(task.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.dueAt?.let { DueText(it, task.repeat) }
        }
    }
}

/** Compleanni, viaggi e scadenze dei prossimi giorni: titolo e quanto manca. */
@Composable
private fun DateRow(item: Countdown, days: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Celebration,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.dateLine(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            when (days) {
                0L -> "Oggi"
                1L -> "Domani"
                else -> "tra $days giorni"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
