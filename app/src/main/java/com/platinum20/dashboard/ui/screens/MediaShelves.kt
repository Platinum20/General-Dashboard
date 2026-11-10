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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
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

/** Una copertina della fila: titolo sopra, immagine, chi lo pubblica sotto. */
@Composable
private fun ShelfTile(image: String, title: String, subtitle: String, width: Dp, height: Dp, shape: Shape, first: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .tileDivider(first)
            .width(width)
            .bouncyPress(0.96f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        CaptionedCover(title, subtitle, titleLines = MediaShelves.TITLE_LINES) {
            Box(Modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (image.isNotBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
                }
            }
        }
    }
}

/**
 * Copertina con le sue scritte (1.437, Antonio): il titolo SOPRA l'immagine e chi lo pubblica SOTTO. Tutte e due
 * partono dal bordo dell'immagine: il titolo è appoggiato in basso e se è lungo va a capo verso l'alto, l'autore
 * parte subito sotto e va a capo verso il basso. Lo spazio del titolo è sempre di [titleLines] righe, così le
 * immagini di una fila stanno alla stessa altezza. Va chiamata dentro la Column della tessera.
 */
@Composable
internal fun CaptionedCover(
    title: String,
    subtitle: String?,
    titleLines: Int = 2,
    centered: Boolean = false,
    textModifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
) {
    val align = if (centered) TextAlign.Center else TextAlign.Start
    val titleStyle = MaterialTheme.typography.labelLarge
    // Un filo di margine: gli angoli arrotondati della tessera non tagliano le lettere.
    Box(
        textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
        contentAlignment = if (centered) Alignment.BottomCenter else Alignment.BottomStart,
    ) {
        // Riga vuota invisibile: tiene l'altezza di [titleLines] righe qualunque sia il titolo.
        Text(" ", style = titleStyle, minLines = titleLines, maxLines = titleLines, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        Text(
            title,
            style = titleStyle,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    cover()
    if (subtitle != null) {
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
        )
    }
}

/**
 * Il divisorio leggero fra una tessera e la successiva in una fila (1.437, Antonio: "altrimenti si confonde"):
 * una linea sottile verticale a metà dello spazio [gap], prima di ogni tessera tranne la prima.
 */
@Composable
internal fun Modifier.tileDivider(first: Boolean, gap: Dp = 12.dp): Modifier {
    if (first) return this
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    return drawBehind {
        val x = -gap.toPx() / 2f
        drawLine(color, Offset(x, size.height * 0.08f), Offset(x, size.height * 0.92f), strokeWidth = 1.dp.toPx())
    }
}

/** Lo stesso divisorio per i contenuti uno sotto l'altro (i video di YouTube): orizzontale, a metà dello spazio [gap]. */
@Composable
internal fun Modifier.stackDivider(first: Boolean, gap: Dp = 16.dp): Modifier {
    if (first) return this
    val color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    return drawBehind {
        val y = -gap.toPx() / 2f
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

/** Mentre la fila arriva: copertine grigie già alla loro misura, niente rotella. */
@Composable
private fun ShelfPlaceholder(width: Dp, height: Dp, shape: Shape) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
        items(4) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Placeholder(width * 0.8f, 12.dp)
                Placeholder(width, height, shape = shape)
                Placeholder(width * 0.5f, 10.dp)
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

/**
 * Musica: i mix per te, gli ascoltati di recente e, in fondo, i nuovi episodi dei podcast che segui (1.437, Antonio:
 * i podcast stanno dentro Musica). Un tocco e parte, il resto nella pagina Musica.
 */
@Composable
internal fun MusicShelf(connected: Boolean, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    remember { PodcastStore.init(context) }
    val latest by PodcastStore.latest.collectAsState()
    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
    val episodes = latest.data.orEmpty().take(MediaShelves.MAX / 2)
    Shelf("Musica", onOpenAll) {
        val size = 132.dp
        if (!connected) {
            ShelfInvite("Collega Spotify per vedere qui i tuoi mix", onOpenAll)
            if (episodes.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(episodes, key = { _, e -> "ep-${e.id}" }) { i, episode ->
                        ShelfTile(episode.image, episode.title, episode.podcastTitle, size, size, MaterialTheme.shapes.medium, first = i == 0) { PodcastStore.play(episode) }
                    }
                }
            }
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
        if (home == null && homeState.loading && episodes.isEmpty()) {
            ShelfPlaceholder(size, size, MaterialTheme.shapes.medium)
            return@Shelf
        }
        if (mixes.isEmpty() && recent.isEmpty() && episodes.isEmpty()) {
            ShelfInvite("Ancora niente da Spotify: apri Musica", onOpenAll)
            return@Shelf
        }
        val shownMixes = mixes.take(MediaShelves.MAX / 2)
        val shownRecent = MediaShelves.mix(emptyList(), recent, { it.uri }, MediaShelves.MAX / 2)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(shownMixes, key = { _, m -> "mix-${m.id}" }) { i, mix ->
                ShelfTile(mix.image, mix.title, mix.subtitle, size, size, MaterialTheme.shapes.medium, first = i == 0) {
                    playTracks(context, mix.tracks, null)
                }
            }
            itemsIndexed(shownRecent, key = { _, r -> "recent-${r.uri}" }) { i, item ->
                val shape = if (item.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.medium
                ShelfTile(item.image, item.title, item.subtitle, size, size, shape, first = i == 0 && shownMixes.isEmpty()) {
                    if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) playTracks(context, recent, item) else onOpenAll()
                }
            }
            itemsIndexed(episodes, key = { _, e -> "ep-${e.id}" }) { i, episode ->
                ShelfTile(
                    episode.image, episode.title, episode.podcastTitle, size, size, MaterialTheme.shapes.medium,
                    first = i == 0 && shownMixes.isEmpty() && shownRecent.isEmpty(),
                ) { PodcastStore.play(episode) }
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
                itemsIndexed(videos, key = { _, v -> v.id }) { i, video ->
                    ShelfTile(video.thumbnail, video.title, video.channel, width, height, MaterialTheme.shapes.medium, first = i == 0) { onPlay(video.watchUrl) }
                }
            }
        }
    }
}

/** Radio (1.437: da sola, i podcast sono passati in Musica): le preferite e poi le ascoltate di recente, tonde. */
@Composable
internal fun RadioShelf(onOpenAll: () -> Unit) {
    val context = LocalContext.current
    remember { RadioStore.init(context) }
    val favorites by RadioStore.favorites.collectAsState()
    val recentRadios by RadioStore.recent.collectAsState()
    val radios = MediaShelves.mix(favorites, recentRadios, { it.id })
    Shelf("Radio", onOpenAll) {
        val size = 120.dp
        if (radios.isEmpty()) {
            ShelfInvite("Scegli una radio", onOpenAll)
            return@Shelf
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(radios, key = { _, s -> "radio-${s.id}" }) { i, station ->
                ShelfTile(station.logo, station.name, RadioGenre.of(station.tags) ?: "Radio", size, size, CircleShape, first = i == 0) { RadioStore.play(station) }
            }
        }
    }
}
