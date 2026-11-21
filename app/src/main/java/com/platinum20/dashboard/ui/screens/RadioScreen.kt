package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.radio.RadioGenre
import com.platinum20.dashboard.media.radio.RadioHero
import com.platinum20.dashboard.media.radio.RadioNow
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.radio.RadioStore

/**
 * La pagina Radio nello stile delle pagine di Spotify e YouTube (1.572, Antonio): in cima il blocco grande con il
 * logo della radio che suona (o la preferita), il nome e il ▶; sotto, sullo sfondo senza card, le tue radio, i generi
 * e le più ascoltate in Italia. Tieni premuta una radio per preferita, sveglia, salva.
 */
internal fun LazyListScope.radioSections(
    popular: Loadable<List<RadioStation>>,
    favorites: List<RadioStation>,
    recent: List<RadioStation>,
    now: RadioNow?,
    genre: String?,
    onGenre: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val top = popular.data.orEmpty()
    val hero = RadioHero.pick(now?.station, favorites, recent, top)
    item(key = "radio-hero") { RadioHeroBlock(hero, now, onBack) }
    val mine = (favorites + recent).distinctBy { it.id }
    if (mine.isNotEmpty()) mediaGroup(key = "mine", title = "Le tue radio", items = mine, itemKey = { it.id }) { _, s -> StationLine(s, now) }
    val genres = RadioGenre.chips(top.map { it.tags })
    if (genres.isNotEmpty()) mediaShelf(key = "genres", title = "Per genere") {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { g -> CanvasChip(selected = g == genre, onClick = { onGenre(if (g == genre) null else g) }, label = g) }
        }
    }
    val shown = if (genre == null) top else top.filter { RadioGenre.primary(it.tags) == genre }
    if (shown.isNotEmpty()) {
        mediaGroup(
            key = "popular",
            title = if (genre == null) "Le più ascoltate in Italia" else "$genre in Italia",
            items = shown,
            itemKey = { it.id },
        ) { _, s -> StationLine(s, now) }
    } else if (popular.loading) item(key = "popular-loading") {
        PlaceholderList(Modifier.padding(horizontal = 16.dp), rows = 4, label = "Carico le radio")
    }
}

/** Il blocco in cima: il logo grande, il nome una volta sola, perché è lì ("In onda adesso", "La tua preferita") e il ▶. */
@Composable
private fun RadioHeroBlock(hero: RadioHero.Pick?, now: RadioNow?, onBack: () -> Unit) {
    if (hero == null) {
        MediaHero(title = "Radio", image = "", onBack = onBack, mark = MediaMark(MediaType.RADIO), line = "Le radio italiane in diretta")
        return
    }
    val station = hero.station
    val onAir = now?.station?.id == station.id
    MediaHero(
        title = station.name,
        image = station.logo,
        onBack = onBack,
        line = RadioHero.line(hero.why),
        detail = RadioHero.detail(hero.why, now?.track, station.tags),
        picture = { StationLogo(station, MediaPage.HERO_COVER.value.toInt(), MaterialTheme.shapes.large) },
        playing = onAir && now?.playing == true,
        onPlay = { if (onAir) RadioStore.togglePause() else RadioStore.play(station) },
        onMore = { SavedLinks.offer(SavedLinks.radio(station)) },
    )
}

/**
 * La riga Canvas di una radio, sullo sfondo: a sinistra il logo, in mezzo il nome e il genere (mentre suona, cosa
 * trasmette), a destra il cuore delle preferite o, se suona, LIVE nel colore di "adesso".
 */
@Composable
private fun StationLine(station: RadioStation, now: RadioNow?) {
    val favorites by RadioStore.favorites.collectAsState()
    val favorite = favorites.any { it.id == station.id }
    val playing = now?.station?.id == station.id
    val haptic = rememberHaptic()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).bouncyPress(0.98f).clip(MaterialTheme.shapes.medium)
            .tapOrSave({ SavedLinks.radio(station) }) { if (playing) RadioStore.togglePause() else RadioStore.play(station) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(station, 44)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (playing) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (playing) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Il genere in italiano, non i tag in inglese con i kbps; mentre suona, cosa trasmette.
                now?.track?.takeIf { playing && it.isNotBlank() } ?: RadioGenre.of(station.tags) ?: "Radio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (playing) {
            Text("LIVE", style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.padding(horizontal = 12.dp))
        } else IconButton(onClick = {
            haptic(if (favorite) Haptic.TICK else Haptic.CONFIRM)
            RadioStore.toggleFavorite(station)
        }) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (favorite) "Togli dalle preferite" else "Aggiungi alle preferite",
                tint = if (favorite) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            style = when {
                sizeDp >= 120 -> MaterialTheme.typography.displaySmall
                sizeDp >= 40 -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelSmall
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (station.logo.isNotBlank()) {
            AsyncImage(model = station.logo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}
