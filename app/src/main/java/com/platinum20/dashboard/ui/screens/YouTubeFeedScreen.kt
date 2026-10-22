package com.platinum20.dashboard.ui.screens

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.Accounts
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo
import kotlinx.coroutines.launch

private val youTubeTabs = listOf("Iscrizioni", "Playlist", "Mi piace", "Più tardi", "Cerca")

/** Il tuo YouTube in versione nativa: iscrizioni, playlist, video piaciuti e ricerca. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeFeedScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenWeb: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    YouTubeAuth.init(context)
    val official by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    // Basta l'accesso a YouTube fatto nell'app; l'API ufficiale di Google resta un'alternativa.
    val connected = official || MediaService.YOUTUBE in loggedIn
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by remember { mutableStateOf<YtPlaylist?>(null) }

    BackHandler(enabled = openPlaylist != null) { openPlaylist = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openPlaylist?.title ?: "YouTube", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (openPlaylist != null) openPlaylist = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (connected) {
                        IconButton(onClick = {
                            val playlist = openPlaylist
                            when {
                                playlist != null -> YouTubeStore.loadPlaylist(context, playlist.id, force = true)
                                tab == 0 -> YouTubeStore.refreshSubscriptions(context, force = true)
                                tab == 1 -> YouTubeStore.refreshPlaylists(context, force = true)
                                tab == 2 -> YouTubeStore.refreshLiked(context, force = true)
                                tab == 3 -> YouTubeStore.refreshWatchLater(context, force = true)
                            }
                        }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna")
                        }
                    }
                    IconButton(onClick = onOpenWeb) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Apri YouTube completo")
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
            if (!connected) {
                ConnectYouTube(onOpenWeb = onOpenWeb, onLogin = onLogin)
                return@Column
            }
            val playlist = openPlaylist
            if (playlist != null) {
                PlaylistVideos(playlist, onPlay)
                return@Column
            }
            TabRow(selectedTabIndex = tab) {
                youTubeTabs.forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, maxLines = 1) })
                }
            }
            when (tab) {
                0 -> SubscriptionsTab(onPlay)
                1 -> PlaylistsTab(onOpen = { openPlaylist = it })
                2 -> LikedTab(onPlay)
                3 -> WatchLaterTab(onPlay)
                else -> SearchTab(onPlay)
            }
        }
    }
}

@Composable
private fun ConnectYouTube(onOpenWeb: () -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var notConfigured by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        busy = false
        if (!YouTubeAuth.handleConsent(context, result.data)) problem = "Accesso annullato"
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.OndemandVideo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Collega il tuo YouTube", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Vedrai qui i nuovi video dei canali che segui, le tue playlist, i Mi piace e Guarda più tardi. " +
                "Basta accedere a YouTube una volta, qui nell'app: nessuna registrazione da fare.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onLogin) { Text("Accedi a YouTube") }
        Spacer(Modifier.height(8.dp))
        TextButton(
            enabled = !busy,
            onClick = {
                busy = true
                problem = null
                scope.launch {
                    when (val outcome = YouTubeAuth.authorize(context)) {
                        is YouTubeAuth.Outcome.Authorized -> busy = false
                        is YouTubeAuth.Outcome.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(outcome.sender).build())
                        is YouTubeAuth.Outcome.Failed -> {
                            busy = false
                            notConfigured = outcome.notConfigured
                            problem = if (outcome.notConfigured) {
                                "Google non riconosce ancora l'app: va registrata una volta su Google Cloud."
                            } else {
                                "Accesso non riuscito: ${outcome.message}"
                            }
                        }
                    }
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Oppure collega l'API ufficiale di Google")
        }
        problem?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            if (notConfigured) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YouTubeAuth.GUIDE_URL)))
                }) { Text("Apri la guida") }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenWeb) { Text("Apri YouTube completo") }
    }
}

@Composable
private fun SubscriptionsTab(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.subscriptions.collectAsState()
    var hideShorts by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { YouTubeStore.refreshSubscriptions(context) }
    val videos = state.data.orEmpty().filter { !hideShorts || !it.isShort }
    FeedList(state, empty = "Nessun video nuovo dai canali che segui") {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = hideShorts, onClick = { hideShorts = !hideShorts }, label = { Text("Nascondi Shorts") })
            }
        }
        items(videos, key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
    }
}

@Composable
private fun PlaylistsTab(onOpen: (YtPlaylist) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.playlists.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshPlaylists(context) }
    FeedList(state, empty = "Non hai ancora playlist") {
        // "Guarda più tardi" e la cronologia non sono disponibili nelle API ufficiali.
        items(state.data.orEmpty(), key = { it.id }) { playlist ->
            Surface(
                onClick = { onOpen(playlist) },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Thumbnail(playlist.thumbnail, Modifier.width(120.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${playlist.count} video",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PlaylistVideos(playlist: YtPlaylist, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val all by YouTubeStore.playlistItems.collectAsState()
    val state = all[playlist.id] ?: Loadable(loading = true)
    LaunchedEffect(playlist.id) { YouTubeStore.loadPlaylist(context, playlist.id) }
    FeedList(state, empty = "Playlist vuota") {
        items(state.data.orEmpty(), key = { it.id }) { video ->
            VideoCard(video, onClick = { onPlay(video.watchUrl + "&list=" + playlist.id) })
        }
    }
}

@Composable
private fun LikedTab(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.liked.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshLiked(context) }
    FeedList(state, empty = "Nessun video con il Mi piace") {
        items(state.data.orEmpty(), key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
    }
}

@Composable
private fun WatchLaterTab(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.watchLater.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshWatchLater(context) }
    FeedList(state, empty = "Nessun video da guardare più tardi") {
        items(state.data.orEmpty(), key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl + "&list=WL") }) }
    }
}

@Composable
private fun SearchTab(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.search.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Cerca su YouTube") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { YouTubeStore.search(context, query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        if (state.data == null && !state.loading && state.error == null) return@Column
        FeedList(state, empty = "Nessun risultato") {
            items(state.data.orEmpty(), key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
        }
    }
}

/** Lista con caricamento, errore e messaggio per la lista vuota. */
@Composable
fun <T> FeedList(state: Loadable<T>, empty: String, content: LazyListScope.() -> Unit) {
    val isEmpty = (state.data as? List<*>)?.isEmpty() ?: (state.data == null)
    when {
        state.data == null && state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.data == null && state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            content()
            if (isEmpty && !state.loading) item {
                Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

@Composable
private fun VideoCard(video: YtVideo, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().bouncyPress(0.97f),
    ) {
        Column {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth())
            Column(Modifier.padding(12.dp)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        video.channel.takeIf { it.isNotBlank() },
                        video.published?.let { relativeTime(it) },
                        "Short".takeIf { video.isShort },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
