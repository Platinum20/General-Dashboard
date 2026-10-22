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
    /** La sera, finiti gli impegni di oggi, l'agenda mostra domani. */
    fun showsTomorrow(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (now.hour < Briefing.EVENING_HOUR) return false
        val nowMs = System.currentTimeMillis()
        return today.orEmpty().none { it.allDay || it.end > nowMs } && !tomorrow.isNullOrEmpty()
    }

    /** Gli impegni da mostrare ora; vuoto se non c'è niente da dire. */
    fun shown(): List<CalendarEvent> = if (showsTomorrow()) tomorrow.orEmpty() else today.orEmpty()

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

/** Toglie l'Agenda da Oggi: resta riaccendibile da Personalizza. */
internal fun hideAgenda(context: Context) {
    AppSettings.setCardVisible(HomeCard.CALENDAR, false)
    Toast.makeText(context, "Agenda nascosta: la riaccendi da Personalizza", Toast.LENGTH_LONG).show()
}

@Composable
fun CalendarCard(agenda: AgendaState) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val granted = agenda.granted
    val tomorrow = agenda.showsTomorrow()
    val events = if (granted && agenda.today != null) agenda.shown() else null

    Section(
        title = if (tomorrow) "Domani" else "Agenda",
        action = if (granted) "Calendario" else null,
        onAction = { inApp?.openCalendar?.invoke() },
        trailing = {
            if (granted) {
                IconButton(onClick = { inApp?.newEvent?.invoke(if (tomorrow) LocalDate.now().plusDays(1) else LocalDate.now()) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Nuovo evento", modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            val list = events
            when {
                // Senza permesso: una riga sola con il pulsante a destra, non un blocco di testo.
                !granted -> Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Vedi qui gli impegni di oggi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { hideAgenda(context) }) { Text("Non ora") }
                    FilledTonalButton(onClick = agenda.requestPermission) { Text("Consenti") }
                }
                list == null -> Spacer(Modifier.height(8.dp))
                list.isEmpty() -> Text(
                    "Nessun impegno oggi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                else -> {
                    val now = System.currentTimeMillis()
                    // Gli eventi già finiti restano visibili ma attenuati.
                    list.take(6).forEach { EventRow(it, past = !it.allDay && it.end < now) { inApp?.openEvent?.invoke(it) } }
                    if (list.size > 6) {
                        Text(
                            "e altri ${list.size - 6}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
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

@Composable
fun TasksCard(onOpenNotes: () -> Unit) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val open = data.openTasks
    // Solo quello che conta adesso: il resto è a un tocco, in Note.
    val today = remember(open) { TodayTaskPicker.pick(open, LocalDateTime.now()) }
    // L'ultimo promemoria spuntato da qui: per qualche secondo si può annullare.
    var lastDone by remember { mutableStateOf<TaskItem?>(null) }
    LaunchedEffect(lastDone) {
        if (lastDone != null) {
            delay(UNDO_MS)
            lastDone = null
        }
    }

    Section(
        title = if (open.isEmpty()) "Da fare" else "Da fare · ${open.size}",
        action = "Tutte",
        onAction = onOpenNotes,
    ) {
        Column(Modifier.animateContentSize(Springs.snappy())) {
            if (open.isEmpty() && lastDone == null) {
                Text(
                    "Niente da fare. Tocca + in basso per aggiungere un promemoria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (today.shown.isEmpty() && open.isNotEmpty() && lastDone == null) {
                Text(
                    "Niente in scadenza oggi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            today.shown.forEachIndexed { index, task ->
                val done = { haptic(Haptic.CONFIRM); lastDone = task; NotesStore.toggleTask(task.id) }
                if (index > 0) RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable(onClick = done)
                        .padding(start = 4.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.done, onCheckedChange = { done() })
                    Column {
                        Text(task.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        task.dueAt?.let { DueText(it, task.repeat) }
                    }
                }
            }
            if (today.more > 0) {
                RowDivider()
                Text(
                    "+ altri ${today.more} in Note",
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
fun CountdownCard() {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Countdown?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val upcoming = data.soon

    Section(
        title = "In arrivo",
        trailing = {
            IconButton(onClick = { adding = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = "Aggiungi", modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (upcoming.isEmpty()) {
                Text(
                    "Aggiungi un compleanno, un viaggio o una scadenza per vedere quanti giorni mancano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            (if (showAll) upcoming else upcoming.take(4)).forEachIndexed { index, item ->
                val days = item.daysLeft()
                if (index > 0) RowDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyPress(0.98f)
                        .clickable { editing = item }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            item.dateLine(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            when (days) {
                                0L -> "Oggi! 🎉"
                                1L -> "Domani"
                                else -> "$days"
                            },
                            style = if (days > 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (days > 1) {
                            Text("giorni", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (upcoming.size > 4) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(start = 8.dp)) {
                    Text(if (showAll) "Mostra meno" else "Mostra tutti (${upcoming.size})")
                }
            }
        }
    }

    if (adding) CountdownDialog(countdown = null, onDismiss = { adding = false })
    editing?.let { CountdownDialog(countdown = it, onDismiss = { editing = null }) }
}
