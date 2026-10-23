package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.radio.RadioNow
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.radio.RadioAlarm
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn

/** Radio in streaming: le più ascoltate in Italia, ricerca in tutto il mondo e preferite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    val popular by RadioStore.popular.collectAsState()
    val search by RadioStore.search.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    val now by RadioStore.now.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { RadioStore.loadPopular() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Radio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
        bottomBar = { now?.let { RadioNowBar(it) } },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isBlank()) searching = false
                },
                placeholder = { Text("Cerca una radio in tutto il mondo") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    searching = true
                    RadioStore.search(query)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list: Loadable<List<RadioStation>> = if (searching) search else popular
            FeedList(list, empty = "Nessuna radio trovata") {
                if (!searching) {
                    item(key = "alarm") { AlarmCard(suggested = now?.station ?: recent.firstOrNull() ?: favorites.firstOrNull()) }
                    stationSection("Preferite", favorites, now)
                    stationSection("Ascoltate di recente", recent.filter { r -> favorites.none { it.id == r.id } }, now)
                    item(key = "popular-header") { SectionTitle("Le più ascoltate in Italia") }
                }
                items(list.data.orEmpty(), key = { "list-${it.id}" }) { StationRow(it, now) }
            }
        }
    }
}

private fun LazyListScope.stationSection(title: String, stations: List<RadioStation>, now: RadioNow?) {
    if (stations.isEmpty()) return
    item(key = "header-$title") { SectionTitle(title) }
    items(stations, key = { "$title-${it.id}" }) { StationRow(it, now) }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StationRow(station: RadioStation, now: RadioNow?) {
    val favorites by RadioStore.favorites.collectAsState()
    val favorite = favorites.any { it.id == station.id }
    val playing = now?.station?.id == station.id
    val haptic = rememberHaptic()
    Surface(
        onClick = { RadioStore.play(station) },
        shape = MaterialTheme.shapes.large,
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card,
        border = if (playing) null else cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StationLogo(station, 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(station.tags, station.country, if (station.bitrate > 0) "${station.bitrate} kbps" else "")
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                haptic(if (favorite) Haptic.TICK else Haptic.CONFIRM)
                RadioStore.toggleFavorite(station)
            }) {
                Icon(
                    if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorite) "Togli dalle preferite" else "Aggiungi alle preferite",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun StationLogo(station: RadioStation, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Radio, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (station.logo.isNotBlank()) {
            AsyncImage(model = station.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}

@Composable
private fun RadioNowBar(now: RadioNow) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StationLogo(now.station, 44)
            Column(Modifier.weight(1f)) {
                Text(now.station.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    now.error ?: now.track.ifBlank { if (now.buffering) "Collegamento…" else "In diretta" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (now.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (now.buffering) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            FilledIconButton(onClick = { RadioStore.togglePause() }) {
                Icon(if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (now.playing) "Pausa" else "Riproduci")
            }
            IconButton(onClick = { RadioStore.stop() }) {
                Icon(Icons.Filled.Stop, contentDescription = "Ferma")
            }
        }
    }
}

/** Sveglia con la radio: ora, giorni e stazione (quella in ascolto o l'ultima ascoltata). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmCard(suggested: RadioStation?) {
    val context = LocalContext.current
    remember { RadioAlarm.init(context) }
    val alarm by RadioAlarm.settings.collectAsState()
    var editing by rememberSaveable { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val station = alarm.station ?: suggested
    CanvasCard(onClick = { editing = true }, modifier = Modifier.fillMaxWidth().bouncyPress(0.98f), shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Alarm, TileAccent.ORANGE)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Sveglia con la radio", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        station == null -> "Ascolta una stazione per sceglierla"
                        alarm.enabled -> "${alarm.timeLabel} · ${alarm.daysLabel} · ${station.name}"
                        else -> "Spenta · ${station.name}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = alarm.enabled,
                enabled = station != null,
                onCheckedChange = { on ->
                    haptic(if (on) Haptic.CONFIRM else Haptic.TICK)
                    RadioAlarm.update(context, alarm.copy(enabled = on, station = station))
                },
            )
        }
    }
    if (editing) {
        val time = rememberTimePickerState(initialHour = alarm.hour, initialMinute = alarm.minute, is24Hour = true)
        var weekdays by remember { mutableStateOf(alarm.weekdaysOnly) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Sveglia con la radio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimePicker(state = time)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CanvasChip(selected = weekdays, onClick = { weekdays = true }, label = "Lun-ven")
                        CanvasChip(selected = !weekdays, onClick = { weekdays = false }, label = "Tutti i giorni")
                    }
                    Text(
                        station?.let { "Stazione: ${it.name}. Parte piano e sale in 30 secondi; se non c'è rete suona la sveglia del telefono." }
                            ?: "Ascolta prima una stazione: la sveglia userà quella.",
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
