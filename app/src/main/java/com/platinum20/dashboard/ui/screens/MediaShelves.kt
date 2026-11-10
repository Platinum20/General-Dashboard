package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.radio.RadioGenre
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyMixes
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.settings.AppSettings
import java.time.LocalDate

/**
 * Le file di Media (1.357, Antonio): al posto delle tre tessere Musica/Video/Radio, quello che c'è dentro.
 * Ogni fila ha il titolo che apre la pagina intera; una fila vuota non si disegna.
 */
internal object MediaShelves {
    /** Quante copertine al massimo in una fila: il resto sta nella pagina intera. */
    const val MAX = 12

    /** Righe del titolo nelle file di Media e in Continua: tutte uguali, così le tessere di una fila sono alte uguali. */
    const val TITLE_LINES = 3

    /** Che cosa mostra la fila Musica: prima i mix, poi gli ascoltati di recente, senza doppioni. */
    fun <T> mix(first: List<T>, then: List<T>, key: (T) -> String, max: Int = MAX): List<T> =
        (first + then).distinctBy(key).take(max)
}

/** Una copertina della fila: immagine, titolo su due righe, una riga piccola. */
@Composable
private fun ShelfTile(image: String, title: String, subtitle: String, width: Dp, height: Dp, shape: Shape, onClick: () -> Unit) {
    Column(
        Modifier
            .width(width)
            .bouncyPress(0.96f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (image.isNotBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
            }
        }
        Spacer(Modifier.height(6.dp))
        TileCaption(title, subtitle, titleLines = MediaShelves.TITLE_LINES)
    }
}

/**
 * Il testo sotto ogni copertina di Media (1.390, Antonio: altezze diverse tra le tessere): il titolo occupa
 * sempre [titleLines] righe e il sottotitolo sempre una, anche se vuoto, così titolo e autore stanno alla
 * stessa altezza in tutta la fila. Un filo di margine: gli angoli arrotondati della tessera non tagliano le lettere.
 */
@Composable
internal fun TileCaption(title: String, subtitle: String?, titleLines: Int = 2, centered: Boolean = false, modifier: Modifier = Modifier) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Column(modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            minLines = titleLines,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Mentre la fila arriva: copertine grigie già alla loro misura, niente rotella. */
@Composable
private fun ShelfPlaceholder(width: Dp, height: Dp, shape: Shape) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(4) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Placeholder(width, height, shape = shape)
                Placeholder(width * 0.8f, 12.dp)
            }
        }
    }
}

/** Una fila intera: titolo che apre la pagina, poi le copertine (o la riga per collegare il servizio). */
@Composable
private fun Shelf(title: String, onOpen: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenableHeader(title, onOpen, Modifier.padding(horizontal = 12.dp))
        content()
    }
}

/** Una riga sola al posto della fila: il servizio non è collegato. Con [action] il pulsante per farlo, lì. */
@Composable
private fun ShelfInvite(text: String, onClick: () -> Unit, action: String? = null) {
    CanvasCard(onClick = onClick, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().bouncyPress(0.98f)) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 4.dp))
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                CanvasButton(action, onClick = onClick)
            }
        }
    }
}

/** Musica: i mix per te e gli ascoltati di recente; un tocco e parte, il resto nella pagina Musica. */
@Composable
internal fun MusicShelf(connected: Boolean, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    Shelf("Musica", onOpenAll) {
        if (!connected) {
            ShelfInvite("Collega Spotify per vedere qui i tuoi mix", onOpenAll)
            return@Shelf
        }
        val homeState by SpotifyStore.home.collectAsState()
        val libraryState by SpotifyStore.library.collectAsState()
        LaunchedEffect(Unit) {
            SpotifyStore.refreshHome()
            SpotifyStore.refreshLibrary()
        }
        val home = homeState.data
        val library = libraryState.data
        val today = remember { LocalDate.now() }
        val mixes = remember(home, library, today) {
            SpotifyMixes.build(
                home?.topArtists.orEmpty(),
                home?.topTracks.orEmpty() + library?.liked.orEmpty() + home?.recent.orEmpty(),
                today.toEpochDay(),
            )
        }
        val recent = home?.recent.orEmpty()
        val size = 132.dp
        if (home == null && homeState.loading) {
            ShelfPlaceholder(size, size, MaterialTheme.shapes.medium)
            return@Shelf
        }
        if (mixes.isEmpty() && recent.isEmpty()) {
            ShelfInvite("Ancora niente da Spotify: apri Musica", onOpenAll)
            return@Shelf
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes.take(MediaShelves.MAX / 2), key = { "mix-${it.id}" }) { mix ->
                ShelfTile(mix.image, mix.title, mix.subtitle, size, size, MaterialTheme.shapes.medium) {
                    playTracks(context, mix.tracks, null)
                }
            }
            items(MediaShelves.mix(emptyList(), recent, { it.uri }, MediaShelves.MAX / 2), key = { "recent-${it.uri}" }) { item ->
                ShelfTile(item.image, item.title, item.subtitle, size, size, if (item.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.medium) {
                    if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) playTracks(context, recent, item) else onOpenAll()
                }
            }
        }
    }
}

/** Video: i nuovi dai canali che segui, con gli stessi filtri del feed (Shorts, live, membri). */
@Composable
internal fun VideoShelf(connected: Boolean, onPlay: (String) -> Unit, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.subscriptions.collectAsState()
    val hideShorts by AppSettings.hideShorts.collectAsState()
    val hideLive by AppSettings.hideLive.collectAsState()
    val hideMembers by AppSettings.hideMembers.collectAsState()
    LaunchedEffect(connected) { if (connected) YouTubeStore.refreshSubscriptions(context) }
    val videos = remember(state.data, hideShorts, hideLive, hideMembers) {
        pickFeed(state.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos.take(MediaShelves.MAX)
    }
    Shelf("Video", onOpenAll) {
        val width = 200.dp
        val height = 112.dp
        when {
            !connected -> ShelfInvite("Collega YouTube per vedere qui i video nuovi", onOpenAll)
            videos.isEmpty() && state.loading -> ShelfPlaceholder(width, height, MaterialTheme.shapes.medium)
            videos.isEmpty() -> ShelfInvite("Niente di nuovo dai canali che segui", onOpenAll)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(videos, key = { it.id }) { video ->
                    ShelfTile(video.thumbnail, video.title, video.channel, width, height, MaterialTheme.shapes.medium) { onPlay(video.watchUrl) }
                }
            }
        }
    }
}

/** Radio e podcast: le radio preferite (tonde) e i nuovi episodi dei podcast che segui. */
@Composable
internal fun ListenShelf(onOpenAll: () -> Unit) {
    val context = LocalContext.current
    remember { RadioStore.init(context) }
    val favorites by RadioStore.favorites.collectAsState()
    val recentRadios by RadioStore.recent.collectAsState()
    val latest by PodcastStore.latest.collectAsState()
    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
    val radios = MediaShelves.mix(favorites, recentRadios, { it.id }, 6)
    val episodes = latest.data.orEmpty().take(MediaShelves.MAX - radios.size)
    Shelf("Radio e podcast", onOpenAll) {
        val size = 120.dp
        if (radios.isEmpty() && episodes.isEmpty()) {
            if (latest.loading) ShelfPlaceholder(size, size, MaterialTheme.shapes.medium)
            else ShelfInvite("Scegli una radio o segui un podcast", onOpenAll)
            return@Shelf
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(radios, key = { "radio-${it.id}" }) { station ->
                ShelfTile(station.logo, station.name, RadioGenre.of(station.tags) ?: "Radio", size, size, CircleShape) { RadioStore.play(station) }
            }
            items(episodes, key = { "ep-${it.id}" }) { episode ->
                ShelfTile(episode.image, episode.title, episode.podcastTitle, size, size, MaterialTheme.shapes.medium) { PodcastStore.play(episode) }
            }
        }
    }
}
