package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreHoriz
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import com.platinum20.dashboard.media.spotify.SpHome
import com.platinum20.dashboard.media.spotify.SpLibrary
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
                // Dopo "Modifica" il nome nuovo si vede subito (1.507).
                title = if (item.kind == SpKind.PLAYLIST) PlaylistManage.title(item) else item.title,
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
            ChildrenList(item, onGone = onBack, onOpen = onOpenItem, onPlay = { playSpotifyList(context, it, null) }, onPlayFrom = { child ->
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
    // Aperta da una tessera di Per te (1.488): parte già sul podcast toccato, indietro torna a Media.
    val opened = remember { PodcastOpen.take() }
    var podcast by remember { mutableStateOf(opened) }
    BackHandler(enabled = podcast != null && opened == null) { podcast = null }
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = podcast?.title ?: "I tuoi podcast",
                subtitle = podcast?.let { p -> p.author.ifBlank { if (p.fromSpotify) "Da Spotify" else "Podcast" } } ?: "Podcast",
                scrollBehavior = barScroll,
                onBack = { if (podcast != null && opened == null) podcast = null else onBack() },
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
/** Il podcast da aprire subito in "I tuoi podcast", toccato in Per te (1.488). */
internal object PodcastOpen {
    private var pending: Podcast? = null
    fun set(p: Podcast?) { pending = p }
    fun take(): Podcast? = pending.also { pending = null }
}

/**
 * La fila "Musica", già "Per te" (1.488, Antonio: "ordinato in base all'ultima volta che l'ho ascoltato, qualunque cosa sia"): mix, playlist e
 * album salvati di Spotify e i podcast che segui in una fila sola, a sinistra l'ultima cosa ascoltata; niente ultime
 * uscite. In fondo "I tuoi podcast" e il tasto Radio.
 */
@Composable
internal fun PerTeRow(onOpen: (SpItem) -> Unit, onOpenMix: (SpMix) -> Unit, onOpenPodcast: (Podcast?) -> Unit, onOpenRadio: () -> Unit, onOpenLiked: () -> Unit, onDiscover: () -> Unit, onOpenSaved: () -> Unit, onOpenAll: () -> Unit) {
    val saved = rememberSavedMedia(video = false)
    val (entries, podcasts) = rememberPerTe()
    val radioLogos = rememberRadioLogos()
    val tile = coverTileWidth()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Il titolo apre Scopri, il tastino a destra la fila intera in verticale (1.493, Antonio: "invertire la funzione").
        OpenableHeader("Musica", onDiscover, Modifier.padding(horizontal = 16.dp)) { HistoryButton(onOpenAll) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Le cose salvate per dopo, prima di tutto; senza niente di salvato la tessera non c'è (1.490, Antonio).
            if (saved.isNotEmpty()) item(key = "saved") { SavedTile(tile, saved, wide = false, onClick = onOpenSaved) }
            items(entries.take(PER_TE_MAX), key = { it.key }) { e ->
                when (e) {
                    // Un tocco su un mix apre la sua pagina con Riproduci, come una playlist (1.486, Antonio).
                    is PerTe.Mix -> MixTile(e.mix, tile) { onOpenMix(e.mix) }
                    is PerTe.Item -> MediaTile(
                        tile, wide = false, image = e.item.image, title = e.item.title, author = e.item.subtitle.substringBefore(" · "),
                        date = null, mark = MediaMarks.of(e.item), click = Modifier.tapOrSave({ SavedLinks.spotify(e.item) }) { onOpen(e.item) },
                    )
                    is PerTe.Liked -> MediaTile(
                        tile, wide = false, image = "", title = "Brani che ti piacciono", author = "${e.count} brani", date = null,
                        mark = null, click = Modifier.clickable(onClick = onOpenLiked),
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    }
                    is PerTe.Artist -> MediaTile(
                        tile, wide = false, image = e.artist.image, title = e.artist.title, author = "Artista", date = null,
                        mark = null, click = Modifier.tapOrSave({ SavedLinks.spotify(e.artist) }) { onOpen(e.artist) }, round = true,
                    )
                    is PerTe.Show -> MediaTile(
                        tile, wide = false, image = e.podcast.artwork, title = e.podcast.title,
                        author = e.podcast.author.ifBlank { if (e.podcast.fromSpotify) "Spotify" else "Podcast" }, date = null,
                        mark = MediaMark(MediaType.PODCAST), click = Modifier.clickable { onOpenPodcast(e.podcast) },
                    )
                }
            }
            item(key = "podcast-all") {
                AllTile(
                    tile, "I tuoi podcast", if (podcasts.isNotEmpty()) "${podcasts.size} che segui" else "Scegline qualcuno",
                    onClick = { onOpenPodcast(null) }, images = podcasts.map { it.artwork }, type = MediaType.PODCAST,
                )
            }
            // Radio è un tasto in fondo a Per te, non più una sezione (1.488, Antonio); coi loghi delle tue radio (1.493).
            item(key = "radio-all") { AllTile(tile, "Radio", "Preferite e più ascoltate", images = radioLogos, type = MediaType.RADIO, onClick = onOpenRadio) }
        }
    }
}

/** Le voci di Per te dalla più ascoltata di recente, e i podcast che segui: la fila e la pagina completa leggono le stesse (1.492). */
@Composable
internal fun rememberPerTe(): Pair<List<PerTe>, List<Podcast>> {
    val context = LocalContext.current
    remember { PodcastStore.init(context) }
    val followedHere by PodcastStore.followed.collectAsState()
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val homeState by SpotifyStore.home.collectAsState()
    val libraryState by SpotifyStore.library.collectAsState()
    val playlists by SpotifyStore.playlists.collectAsState()
    val listened by SpotifyStore.listened.collectAsState()
    val made by com.platinum20.dashboard.media.spotify.MadeStore.mixes.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshPlaylists()
        SpotifyStore.refreshListened()
        com.platinum20.dashboard.media.spotify.MadeStore.refresh(context)
    }
    val history by com.platinum20.dashboard.media.MediaHistory.items.collectAsState()
    val heard = rememberHeardAt()
    val home = homeState.data
    val library = libraryState.data
    val today = remember { LocalDate.now() }
    val podcasts = remember(followedHere, spotifyShows) { PodcastStore.merge(followedHere, spotifyShows) }
    val entries = remember(home, library, playlists.data, podcasts, heard, history, listened.data, made) {
        // Anche gli ultimi artisti ascoltati (1.488, Antonio: "non tutti, tipo 3"), al loro posto nel tempo.
        val artists = listened.data.orEmpty().sortedByDescending { it.at }.distinctBy { it.artist.id }.take(PER_TE_ARTISTS)
        val podcastAt = history.filter { it.kind == ResumeKind.PODCAST && it.feed.isNotBlank() }
            .groupBy { it.feed }.mapValues { (_, l) -> l.maxOf { it.at } }
        val all = mixesOf(home, library, today, made).map { PerTe.Mix(it) } +
            // Solo raccolte, mai singoli brani (1.496, Antonio: "mix album playlist ecc, niente ultimi brani ascoltati").
            (playlists.data.orEmpty() + library?.albums.orEmpty()).filter { it.kind != SpKind.TRACK }.distinctBy { it.uri }.map { PerTe.Item(it) } +
            podcasts.map { PerTe.Show(it) } +
            artists.map { PerTe.Artist(it.artist, it.at.toEpochMilli()) } +
            // I brani che ti piacciono sono una tessera come una playlist (1.488, Antonio: "dentro Musica").
            listOfNotNull(library?.liked?.takeIf { it.isNotEmpty() }?.let { PerTe.Liked(it.size, it.first().image) })
        com.platinum20.dashboard.media.MediaMerge.byRecency(all) { e ->
            when (e) {
                is PerTe.Mix -> heard[mixLink(e.mix)]
                is PerTe.Item -> heard[e.item.uri]
                is PerTe.Show -> podcastAt[e.podcast.feedUrl]
                is PerTe.Artist -> e.at
                is PerTe.Liked -> heard[LIKED_LINK]
            }
        }
    }
    return entries to podcasts
}

/** I loghi delle radio preferite e ascoltate di recente, per la copertina della tessera Radio (1.493). */
@Composable
private fun rememberRadioLogos(): List<String> {
    val context = LocalContext.current
    remember { com.platinum20.dashboard.media.radio.RadioStore.init(context) }
    val favorites by com.platinum20.dashboard.media.radio.RadioStore.favorites.collectAsState()
    val recent by com.platinum20.dashboard.media.radio.RadioStore.recent.collectAsState()
    return remember(favorites, recent) { (favorites + recent).map { it.logo }.filter { it.isNotBlank() }.distinct() }
}

/** Una tessera di Per te: un mix, una playlist o un album di Spotify, un podcast che segui. */
internal sealed interface PerTe {
    val key: String
    data class Mix(val mix: SpMix) : PerTe { override val key get() = "mix-" + mix.id }
    data class Item(val item: SpItem) : PerTe { override val key get() = "sp-" + item.uri }
    data class Show(val podcast: Podcast) : PerTe { override val key get() = "pod-" + podcast.feedUrl }
    data class Artist(val artist: SpItem, val at: Long) : PerTe { override val key get() = "art-" + artist.uri }
    data class Liked(val count: Int, val image: String) : PerTe { override val key get() = "liked" }
}

/** Il "da dove" dei brani che ti piacciono, quando partono dalla loro pagina (1.488). */
internal const val LIKED_LINK = "spotify:collection:tracks"

/** Il "da dove" dei brani di un mix in Riprendi (1.488): così Per te sa quando l'hai ascoltato. */
internal fun mixLink(mix: SpMix): String = "mix:" + mix.id

/** Quanti degli ultimi artisti ascoltati in Per te. */
private const val PER_TE_ARTISTS = 3

/** Quante tessere in Per te prima delle due fisse in fondo. */
private const val PER_TE_MAX = 24

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

    /** Quanto si vede della scheda video successiva: dice che la fila scorre di lato. */
    const val PEEK = 40

    /**
     * Larghezza delle schede video grandi della fila Video (1.503, Antonio: "come la pagina verticale ma in
     * orizzontale"): quasi tutto lo schermo, col margine a sinistra, lo spazio e un pezzo della successiva a destra.
     */
    fun bigVideoWidth(screenDp: Int): Int = screenDp - EDGE - GAP - PEEK
}

@Composable
internal fun bigVideoWidth(): Dp = CoverTiles.bigVideoWidth(LocalConfiguration.current.screenWidthDp).dp

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

/**
 * Un mix con una copertina sua (1.497, Antonio: "migliora le copertine dei mix"): un mosaico delle copertine dei brani,
 * sfumato nel colore del mix, e il nome grande in basso. Ogni mix ha il suo colore, sempre lo stesso.
 */
@Composable
private fun MixTile(mix: SpMix, tile: Dp, onClick: () -> Unit) {
    val art = remember(mix.id, mix.tracks) { MixArt.images(mix) }
    val color = Color(MixArt.color(mix))
    MediaTile(
        tile, wide = false, image = if (art.size < 4) art.firstOrNull() ?: mix.image else "", title = mix.title, author = mix.subtitle, date = null,
        mark = null,
        click = Modifier.tapOrSave({ SavedLinks.mix(mix.id, mix.title, mix.subtitle, mix.image) }, onClick),
    ) {
        if (art.size >= 4) CoverMosaic(art, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to color.copy(alpha = 0.15f), 0.45f to color.copy(alpha = 0.55f), 1f to color.copy(alpha = 0.95f),
                ),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                mix.badge.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                color = Color.White.copy(alpha = 0.85f), maxLines = 1,
            )
            Text(
                MixArt.name(mix),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp),
                color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** La copertina dei mix, pura per i test: quali immagini, che colore, che nome scritto sopra. */
internal object MixArt {
    /** Colori scuri, così la scritta bianca si legge sempre. */
    val PALETTE = longArrayOf(0xFF6E3A8E, 0xFF1F6F62, 0xFF9C3F32, 0xFF2D4F8F, 0xFF7A5F1A, 0xFF93305F, 0xFF3D5A2A, 0xFF5A3A2A)

    fun images(mix: SpMix): List<String> = mix.tracks.map { it.image }.filter { it.isNotBlank() }.distinct().take(4)

    /** Lo stesso mix ha sempre lo stesso colore: dal tipo (badge) e dal nome. */
    fun color(mix: SpMix): Long = PALETTE[Math.floorMod((mix.badge + "|" + mix.title).hashCode(), PALETTE.size)]

    /** Sulla copertina il nome senza ripetere l'etichetta: "Daily Mix 2" resta, "Mix di Daft Punk" diventa "Daft Punk". */
    fun name(mix: SpMix): String = mix.title.removePrefix("Mix di ").removePrefix("Radio di ").ifBlank { mix.title }
}

/** I mix di oggi (gli stessi in Per te e nella pagina del mix): dai tuoi artisti e brani preferiti. */
internal fun mixesOf(home: SpHome?, library: SpLibrary?, today: LocalDate, made: List<SpMix> = emptyList()): List<SpMix> =
    // Prima le nostre playlist (Release Radar, Discover Weekly, Daily Mix, Top 50, radio: 1.491), poi i "Mix di".
    made + SpotifyMixes.build(
        home?.topArtists.orEmpty(),
        home?.topTracks.orEmpty() + library?.liked.orEmpty() + home?.recent.orEmpty(),
        today.toEpochDay(),
    )

/** Brani di una fila (o di un mix) nella coda unica, da [startAt] in poi. */
internal fun playTracks(context: android.content.Context, row: List<SpItem>, startAt: SpItem?, link: String = "") {
    val tracks = row.filter { it.kind == SpKind.TRACK || it.kind == SpKind.EPISODE }
    if (tracks.isEmpty()) return
    val items = tracks.map {
        com.platinum20.dashboard.media.ResumeItem(
            ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image,
            at = System.currentTimeMillis(), link = link,
        )
    }
    val start = startAt?.let { s -> tracks.indexOfFirst { it.uri == s.uri } }?.coerceAtLeast(0) ?: 0
    com.platinum20.dashboard.media.CanvasPlayer.playList(context, items, start)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChildrenList(parent: SpItem, onGone: () -> Unit, onOpen: (SpItem) -> Unit, onPlay: (SpItem) -> Unit, onPlayFrom: (SpItem) -> Unit) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val all by SpotifyStore.children.collectAsState()
    val state = all[parent.id] ?: Loadable(loading = true)
    LaunchedEffect(parent.id) { SpotifyStore.loadChildren(parent) }
    val isPlaylist = parent.kind == SpKind.PLAYLIST
    // Le tue playlist si gestiscono da qui (1.507): serve sapere se è tua, e la sua descrizione.
    LaunchedEffect(parent.id) { if (isPlaylist) PlaylistManage.load(parent.id) }
    val info = PlaylistManage.infos[parent.id]?.first
    val arranging = isPlaylist && PlaylistManage.arranging[parent.id] == true
    androidx.activity.compose.BackHandler(enabled = arranging) { PlaylistManage.arranging.remove(parent.id) }
    val tracks = state.data.orEmpty()
    // Mentre si trascina la lista è quella in mano; Spotify riceve lo spostamento solo al rilascio.
    var dragging by remember(parent.id) { mutableStateOf<List<SpItem>?>(null) }
    var dragFrom by remember(parent.id) { mutableStateOf(-1) }
    var dragKey by remember(parent.id) { mutableStateOf<String?>(null) }
    val shown = dragging ?: tracks
    val keys = remember(shown) { SpotifyPlaylists.keys(shown) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorder = rememberReorderState(listState, canMove = { arranging && it is String && it in keys }) { from, to ->
        val list = dragging ?: tracks
        val k = SpotifyPlaylists.keys(list)
        val i = k.indexOf(from as String)
        val j = k.indexOf(to as String)
        if (i < 0 || j < 0) return@rememberReorderState
        if (dragFrom < 0) {
            dragFrom = i
            dragKey = from
        }
        view.haptic(Haptic.TICK)
        dragging = SpotifyPlaylists.moved(list, i, j)
    }
    LaunchedEffect(reorder.dragging) {
        val moved = dragging
        if (reorder.dragging != null || moved == null) return@LaunchedEffect
        val to = SpotifyPlaylists.keys(moved).indexOf(dragKey)
        if (dragFrom >= 0 && to >= 0) PlaylistManage.move(context, parent, dragFrom, to)
        dragging = null
        dragFrom = -1
        dragKey = null
    }
    // Con i dati (anche vuoti) l'avviso va sotto il titolo, così "Riproduci" resta lì sopra.
    val note = state.error?.takeIf { state.data != null }
    FeedList(if (note != null) state.copy(error = null) else state, empty = if (note != null) "" else "Vuoto", listState = listState) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(parent.image, if (parent.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.medium, 96.dp, MediaMarks.of(parent))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (isPlaylist) PlaylistManage.title(parent) else parent.title,
                        style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(parent.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (arranging) {
                            CanvasButton("Fine", onClick = { PlaylistManage.arranging.remove(parent.id) }, primary = true)
                        } else if (parent.kind != SpKind.ARTIST) {
                            FilledTonalButton(onClick = { onPlay(parent) }) { Text("Riproduci") }
                        }
                        if (isPlaylist && !arranging && info != null) {
                            RoundIconButton(Icons.Rounded.MoreHoriz, "Altro") {
                                view.haptic(Haptic.CONFIRM)
                                PlaylistManage.openMenu(context, parent, onGone)
                            }
                        }
                    }
                }
            }
        }
        info?.description?.takeIf { it.isNotBlank() }?.let { text ->
            item(key = "description") {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        if (arranging) item(key = "arrange-hint") {
            Text(
                "Trascina un brano dalla maniglia per spostarlo; toccalo per toglierlo o portarlo in cima.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        note?.let { message ->
            item(key = "note") {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (arranging) {
            items(shown.size, key = { keys[it] }) { i ->
                val child = shown[i]
                val key = keys[i]
                ArrangeRow(
                    child,
                    modifier = (if (reorder.isDragging(key)) Modifier else Modifier.animateItem()).reorderItem(reorder, key),
                    handle = Modifier.reorderHandle(reorder, key) { view.haptic(Haptic.GRAB) },
                    onClick = { PlaylistManage.trackMenu(context, parent, child, i, shown.size) },
                )
            }
        } else {
            items(shown.size, key = { keys[it] }) { i ->
                val child = shown[i]
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
}

/** Un brano mentre riordini la playlist (1.507): a destra la maniglia, un tocco apre Togli / In cima / In fondo. */
@Composable
private fun ArrangeRow(item: SpItem, modifier: Modifier, handle: Modifier, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item.image, MaterialTheme.shapes.small, 56.dp, MediaMarks.of(item))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                Icons.Rounded.DragHandle, contentDescription = "Sposta",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(8.dp).size(24.dp),
            )
        }
    }
}

/** Un brano, album o playlist di Spotify nelle liste che mescolano musica e video (1.488): la riga [MediaLine]. */
@Composable
internal fun TrackLine(item: SpItem, onClick: () -> Unit) = MediaLine(
    wide = false, image = item.image, title = item.title, author = item.subtitle, mark = MediaMarks.of(item),
    click = if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) Modifier.tapOrSave({ SavedLinks.spotify(item) }, onClick) else Modifier.clickable(onClick = onClick),
    round = item.kind == SpKind.ARTIST,
)

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
