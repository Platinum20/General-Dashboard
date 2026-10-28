package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.capture.SavedLinks
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
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

private val youTubeTabs = listOf("Home", "Iscrizioni", "Cronologia", "Canali")
private const val TAB_HOME = 0
private const val TAB_SUBS = 1
private const val TAB_HISTORY = 2
private const val TAB_CHANNELS = 3

/** Le tue liste di YouTube, nella fila di riquadri in cima a Video: si aprono come pagine. */
private enum class YtList(val title: String) { PLAYLISTS("Playlist"), LIKED("Mi piace"), LATER("Più tardi") }

/**
 * Il tuo YouTube nell'app: in cima i riquadri delle tue liste e dei video del telefono, sotto tre schede
 * (Home, Iscrizioni, Cronologia, Canali) larghe in parti uguali fino al bordo. Escludi Shorts/live stanno in Impostazioni. Cerca = trascinare giù.
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
    // Basta l'accesso a YouTube fatto nell'app; il collegamento ufficiale sta in Impostazioni › Account.
    val connected = official || MediaService.YOUTUBE in loggedIn
    // Si apre sulle Iscrizioni; la Home è subito a sinistra.
    var tab by rememberSaveable { mutableIntStateOf(TAB_SUBS) }
    var list by rememberSaveable { mutableStateOf<YtList?>(null) }
    var openPlaylist by remember { mutableStateOf<YtPlaylist?>(null) }
    var openChannel by remember { mutableStateOf<YtChannel?>(null) }
    // Escludi Shorts / live si scelgono una volta in Impostazioni › Fa da solo.
    val hideShorts by AppSettings.hideShorts.collectAsState()
    val hideLive by AppSettings.hideLive.collectAsState()
    BackHandler(enabled = openPlaylist != null || openChannel != null || list != null) {
        when {
            openPlaylist != null -> openPlaylist = null
            openChannel != null -> openChannel = null
            else -> list = null
        }
    }

    val subs by YouTubeStore.subscriptions.collectAsState()
    val fresh = subs.data.orEmpty().count { v -> v.published?.let { Duration.between(it, Instant.now()).toHours() < 24 } == true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopLevelBar(
                title = openPlaylist?.title ?: openChannel?.title ?: list?.title ?: "Video",
                subtitle = when {
                    openPlaylist != null -> "Playlist · ${openPlaylist?.count} video"
                    openChannel != null -> "Tutti i video, dal più recente"
                    list == YtList.LATER -> "I video salvati nelle note"
                    list != null -> "YouTube"
                    fresh > 0 -> "$fresh video nuovi oggi"
                    else -> "YouTube e i video del telefono"
                },
                onBack = {
                    when {
                        openPlaylist != null -> openPlaylist = null
                        openChannel != null -> openChannel = null
                        list != null -> list = null
                        else -> onBack()
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
                ConnectYouTube(onLogin = onLogin, onOpenPhone = onOpenPhone)
                return@Column
            }
            val playlist = openPlaylist
            if (playlist != null) {
                PlaylistVideos(playlist, onPlay)
                return@Column
            }
            val channel = openChannel
            if (channel != null) {
                ChannelVideos(channel, hideShorts, hideLive, onPlay)
                return@Column
            }
            when (list) {
                YtList.PLAYLISTS -> {
                    PlaylistsTab(onOpen = { openPlaylist = it })
                    return@Column
                }
                YtList.LIKED -> {
                    val liked by YouTubeStore.liked.collectAsState()
                    com.platinum20.dashboard.media.youtube.YtFavorites.init(context)
                    val mine by com.platinum20.dashboard.media.youtube.YtFavorites.items.collectAsState()
                    // In cima i Mi piace messi dal player dell'app, poi quelli del tuo account YouTube.
                    val all = liked.copy(data = (mine + liked.data.orEmpty()).distinctBy { it.id }.takeIf { liked.data != null || mine.isNotEmpty() })
                    SimpleVideos(all, "Nessun video con il Mi piace", onPlay) { YouTubeStore.refreshLiked(context) }
                    return@Column
                }
                YtList.LATER -> {
                    // Più tardi = i video di YouTube che hai salvato nelle note, non la playlist di YouTube.
                    NotesStore.init(context)
                    val notes by NotesStore.data.collectAsState()
                    val saved = remember(notes.notes) { SavedLinks.youtubeVideos(notes.notes) }
                    SimpleVideos(Loadable(saved), "Salva un video nelle note e lo ritrovi qui", onPlay) { }
                    return@Column
                }
                null -> Unit
            }
            // Prima le tue liste (si aprono come pagine), poi le schede proprio sopra i video che cambiano.
            ListTiles(onOpen = { list = it }, onOpenPhone = onOpenPhone)
            // Le quattro schede in parti uguali fino al bordo destro: niente da scorrere di lato.
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                youTubeTabs.forEachIndexed { index, label ->
                    CanvasTabChip(selected = tab == index, onClick = { tab = index }, label = label, modifier = Modifier.weight(1f))
                }
            }
            when (tab) {
                TAB_HOME -> HomeTab(hideShorts, hideLive, onPlay)
                TAB_SUBS -> SubscriptionsTab(hideShorts, hideLive, onPlay)
                TAB_CHANNELS -> ChannelsTab(onOpen = { openChannel = it })
                else -> HistoryTab(onPlay)
            }
        }
    }
}

/** La fila di riquadri in cima: Playlist, Mi piace, Più tardi e i video del telefono. */
@Composable
private fun ListTiles(onOpen: (YtList) -> Unit, onOpenPhone: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListTile(Icons.AutoMirrored.Outlined.PlaylistPlay, TileAccent.ORANGE, "Playlist") { onOpen(YtList.PLAYLISTS) }
        ListTile(Icons.Outlined.ThumbUp, TileAccent.BLUE, "Mi piace") { onOpen(YtList.LIKED) }
        ListTile(Icons.Outlined.WatchLater, TileAccent.PURPLE, "Più tardi") { onOpen(YtList.LATER) }
        ListTile(Icons.Outlined.PhoneAndroid, TileAccent.GREEN, "Telefono", onOpenPhone)
    }
}

@Composable
private fun RowScope.ListTile(icon: ImageVector, accent: TileAccent, label: String, onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight().bouncyPress(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconTile(icon, accent, size = 32.dp, iconSize = 18.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun HomeTab(hideShorts: Boolean, hideLive: Boolean, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.home.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshHome(context) }
    VideoFeed(state, empty = "La Home è vuota", hideShorts, hideLive, onPlay = onPlay)
}

@Composable
private fun SubscriptionsTab(hideShorts: Boolean, hideLive: Boolean, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.subscriptions.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshSubscriptions(context) }
    VideoFeed(state, empty = "Nessun video nuovo dai canali che segui", hideShorts, hideLive, onPlay = onPlay)
}

/** Quello che resta del feed con i filtri: gli Short (fila a parte) e gli altri video. */
internal data class FeedPick(val shorts: List<YtVideo>, val videos: List<YtVideo>)

/**
 * Escludi Shorts toglie la fila degli Short; Escludi live toglie le dirette in corso e in arrivo;
 * Escludi video per membri toglie quelli che senza abbonamento al canale non si possono guardare.
 */
internal fun pickFeed(all: List<YtVideo>, hideShorts: Boolean, hideLive: Boolean, hideMembers: Boolean = false): FeedPick {
    val kept = all
        .let { list -> if (hideLive) list.filter { !it.live && !it.upcoming } else list }
        .let { list -> if (hideMembers) list.filter { !it.membersOnly } else list }
    return FeedPick(
        shorts = if (hideShorts) emptyList() else kept.filter { it.isShort },
        videos = kept.filter { !it.isShort },
    )
}

/**
 * Feed di video con i filtri (Shorts, live): gli Short in una fila orizzontale (se non sono esclusi)
 * e i video con le anteprime grandi. Cosa stai guardando lo mostra già il mini player.
 */
@Composable
fun VideoFeed(state: Loadable<List<YtVideo>>, empty: String, hideShorts: Boolean, hideLive: Boolean, onPlay: (String) -> Unit) {
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val pick = pickFeed(state.data.orEmpty(), hideShorts, hideLive, hideMembers)
    FeedList(state, empty = empty) {
        if (pick.shorts.isNotEmpty()) {
            item(key = "shorts") { ShortsShelf(pick.shorts, onPlay) }
        }
        items(pick.videos, key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
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

/** Un canale nella scheda Canali, con il suo ultimo video (se è tra le Iscrizioni scaricate). */
internal data class ChannelLine(val channel: YtChannel, val latest: Instant?, val avatar: String)

/** Prima i canali che hanno pubblicato da poco, poi gli altri in ordine alfabetico. */
internal fun channelLines(channels: List<YtChannel>, recent: List<YtVideo>): List<ChannelLine> {
    val newest = recent.filter { it.channelId.isNotBlank() }.groupBy { it.channelId }
    return channels.distinctBy { it.id }.map { c ->
        val videos = newest[c.id].orEmpty()
        ChannelLine(
            channel = c,
            latest = videos.mapNotNull { it.published }.maxOrNull(),
            avatar = c.thumbnail.ifBlank { videos.firstOrNull { it.channelAvatar.isNotBlank() }?.channelAvatar.orEmpty() },
        )
    }.sortedWith(compareByDescending<ChannelLine> { it.latest ?: Instant.MIN }.thenBy { it.channel.title.lowercase() })
}

/** I canali a cui sei iscritto: un tocco e vedi solo i video di quel canale. */
@Composable
private fun ChannelsTab(onOpen: (YtChannel) -> Unit) {
    val context = LocalContext.current
    val state by YouTubeStore.channels.collectAsState()
    val subs by YouTubeStore.subscriptions.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshChannels(context) }
    val lines = remember(state.data, subs.data) { channelLines(state.data.orEmpty(), subs.data.orEmpty()) }
    FeedList(state, empty = "Non segui ancora nessun canale") {
        items(lines, key = { it.channel.id }) { line ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncyPress(0.97f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onOpen(line.channel.copy(thumbnail = line.avatar)) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChannelAvatar(line.avatar, line.channel.title, 44)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(line.channel.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    line.latest?.let {
                        Text(
                            "Ultimo video ${relativeTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * La pagina di un canale aperta da fuori (tocco sul canale nella pagina del video): la stessa lista
 * di tutti i video della scheda Canali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeChannelScreen(channel: YtChannel, onBack: () -> Unit, onPlay: (String) -> Unit) {
    val hideShorts by AppSettings.hideShorts.collectAsState()
    val hideLive by AppSettings.hideLive.collectAsState()
    Scaffold(
        topBar = { TopLevelBar(title = channel.title, subtitle = "Tutti i video, dal più recente", onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            ChannelVideos(channel, hideShorts, hideLive, onPlay)
        }
    }
}

/**
 * Tutti i video di un canale in ordine cronologico, dal più recente: arrivando in fondo si carica la
 * pagina dopo, finché il canale non ne ha più. Stessi filtri (Shorts, live) delle altre schede.
 */
@Composable
private fun ChannelVideos(channel: YtChannel, hideShorts: Boolean, hideLive: Boolean, onPlay: (String) -> Unit) {
    val all by YouTubeStore.channelVideos.collectAsState()
    val more by YouTubeStore.channelHasMore.collectAsState()
    val state = all[channel.id] ?: Loadable(loading = true)
    LaunchedEffect(channel.id) { YouTubeStore.loadChannel(channel) }
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val pick = pickFeed(state.data.orEmpty(), hideShorts, hideLive, hideMembers)
    val count = state.data.orEmpty().size
    FeedList(state, empty = "Nessun video in questo canale") {
        if (pick.shorts.isNotEmpty()) {
            item(key = "shorts") { ShortsShelf(pick.shorts, onPlay) }
        }
        items(pick.videos, key = { it.id }) { VideoCard(it, onClick = { onPlay(it.watchUrl) }) }
        if (channel.id in more) {
            // Composto solo quando si arriva vicino al fondo della lista: lì parte la pagina dopo.
            item(key = "more") {
                LaunchedEffect(count) { YouTubeStore.loadMoreChannel(channel) }
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
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
            .tapOrSave({ SavedLinks.youtube(video) }, onClick)
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
            .tapOrSave({ SavedLinks.youtube(video) }, onClick),
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

/** Durata, IN DIRETTA o ANTEPRIMA (con l'orario d'inizio) sulla copertina. */
@Composable
private fun VideoBadge(video: YtVideo, modifier: Modifier) {
    val text = when {
        video.live -> "● IN DIRETTA"
        video.upcoming -> com.platinum20.dashboard.media.youtube.YtPremiere.badge(video.startsAt)
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
