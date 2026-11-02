package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.radio.RadioAlarm
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore

/**
 * Sveglia con la radio in Impostazioni › Cosa ti avvisa (stava nella pagina Radio): ora, giorni e
 * stazione, scelta tra preferite e ascoltate di recente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadioAlarmSetting() {
    val context = LocalContext.current
    remember { RadioAlarm.init(context); RadioStore.init(context) }
    val alarm by RadioAlarm.settings.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    val now by RadioStore.now.collectAsState()
    val choices = (favorites + recent).distinctBy { it.id }
    val suggested = alarm.station ?: now?.station ?: choices.firstOrNull()
    var editing by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberHaptic()
    SettingsItem(
        icon = Icons.Outlined.Alarm,
        accent = TileAccent.ORANGE,
        title = "Sveglia con la radio",
        subtitle = when {
            suggested == null -> "Ascolta una stazione per sceglierla"
            alarm.enabled -> "${alarm.timeLabel} · ${alarm.daysLabel} · ${suggested.name}"
            else -> "Spenta · ${suggested.name}"
        },
        onClick = { editing = true },
        trailing = {
            Switch(
                checked = alarm.enabled,
                enabled = suggested != null,
                onCheckedChange = { on ->
                    haptic(if (on) Haptic.CONFIRM else Haptic.TICK)
                    RadioAlarm.update(context, alarm.copy(enabled = on, station = suggested))
                },
            )
        },
    )
    if (editing) {
        val time = rememberTimePickerState(initialHour = alarm.hour, initialMinute = alarm.minute, is24Hour = true)
        var weekdays by remember { mutableStateOf(alarm.weekdaysOnly) }
        var station by remember { mutableStateOf<RadioStation?>(suggested) }
        CanvasDialog(
            onDismissRequest = { editing = false },
            title = { Text("Sveglia con la radio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(state = time)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CanvasChip(selected = weekdays, onClick = { weekdays = true }, label = "Lun-ven")
                        CanvasChip(selected = !weekdays, onClick = { weekdays = false }, label = "Tutti i giorni")
                    }
                    if (choices.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { s -> CanvasChip(selected = station?.id == s.id, onClick = { station = s }, label = s.name) }
                        }
                    }
                    Text(
                        station?.let { "Stazione: ${it.name}. Parte piano e sale in 30 secondi; se non c'è rete suona la sveglia del telefono." }
                            ?: "Ascolta prima una stazione in Radio e podcast: la sveglia userà quella.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = station != null, onClick = {
                    RadioAlarm.update(context, alarm.copy(enabled = true, hour = time.hour, minute = time.minute, weekdaysOnly = weekdays, station = station))
                    editing = false
                }) { Text("Attiva") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Annulla") } },
        )
    }
}
