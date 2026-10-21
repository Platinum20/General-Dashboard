package com.platinum20.dashboard.ui.screens

import android.Manifest
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.platinum20.dashboard.notes.NotesStore
import java.time.Instant
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
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        trailing()
    }
}

@Composable
fun CalendarCard() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    var events by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var reload by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        reload++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = CalendarRepository.hasPermission(context)
        reload++
    }
    LaunchedEffect(granted, reload) {
        if (granted) events = CalendarRepository.eventsOn(context)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(
                icon = Icons.Outlined.CalendarMonth,
                title = "Agenda di oggi",
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
                !granted -> Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        "Consenti l'accesso al calendario per vedere qui gli impegni di oggi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) },
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    ) { Text("Consenti") }
                }
                list == null -> Spacer(Modifier.height(8.dp))
                list.isEmpty() -> Text(
                    "Nessun impegno oggi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
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
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val open = data.openTasks

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(vertical = 8.dp)) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { NotesStore.toggleTask(task.id) }
                        .padding(start = 8.dp, end = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = task.done, onCheckedChange = { NotesStore.toggleTask(task.id) })
                    Text(task.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (open.size > 5) {
                Text(
                    "e altri ${open.size - 5}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Aggiungi un promemoria") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    NotesStore.addTask(draft)
                    draft = ""
                }),
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(onClick = {
                            NotesStore.addTask(draft)
                            draft = ""
                        }) { Icon(Icons.Outlined.Add, contentDescription = "Aggiungi") }
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
