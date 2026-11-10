package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.radio.RadioGenre
import androidx.compose.foundation.lazy.itemsIndexed
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

/** La parte radio della pagina unica "Radio e podcast": preferite, recenti e le più ascoltate in Italia. */
internal fun LazyListScope.radioSections(
    popular: Loadable<List<RadioStation>>,
    favorites: List<RadioStation>,
    recent: List<RadioStation>,
    now: RadioNow?,
) {
    stationSection("Radio preferite", favorites, now)
    stationSection("Radio ascoltate di recente", recent.filter { r -> favorites.none { it.id == r.id } }, now)
    val top = popular.data.orEmpty()
    if (top.isNotEmpty()) {
        item(key = "popular-header") { SectionTitle("Le radio più ascoltate in Italia") }
        itemsIndexed(top, key = { _, s -> "list-${s.id}" }) { i, s -> StationRow(s, now, i == 0, i == top.lastIndex) }
    } else if (popular.loading) item(key = "popular-loading") {
        PlaceholderList(rows = 4, label = "Carico le radio")
    }
}

private fun LazyListScope.stationSection(title: String, stations: List<RadioStation>, now: RadioNow?) {
    if (stations.isEmpty()) return
    item(key = "header-$title") { SectionTitle(title) }
    itemsIndexed(stations, key = { _, s -> "$title-${s.id}" }) { i, s -> StationRow(s, now, i == 0, i == stations.lastIndex) }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text, Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun StationRow(station: RadioStation, now: RadioNow?, first: Boolean, last: Boolean) {
    val favorites by RadioStore.favorites.collectAsState()
    val favorite = favorites.any { it.id == station.id }
    val playing = now?.station?.id == station.id
    val haptic = rememberHaptic()
    // Le radio di una sezione sono righe di un'unica card (1.423, punto 8 A): 12 a schermo invece di 9.
    Box(
        Modifier.fillMaxWidth()
            .cardGroupItem(first, last, if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.card)
            .tapOrSave({ SavedLinks.radio(station) }) { RadioStore.play(station) },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StationLogo(station, 48)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    // Il genere in italiano, non i tag in inglese con i kbps (punto 42).
                    RadioGenre.of(station.tags) ?: "Radio",
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
        // Senza logo (o se non si carica, sta sotto) le iniziali: ogni radio si riconosce, non la stessa icona per tutte.
        Text(
            RadioGenre.initials(station.name),
            style = if (sizeDp >= 40) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (station.logo.isNotBlank()) {
            AsyncImage(model = station.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}

