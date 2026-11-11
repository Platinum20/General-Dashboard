package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
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
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.settings.AppSettings
import java.time.LocalDate

/**
 * Le file di Media (1.357, Antonio): al posto delle tre tessere Musica/Video/Radio, quello che c'è dentro.
 * Ogni fila ha il titolo che apre la pagina intera; una fila vuota non si disegna.
 */
internal object MediaShelves {
    /** Quante copertine al massimo in una fila: il resto sta nella pagina intera. */
    const val MAX = 12

    /** Righe del titolo nelle file di Media e in Continua (1.461, Antonio: massimo 2, poi i puntini). */
    const val TITLE_LINES = 2

    /** Fila Video (1.442, Antonio): i video dei canali a cui sei iscritto, dal più nuovo; senza data in fondo. */
    fun newestFirst(videos: List<YtVideo>): List<YtVideo> =
        videos.sortedWith(compareByDescending<YtVideo, java.time.Instant?>(nullsFirst()) { it.published })

    /** Che cosa mostra la fila Musica: prima i mix, poi gli ascoltati di recente, senza doppioni. */
    fun <T> mix(first: List<T>, then: List<T>, key: (T) -> String, max: Int = MAX): List<T> =
        (first + then).distinctBy(key).take(max)

    /** Prima quelli nuovi, poi gli altri, ognuno nel suo ordine (uscite non aperte, episodi non ascoltati). */
    fun <T> freshFirst(list: List<T>, isNew: (T) -> Boolean): List<T> = list.filter(isNew) + list.filterNot(isNew)

    /** Il titolo di una fila con quanti nuovi ci sono: "Video · 6 nuovi"; solo il nome se zero. */
    fun title(name: String, fresh: Int): String =
        com.platinum20.dashboard.media.MediaFresh.shelfCount(fresh).let { if (it.isEmpty()) name else "$name · $it" }
}

/** Una copertina della fila: immagine, poi titolo e chi lo pubblica sotto. */
@Composable
private fun ShelfTile(
    image: String, title: String, subtitle: String, width: Dp, height: Dp, shape: Shape, first: Boolean,
    /** Uscito dopo l'ultima visita: pallino blu (blu = adesso, regola 2). */
    dot: Boolean = false,
    /** Già visto, mentre accanto ci sono dei nuovi: sbiadito. */
    faded: Boolean = false,
    /** Sopra la copertina in basso a sinistra, per i Mix. */
    badge: String? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .width(width)
            .alpha(if (faded) FADED_ALPHA else 1f)
            .bouncyPress(0.96f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        CaptionedCover(title, subtitle, titleLines = MediaShelves.TITLE_LINES) {
            Box(Modifier.size(width, height).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (image.isNotBlank()) {
                    AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width, height))
                }
                if (dot) Box(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).size(10.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                if (badge != null) Text(
                    badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Copertina con le sue scritte (1.461, Antonio: di nuovo sotto, come prima del 1.437): l'immagine, poi il titolo
 * al massimo su [titleLines] righe e chi lo pubblica su una riga, con i puntini se non ci stanno. Altezze fisse:
 * il titolo occupa sempre [titleLines] righe e l'autore una, così le tessere di una fila sono alte uguali.
 * Va chiamata dentro la Column della tessera.
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
    cover()
    @Composable
    fun texts(title: String, subtitle: String?, fixed: Boolean) = Column {
        // Un filo di margine: gli angoli arrotondati della tessera non tagliano le lettere.
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            minLines = if (fixed) titleLines else 1,
            maxLines = titleLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 6.dp),
        )
        if (subtitle != null) Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = align,
            modifier = textModifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
        )
    }
    // Altezza fissa (titolo su [titleLines] righe + autore): la tessera tiene sempre quello spazio, invisibile,
    // ma l'autore sta subito sotto il titolo. Titolo su una riga → autore sulla seconda, terza vuota (Antonio, 2026-11-11).
    Box {
        Box(Modifier.alpha(0f).clearAndSetSemantics { }) { texts(" ", subtitle?.let { " " }, fixed = true) }
        texts(title, subtitle, fixed = false)
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
 * Musica (1.475, Antonio): in ordine fisso i mix, le uscite nuove, gli episodi nuovi e in fondo le radio (tonde).
 * La riga sotto il titolo dice cos'è ("Mix", "Uscita · …", "Episodio · …", "Radio"). Un tocco e parte.
 */
@Composable
internal fun MusicShelf(connected: Boolean, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    remember { PodcastStore.init(context); RadioStore.init(context) }
    val latest by PodcastStore.latest.collectAsState()
    val history by com.platinum20.dashboard.media.MediaHistory.items.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recentRadios by RadioStore.recent.collectAsState()
    val radioNow by RadioStore.now.collectAsState()
    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
    val episodes = remember(latest.data, history) { newEpisodes(latest.data.orEmpty(), history).take(4) }
    val radios = MediaShelves.mix(favorites, recentRadios, { it.id }, max = 6)
    val homeState by SpotifyStore.home.collectAsState()
    val libraryState by SpotifyStore.library.collectAsState()
    val releasesState by SpotifyStore.releases.collectAsState()
    val seen by SpotifyStore.seenReleases.collectAsState()
    LaunchedEffect(connected) {
        if (connected) {
            SpotifyStore.refreshHome()
            SpotifyStore.refreshLibrary()
            SpotifyStore.refreshReleases()
        }
    }
    val home = homeState.data
    val library = libraryState.data
    val today = remember { LocalDate.now() }
    val mixes = remember(home, library, today) {
        if (!connected) emptyList() else SpotifyMixes.build(
            home?.topArtists.orEmpty(),
            home?.topTracks.orEmpty() + library?.liked.orEmpty() + home?.recent.orEmpty(),
            today.toEpochDay(),
        ).take(3)
    }
    val releases = if (connected) releasesState.data.orEmpty().filter { it.item.id !in seen }.take(4) else emptyList()
    Shelf(MediaShelves.title("Musica", releases.size + episodes.size), onOpenAll) {
        val size = 132.dp
        if (!connected) ShelfInvite("Collega Spotify per vedere qui i tuoi mix", onOpenAll)
        if (connected && home == null && homeState.loading && episodes.isEmpty() && radios.isEmpty()) {
            ShelfPlaceholder(size, size, MaterialTheme.shapes.medium)
            return@Shelf
        }
        if (mixes.isEmpty() && releases.isEmpty() && episodes.isEmpty() && radios.isEmpty()) {
            if (connected) ShelfInvite("Ancora niente da Spotify: apri Musica", onOpenAll)
            return@Shelf
        }
        val medium = MaterialTheme.shapes.medium
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(mixes, key = { _, m -> "mix-${m.id}" }) { i, mix ->
                ShelfTile(mix.image, mix.title, "Mix", size, size, medium, first = i == 0, badge = "Mix") {
                    playTracks(context, mix.tracks, null)
                }
            }
            items(releases, key = { "rel-${it.item.id}" }) { release ->
                ShelfTile(release.item.image, release.item.title, "Uscita · " + release.item.subtitle.substringBefore(" · "), size, size, medium, first = false, dot = true) {
                    SpotifyStore.markReleaseSeen(release.item.id)
                    playSpotifyList(context, release.item, null)
                }
            }
            items(episodes, key = { "ep-${it.id}" }) { episode ->
                ShelfTile(episode.image, episode.title, "Episodio · " + episode.podcastTitle, size, size, medium, first = false, dot = true) {
                    PodcastStore.play(episode)
                }
            }
            items(radios, key = { "radio-${it.id}" }) { station ->
                val track = radioNow?.takeIf { it.station.id == station.id }?.track.orEmpty()
                ShelfTile(station.logo, station.name, if (track.isNotBlank()) "In onda: $track" else "Radio", size, size, CircleShape, first = false) {
                    RadioStore.play(station)
                }
            }
        }
    }
}

/**
 * Video: i nuovi dai canali a cui sei iscritto, dal più recente, con gli stessi filtri del feed. Quelli usciti dopo
 * l'ultima visita a Video hanno il pallino e gli altri sono sbiaditi (1.475, Antonio: come in News).
 */
@Composable
internal fun VideoShelf(connected: Boolean, onPlay: (String) -> Unit, onOpenAll: () -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.subscriptions.collectAsState()
    val hideLive by AppSettings.hideLive.collectAsState()
    val hideMembers by AppSettings.hideMembers.collectAsState()
    remember { com.platinum20.dashboard.media.VideoVisit.init(context) }
    val since by com.platinum20.dashboard.media.VideoVisit.since.collectAsState()
    LaunchedEffect(connected) { if (connected) YouTubeStore.refreshSubscriptions(context) }
    val videos = remember(state.data, hideLive, hideMembers) {
        MediaShelves.newestFirst(pickFeed(state.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos).take(MediaShelves.MAX)
    }
    val fresh = com.platinum20.dashboard.media.MediaFresh.newVideos(videos, since)
    Shelf(MediaShelves.title("Video", if (connected) fresh else 0), onOpenAll) {
        val width = 200.dp
        val height = 112.dp
        when {
            !connected -> ShelfInvite("Collega YouTube per vedere qui i video nuovi", onOpenAll)
            videos.isEmpty() && state.loading -> ShelfPlaceholder(width, height, MaterialTheme.shapes.medium)
            videos.isEmpty() -> ShelfInvite("Niente di nuovo dai canali che segui", onOpenAll)
            else -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(videos, key = { _, v -> v.id }) { i, video ->
                    val isNew = com.platinum20.dashboard.media.MediaFresh.isNew(video.published, since)
                    ShelfTile(
                        video.thumbnail, video.title, video.channel, width, height, MaterialTheme.shapes.medium, first = i == 0,
                        dot = isNew, faded = fresh > 0 && !isNew,
                    ) { onPlay(video.watchUrl) }
                }
            }
        }
    }
}
