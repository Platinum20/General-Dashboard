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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.spotify.SpArtistPage
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpotifyArtist
import com.platinum20.dashboard.media.spotify.SpotifyStore

/**
 * La pagina di un artista (1.479, Antonio: "se vado su Pink Floyd dev'essere come Spotify"): la foto grande col nome
 * sopra, follower e generi, il tasto play, i Popolari numerati (cinque, poi "Altri"), la Discografia divisa in Album e
 * Singoli e EP, e dove appare. Ci si arriva dalle storie in cima a Media, dalla ricerca e dal nome nel player.
 */
@Composable
internal fun ArtistScreen(artist: SpItem, onBack: () -> Unit, onOpenItem: (SpItem) -> Unit) {
    val context = LocalContext.current
    val pages by SpotifyStore.artistPages.collectAsState()
    LaunchedEffect(artist.id) { SpotifyStore.loadArtist(artist) }
    val state = pages[artist.id] ?: Loadable(loading = true)
    val page = state.data
    var showAll by rememberSaveable { mutableStateOf(false) }
    var singles by rememberSaveable { mutableIntStateOf(0) }
    val popular = page?.popular.orEmpty()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "head") { ArtistHeader(page?.photo?.ifBlank { null } ?: artist.image, page?.artist?.title ?: artist.title) }
            item(key = "meta") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        page?.let(::metaLine).orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (popular.isNotEmpty()) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .bouncyPress(0.9f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { playTracks(context, popular, null) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Riproduci", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
                        }
                    }
                }
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
            if (page != null && (page.albums.isNotEmpty() || page.singles.isNotEmpty())) {
                item(key = "disc-h") { ArtistTitle("Discografia") }
                item(key = "disc-chips") {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (page.albums.isNotEmpty()) CanvasChip(selected = singles == 0 || page.singles.isEmpty(), onClick = { singles = 0 }, label = "Album")
                        if (page.singles.isNotEmpty()) CanvasChip(selected = singles == 1 || page.albums.isEmpty(), onClick = { singles = 1 }, label = "Singoli e EP")
                    }
                }
                item(key = "disc") {
                    val list = if ((singles == 1 && page.singles.isNotEmpty()) || page.albums.isEmpty()) page.singles else page.albums
                    ReleaseCovers(list, onOpenItem)
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

/** La foto grande dell'artista, che sfuma nello sfondo, col nome sopra in grande. */
@Composable
private fun ArtistHeader(photo: String, name: String) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (photo.isNotBlank()) {
            AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to background)),
        )
        Text(
            name,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ArtistTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** Un brano popolare: il numero a sinistra, copertina e titolo, la durata a destra. */
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
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Cover(track.image, MaterialTheme.shapes.small, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun ReleaseCovers(list: List<SpItem>, onOpen: (SpItem) -> Unit, showArtist: Boolean = false) {
    val tile = coverTileWidth()
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { "rel-" + it.uri }) { album ->
            Column(Modifier.width(tile).bouncyPress(0.96f).clickable { onOpen(album) }) {
                val year = album.subtitle.substringAfterLast(" · ", "")
                CaptionedCover(album.title, if (showArtist) album.subtitle.substringBefore(" · ") else year) {
                    Cover(album.image, MaterialTheme.shapes.medium, tile)
                }
            }
        }
    }
}
