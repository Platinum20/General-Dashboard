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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.DateIdea
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.HabitIdea
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.work.MorningWorker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
        // Di vetro come le altre finestre.
        modifier = Modifier.glassDialog(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
    ) {
        DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = Color.Transparent))
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
        CanvasDialog(
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

/** Solo l'ora (l'avviso di un'abitudine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text("Avviso alle") },
        text = { TimePicker(state = timeState) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(timeState.hour, timeState.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
