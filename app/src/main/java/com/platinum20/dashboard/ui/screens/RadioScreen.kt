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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign

/**
 * La pagina Radio (1.529, Antonio scelta B/C): in cima le tue radio come loghi tondi (preferite, poi le ultime
 * ascoltate), "In onda adesso" con quello che sta trasmettendo la radio che suona, poi i generi in chip che
 * restringono le più ascoltate in Italia. Tieni premuta una radio per preferita, sveglia, salva.
 */
internal fun LazyListScope.radioSections(
    popular: Loadable<List<RadioStation>>,
    favorites: List<RadioStation>,
    recent: List<RadioStation>,
    now: RadioNow?,
    genre: String?,
    onGenre: (String?) -> Unit,
) {
    val mine = (favorites + recent).distinctBy { it.id }
    if (mine.isNotEmpty()) item(key = "mine") { MyStations(mine, now) }
    now?.let { playing ->
        item(key = "onair-header") { SectionTitle("In onda adesso") }
        item(key = "onair-${playing.station.id}") { StationRow(playing.station, playing, first = true, last = true) }
    }
    val top = popular.data.orEmpty()
    val genres = RadioGenre.chips(top.map { it.tags })
    if (genres.isNotEmpty()) {
        item(key = "genre-header") { SectionTitle("Per genere") }
        item(key = "genre-chips") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                genres.forEach { g -> CanvasChip(selected = g == genre, onClick = { onGenre(if (g == genre) null else g) }, label = g) }
            }
        }
    }
    val shown = if (genre == null) top else top.filter { RadioGenre.primary(it.tags) == genre }
    if (shown.isNotEmpty()) {
        item(key = "popular-header") { SectionTitle(if (genre == null) "Le più ascoltate in Italia" else "$genre in Italia") }
        itemsIndexed(shown, key = { _, s -> "list-${s.id}" }) { i, s -> StationRow(s, now, i == 0, i == shown.lastIndex) }
    } else if (popular.loading) item(key = "popular-loading") {
        PlaceholderList(rows = 4, label = "Carico le radio")
    }
}

/** Le tue radio in loghi tondi, la preferita prima; quella che suona ha il nome blu. */
@Composable
private fun MyStations(stations: List<RadioStation>, now: RadioNow?) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stations.forEach { station ->
            val playing = now?.station?.id == station.id
            Column(
                Modifier.width(72.dp).bouncyPress(0.94f).clip(MaterialTheme.shapes.medium)
                    .tapOrSave({ SavedLinks.radio(station) }) { RadioStore.play(station) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StationLogo(station, 64, CircleShape)
                Spacer(Modifier.size(6.dp))
                Text(
                    station.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
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
                    // Il genere in italiano, non i tag in inglese con i kbps (punto 42); mentre suona, cosa trasmette.
                    now?.track?.takeIf { playing && it.isNotBlank() } ?: RadioGenre.of(station.tags) ?: "Radio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (playing) {
                Text("LIVE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
            } else IconButton(onClick = {
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
fun StationLogo(station: RadioStation, sizeDp: Int, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(shape)
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

