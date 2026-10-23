package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private val youTubeTabs = listOf("Home", "Iscrizioni", "Playlist", "Mi piace", "Più tardi", "Cronologia", "Cerca")
private const val TAB_HOME = 0
private const val TAB_SUBS = 1
private const val TAB_PLAYLISTS = 2
private const val TAB_LIKED = 3
private const val TAB_LATER = 4
private const val TAB_HISTORY = 5
private const val TAB_SEARCH = 6

/**
 * Il tuo YouTube nell'app: Home, iscrizioni, playlist, Mi piace, Guarda più tardi e ricerca,
 * con anteprime grandi, durata e foto del canale. I video si aprono nel player dell'app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeFeedScreen(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onLogin: () -> Unit,
    /** I video del telefono, i file e i link: stanno anche loro in Video. */
    onOpenPhone: () -> Unit,
) {
    val context = LocalContext.current
    YouTubeAuth.init(context)
    val official by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    // Basta l'accesso a YouTube fatto nell'app; l'API ufficiale di Google resta un'alternativa.
    val connected = official || MediaService.YOUTUBE in loggedIn
    // Collegamento ufficiale raggiungibile anche quando l'accesso nell'app c'è già ma il feed non si carica.
    var setup by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = setup && !official) { setup = false }
    // Si apre sulle Iscrizioni; la Home è subito a sinistra.
    var tab by rememberSaveable { mutableIntStateOf(TAB_SUBS) }
    var openPlaylist by remember { mutableStateOf<YtPlaylist?>(null) }
    // Filtri del feed nella stessa fila delle schede: una sola barra che scorre, non due.
    var hideShorts by rememberSaveable { mutableStateOf(true) }
    var onlyLive by rememberSaveable { mutableStateOf(false) }
    val feedTab = tab == TAB_HOME || tab == TAB_SUBS
    BackHandler(enabled = openPlaylist != null) { openPlaylist = null }

    val subs by YouTubeStore.subscriptions.collectAsState()
    val fresh = subs.data.orEmpty().count { v -> v.published?.let { Duration.between(it, Instant.now()).toHours() < 24 } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopLevelBar(
                title = openPlaylist?.title ?: "Video",
                subtitle = when {
                    openPlaylist != null -> "Playlist · ${openPlaylist?.count} video"
                    fresh > 0 -> "$fresh video nuovi oggi"
                    else -> "YouTube e i video del telefono"
                },
                onBack = { if (openPlaylist != null) openPlaylist = null else onBack() },
            ) {
                // Senza accesso la pagina di collegamento è già quella mostrata: la chiave servirebbe a nulla.
                if (connected && !official) RoundIconButton(Icons.Outlined.Key, "Collegamento ufficiale") { setup = !setup }
                if (connected && openPlaylist == null) {
                    RoundIconButton(Icons.Outlined.Search, "Cerca") { tab = TAB_SEARCH }
                }
                if (connected) {
                    RoundIconButton(Icons.Outlined.Refresh, "Aggiorna") {
                        val playlist = openPlaylist
                        when {
                            playlist != null -> YouTubeStore.loadPlaylist(context, playlist.id, force = true)
                            tab == TAB_HOME -> YouTubeStore.refreshHome(context, force = true)
                            tab == TAB_SUBS -> YouTubeStore.refreshSubscriptions(context, force = true)
                            tab == TAB_PLAYLISTS -> YouTubeStore.refreshPlaylists(context, force = true)
                            tab == TAB_LIKED -> YouTubeStore.refreshLiked(context, force = true)
                            tab == TAB_LATER -> YouTubeStore.refreshWatchLater(context, force = true)
                        }
                    }
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (!connected || (setup && !official)) {
                ConnectYouTube(onLogin = onLogin, onOpenPhone = onOpenPhone)
                return@Column
            }
            val playlist = openPlaylist
            if (playlist != null) {
                PlaylistVideos(playlist, onPlay)
                return@Column
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                items(youTubeTabs.size) { index ->
                    CanvasChip(selected = tab == index, onClick = { tab = index }, label = youTubeTabs[index])
                }
                // Il vecchio "Video": file, link e video registrati, a un tocco dalle schede di YouTube.
                item(key = "phone") { CanvasChip(selected = false, onClick = onOpenPhone, label = "Sul telefono ›") }
                if (feedTab) {
                    item(key = "divider") {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = 1.dp, height = 24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    item(key = "shorts") {
                        CanvasChip(selected = hideShorts, onClick = { hideShorts = !hideShorts }, label = "Nascondi Shorts")
                    }
                    item(key = "live") {
                        CanvasChip(selected = onlyLive, onClick = { onlyLive = !onlyLive }, label = "Solo dirette")
                    }
                }
            }
            when (tab) {
                TAB_HOME -> HomeTab(hideShorts, onlyLive, onPlay)
                TAB_SUBS -> SubscriptionsTab(hideShorts, onlyLive, onPlay)
                TAB_PLAYLISTS -> PlaylistsTab(onOpen = { openPlaylist = it })
                TAB_LIKED -> {
                    val liked by YouTubeStore.liked.collectAsState()
                    com.platinum20.dashboard.media.youtube.YtFavorites.init(context)
                    val mine by com.platinum20.dashboard.media.youtube.YtFavorites.items.collectAsState()
                    // In cima i Mi piace messi dal player dell'app, poi quelli del tuo account YouTube.
                    val all = liked.copy(data = (mine + liked.data.orEmpty()).distinctBy { it.id }.takeIf { liked.data != null || mine.isNotEmpty() })
                    SimpleVideos(all, "Nessun video con il Mi piace", onPlay) { YouTubeStore.refreshLiked(context) }
                }
                TAB_LATER -> {
                    val later by YouTubeStore.watchLater.collectAsState()
                    SimpleVideos(later, "Nessun video da guardare più tardi", onPlay, list = "WL") { YouTubeStore.refreshWatchLater(context) }
                }
                TAB_HISTORY -> HistoryTab(onPlay)
                else -> SearchTab(onPlay)
            }
        }
    }
}

@Composable
private fun ConnectYouTube(onLogin: () -> Unit, onOpenPhone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

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
        IconTile(Icons.Outlined.OndemandVideo, TileAccent.ORANGE, size = 72.dp, iconSize = 36.dp)
        Spacer(Modifier.height(20.dp))
        Text("Collega il tuo YouTube", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Vedrai qui la tua Home, i nuovi video dei canali che segui, le playlist, i Mi piace e Guarda più tardi. " +
                "Basta accedere a YouTube una volta, qui nell'app.",
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
        TextButton(onClick = onOpenPhone) { Text("Guarda i video del telefono") }
        problem?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun HomeTab(hideShorts: Boolean, onlyLive: Boolean, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.home.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshHome(context) }
    VideoFeed(state, empty = "La Home è vuota: tocca Aggiorna", hideShorts, onlyLive, onPlay = onPlay)
}

@Composable
private fun SubscriptionsTab(hideShorts: Boolean, onlyLive: Boolean, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.subscriptions.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshSubscriptions(context) }
    VideoFeed(state, empty = "Nessun video nuovo dai canali che segui", hideShorts, onlyLive, onPlay = onPlay)
}

/**
 * Feed di video con i filtri (Short, dirette): in cima cosa stai guardando, poi gli Short
 * in una fila orizzontale (se non sono nascosti) e i video con le anteprime grandi.
 */
@Composable
fun VideoFeed(state: Loadable<List<YtVideo>>, empty: String, hideShorts: Boolean, onlyLive: Boolean, onPlay: (String) -> Unit) {
    val all = state.data.orEmpty()
    val lives = all.filter { it.live || it.upcoming }.sortedByDescending { it.live }
    val shorts = all.filter { it.isShort }
    val videos = if (onlyLive) lives else all.filter { !it.isShort }
    FeedList(state, empty = empty) {
        item(key = "now") { WatchingHero(onPlay) }
        if (onlyLive && lives.isEmpty() && state.data != null) item(key = "nolive") {
            Text(
                "Nessuna diretta in corso o in arrivo",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
        if (!onlyLive && !hideShorts && shorts.isNotEmpty()) {
            item(key = "shorts") { ShortsShelf(shorts, onPlay) }
        }
        items(videos, key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
    }
}

@Composable
private fun SimpleVideos(state: Loadable<List<YtVideo>>, empty: String, onPlay: (String) -> Unit, list: String? = null, load: () -> Unit) {
    LaunchedEffect(Unit) { load() }
    FeedList(state, empty = empty) {
        items(state.data.orEmpty(), key = { it.id }) { video ->
            VideoCard(video, onClick = { onPlay(video.watchUrl + (list?.let { "&list=$it" } ?: "")) })
        }
    }
}

/** Il video che stai guardando (o hai in pausa) nel player dell'app: un tocco e ci torni. */
@Composable
private fun WatchingHero(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val now by YtPlayer.now.collectAsState()
    val stream = now?.stream ?: return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val container = if (dark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.inverseSurface
    val content = if (dark) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.inverseOnSurface
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .clickable { onPlay("https://www.youtube.com/watch?v=${stream.id}") }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = stream.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (now?.playing == true) "Stai guardando" else "In pausa").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.7f),
            )
            Text(stream.title, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        FilledIconButton(
            onClick = { YtPlayer.toggle(context) },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = content, contentColor = container),
        ) {
            Icon(if (now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/pausa")
        }
    }
}

@Composable
private fun ShortsShelf(shorts: List<YtVideo>, onPlay: (String) -> Unit) {
    Column {
        SectionHeader("Shorts · ${shorts.size}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shorts.take(20), key = { it.id }) { short ->
                Column(
                    Modifier
                        .width(120.dp)
                        .bouncyPress(0.96f)
                        .clickable { onPlay(short.watchUrl) }
                ) {
                    AsyncImage(
                        model = "https://i.ytimg.com/vi/${short.id}/hqdefault.jpg",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 16f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(short.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTab(onOpen: (YtPlaylist) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.playlists.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshPlaylists(context) }
    FeedList(state, empty = "Non hai ancora playlist") {
        items(state.data.orEmpty(), key = { it.id }) { playlist ->
            CanvasCard(Modifier.fillMaxWidth().bouncyPress(0.97f), onClick = { onOpen(playlist) }) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(140.dp)) {
                        Thumbnail(playlist.thumbnail, Modifier.fillMaxWidth())
                        Row(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.PlaylistPlay, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${playlist.count}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(playlist.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${playlist.count} video",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            VideoRow(video, onClick = { onPlay(video.watchUrl + "&list=" + playlist.id) })
        }
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
            shape = CircleShape,
            colors = canvasFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { YouTubeStore.search(context, query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .autoFocus(state.data == null),
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

/** Video come nell'app di YouTube: anteprima grande con la durata, sotto foto del canale, titolo e dettagli. */
@Composable
fun VideoCard(video: YtVideo, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.97f)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.clip(MaterialTheme.shapes.large)) {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth(), large = true)
            VideoBadge(video, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            WatchedBar(video, Modifier.align(Alignment.BottomCenter))
        }
        Row(Modifier.padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            ChannelAvatar(video.channelAvatar, video.channel, 36)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    videoMeta(video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Versione compatta in riga: playlist e "prossimi" nel player. */
@Composable
fun VideoRow(video: YtVideo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.97f)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(150.dp).clip(MaterialTheme.shapes.medium)) {
            Thumbnail(video.thumbnail, Modifier.fillMaxWidth())
            VideoBadge(video, Modifier.align(Alignment.BottomEnd).padding(4.dp))
            WatchedBar(video, Modifier.align(Alignment.BottomCenter))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                videoMeta(video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Canale, visualizzazioni e quando è uscito. */
private fun videoMeta(video: YtVideo): String = listOfNotNull(
    video.channel.takeIf { it.isNotBlank() },
    video.views.takeIf { it >= 0 }?.let { "${compactCount(it)} visualizzazioni" },
    video.published?.let { relativeTime(it) } ?: video.uploadedText.takeIf { it.isNotBlank() },
).joinToString(" · ")

/** Barra rossa sotto l'anteprima: quanto hai già visto (nell'app o su YouTube). */
@Composable
private fun WatchedBar(video: YtVideo, modifier: Modifier) {
    val history by YtHistory.items.collectAsState()
    val progress = history[video.id]?.progress ?: (video.watchedPercent / 100f)
    if (progress < 0.02f) return
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.35f))
    ) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(Color(0xFFFF0033)))
    }
}

/** I video guardati nell'app (con il punto in cui eri) e la cronologia del tuo account YouTube. */
@Composable
private fun HistoryTab(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        YtHistory.init(context)
        YouTubeStore.refreshHistory(context)
    }
    val local by YtHistory.items.collectAsState()
    val web by YouTubeStore.history.collectAsState()
    val watched = local.values.sortedByDescending { it.watchedAt }
    val ids = watched.map { it.id }.toSet()
    val fromYouTube = web.data.orEmpty().filter { it.id !in ids }
    val state = Loadable(data = watched.map { it.id } + fromYouTube.map { it.id }, loading = web.loading && watched.isEmpty())
    // Svuotare cancella anche i punti in cui eri: prima si chiede, poi per qualche secondo si può annullare.
    var confirmClear by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<Pair<Map<String, YtWatched>, List<ResumeItem>>?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(6_000)
            undo = null
        }
    }
    if (confirmClear) {
        CanvasDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Svuotare la cronologia?") },
            text = { Text("${watched.size} video guardati qui e i punti in cui eri. Spariscono anche da Riprendi in Media.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    MediaHistory.init(context)
                    undo = YtHistory.clear() to MediaHistory.removeKind(ResumeKind.YOUTUBE)
                }) { Text("Svuota") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } },
        )
    }
    Column(Modifier.fillMaxSize()) {
        undo?.let { (videos, resume) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cronologia svuotata", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    YtHistory.restore(videos)
                    MediaHistory.restore(resume)
                    undo = null
                }) { Text("Annulla") }
            }
        }
        FeedList(state, empty = "Ancora nessun video guardato") {
            if (watched.isNotEmpty()) {
                item(key = "local-h") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeader("Guardati qui") }
                        TextButton(onClick = { confirmClear = true }) { Text("Svuota") }
                    }
                }
                items(watched, key = { "l" + it.id }) { w ->
                    val video = YtVideo(
                        id = w.id, title = w.title, channel = w.channel, channelId = "", published = null,
                        durationSec = (w.durationMs / 1000).toInt(), channelAvatar = w.channelAvatar,
                        uploadedText = "visto " + relativeTime(java.time.Instant.ofEpochMilli(w.watchedAt)),
                    )
                    VideoRow(video, onClick = { onPlay(video.watchUrl) })
                }
            }
            if (fromYouTube.isNotEmpty()) {
                item(key = "web-h") { SectionHeader("Dalla tua cronologia di YouTube") }
                items(fromYouTube, key = { "w" + it.id }) { VideoRow(it, onClick = { onPlay(it.watchUrl) }) }
            }
        }
    }
}

/** 1234 → "1234", 15300 → "15,3K", 2100000 → "2,1 Mln", 1700000000 → "1,7 Mld". */
internal fun compactCount(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1f Mld".format(java.util.Locale.ITALY, n / 1_000_000_000.0).replace(",0 ", " ")
    n >= 1_000_000 -> "%.1f Mln".format(java.util.Locale.ITALY, n / 1_000_000.0).replace(",0 ", " ")
    n >= 10_000 -> "%.1fK".format(java.util.Locale.ITALY, n / 1000.0).replace(",0K", "K")
    else -> n.toString()
}

/** Durata, IN DIRETTA o IN ARRIVO sull'anteprima. */
@Composable
private fun VideoBadge(video: YtVideo, modifier: Modifier) {
    val text = when {
        video.live -> "● IN DIRETTA"
        video.upcoming -> "IN ARRIVO"
        video.isShort -> "SHORT"
        video.durationSec > 0 -> formatDuration(video.durationSec * 1000L)
        else -> return
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White,
        modifier = modifier
            .background(if (video.live) Color(0xFFCC0000) else Color.Black.copy(alpha = 0.78f), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Foto del canale; senza foto, l'iniziale in un cerchio colorato. */
@Composable
fun ChannelAvatar(url: String, name: String, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(avatarColor(name)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        if (url.isNotBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(sizeDp.dp))
        }
    }
}

private val avatarPalette = listOf(0xFF3550E8, 0xFFD9572B, 0xFF1E8A57, 0xFF7A44C2, 0xFFB8336A, 0xFF0E7C86)

private fun avatarColor(name: String): Color = Color(avatarPalette[(name.hashCode() and 0x7fffffff) % avatarPalette.size])

@Composable
private fun Thumbnail(url: String, modifier: Modifier, large: Boolean = false) {
    AsyncImage(
        model = if (large) url.replace("/mqdefault.jpg", "/hqdefault.jpg") else url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(if (large) MaterialTheme.shapes.large else MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}
