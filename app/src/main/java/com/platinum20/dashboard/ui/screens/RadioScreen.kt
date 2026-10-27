package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.SavedLinks
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.remember

/** La parte Radio di "Radio e podcast": le più ascoltate in Italia, ricerca nel mondo, preferite (la sveglia sta in Impostazioni). */
@Composable
internal fun RadioBody() {
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

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.isBlank()) searching = false
            },
            placeholder = { Text("Cerca una radio in tutto il mondo") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            colors = canvasFieldColors(),
            shape = MaterialTheme.shapes.large,
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
                stationSection("Preferite", favorites, now)
                stationSection("Ascoltate di recente", recent.filter { r -> favorites.none { it.id == r.id } }, now)
                item(key = "popular-header") { SectionTitle("Le più ascoltate in Italia") }
            }
            items(list.data.orEmpty(), key = { "list-${it.id}" }) { StationRow(it, now) }
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
    SectionHeader(text, Modifier.padding(top = 8.dp))
}

@Composable
private fun StationRow(station: RadioStation, now: RadioNow?) {
    val favorites by RadioStore.favorites.collectAsState()
    val favorite = favorites.any { it.id == station.id }
    val playing = now?.station?.id == station.id
    val haptic = rememberHaptic()
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card,
        border = if (playing) null else cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f).clip(MaterialTheme.shapes.large)
            .tapOrSave({ SavedLinks.radio(station) }) { RadioStore.play(station) },
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

