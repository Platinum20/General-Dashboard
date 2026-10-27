package com.platinum20.dashboard.ui.screens

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
import androidx.compose.ui.unit.dp
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
 * Musica: il tuo Spotify (API ufficiali, suona nel suo riproduttore dentro l'app). Una pagina sola
 * che scorre, con le sezioni in file orizzontali: nuove uscite, mix, playlist, preferiti, album.
 * Cerca = trascinare giù (Cerca ovunque già su Musica). I podcast stanno in "Radio e podcast".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val official by SpotifyAuth.connected.collectAsState()
    // Il riproduttore di Spotify si prepara appena si entra: al primo tocco la musica parte subito.
    LaunchedEffect(official) { if (official) SpotifyPlayer.start(context) }
    val loggedIn by Accounts.loggedIn.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    // Basta l'accesso fatto nell'app; il collegamento ufficiale sta in Impostazioni › Account.
    val connected = official || MediaService.SPOTIFY in loggedIn
    val message by SpotifyStore.message.collectAsState()
    var opened by remember { mutableStateOf<SpItem?>(null) }

    BackHandler(enabled = opened != null) { opened = null }

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
    // Stato della riproduzione aggiornato mentre la schermata è aperta.
    var visible by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { visible = true }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { visible = false }
    LaunchedEffect(connected, visible) {
        while (connected && visible) {
            SpotifyStore.refreshPlayback()
            delay(5_000)
        }
    }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = opened?.title ?: "Musica",
                subtitle = "Spotify",
                onBack = { if (opened != null) opened = null else onBack() },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (!connected) {
                ConnectSpotify(onLogin)
                return@Column
            }
            val parent = opened
            if (parent != null) {
                ChildrenList(parent, onOpen = { opened = it }, onPlay = { playSpotifyList(context, it, null) }, onPlayFrom = { child ->
                    // Da un album o una playlist si continua con i brani successivi, nella coda unica.
                    if (parent.kind == SpKind.ALBUM || parent.kind == SpKind.PLAYLIST || parent.kind == SpKind.SHOW) {
                        playSpotifyList(context, parent, child)
                    } else {
                        SpotifyPlayer.play(context, child)
                    }
                })
                return@Column
            }
            MusicHome(onOpen = { opened = it })
        }
    }
}

/** La pagina unica di Musica: ogni sezione è una fila che scorre di lato. */
@Composable
private fun MusicHome(onOpen: (SpItem) -> Unit) {
    val context = LocalContext.current
    val homeState by SpotifyStore.home.collectAsState()
    val playlistsState by SpotifyStore.playlists.collectAsState()
    val libraryState by SpotifyStore.library.collectAsState()
    val releases by SpotifyStore.releases.collectAsState()
    val seen by SpotifyStore.seenReleases.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshHome()
        SpotifyStore.refreshPlaylists()
        SpotifyStore.refreshLibrary()
        SpotifyStore.refreshReleases()
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
    // Un album, una playlist o un artista si apre; un brano parte, e la sua fila continua nella coda.
    val open: (SpItem, List<SpItem>) -> Unit = { item, row ->
        if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) playTracks(context, row, item) else onOpen(item)
    }
    val loading = home == null && homeState.loading
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (loading) item(key = "loading") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        listOfNotNull(homeState.error, playlistsState.error, libraryState.error).distinct().forEach { error ->
            item(key = "error-$error") { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        // In cima quello che è uscito da poco: lo scopo di News, applicato alla musica.
        val recentReleases = releases.data.orEmpty()
        if (recentReleases.isNotEmpty()) item(key = "releases") {
            ReleasesRow(recentReleases, seen) { release ->
                SpotifyStore.markReleaseSeen(release.item.id)
                onOpen(release.item)
            }
        }
        if (mixes.isNotEmpty()) item(key = "mixes") { MixRow(mixes) }
        coverRow("Le tue playlist", playlistsState.data.orEmpty(), open)
        coverRow("Ascoltati di recente", home?.recent.orEmpty(), open)
        coverRow("Brani che ti piacciono", library?.liked.orEmpty(), open)
        coverRow("Album salvati", library?.albums.orEmpty(), open)
        coverRow("I tuoi brani del mese", home?.topTracks.orEmpty(), open)
        coverRow("Artisti che ascolti di più", home?.topArtists.orEmpty(), open)
    }
}

/** "Nuove uscite": copertine in fila, con il pallino finché non le apri. */
@Composable
private fun ReleasesRow(list: List<SpRelease>, seen: Set<String>, onOpen: (SpRelease) -> Unit) {
    val today = LocalDate.now()
    val fresh = NewReleases.unseen(list, seen)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(if (fresh > 0) "Nuove uscite · $fresh da ascoltare" else "Nuove uscite", Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { "release-${it.item.id}" }) { release ->
                Column(
                    Modifier
                        .width(128.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .bouncyPress(0.96f)
                        .clickable { onOpen(release) },
                ) {
                    Box {
                        Cover(release.item.image, MaterialTheme.shapes.medium, 128)
                        if (release.item.id !in seen) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(release.item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        release.item.subtitle.substringBefore(" · ") + " · " + NewReleases.ago(release.released, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Una sezione di Musica: titolo e copertine in fila orizzontale. */
private fun LazyListScope.coverRow(title: String, list: List<SpItem>, onItem: (SpItem, List<SpItem>) -> Unit) {
    if (list.isEmpty()) return
    item(key = "row-$title") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(title, Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { "$title-${it.uri}" }) { item -> CoverTile(item, onClick = { onItem(item, list) }) }
            }
        }
    }
}

@Composable
private fun CoverTile(item: SpItem, onClick: () -> Unit) {
    val round = item.kind == SpKind.ARTIST
    Column(
        Modifier
            .width(128.dp)
            .clip(MaterialTheme.shapes.medium)
            .bouncyPress(0.96f)
            // Tenendo premuto un brano si salva nelle note.
            .tapOrSave({ SavedLinks.spotify(item) }, onClick),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Cover(item.image, if (round) CircleShape else MaterialTheme.shapes.medium, 128)
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!round) {
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

/** "Mix per te": la foto dell'artista con la scritta Mix sopra; un tocco e parte tutto il mix. */
@Composable
private fun MixRow(mixes: List<SpMix>) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Mix per te", Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(mixes, key = { it.id }) { mix ->
                Column(
                    Modifier
                        .width(128.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .bouncyPress(0.96f)
                        .clickable { playTracks(context, mix.tracks, null) },
                ) {
                    Box {
                        Cover(mix.image, MaterialTheme.shapes.medium, 128)
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
                    Spacer(Modifier.height(6.dp))
                    Text(mix.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        mix.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Brani di una fila (o di un mix) nella coda unica, da [startAt] in poi. */
private fun playTracks(context: android.content.Context, row: List<SpItem>, startAt: SpItem?) {
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
private fun ConnectSpotify(onLogin: () -> Unit) {
    val context = LocalContext.current
    val error by SpotifyAuth.error.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Collega il tuo Spotify", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ascoltati di recente, i tuoi preferiti, playlist, album e podcast. Basta accedere al tuo account " +
                "Spotify una volta, qui nell'app: nessuna registrazione da fare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin) { Text("Accedi a Spotify") }
        Spacer(Modifier.height(8.dp))
        // Come in YouTube: il collegamento ufficiale è un'alternativa discreta, senza codici da incollare.
        var authUrl by remember { mutableStateOf<String?>(null) }
        TextButton(onClick = { authUrl = SpotifyAuth.loginUrl().toString() }) {
            Text("Oppure usa il collegamento ufficiale di Spotify")
        }
        authUrl?.let { url -> SpotifyAuthDialog(url, onDone = { authUrl = null }) }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
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
                Cover(parent.image, if (parent.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.medium, 96)
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
private fun ItemRow(item: SpItem, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        // Tenendo premuto un brano si salva nelle note (album e playlist no: non si riaprono fermi).
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f).clip(MaterialTheme.shapes.large)
            .tapOrSave({ SavedLinks.spotify(item) }, onClick),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item.image, if (item.kind == SpKind.ARTIST) CircleShape else MaterialTheme.shapes.small, 56)
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
private fun Cover(url: String, shape: Shape, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}

/**
 * Un album, una playlist o un podcast di Spotify: i brani già caricati entrano nella coda unica da
 * [startAt] in poi, così si vedono, si spostano e si mischiano con le altre fonti. Se la lista non
 * c'è ancora, suona come prima dentro Spotify.
 */
private fun playSpotifyList(context: android.content.Context, parent: SpItem, startAt: SpItem?) {
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
