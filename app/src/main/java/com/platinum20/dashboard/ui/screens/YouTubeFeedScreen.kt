package com.platinum20.dashboard.ui.screens



import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import com.platinum20.dashboard.media.youtube.YtRatings
import com.platinum20.dashboard.capture.CaptureFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import com.platinum20.dashboard.settings.AppSettings
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.Dp
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

/**
 * La Cronologia di YouTube (1.481, Antonio: Video non è più una pagina con le schede; questa è una riga in fondo a
 * Media): i video guardati qui, poi quelli della tua cronologia di YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeHistoryScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(barScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopLevelBar(title = "Cronologia", subtitle = "Video e brani", scrollBehavior = barScroll, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) { HistoryTab(onPlay) }
    }
}

/** Quello che resta del feed con i filtri: gli Short (fila a parte) e gli altri video. */
internal data class FeedPick(val shorts: List<YtVideo>, val videos: List<YtVideo>)

/**
 * Escludi Shorts toglie la fila degli Short; Escludi live toglie le dirette in corso e in arrivo;
 * Escludi video per membri toglie quelli che senza abbonamento al canale non si possono guardare.
 */
internal fun pickFeed(all: List<YtVideo>, hideShorts: Boolean, hideLive: Boolean, hideMembers: Boolean = false): FeedPick {
    // Senza titolo né canale è una pubblicità sfuggita al filtro della pagina: un video vero li ha sempre.
    val kept = all.filter { it.title.isNotBlank() || it.channel.isNotBlank() }
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
fun VideoFeed(
    state: Loadable<List<YtVideo>>, empty: String, hideShorts: Boolean, hideLive: Boolean, onPlay: (String) -> Unit,
    onNearEnd: (() -> Unit)? = null,
    /** L'ultima visita (Iscrizioni): sotto i video usciti dopo va il segno ULTIMA VISITA, come in News. 0 = niente segno. */
    since: Long = 0L,
) {
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val pick = pickFeed(state.data.orEmpty(), hideShorts, hideLive, hideMembers)
    val marker = com.platinum20.dashboard.media.MediaFresh.markerIndex(pick.videos, since)
    val count = state.data.orEmpty().size
    FeedList(state, empty = empty) {
        if (pick.shorts.isNotEmpty()) {
            item(key = "shorts") { ShortsShelf(pick.shorts, onPlay) }
        }
        itemsIndexed(pick.videos, key = { _, v -> v.id }) { i, video ->
            // Qualche video prima del fondo parte già la pagina dopo: scorrendo non ci si ferma ad aspettare.
            if (onNearEnd != null && i >= pick.videos.size - NEAR_END) LaunchedEffect(count) { onNearEnd() }
            if (i == marker) NewsVisitMarker("Ultima visita", Modifier.padding(bottom = 8.dp))
            VideoCard(video, onClick = { onPlay(video.watchUrl) }, first = i == 0)
        }
        if (onNearEnd != null && count > 0) {
            item(key = "more") {
                LaunchedEffect(count) { onNearEnd() }
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingPulse() }
            }
        }
    }
}

/** Quanti video prima del fondo si chiedono i consigliati dopo. */
private const val NEAR_END = 6

@Composable
private fun SimpleVideos(state: Loadable<List<YtVideo>>, empty: String, onPlay: (String) -> Unit, list: String? = null, load: () -> Unit) {
    LaunchedEffect(Unit) { load() }
    FeedList(state, empty = empty) {
        itemsIndexed(state.data.orEmpty(), key = { _, v -> v.id }) { i, video ->
            VideoCard(video, onClick = { onPlay(video.watchUrl + (list?.let { "&list=$it" } ?: "")) }, first = i == 0)
        }
    }
}

@Composable
private fun ShortsShelf(shorts: List<YtVideo>, onPlay: (String) -> Unit) {
    Column {
        SectionHeader("Shorts · ${shorts.size}")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(shorts.take(20), key = { _, v -> v.id }) { i, short ->
                Column(
                    Modifier
                        .width(120.dp)
                        .bouncyPress(0.96f)
                        .clickable { onPlay(short.watchUrl) }
                ) {
                    CaptionedCover(short.title, null) {
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
                    }
                }
            }
        }
    }
}

/** Una riga fissa in cima a Cronologia (Mi piace). */
@Composable
internal fun ListRow(icon: ImageVector, accent: TileAccent, title: String, subtitle: String, onClick: () -> Unit) {
    CanvasCard(Modifier.fillMaxWidth().bouncyPress(0.97f), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, accent, size = 40.dp, iconSize = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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

/**
 * La pagina di un canale come su YouTube (1.481, Antonio: "esattamente come YouTube", non solo gli ultimi video):
 * copertina, foto, nome, iscritti, descrizione, Iscriviti, poi le schede Video, Shorts, Live e Playlist che il canale
 * ha. Ci si arriva dalle storie di Media, dal canale sotto ogni video e dalla pagina del video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeChannelScreen(
    channel: YtChannel,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenPlaylist: (YtPlaylist) -> Unit = {},
) {
    val context = LocalContext.current
    val hideLive by AppSettings.hideLive.collectAsState()
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val infos by YouTubeStore.channelInfo.collectAsState()
    val tabsState by YouTubeStore.channelTabs.collectAsState()
    val videosAll by YouTubeStore.channelVideos.collectAsState()
    val more by YouTubeStore.channelHasMore.collectAsState()
    LaunchedEffect(channel.id) {
        YouTubeStore.loadChannelInfo(channel)
        YouTubeStore.loadChannel(channel)
    }
    YtRatings.init(context)
    val changedSubs by YtRatings.subscribed.collectAsState()
    val channels by YouTubeStore.channels.collectAsState()
    val subscribed = YtRatings.isSubscribed(channel.id, changedSubs, channels.data)
    val info = infos[channel.id]?.data
    val tabs = remember(info) { channelTabs(info?.tabs?.keys) }
    var tab by rememberSaveable { mutableStateOf(org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS) }
    LaunchedEffect(tab, info != null) {
        if (tab != org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS) YouTubeStore.loadChannelTab(channel, tab)
    }
    var descriptionOpen by rememberSaveable { mutableStateOf(false) }
    val videos = videosAll[channel.id] ?: Loadable(loading = true)
    val pick = pickFeed(videos.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers)
    val other = tabsState[channel.id + "/" + tab]
    val name = info?.name?.ifBlank { null } ?: channel.title
    val avatar = info?.avatar?.ifBlank { null } ?: channel.thumbnail
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.statusBarsPadding(),
        ) {
            item(key = "top") { Spacer(Modifier.height(48.dp)) }
            if (!info?.banner.isNullOrBlank()) item(key = "banner") {
                AsyncImage(
                    model = info!!.banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(6.2f / 1f).clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            item(key = "head") {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChannelAvatar(avatar, name, 72)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val line = channelLine(info?.subscribers ?: -1, info?.verified == true)
                        if (line.isNotBlank()) {
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            info?.description?.takeIf { it.isNotBlank() }?.let { text ->
                item(key = "about") {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descriptionOpen) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp).clickable { descriptionOpen = !descriptionOpen },
                    )
                }
            }
            item(key = "subscribe") {
                CanvasButton(
                    if (subscribed) "Iscritto" else "Iscriviti",
                    onClick = {
                        val ok = YtRatings.toggleSubscribe(context, channel.id, subscribed) {
                            CaptureFeedback.tell(context, "YouTube non ha preso l'iscrizione, riprova")
                        }
                        if (!ok) CaptureFeedback.tell(context, "Accedi a YouTube in Impostazioni › Account")
                    },
                    primary = !subscribed,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                )
            }
            if (tabs.size > 1) item(key = "tabs") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEach { (filter, label) ->
                        CanvasTabChip(selected = tab == filter, onClick = { tab = filter }, label = label, modifier = Modifier.weight(1f))
                    }
                }
            }
            when (tab) {
                org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS -> {
                    if (videos.data == null && videos.loading) item(key = "v-loading") { PlaceholderList(Modifier.padding(horizontal = 16.dp), rows = 3, label = "Carico i video") }
                    videos.error?.takeIf { videos.data == null }?.let { e -> item(key = "v-error") { ChannelNote(e) } }
                    itemsIndexed(pick.videos, key = { _, v -> "v-" + v.id }) { i, v ->
                        Box(Modifier.padding(horizontal = 16.dp)) { VideoCard(v, onClick = { onPlay(v.watchUrl) }, first = i == 0) }
                    }
                    if (channel.id in more) item(key = "v-more") {
                        LaunchedEffect(pick.videos.size) { YouTubeStore.loadMoreChannel(channel) }
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingPulse() }
                    }
                }
                else -> {
                    if (other == null || (other.data == null && other.loading)) item(key = "t-loading") {
                        PlaceholderList(Modifier.padding(horizontal = 16.dp), rows = 3, label = "Carico")
                    }
                    other?.error?.let { e -> item(key = "t-error") { ChannelNote(e) } }
                    val data = other?.data
                    if (data != null && data.videos.isEmpty() && data.playlists.isEmpty()) item(key = "t-empty") { ChannelNote("Niente qui") }
                    if (tab == org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.SHORTS) {
                        // Gli Short in griglia da tre, verticali, come sul canale in YouTube.
                        items(data?.videos.orEmpty().chunked(3), key = { row -> "s-" + row.first().id }) { row ->
                            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { short ->
                                    AsyncImage(
                                        model = "https://i.ytimg.com/vi/${short.id}/hqdefault.jpg",
                                        contentDescription = short.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).aspectRatio(9f / 16f).clip(MaterialTheme.shapes.medium)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .bouncyPress(0.96f).clickable { onPlay(short.watchUrl) },
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        items(data?.videos.orEmpty(), key = { "t-" + it.id }) { v ->
                            Box(Modifier.padding(horizontal = 16.dp)) { VideoCard(v, onClick = { onPlay(v.watchUrl) }) }
                        }
                    }
                    items(data?.playlists.orEmpty(), key = { "p-" + it.id }) { p -> PlaylistLine(p) { onOpenPlaylist(p) } }
                }
            }
        }
        Box(Modifier.statusBarsPadding().padding(8.dp)) {
            RoundIconButton(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Indietro", onClick = onBack)
        }
    }
}

/** Le schede del canale, nell'ordine di YouTube; Video c'è sempre. */
internal fun channelTabs(available: Set<String>?): List<Pair<String, String>> {
    val tabs = org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.VIDEOS
    return listOf(
        tabs to "Video",
        org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.SHORTS to "Shorts",
        org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.LIVESTREAMS to "Live",
        org.schabi.newpipe.extractor.channel.tabs.ChannelTabs.PLAYLISTS to "Playlist",
    ).filter { (filter, _) -> filter == tabs || available.orEmpty().contains(filter) }
}

/** "1,2 Mln di iscritti · Verificato"; vuoto se il canale nasconde gli iscritti e non è verificato. */
internal fun channelLine(subscribers: Long, verified: Boolean): String =
    listOfNotNull(
        subscribers.takeIf { it >= 0 }?.let { "${compactCount(it)} iscritti" },
        "Verificato".takeIf { verified },
    ).joinToString(" · ")

@Composable
private fun ChannelNote(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
}

/** Una playlist del canale: miniatura, titolo, quanti video. */
@Composable
internal fun PlaylistLine(p: YtPlaylist, onClick: () -> Unit, padded: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = if (padded) 16.dp else 0.dp).bouncyPress(0.97f).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp)) {
            Thumbnail(p.thumbnail, Modifier.fillMaxWidth(), large = false)
            MediaMarkOverlay(MediaMark(MediaType.VIDEO, if (p.count > 0) "${p.count}" else null), compact = true, inset = 4.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (p.count > 0) Text("${p.count} video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        itemsIndexed(pick.videos, key = { _, v -> v.id }) { i, v -> VideoCard(v, onClick = { onPlay(v.watchUrl) }, first = i == 0) }
        if (channel.id in more) {
            // Composto solo quando si arriva vicino al fondo della lista: lì parte la pagina dopo.
            item(key = "more") {
                LaunchedEffect(count) { YouTubeStore.loadMoreChannel(channel) }
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    LoadingPulse()
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
        state.data == null && state.loading -> PlaceholderList(Modifier.padding(top = 12.dp), label = "Carico i video")
        state.data == null && state.error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(state.error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) item { PlaceholderList(rows = 3, label = "Carico i video") }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            content()
            if (isEmpty && !state.loading) item {
                Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp))
            }
        }
    }
}

/**
 * Video in grande (1.461, Antonio: titolo di nuovo sotto): l'anteprima, poi il titolo su due righe al massimo,
 * poi foto del canale, canale e dettagli su una riga. Altezze fisse (1.442), niente divisori.
 */
@Composable
fun VideoCard(video: YtVideo, onClick: () -> Unit, first: Boolean = true) {
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
            MediaMarkOverlay(MediaMark(MediaType.VIDEO), inset = 8.dp)
            WatchedBar(video, Modifier.align(Alignment.BottomCenter))
        }
        // Altezza fissa (1.442): sempre lo spazio di due righe di titolo + canale, la lista non salta da un video
        // all'altro; ma il canale sta subito sotto il titolo, anche quando il titolo è su una riga (Antonio, 2026-11-11).
        Box {
            Box(Modifier.alpha(0f).clearAndSetSemantics { }) { VideoCardText(" ", " ", null, null, fixed = true) }
            // Un tocco sulla riga del canale apre la sua pagina (1.481), ovunque ci sia un video.
            val inApp = LocalInAppNav.current
            val onChannel = inApp?.openRoute?.takeIf { video.channelId.isNotBlank() }?.let { open ->
                { open(com.platinum20.dashboard.Routes.youtubeChannel(YtChannel(video.channelId, video.channel, video.channelAvatar))) }
            }
            VideoCardText(video.title, videoMeta(video), video.channelAvatar, video.channel, fixed = false, onChannel = onChannel)
        }
    }
}

/**
 * Un video in una fila orizzontale (1.482, Antonio: le liste lunghe scorrono di lato): l'anteprima con simbolo e durata,
 * sotto il titolo su due righe e il canale con quando è uscito.
 */
@Composable
internal fun VideoTile(video: YtVideo, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).bouncyPress(0.96f).tapOrSave({ SavedLinks.youtube(video) }, onClick)) {
        CaptionedCover(video.title, listOfNotNull(video.channel.takeIf { it.isNotBlank() }, video.published?.let { relativeTime(it) }).joinToString(" · ")) {
            Box(Modifier.clip(MaterialTheme.shapes.medium)) {
                Thumbnail(video.thumbnail, Modifier.fillMaxWidth(), large = true)
                VideoBadge(video, Modifier.align(Alignment.BottomEnd).padding(6.dp))
                MediaMarkOverlay(MediaMark(MediaType.VIDEO))
                WatchedBar(video, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Titolo e riga del canale di [VideoCard]; [fixed] = occupa sempre due righe di titolo. */
@Composable
private fun VideoCardText(title: String, meta: String, avatar: String?, channel: String?, fixed: Boolean, onChannel: (() -> Unit)? = null) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            minLines = if (fixed) 2 else 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp),
        )
        Row(
            Modifier.then(if (onChannel != null) Modifier.clickable(onClick = onChannel) else Modifier).padding(start = 2.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (channel != null) ChannelAvatar(avatar.orEmpty(), channel, 24) else Spacer(Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(top = 3.dp),
            )
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
            MediaMarkOverlay(MediaMark(MediaType.VIDEO), compact = true, inset = 4.dp)
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
        // Blu Canvas (1.475): è "in corso", non il rosso di YouTube.
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
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
    // Anche i brani di Spotify (1.482, Antonio: una Cronologia sola): insieme ai video, dal più recente.
    val played by com.platinum20.dashboard.media.spotify.SpotifyStore.played.collectAsState()
    LaunchedEffect(Unit) { com.platinum20.dashboard.media.spotify.SpotifyStore.refreshListened() }
    val recent: List<Pair<Any, Long>> = (watched.map { it to it.watchedAt } + played.map { (track, at) -> track to at.toEpochMilli() })
        .sortedByDescending { it.second }
    val state = Loadable(data = recent.map { it.second } + fromYouTube.map { it.id }, loading = web.loading && recent.isEmpty())
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
                ConfirmButton(danger = true, onClick = {
                    confirmClear = false
                    MediaHistory.init(context)
                    undo = YtHistory.clear() to MediaHistory.removeKind(ResumeKind.YOUTUBE)
                }) { Text("Svuota") }
            },
            dismissButton = { DismissButton(onClick = { confirmClear = false }) { Text("Annulla") } },
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
            if (recent.isNotEmpty()) {
                item(key = "local-h") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SectionHeader("Di recente") }
                        if (watched.isNotEmpty()) DangerButton(onClick = { confirmClear = true }) { Text("Svuota") }
                    }
                }
                items(recent, key = { (e, at) -> if (e is YtWatched) "l" + e.id else "s" + (e as com.platinum20.dashboard.media.spotify.SpItem).uri + at }) { (e, _) ->
                    if (e is com.platinum20.dashboard.media.spotify.SpItem) {
                        val tracks = played.map { it.first }
                        ItemRow(e, onClick = { playTracks(context, tracks, e) })
                    } else {
                        val w = e as YtWatched
                        val video = YtVideo(
                            id = w.id, title = w.title, channel = w.channel, channelId = w.channelId, published = null,
                            durationSec = (w.durationMs / 1000).toInt(), channelAvatar = w.channelAvatar,
                            uploadedText = "visto " + relativeTime(java.time.Instant.ofEpochMilli(w.watchedAt)),
                        )
                        VideoRow(video, onClick = { onPlay(video.watchUrl) })
                    }
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


/** Una playlist di YouTube come pagina sua (1.357): prima si apriva dentro la pagina Video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubePlaylistScreen(playlist: YtPlaylist, onBack: () -> Unit, onPlay: (String) -> Unit) {
    Scaffold(
        topBar = { TopLevelBar(title = playlist.title, subtitle = if (playlist.count > 0) "Playlist · ${playlist.count} video" else "Playlist", onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) { PlaylistVideos(playlist, onPlay) }
    }
}

