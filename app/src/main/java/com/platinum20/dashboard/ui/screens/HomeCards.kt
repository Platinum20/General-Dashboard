package com.platinum20.dashboard.ui.screens

import android.Manifest
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
import androidx.compose.material3.ElevatedCard
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
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.Briefing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Intestazione comune delle card della Dashboard. */
@Composable
fun CardHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        trailing()
    }
}

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

@Composable
fun CalendarCard(agenda: AgendaState) {
    val context = LocalContext.current
    val granted = agenda.granted
    val tomorrow = agenda.showsTomorrow()
    val events = if (granted && agenda.today != null) agenda.shown() else null

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(
                icon = Icons.Outlined.CalendarMonth,
                title = if (tomorrow) "Domani" else "Agenda di oggi",
                onClick = if (granted) ({ CalendarRepository.openCalendar(context) }) else null,
                trailing = {
                    if (granted) {
                        IconButton(onClick = { CalendarRepository.newEvent(context) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Nuovo evento")
                        }
                    }
                },
            )
            val list = events
            when {
                // Senza permesso: una riga sola con il pulsante a destra, non un blocco di testo.
                !granted -> Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Vedi qui gli impegni di oggi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(onClick = agenda.requestPermission) { Text("Consenti") }
                }
                list == null -> Spacer(Modifier.height(8.dp))
                list.isEmpty() -> Text(
                    "Nessun impegno oggi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                else -> {
                    val now = System.currentTimeMillis()
                    // Gli eventi già finiti restano visibili ma attenuati.
                    list.take(6).forEach { EventRow(it, past = !it.allDay && it.end < now) { CalendarRepository.openEvent(context, it) } }
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
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        if (NotesStore.addTask(draft) != null) haptic(Haptic.CONFIRM)
        draft = ""
    }
    val open = data.openTasks
    // L'ultimo promemoria spuntato da qui: per qualche secondo si può annullare.
    var lastDone by remember { mutableStateOf<TaskItem?>(null) }
    LaunchedEffect(lastDone) {
        if (lastDone != null) {
            delay(UNDO_MS)
            lastDone = null
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.animateContentSize(Springs.snappy()).padding(vertical = 8.dp)) {
            CardHeader(
                icon = Icons.Outlined.Checklist,
                title = if (open.isEmpty()) "Da fare" else "Da fare · ${open.size}",
                onClick = onOpenNotes,
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
            open.take(5).forEach { task ->
                val done = { haptic(Haptic.CONFIRM); lastDone = task; NotesStore.toggleTask(task.id) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = done)
                        .padding(start = 8.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.done, onCheckedChange = { done() })
                    Column {
                        Text(task.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        task.dueAt?.let { DueText(it, task.repeat) }
                    }
                }
            }
            if (open.size > 5) {
                Text(
                    "e altri ${open.size - 5}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            lastDone?.let { undone ->
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
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Aggiungi un promemoria") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(onClick = add) { Icon(Icons.Outlined.Add, contentDescription = "Aggiungi") }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            QuickPreview(draft, Modifier.padding(start = 28.dp, bottom = 8.dp))
        }
    }
}

/** Scadenza di un promemoria, in rosso se già passata. */
@Composable
fun DueText(dueAt: Long, repeat: Repeat? = null) {
    val overdue = dueAt < System.currentTimeMillis()
    Text(
        (if (overdue) "Scaduto · " else "⏰ ") + dueLabel(dueAt) + (repeat?.let { " · ${it.label}" } ?: ""),
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

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(
                icon = Icons.Outlined.HourglassTop,
                title = "In arrivo",
                trailing = {
                    IconButton(onClick = { adding = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Aggiungi")
                    }
                },
            )
            if (upcoming.isEmpty()) {
                Text(
                    "Aggiungi un compleanno, un viaggio o una scadenza per vedere quanti giorni mancano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            (if (showAll) upcoming else upcoming.take(4)).forEach { item ->
                val days = item.daysLeft()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = item }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            longDate(item.nextDate()) + if (item.yearly) " · ogni anno" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        when (days) {
                            0L -> "Oggi! 🎉"
                            1L -> "Domani"
                            else -> "$days giorni"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
