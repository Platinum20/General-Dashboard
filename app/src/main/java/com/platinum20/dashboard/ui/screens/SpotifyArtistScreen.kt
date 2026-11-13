package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.spotify.SpArtistPage
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpReleaseGroup
import com.platinum20.dashboard.media.spotify.SpotifyArtist
import com.platinum20.dashboard.media.spotify.SpotifyStore

/**
 * La pagina di un artista (1.481, Antonio: "se vado su Pink Floyd dev'essere come Spotify"): la foto grande col nome
 * sopra, follower e generi, il tasto play, i Popolari numerati (cinque, poi "Altri"), la Discografia divisa in Album e
 * Singoli e EP, Raccolte (tutte, 1.494), e dove appare; il titolo Discografia apre l'elenco intero. Ci si arriva dalle storie in cima a Media, dalla ricerca e dal nome nel player.
 */
@Composable
internal fun ArtistScreen(artist: SpItem, onBack: () -> Unit, onOpenItem: (SpItem) -> Unit) {
    val context = LocalContext.current
    val pages by SpotifyStore.artistPages.collectAsState()
    LaunchedEffect(artist.id) { SpotifyStore.loadArtist(artist) }
    val state = pages[artist.id] ?: Loadable(loading = true)
    val page = state.data
    var showAll by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableIntStateOf(0) }
    var everything by rememberSaveable { mutableStateOf(false) }
    val popular = page?.popular.orEmpty()
    if (everything && page != null) {
        BackHandler { everything = false }
        DiscographyScreen(page, loading = state.loading, onBack = { everything = false }, onOpenItem = onOpenItem)
        return
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // La testata (1.529, Antonio scelta B come album e playlist): foto tonda al centro, nome, una riga sotto,
            // poi Casuale · Riproduci · Altro in fila al centro.
            item(key = "head") {
                ArtistHeader(
                    photo = page?.photo?.ifBlank { null } ?: artist.image,
                    name = page?.artist?.title ?: artist.title,
                    meta = page?.let(::metaLine).orEmpty(),
                    canPlay = popular.isNotEmpty(),
                    onShuffle = { playTracks(context, popular.shuffled(), null) },
                    onPlay = { playTracks(context, popular, null) },
                    onMore = { SavedLinks.spotify(page?.artist ?: artist)?.let(SavedLinks::offer) },
                )
            }
            if (page == null && state.loading) item(key = "loading") { PlaceholderList(rows = 5, label = "Carico l'artista") }
            state.error?.takeIf { page == null }?.let { error ->
                item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            if (popular.isNotEmpty()) {
                item(key = "popular-h") { ArtistTitle("Popolari") }
                val shown = if (showAll) popular else popular.take(SpotifyArtist.POPULAR_SHORT)
                itemsIndexed(shown, key = { _, t -> "pop-" + t.uri }) { i, track ->
                    PopularRow(i + 1, track, artistName = page?.artist?.title ?: artist.title) { playTracks(context, popular, track) }
                }
                if (popular.size > SpotifyArtist.POPULAR_SHORT) item(key = "popular-more") {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text(if (showAll) "Meno" else "Altri", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            val groups = page?.let { p -> OWN_GROUPS.filter { it.of(p).isNotEmpty() } }.orEmpty()
            if (page != null && groups.isNotEmpty()) {
                // Il titolo apre l'elenco intero; la fila mostra tutto il gruppo scelto, dal più nuovo.
                item(key = "disc-h") { ArtistTitle("Discografia", onOpen = { everything = true }) }
                item(key = "disc-chips") {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { g ->
                            CanvasChip(selected = g == groups.getOrElse(group) { groups[0] }, onClick = { group = groups.indexOf(g) }, label = g.label)
                        }
                    }
                }
                item(key = "disc") {
                    val chosen = groups.getOrElse(group) { groups[0] }
                    ReleaseCovers(chosen.of(page), onOpenItem, onAll = { everything = true })
                }
            }
            if (page != null && page.appearsOn.isNotEmpty()) {
                item(key = "appears-h") { ArtistTitle("Appare in") }
                item(key = "appears") { ReleaseCovers(page.appearsOn, onOpenItem, showArtist = true) }
            }
        }
        Box(Modifier.statusBarsPadding().padding(8.dp)) {
            RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro", onClick = onBack)
        }
    }
}

/** "1,2 Mln di follower · rock, prog": quello che Spotify dice dell'artista, in una riga. */
internal fun metaLine(page: SpArtistPage): String =
    listOf(SpotifyArtist.followersLine(page.followers), page.genres.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * La testata dell'artista (1.529, scelta B): come album e playlist, la foto (tonda, è una persona) al centro, il nome,
 * follower e generi in una riga, e i tre tasti in fila. "Altro" apre lo stesso fumetto della pressione lunga.
 */
@Composable
private fun ArtistHeader(
    photo: String,
    name: String,
    meta: String,
    canPlay: Boolean,
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    onMore: () -> Unit,
) {
    val haptic = rememberHaptic()
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Cover(photo, CircleShape, 200.dp, null)
        Spacer(Modifier.height(14.dp))
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canPlay) {
                RoundIconButton(Icons.Rounded.Shuffle, "Casuale") { haptic(Haptic.CONFIRM); onShuffle() }
                CanvasButton("Riproduci", onClick = onPlay, icon = Icons.Rounded.PlayArrow, primary = true)
            }
            RoundIconButton(Icons.Rounded.MoreHoriz, "Altro") { haptic(Haptic.CONFIRM); onMore() }
        }
    }
}

/** Il titolo di un blocco; con [onOpen] è inchiostro + freccia e si tocca per aprire la pagina intera. */
@Composable
private fun ArtistTitle(text: String, onOpen: (() -> Unit)? = null) {
    val haptic = rememberHaptic()
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            .clip(MaterialTheme.shapes.small)
            .then(if (onOpen != null) Modifier.clickable { haptic(Haptic.TICK); onOpen() } else Modifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        if (onOpen != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Apri", modifier = Modifier.size(24.dp))
        }
    }
}

/** I gruppi dell'artista stesso (le sue uscite); "Appare in" ha la sua fila sotto. */
private val OWN_GROUPS = listOf(SpReleaseGroup.ALBUM, SpReleaseGroup.SINGLE, SpReleaseGroup.COMPILATION)

/**
 * "Tutta la discografia" (1.494, Antonio: "tutto quello che hanno pubblicato, anche gli album, tutto quanto"):
 * ogni uscita dalla più nuova, una riga ciascuna (copertina, titolo, tipo · anno), i chip per restringere a un gruppo.
 * Toccata un'uscita si apre la sua pagina con i brani.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscographyScreen(page: SpArtistPage, loading: Boolean, onBack: () -> Unit, onOpenItem: (SpItem) -> Unit) {
    // Le altre pagine si chiedono solo qui, una volta (1.525): aprire l'artista ne scarica solo la prima di ogni gruppo.
    LaunchedEffect(page.artist.id) { SpotifyStore.loadDiscography(page.artist.id) }
    var only by rememberSaveable { mutableStateOf<SpReleaseGroup?>(null) }
    val groups = SpReleaseGroup.entries.filter { it.of(page).isNotEmpty() }
    val rows = if (only == SpReleaseGroup.APPEARS) page.appearsOn.map { it to SpReleaseGroup.APPEARS } else SpotifyArtist.everything(page, only)
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopLevelBar(
            title = page.artist.title,
            subtitle = "Discografia · " + releasesLine(maxOf(page.ownTotal, SpotifyArtist.everything(page).size)),
            onBack = onBack,
            scrollBehavior = scroll,
        )
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            if (groups.size > 1) item(key = "chips") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { CanvasChip(selected = only == null, onClick = { only = null }, label = "Tutto") }
                    items(groups) { g -> CanvasChip(selected = only == g, onClick = { only = g }, label = g.label) }
                }
            }
            items(rows, key = { (item, g) -> g.name + item.uri }) { (item, g) ->
                ReleaseRow(item, g, page.released[item.id]?.year) { onOpenItem(item) }
            }
            if (loading && !page.complete) item(key = "more") { PlaceholderList(rows = 3, label = "Carico il resto della discografia") }
        }
    }
}

/** "1 uscita", "42 uscite". */
internal fun releasesLine(n: Int): String = if (n == 1) "1 uscita" else "$n uscite"

/** Una riga dell'elenco intero: copertina a sinistra, titolo, sotto "Album · 1973" (o l'artista, per "Appare in"). */
@Composable
private fun ReleaseRow(item: SpItem, group: SpReleaseGroup, year: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .tapOrSave({ SavedLinks.spotify(item) }, onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item.image, MaterialTheme.shapes.small, 56.dp, MediaMarks.of(item))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (group == SpReleaseGroup.APPEARS) item.subtitle else listOfNotNull(group.one, year?.toString()).joinToString(" · ")
            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Un brano popolare (1.529, come i brani di un album): il numero a sinistra, il titolo, la durata a destra. */
@Composable
private fun PopularRow(rank: Int, track: SpItem, artistName: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .tapOrSave({ SavedLinks.spotify(track) }, onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Con altri artisti si vede con chi è; da solo basta il titolo.
            val others = track.subtitle.split(", ").filter { it.isNotBlank() && !it.equals(artistName, ignoreCase = true) }
            if (others.isNotEmpty()) {
                Text(
                    "con " + others.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (track.durationMs > 0) {
            Text(
                duration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** 252000 → "4:12". */
internal fun duration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** Album o singoli in fila: copertina, titolo, sotto l'anno (o l'artista, per "Appare in"). */
@Composable
private fun ReleaseCovers(list: List<SpItem>, onOpen: (SpItem) -> Unit, showArtist: Boolean = false, onAll: (() -> Unit)? = null) {
    val tile = coverTileWidth()
    // Il gruppo cambia coi chip: la fila riparte dall'inizio, dalla più nuova.
    val state = rememberLazyListState()
    LaunchedEffect(list) { state.scrollToItem(0) }
    LazyRow(state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { "rel-" + it.uri }) { album ->
            Column(Modifier.width(tile).bouncyPress(0.96f).clickable { onOpen(album) }) {
                val year = album.subtitle.substringAfterLast(" · ", "")
                CaptionedCover(album.title, if (showArtist) album.subtitle.substringBefore(" · ") else year) {
                    Cover(album.image, MaterialTheme.shapes.medium, tile, MediaMarks.of(album))
                }
            }
        }
        if (onAll != null && list.size > ALL_AFTER) item(key = "all") { AllTile(tile, "Tutta la discografia", releasesLine(list.size), images = list.take(4).map { it.image }, onClick = onAll) }
    }
}

/** Con poche uscite la fila basta da sola; oltre, in fondo c'è la discografia intera. */
private const val ALL_AFTER = 6
