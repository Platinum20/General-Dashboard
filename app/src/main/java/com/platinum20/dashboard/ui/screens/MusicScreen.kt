package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.SavedLink
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
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtMusic
import com.platinum20.dashboard.media.youtube.YtVideo
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import kotlinx.coroutines.delay

private val musicTabs = listOf("Per te", "Playlist", "Libreria", "Cerca")

/**
 * Musica: Spotify (con le API ufficiali, suona nel suo riproduttore dentro l'app) e YouTube Music
 * insieme, nelle stesse liste e nella stessa ricerca. I podcast stanno in "Radio e podcast".
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
    // Basta l'accesso fatto nell'app; il collegamento con un'app per sviluppatori resta un'alternativa.
    val connected = official || MediaService.SPOTIFY in loggedIn
    // Collegamento ufficiale raggiungibile anche quando l'accesso nell'app c'è già ma il feed non si carica.
    var setup by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = setup && !official) { setup = false }
    val message by SpotifyStore.message.collectAsState()
    val playback by SpotifyStore.playback.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                subtitle = if (opened != null) "Spotify" else "Spotify e YouTube Music",
                onBack = { if (opened != null) opened = null else onBack() },
                actions = {
                    if (!official) {
                        IconButton(onClick = { setup = !setup }) {
                            Icon(Icons.Outlined.Key, contentDescription = "Collegamento ufficiale")
                        }
                    }
                    if (connected) {
                        IconButton(onClick = {
                            val item = opened
                            when {
                                item != null -> SpotifyStore.loadChildren(item, force = true)
                                tab == 0 -> SpotifyStore.refreshHome(force = true)
                                tab == 1 -> SpotifyStore.refreshPlaylists(force = true)
                                tab == 2 -> SpotifyStore.refreshLibrary(force = true)
                            }
                        }) { Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna") }
                        IconButton(onClick = {
                            if (official) SpotifyAuth.disconnect() else Accounts.logout(MediaService.SPOTIFY)
                            SpotifyWebSession.clear()
                            SpotifyStore.clear()
                        }) { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = "Scollega Spotify") }
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (setup && !official) {
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
            // Pillole come in Video e in Radio e podcast.
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                musicTabs.forEachIndexed { index, label -> CanvasChip(selected = tab == index, onClick = { tab = index }, label = label) }
            }
            val onItem: (SpItem) -> Unit = { item ->
                if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) SpotifyPlayer.play(context, item) else opened = item
            }
            when (tab) {
                0 -> HomeTab(connected, onItem, onLogin)
                1 -> if (connected) PlaylistsTab(onItem) else ConnectSpotify(onLogin)
                2 -> if (connected) LibraryTab(onItem) else ConnectSpotify(onLogin)
                else -> SearchTab(connected, onItem)
            }
        }
    }
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
private fun HomeTab(connected: Boolean, onItem: (SpItem) -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val state by SpotifyStore.home.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val ytOfficial by YouTubeAuth.connected.collectAsState()
    val youTube = ytOfficial || MediaService.YOUTUBE in loggedIn
    val ytLists by YouTubeStore.playlistItems.collectAsState()
    LaunchedEffect(connected) { if (connected) SpotifyStore.refreshHome() }
    LaunchedEffect(youTube) { if (youTube) YouTubeStore.loadPlaylist(context, YtMusic.LIKED) }
    val ytLiked = ytLists[YtMusic.LIKED]?.data.orEmpty()
    val home = state.data
    val releases by SpotifyStore.releases.collectAsState()
    val seen by SpotifyStore.seenReleases.collectAsState()
    LaunchedEffect(connected) { if (connected) SpotifyStore.refreshReleases() }
    val recentReleases = releases.data.orEmpty().takeIf { connected }.orEmpty()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!connected) item(key = "connect") { ConnectCard(onLogin) }
        else if (home == null && state.loading) item(key = "loading") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
        state.error?.takeIf { connected }?.let { error -> item(key = "error") { Text(error, color = MaterialTheme.colorScheme.error) } }
        // In cima quello che è uscito da poco: lo scopo di News, applicato alla musica.
        if (recentReleases.isNotEmpty()) item(key = "releases") {
            ReleasesRow(recentReleases, seen) { release ->
                SpotifyStore.markReleaseSeen(release.item.id)
                onItem(release.item)
            }
        }
        if (home != null && connected) {
            section("Ascoltati di recente", home.recent.take(10), onItem)
        }
        // YouTube Music accanto a Spotify: i brani con il "Mi piace", in solo audio nel player dell'app.
        if (ytLiked.isNotEmpty()) ytSection("Mi piace su YouTube Music", ytLiked.take(15), ytLiked)
        if (home != null && connected) {
            section("I tuoi brani del mese", home.topTracks.take(15), onItem)
            section("Artisti che ascolti di più", home.topArtists, onItem)
        }
        if (!connected && !youTube) item(key = "yt-hint") {
            Text(
                "Cerca un brano: lo trovi anche su YouTube Music, senza account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "Nuove uscite": copertine in fila, con il pallino finché non le apri. */
@Composable
private fun ReleasesRow(list: List<SpRelease>, seen: Set<String>, onOpen: (SpRelease) -> Unit) {
    val today = LocalDate.now()
    val fresh = NewReleases.unseen(list, seen)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(if (fresh > 0) "Nuove uscite · $fresh da ascoltare" else "Nuove uscite", Modifier.padding(top = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

/** Invito discreto a collegare Spotify: la musica di YouTube intanto c'è già. */
@Composable
private fun ConnectCard(onLogin: () -> Unit) {
    CanvasCard(onClick = onLogin, modifier = Modifier.fillMaxWidth().bouncyPress(0.98f), shape = MaterialTheme.shapes.extraLarge) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.GraphicEq, TileAccent.GREEN)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Collega Spotify", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Playlist, libreria e ascolti recenti accanto alla musica di YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.ytSection(title: String, list: List<YtVideo>, queue: List<YtVideo>) {
    if (list.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, Modifier.padding(top = 8.dp)) }
    items(list, key = { "$title-${it.id}" }) { video -> YtSongRow(video, queue) }
}

/** Un brano di YouTube Music: copertina, titolo e artista, con il marchio della fonte. */
@Composable
private fun YtSongRow(video: YtVideo, queue: List<YtVideo>) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f).clip(MaterialTheme.shapes.large)
            .tapOrSave({ SavedLinks.youtube(video).asMusic() }) { YtMusic.play(context, video, queue) },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(video.thumbnail, MaterialTheme.shapes.small, 56)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(video.channel.removeSuffix(" - Topic").ifBlank { null }, "YouTube Music").joinToString(" · "),
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
private fun PlaylistsTab(onItem: (SpItem) -> Unit) {
    val state by SpotifyStore.playlists.collectAsState()
    LaunchedEffect(Unit) { SpotifyStore.refreshPlaylists() }
    FeedList(state, empty = "Nessuna playlist") {
        items(state.data.orEmpty(), key = { it.id }) { ItemRow(it, onClick = { onItem(it) }) }
    }
}

@Composable
private fun LibraryTab(onItem: (SpItem) -> Unit) {
    val state by SpotifyStore.library.collectAsState()
    LaunchedEffect(Unit) { SpotifyStore.refreshLibrary() }
    val library = state.data
    FeedList(state, empty = "") {
        library ?: return@FeedList
        section("Brani che ti piacciono", library.liked, onItem)
        section("Album salvati", library.albums, onItem)
    }
}

/** Una ricerca sola per Spotify e YouTube Music: risultati di entrambi, uno sotto l'altro. */
@Composable
private fun SearchTab(connected: Boolean, onItem: (SpItem) -> Unit) {
    val spotify by SpotifyStore.search.collectAsState()
    val youTube by YtMusic.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Brani, artisti, album") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            colors = canvasFieldColors(),
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (connected) SpotifyStore.search(query)
                YtMusic.search(query)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val sp = spotify.data.orEmpty().filter { it.kind != SpKind.SHOW && it.kind != SpKind.EPISODE }
        val yt = youTube.data.orEmpty()
        val loading = (connected && spotify.loading) || youTube.loading
        if (sp.isEmpty() && yt.isEmpty() && !loading && youTube.error == null && spotify.error == null) return@Column
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) item(key = "loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            if (connected) section("Su Spotify", sp, onItem)
            ytSection("Su YouTube Music", yt, yt)
            listOfNotNull(spotify.error.takeIf { connected }, youTube.error).forEach { error ->
                item(key = "err-$error") { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            if (!loading && sp.isEmpty() && yt.isEmpty()) item(key = "empty") {
                Text("Nessun risultato", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

private fun LazyListScope.section(title: String, list: List<SpItem>, onItem: (SpItem) -> Unit) {
    if (list.isEmpty()) return
    item(key = "header-$title") { SectionHeader(title, Modifier.padding(top = 8.dp)) }
    items(list, key = { "$title-${it.uri}" }) { ItemRow(it, onClick = { onItem(it) }) }
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

/** Login di Spotify dentro l'app: la pagina di autorizzazione in una finestra, il ritorno lo intercettiamo noi. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SpotifyAuthDialog(url: String, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                    Text("Collega Spotify", style = MaterialTheme.typography.titleMedium)
                }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = browserUserAgent(settings.userAgentString)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val target = request.url
                                    if (SpotifyAuth.isRedirect(target)) {
                                        SpotifyAuth.handleRedirect(target)
                                        onDone()
                                        return true
                                    }
                                    return target.scheme != "http" && target.scheme != "https"
                                }
                            }
                            loadWhenAttached(url)
                        }.inFrame(ctx)
                    },
                    onRelease = { frame -> (frame.getChildAt(0) as? WebView)?.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Un video di YouTube ascoltato come musica: nella nota si riapre nel player della musica. */
private fun SavedLink.asMusic(): SavedLink =
    if (this is SavedLink.Media) copy(item = item.copy(kind = ResumeKind.MUSIC)) else this

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
