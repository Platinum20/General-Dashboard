package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastStore
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.browserUserAgent
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.media.loadWhenAttached
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebViewClient
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.annotation.SuppressLint
import androidx.compose.material.icons.outlined.Key
import com.platinum20.dashboard.media.spotify.SpotifyWebSession
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.Accounts
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.platinum20.dashboard.media.PlaySource
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.NewReleases
import com.platinum20.dashboard.media.spotify.SpRelease
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import java.time.LocalDate
import com.platinum20.dashboard.media.spotify.SpPlayback
import com.platinum20.dashboard.media.spotify.SpotifyApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpMix
import com.platinum20.dashboard.media.spotify.SpotifyMixes
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import kotlinx.coroutines.delay

/**
 * Una cosa di Spotify aperta da Media (1.481, Antonio: Musica non è più una pagina a parte): l'artista ha la sua
 * pagina, album, playlist e podcast l'elenco. Un album aperto da un artista è un'altra pagina, col gesto indietro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyItemScreen(item: SpItem, onBack: () -> Unit, onOpenItem: (SpItem) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val official by SpotifyAuth.connected.collectAsState()
    // Il riproduttore di Spotify si prepara appena si entra: al primo tocco la musica parte subito.
    LaunchedEffect(official) { if (official) SpotifyPlayer.start(context) }
    SpotifyNotices()
    if (item.kind == SpKind.ARTIST) {
        ArtistScreen(item, onBack = onBack, onOpenItem = onOpenItem)
        return
    }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = item.title,
                subtitle = when (item.kind) {
                    SpKind.ALBUM -> "Album"
                    SpKind.PLAYLIST -> "Playlist"
                    SpKind.SHOW -> "Podcast"
                    else -> "Spotify"
                },
                scrollBehavior = barScroll,
                onBack = onBack,
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            ChildrenList(item, onOpen = onOpenItem, onPlay = { playSpotifyList(context, it, null) }, onPlayFrom = { child ->
                // Da un album o una playlist si continua con i brani successivi, nella coda unica.
                if (item.kind == SpKind.ALBUM || item.kind == SpKind.PLAYLIST || item.kind == SpKind.SHOW) {
                    playSpotifyList(context, item, child)
                } else {
                    SpotifyPlayer.play(context, child)
                }
            })
        }
    }
}

/** Gli avvisi brevi di Spotify (serve Premium, nessun dispositivo) nella bolla in basso. */
@Composable
internal fun SpotifyNotices() {
    val context = LocalContext.current
    val message by SpotifyStore.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            CaptureFeedback.tell(context, it)
            SpotifyStore.consumeMessage()
        }
    }
    val playerError by SpotifyPlayer.error.collectAsState()
    LaunchedEffect(playerError) {
        playerError?.let {
            CaptureFeedback.tell(context, it)
            SpotifyPlayer.consumeError()
        }
    }
}

/**
 * "I tuoi podcast" (1.481, Antonio: una riga in fondo a Media, non un titolo da toccare): quelli che segui, un tocco
 * apre gli episodi qui dentro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { PodcastStore.init(context) }
    val podcastNow by PodcastStore.now.collectAsState()
    var podcast by remember { mutableStateOf<Podcast?>(null) }
    BackHandler(enabled = podcast != null) { podcast = null }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = podcast?.title ?: "I tuoi podcast",
                subtitle = podcast?.let { p -> p.author.ifBlank { if (p.fromSpotify) "Da Spotify" else "Podcast" } } ?: "Podcast",
                scrollBehavior = barScroll,
                onBack = { if (podcast != null) podcast = null else onBack() },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            podcast?.let { page ->
                PodcastPage(page, podcastNow)
                return@Column
            }
            FollowedPodcasts(onOpen = { podcast = it })
        }
    }
}
/**
 * "Per te" (1.481, Antonio: i mix per primi): i mix fatti dai tuoi ascolti, poi le uscite degli artisti che segui
 * (col pallino finché non le apri), in una fila sola. Si carica da sola; senza niente non si disegna.
 */
@Composable
internal fun PerTeRow(onOpen: (SpItem) -> Unit) {
    val context = LocalContext.current
    val homeState by SpotifyStore.home.collectAsState()
    val libraryState by SpotifyStore.library.collectAsState()
    val releases by SpotifyStore.releases.collectAsState()
    val seen by SpotifyStore.seenReleases.collectAsState()
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
    val list = MediaShelves.freshFirst(releases.data.orEmpty()) { it.item.id !in seen }
    if (mixes.isEmpty() && list.isEmpty()) return
    val tile = coverTileWidth()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Per te", Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(mixes, key = { _, m -> "mix-${m.id}" }) { _, mix -> MixTile(mix, tile) { playTracks(context, mix.tracks, null) } }
            itemsIndexed(list, key = { _, r -> "release-${r.item.id}" }) { _, release ->
                Column(
                    Modifier
                        .width(tile)
                        .bouncyPress(0.96f)
                        .clickable {
                            SpotifyStore.markReleaseSeen(release.item.id)
                            onOpen(release.item)
                        },
                ) {
                    CaptionedCover(release.item.title, release.item.subtitle.substringBefore(" · ") + " · " + NewReleases.ago(release.released, today)) {
                        Box {
                            Cover(release.item.image, MaterialTheme.shapes.medium, tile, MediaMarks.of(release.item))
                            if (release.item.id !in seen) NewDot(Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
}
/** Il pallino blu di una cosa nuova sulla copertina. */
@Composable
internal fun NewDot(modifier: Modifier) {
    Box(modifier.padding(6.dp).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
}

/**
 * Radio dentro Musica (1.475, Antonio): le preferite e le ascoltate di recente in loghi tondi, sotto la canzone in onda
 * quando la radio la manda; l'ultima tessera "Tutte le radio" apre l'elenco delle più ascoltate.
 */
@Composable
internal fun RadioRow(onOpenAll: () -> Unit) {
    val context = LocalContext.current
    remember { com.platinum20.dashboard.media.radio.RadioStore.init(context) }
    val favorites by com.platinum20.dashboard.media.radio.RadioStore.favorites.collectAsState()
    val recent by com.platinum20.dashboard.media.radio.RadioStore.recent.collectAsState()
    val now by com.platinum20.dashboard.media.radio.RadioStore.now.collectAsState()
    val radios = MediaShelves.mix(favorites, recent, { it.id })
    val tile = coverTileWidth()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Radio", Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(radios, key = { "radio-${it.id}" }) { station ->
                val track = now?.takeIf { it.station.id == station.id }?.track.orEmpty()
                val genre = com.platinum20.dashboard.media.radio.RadioGenre.of(station.tags) ?: "Radio"
                Column(
                    Modifier.width(tile).bouncyPress(0.96f).clickable { com.platinum20.dashboard.media.radio.RadioStore.play(station) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CaptionedCover(station.name, if (track.isNotBlank()) "In onda: $track" else genre, centered = true) {
                        Cover(station.logo, CircleShape, tile, MediaMark(MediaType.RADIO, MediaMarks.LIVE))
                    }
                }
            }
            item(key = "radio-all") {
                Column(Modifier.width(tile).bouncyPress(0.96f).clickable(onClick = onOpenAll), horizontalAlignment = Alignment.CenterHorizontally) {
                    CaptionedCover("Tutte le radio", "Le più ascoltate", centered = true) {
                        Box(Modifier.size(tile).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

/** "I tuoi podcast": quelli che segui (anche salvati su Spotify), uno per riga; senza, qualche idea da cui partire. */
@Composable
private fun FollowedPodcasts(onOpen: (Podcast) -> Unit) {
    val followedHere by PodcastStore.followed.collectAsState()
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val followed = remember(followedHere, spotifyShows) { PodcastStore.merge(followedHere, spotifyShows) }
    val search by PodcastStore.search.collectAsState()
    var idea by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (followed.isEmpty()) {
            item(key = "start") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Non segui ancora nessun podcast", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        podcastIdeas.forEach { text ->
                            CanvasChip(selected = idea == text, onClick = {
                                idea = text
                                PodcastStore.search(text)
                            }, label = text)
                        }
                    }
                }
            }
            if (idea != null) items(search.data.orEmpty(), key = { "idea-" + it.feedUrl }) { podcast -> PodcastLine(podcast, onOpen) }
        }
        items(followed, key = { it.feedUrl }) { podcast -> PodcastLine(podcast, onOpen) }
    }
}

@Composable
private fun PodcastLine(podcast: Podcast, onOpen: (Podcast) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).bouncyPress(0.97f).clickable { onOpen(podcast) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(podcast.artwork, MaterialTheme.shapes.medium, 56.dp, MediaMark(MediaType.PODCAST))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(podcast.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                podcast.author.ifBlank { if (podcast.fromSpotify) "Spotify" else "Podcast" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Larghezza delle copertine: tre intere per schermo, così nessuna resta tagliata al bordo. */
@Composable
internal fun coverTileWidth(): Dp = CoverTiles.width(LocalConfiguration.current.screenWidthDp).dp

/** Calcolo puro delle copertine di Musica, separato per i test. */
internal object CoverTiles {
    const val PER_SCREEN = 3
    const val EDGE = 16
    const val GAP = 12

    /** Tre copertine più i margini e gli spazi tra loro riempiono esattamente lo schermo. */
    fun width(screenDp: Int): Int = (screenDp - 2 * EDGE - (PER_SCREEN - 1) * GAP) / PER_SCREEN
}

@Composable
internal fun CoverTile(item: SpItem, first: Boolean, onClick: () -> Unit) {
    val round = item.kind == SpKind.ARTIST
    val tile = coverTileWidth()
    Column(
        Modifier
            .width(tile)
            .bouncyPress(0.96f)
            // Tenendo premuto un brano si salva nelle note.
            .tapOrSave({ SavedLinks.spotify(item) }, onClick),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        CaptionedCover(item.title, if (round) "Artista" else item.subtitle, centered = round) {
            Cover(item.image, if (round) CircleShape else MaterialTheme.shapes.medium, tile, MediaMarks.of(item))
        }
    }
}

/** Un mix: la foto dell'artista con la scritta Mix sopra. */
@Composable
private fun MixTile(mix: SpMix, tile: Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(tile)
            .bouncyPress(0.96f)
            .clickable(onClick = onClick),
    ) {
        CaptionedCover(mix.title, mix.subtitle) {
            Box {
                Cover(mix.image, MaterialTheme.shapes.medium, tile, MediaMark(MediaType.MUSIC))
                Text(
                    "Mix",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Brani di una fila (o di un mix) nella coda unica, da [startAt] in poi. */
internal fun playTracks(context: android.content.Context, row: List<SpItem>, startAt: SpItem?) {
    val tracks = row.filter { it.kind == SpKind.TRACK || it.kind == SpKind.EPISODE }
    if (tracks.isEmpty()) return
    val items = tracks.map {
        com.platinum20.dashboard.media.ResumeItem(
            ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image,
            at = System.currentTimeMillis(),
        )
    }
    val start = startAt?.let { s -> tracks.indexOfFirst { it.uri == s.uri } }?.coerceAtLeast(0) ?: 0
    com.platinum20.dashboard.media.CanvasPlayer.playList(context, items, start)
}

@Composable
private fun ChildrenList(parent: SpItem, onOpen: (SpItem) -> Unit, onPlay: (SpItem) -> Unit, onPlayFrom: (SpItem) -> Unit) {
    val context = LocalContext.current
    val all by SpotifyStore.children.collectAsState()
    val state = all[parent.id] ?: Loadable(loading = true)
    LaunchedEffect(parent.id) { SpotifyStore.loadChildren(parent) }
    // Con i dati (anche vuoti) l'avviso va sotto il titolo, così "Riproduci" resta lì sopra.
    val note = state.error?.takeIf { state.data != null }
    FeedList(if (note != null) state.copy(error = null) else state, empty = if (note != null) "" else "Vuoto") {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(parent.image, if (parent.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.medium, 96.dp, MediaMarks.of(parent))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(parent.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(parent.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (parent.kind != SpKind.ARTIST) {
                            FilledTonalButton(onClick = { onPlay(parent) }) { Text("Riproduci") }
                        }
                    }
                }
            }
        }
        note?.let { message ->
            item(key = "note") {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(state.data.orEmpty(), key = { it.uri + it.id }) { child ->
            ItemRow(child, onClick = {
                if (child.kind == SpKind.TRACK || child.kind == SpKind.EPISODE) {
                    onPlayFrom(child)
                } else {
                    onOpen(child)
                }
            })
        }
    }
}

@Composable
internal fun ItemRow(item: SpItem, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        // Tenendo premuto un brano si salva nelle note (album e playlist no: non si riaprono fermi).
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f).clip(MaterialTheme.shapes.large)
            .tapOrSave({ SavedLinks.spotify(item) }, onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item.image, if (item.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.small, 56.dp, MediaMarks.of(item))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun Cover(url: String, shape: Shape, size: Dp, mark: MediaMark? = null) {
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
        val compact = size < 80.dp
        if (shape == CircleShape) MediaMarkOverlay(mark, compact, inset = size * 0.1f) else MediaMarkOverlay(mark, compact)
    }
}

/**
 * Un album, una playlist o un podcast di Spotify: i brani già caricati entrano nella coda unica da
 * [startAt] in poi, così si vedono, si spostano e si mischiano con le altre fonti. Se la lista non
 * c'è ancora, suona come prima dentro Spotify.
 */
internal fun playSpotifyList(context: android.content.Context, parent: SpItem, startAt: SpItem?) {
    val tracks = SpotifyStore.children.value[parent.id]?.data.orEmpty()
        .filter { it.kind == SpKind.TRACK || it.kind == SpKind.EPISODE }
    if (tracks.isEmpty()) return SpotifyPlayer.play(context, parent, startAt)
    val items = tracks.map {
        com.platinum20.dashboard.media.ResumeItem(
            com.platinum20.dashboard.media.ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image.ifBlank { parent.image },
            at = System.currentTimeMillis(), link = parent.uri,
        )
    }
    val start = startAt?.let { s -> tracks.indexOfFirst { it.uri == s.uri } }?.coerceAtLeast(0) ?: 0
    com.platinum20.dashboard.media.CanvasPlayer.playList(context, items, start)
}
