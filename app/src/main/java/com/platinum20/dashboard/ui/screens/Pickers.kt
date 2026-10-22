package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.work.MorningWorker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private val longDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

/** "Oggi 18:00", "Domani 9:30", "ven 25 ott 10:00". */
fun dueLabel(dueAt: Long): String {
    val dt = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    val day = when (dt.toLocalDate()) {
        today -> "Oggi"
        today.plusDays(1) -> "Domani"
        today.minusDays(1) -> "Ieri"
        else -> dt.toLocalDate().format(dayFormat)
    }
    return "$day ${dt.format(timeFormat)}"
}

fun longDate(date: LocalDate): String = date.format(longDayFormat).replaceFirstChar { it.uppercase() }

/** I DatePicker di Material lavorano in millisecondi UTC a mezzanotte. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toPickerMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.fromPickerMillis()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) {
        DatePicker(state = state)
    }
}

/** Prima il giorno, poi l'ora. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickDialog(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val start = remember {
        initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
            ?: LocalDateTime.now().plusHours(1).withMinute(0)
    }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val timeState = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)

    val picked = date
    if (picked == null) {
        DatePickDialog(initial = start.toLocalDate(), onDismiss = onDismiss, onConfirm = { date = it })
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(longDate(picked)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(picked.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli())
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { date = null }) { Text("Indietro") } },
        )
    }
}

/**
 * Modifica di un promemoria: testo e scadenza con avviso.
 * [onDeleted] riceve il promemoria eliminato, così chi apre il dialog può offrire "Annulla".
 */
@Composable
fun TaskEditDialog(task: TaskItem, onDismiss: () -> Unit, onDeleted: (TaskItem) -> Unit = {}) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(task.text) }
    var due by remember { mutableStateOf(task.dueAt) }
    var repeat by remember { mutableStateOf(task.repeat) }
    var repeatMenu by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Promemoria") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        due?.let { "Avviso: " + dueLabel(it) } ?: "Aggiungi scadenza e avviso",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (due != null) {
                        IconButton(onClick = { due = null; repeat = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Togli scadenza")
                        }
                    }
                }
                // Ripetere ha senso solo con una scadenza da spostare.
                if (due != null) {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { repeatMenu = true }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                repeat?.let { "Si ripete " + it.label } ?: "Non si ripete",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                            DropdownMenuItem(text = { Text("Non si ripete") }, onClick = { repeat = null; repeatMenu = false })
                            Repeat.entries.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.label.replaceFirstChar { it.uppercase() }) },
                                    onClick = { repeat = r; repeatMenu = false },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) {
                    NotesStore.deleteTask(task.id)
                    onDeleted(task)
                } else {
                    NotesStore.editTask(task.id, text)
                    NotesStore.setTaskRepeat(task.id, repeat.takeIf { due != null })
                    NotesStore.setTaskDue(task.id, due)
                }
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = {
            TextButton(onClick = {
                NotesStore.deleteTask(task.id)
                onDeleted(task)
                onDismiss()
            }) { Text("Elimina") }
        },
    )

    if (picking) {
        DateTimePickDialog(
            initial = due,
            onDismiss = { picking = false },
            onConfirm = {
                due = it
                picking = false
                if (Build.VERSION.SDK_INT >= 33 && !MorningWorker.canNotify(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
}

/**
 * Sotto il campo di un nuovo promemoria: mostra quando suonerà l'avviso
 * mentre si scrive "domani alle 9…", così si vede subito se è stato capito.
 */
@Composable
fun QuickPreview(draft: String, modifier: Modifier = Modifier) {
    val entry = remember(draft) { if (draft.isBlank()) null else QuickParse.parse(draft) }
    val due = entry?.due ?: return
    val ms = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            dueLabel(ms) + (entry.repeat?.let { " · ${it.label}" } ?: "") + " · \"${entry.text}\"",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Nuova abitudine o modifica (nome, eliminazione) di una esistente. [onDeleted] offre "Annulla". */
@Composable
fun HabitDialog(habit: Habit?, onDismiss: () -> Unit, onDeleted: (Habit) -> Unit = {}) {
    var title by remember { mutableStateOf(habit?.title.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (habit == null) "Nuova abitudine" else "Abitudine") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Es. Bere 2 litri d'acqua") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (habit != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Serie attuale: ${habit.streak()} giorni · in tutto ${habit.done.size} giorni",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                NotesStore.saveHabit(habit?.id, title)
                onDismiss()
            }, enabled = title.isNotBlank()) { Text("Salva") }
        },
        dismissButton = {
            if (habit != null) {
                TextButton(onClick = {
                    NotesStore.deleteHabit(habit.id)
                    onDismiss()
                    onDeleted(habit)
                }) { Text("Elimina") }
            } else {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}

/** Nuovo conto alla rovescia o modifica di uno esistente. */
@Composable
fun CountdownDialog(countdown: Countdown?, onDismiss: () -> Unit, onDeleted: (Countdown) -> Unit = {}) {
    var title by remember { mutableStateOf(countdown?.title.orEmpty()) }
    var date by remember { mutableStateOf(countdown?.date ?: LocalDate.now().plusDays(7)) }
    var yearly by remember { mutableStateOf(countdown?.yearly ?: false) }
    var picking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (countdown == null) "Nuovo conto alla rovescia" else "Conto alla rovescia") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Es. Compleanno di Sara, Vacanze") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { picking = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Text(longDate(date), style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { yearly = !yearly },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ogni anno", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = yearly, onCheckedChange = hapticToggle { yearly = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    NotesStore.saveCountdown(countdown?.id, title, date, yearly)
                    onDismiss()
                },
                enabled = title.isNotBlank(),
            ) { Text("Salva") }
        },
        dismissButton = {
            if (countdown != null) {
                TextButton(onClick = {
                    NotesStore.deleteCountdown(countdown.id)
                    onDismiss()
                    onDeleted(countdown)
                }) { Text("Elimina") }
            } else {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )

    if (picking) {
        DatePickDialog(initial = date, onDismiss = { picking = false }, onConfirm = {
            date = it
            picking = false
        })
    }
}
