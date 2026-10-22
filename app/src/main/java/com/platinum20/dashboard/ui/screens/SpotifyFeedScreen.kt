package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.browserUserAgent
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.platinum20.dashboard.media.spotify.SpPlayback
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import kotlinx.coroutines.delay

private val spotifyTabs = listOf("Per te", "Playlist", "Libreria", "Cerca")

/** Il tuo Spotify in versione nativa, con le API ufficiali. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyFeedScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val official by SpotifyAuth.connected.collectAsState()
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
    val snackbar = remember { SnackbarHostState() }

    BackHandler(enabled = opened != null) { opened = null }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            SpotifyStore.consumeMessage()
        }
    }
    val playerError by SpotifyPlayer.error.collectAsState()
    LaunchedEffect(playerError) {
        playerError?.let {
            snackbar.showSnackbar(it)
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
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(opened?.title ?: "Spotify", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (opened != null) opened = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
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
        bottomBar = {
            val state = playback
            if (connected && state?.item != null) NowPlayingBar(state)
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (!connected || (setup && !official)) {
                ConnectSpotify(onLogin)
                return@Column
            }
            val parent = opened
            if (parent != null) {
                ChildrenList(parent, onOpen = { opened = it }, onPlay = { SpotifyPlayer.play(context, it) }, onPlayFrom = { child ->
                    // Da un album o una playlist si continua con i brani successivi.
                    if (parent.kind == SpKind.ALBUM || parent.kind == SpKind.PLAYLIST || parent.kind == SpKind.SHOW) {
                        SpotifyPlayer.play(context, parent, startAt = child)
                    } else {
                        SpotifyPlayer.play(context, child)
                    }
                })
                return@Column
            }
            // Scorrevole: con cinque schede le etichette non vengono più tagliate.
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                spotifyTabs.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, maxLines = 1) })
                }
            }
            val onItem: (SpItem) -> Unit = { item ->
                if (item.kind == SpKind.TRACK || item.kind == SpKind.EPISODE) SpotifyPlayer.play(context, item) else opened = item
            }
            when (tab) {
                0 -> HomeTab(onItem)
                1 -> PlaylistsTab(onItem)
                2 -> LibraryTab(onItem)
                else -> SearchTab(onItem)
            }
        }
    }
}

@Composable
private fun ConnectSpotify(onLogin: () -> Unit) {
    val context = LocalContext.current
    val clientId by SpotifyAuth.clientId.collectAsState()
    val error by SpotifyAuth.error.collectAsState()
    var draft by rememberSaveable { mutableStateOf(clientId) }

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
        Spacer(Modifier.height(32.dp))
        Text(
            "In alternativa, se hai creato un'app su developer.spotify.com, incolla qui il suo Client ID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            label = { Text("Client ID dell'app Spotify") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        var authUrl by remember { mutableStateOf<String?>(null) }
        OutlinedButton(
            enabled = draft.length >= 20,
            onClick = {
                SpotifyAuth.setClientId(draft)
                authUrl = SpotifyAuth.loginUrl().toString()
            },
        ) { Text("Collega con il Client ID") }
        authUrl?.let { url -> SpotifyAuthDialog(url, onDone = { authUrl = null }) }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HomeTab(onItem: (SpItem) -> Unit) {
    val state by SpotifyStore.home.collectAsState()
    LaunchedEffect(Unit) { SpotifyStore.refreshHome() }
    val home = state.data
    FeedList(state, empty = "") {
        home ?: return@FeedList
        item {
            Text(
                "Ciao ${home.profile.name}" + if (home.profile.premium) " · Premium" else "",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        section("Ascoltati di recente", home.recent.take(15), onItem)
        section("I tuoi brani del mese", home.topTracks.take(15), onItem)
        section("Artisti che ascolti di più", home.topArtists, onItem)
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
        section("Podcast", library.shows, onItem)
    }
}

@Composable
private fun SearchTab(onItem: (SpItem) -> Unit) {
    val state by SpotifyStore.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Artisti, brani, album, podcast") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { SpotifyStore.search(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (state.data == null && !state.loading && state.error == null) return@Column
        FeedList(state, empty = "Nessun risultato") {
            items(state.data.orEmpty(), key = { it.uri }) { ItemRow(it, onClick = { onItem(it) }) }
        }
    }
}

@Composable
private fun ChildrenList(parent: SpItem, onOpen: (SpItem) -> Unit, onPlay: (SpItem) -> Unit, onPlayFrom: (SpItem) -> Unit) {
    val context = LocalContext.current
    val all by SpotifyStore.children.collectAsState()
    val state = all[parent.id] ?: Loadable(loading = true)
    LaunchedEffect(parent.id) { SpotifyStore.loadChildren(parent) }
    FeedList(state, empty = "Vuoto") {
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
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(list, key = { "$title-${it.uri}" }) { ItemRow(it, onClick = { onItem(it) }) }
}

@Composable
private fun ItemRow(item: SpItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f),
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

@Composable
private fun NowPlayingBar(state: SpPlayback) {
    val item = state.item ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(item.image, MaterialTheme.shapes.small, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(item.subtitle, state.device).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { SpotifyStore.control(SpotifyApi::previous) }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Indietro")
            }
            FilledIconButton(onClick = {
                SpotifyStore.control(if (state.playing) SpotifyApi::pause else SpotifyApi::resume)
            }) {
                Icon(if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.playing) "Pausa" else "Riproduci")
            }
            IconButton(onClick = { SpotifyStore.control(SpotifyApi::next) }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Avanti")
            }
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
                            loadUrl(url)
                        }
                    },
                    onRelease = { it.destroy() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
