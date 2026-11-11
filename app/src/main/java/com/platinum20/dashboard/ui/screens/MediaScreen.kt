package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.Routes
import androidx.compose.runtime.DisposableEffect
import com.platinum20.dashboard.media.youtube.YouTubeStore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.adblock.AdBlocker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.platinum20.dashboard.media.MediaService
import androidx.compose.runtime.LaunchedEffect
import com.platinum20.dashboard.media.podcast.PodcastStore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.FilterChip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.platinum20.dashboard.settings.AppSettings
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.remember
import com.platinum20.dashboard.media.MediaHistory
import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.VideoLibrary
import com.platinum20.dashboard.media.youtube.player.YtPlayer

import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import com.platinum20.dashboard.media.podcast.EpisodeDate
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor

/**
 * Media = un unico feed (1.479, Antonio: "niente tre menu, un unico feed"): in cima le storie dei canali e degli
 * artisti che segui, poi quello che suona con la coda dentro, Per te (i mix per primi), Continua, le Novità (video e
 * episodi insieme, dal più nuovo, ULTIMA VISITA in mezzo), le radio e in fondo quattro righe fisse.
 * Video e Musica non sono più pagine a parte.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    bottomPadding: PaddingValues,
    onOpenPlayer: (Playing) -> Unit,
    /** Un video del feed: si apre nel player. */
    onPlayVideo: (String) -> Unit = {},
    /** Una pagina dentro Media (canale, artista, radio, Mi piace, Cronologia, I tuoi podcast, accesso). */
    onOpenRoute: (String) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    YouTubeAuth.init(context)
    SpotifyAuth.init(context)
    PodcastStore.init(context)
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val spotifyLinked by SpotifyAuth.connected.collectAsState()
    // Il riproduttore di Spotify si prepara già da qui: al primo tocco la musica parte subito.
    LaunchedEffect(spotifyLinked) { if (spotifyLinked) com.platinum20.dashboard.media.spotify.SpotifyPlayer.start(context) }
    SpotifyNotices()
    val youTubeLinked by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val now by Playback.now.collectAsState()
    val history by MediaHistory.items.collectAsState()
    val videos by VideoLibrary.recent.collectAsState()
    val resumable = remember(history, videos) { Resume.pick(history + videos.map(ResumeItem::fromVideo)) }
    val openResume: (ResumeItem) -> Unit = { item -> openResumeItem(context, item) { route -> onOpenRoute(route) } }
    val queue by com.platinum20.dashboard.media.CanvasPlayer.queue.collectAsState()
    // Quando nulla suona, il blocco colorato è l'ultima cosa vista o ascoltata, di qualunque fonte:
    // un tocco la fa ripartire dal punto esatto in cui era rimasta (la coda resta sotto).
    val hero = resumable.firstOrNull()?.takeIf { now == null }
    val continueList = resumable.filter { it != hero }.take(12)
    // Aprendo Media si rilegge anche la cronologia di YouTube: "Riprendi" è l'ultima cosa vista, anche sul PC.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        Accounts.refresh()
        com.platinum20.dashboard.media.youtube.YtSync.foreground(context)
    }
    val spotifyOn = spotifyLinked || MediaService.SPOTIFY in loggedIn
    val youTubeOn = youTubeLinked || MediaService.YOUTUBE in loggedIn
    // L'ultima visita (ora di Media, prima di Video): quello uscito dopo sta sopra ULTIMA VISITA e accende le storie.
    // Aprire un video e tornare non conta come visita nuova.
    remember { com.platinum20.dashboard.media.VideoVisit.init(context) }
    val since = remember { com.platinum20.dashboard.media.VideoVisit.enter() }
    DisposableEffect(Unit) { onDispose { com.platinum20.dashboard.media.VideoVisit.leave() } }
    val hideLive by AppSettings.hideLive.collectAsState()
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val subs by YouTubeStore.subscriptions.collectAsState()
    val channels by YouTubeStore.channels.collectAsState()
    val recommended by YouTubeStore.home.collectAsState()
    val artists by com.platinum20.dashboard.media.spotify.SpotifyStore.artists.collectAsState()
    val library by com.platinum20.dashboard.media.spotify.SpotifyStore.library.collectAsState()
    val latestEpisodes by PodcastStore.latest.collectAsState()
    val followedHere by PodcastStore.followed.collectAsState()
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val followedCount = remember(followedHere, spotifyShows) { PodcastStore.merge(followedHere, spotifyShows).size }
    LaunchedEffect(youTubeOn) {
        if (youTubeOn) {
            YouTubeStore.refreshSubscriptions(context)
            YouTubeStore.refreshChannels(context)
            YouTubeStore.refreshHome(context)
        }
    }
    LaunchedEffect(spotifyOn) {
        if (spotifyOn) {
            com.platinum20.dashboard.media.spotify.SpotifyStore.refreshHome()
            com.platinum20.dashboard.media.spotify.SpotifyStore.refreshLibrary()
            com.platinum20.dashboard.media.spotify.SpotifyStore.refreshReleases()
        }
    }
    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
    // Nel feed niente Short (verticali, non stanno tra i video grandi); le live solo se non le escludi.
    val subVideos = if (youTubeOn) pickFeed(subs.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos else emptyList()
    val tips = if (youTubeOn) pickFeed(recommended.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos else emptyList()
    val listened = remember(history) { history.filter { it.kind == ResumeKind.PODCAST }.map { it.key }.toSet() }
    val feed = remember(subVideos, latestEpisodes.data, tips, since, listened) {
        com.platinum20.dashboard.media.MediaFeed.build(subVideos, latestEpisodes.data.orEmpty(), tips, since, listened)
    }
    val stories = remember(channels.data, subs.data, artists.data, since, youTubeOn, spotifyOn) {
        com.platinum20.dashboard.media.MediaStories.build(
            if (youTubeOn) channels.data.orEmpty() else emptyList(),
            subs.data.orEmpty(),
            if (spotifyOn) artists.data.orEmpty() else emptyList(),
            since,
        )
    }
    val freshVideos = com.platinum20.dashboard.media.MediaFresh.newVideos(subVideos, since)
    val freshEpisodes = remember(latestEpisodes.data, history) { newEpisodes(latestEpisodes.data.orEmpty(), history).size }

    // La coda si apre nello stesso riquadro del player (1.357): una coda sola, un posto solo.
    var queueOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            TopLevelBar(
                title = "Media",
                subtitle = com.platinum20.dashboard.media.MediaFresh.mediaLine(freshVideos, freshEpisodes) ?: "Video, musica e podcast",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            state = rememberReselectListState(Routes.MEDIA),
            contentPadding = PaddingValues(
                top = inner.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + (if (now != null) 88.dp else 16.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (stories.isNotEmpty()) item(key = "stories") {
                StoriesRow(stories) { story ->
                    onOpenRoute(
                        when (story.kind) {
                            com.platinum20.dashboard.media.MediaStory.Kind.CHANNEL ->
                                Routes.youtubeChannel(com.platinum20.dashboard.media.youtube.YtChannel(story.id, story.title, story.image))
                            com.platinum20.dashboard.media.MediaStory.Kind.ARTIST -> Routes.spotifyItem(
                                com.platinum20.dashboard.media.spotify.SpItem(
                                    story.id, "spotify:artist:${story.id}", com.platinum20.dashboard.media.spotify.SpKind.ARTIST,
                                    story.title, "Artista", story.image,
                                ),
                            )
                        },
                    )
                }
            }
            now?.let { playing ->
                item(key = "now") {
                    MediaNowCard(
                        playing, onOpen = { onOpenPlayer(playing) }, modifier = Modifier.padding(horizontal = 16.dp),
                        next = queue.upcoming.firstOrNull()?.item?.title,
                        more = (queue.upcoming.size - 1).coerceAtLeast(0),
                        onQueue = { queueOpen = true },
                    )
                }
            }
            hero?.let { last ->
                item(key = "resume") {
                    MediaResumeCard(last, onClick = { openResume(last) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            // La coda sta nel blocco quando qualcosa suona; da ferma, sotto "Riprendi".
            if (now == null && queue.upcoming.isNotEmpty()) item(key = "queue") {
                QueuePeek(
                    onOpenQueue = { queueOpen = true },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            // Per te (1.479, Antonio): prima i mix, poi le uscite.
            if (spotifyOn) item(key = "perte") { PerTeRow(onOpen = { onOpenRoute(Routes.spotifyItem(it)) }) }
            if (continueList.isNotEmpty()) {
                item(key = "continue-h") { SectionHeader("Continua", Modifier.padding(horizontal = 16.dp)) }
                item(key = "continue") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(continueList, key = { _, r -> "${r.kind}-${r.key}" }) { i, item -> ContinueTile(item, first = i == 0, onClick = { openResume(item) }) }
                    }
                }
            }
            // Novità: un feed solo, video ed episodi insieme; sotto ULTIMA VISITA il resto, sbiadito, con qualche consigliato.
            if (feed.fresh.isNotEmpty() || feed.older.isNotEmpty()) {
                item(key = "feed-h") { SectionHeader("Novità", Modifier.padding(horizontal = 16.dp)) }
            }
            items(feed.fresh, key = { "f-" + it.key }) { entry -> FeedRow(entry, faded = false, onPlayVideo) }
            if (feed.fresh.isNotEmpty() && feed.older.isNotEmpty()) item(key = "feed-marker") {
                NewsVisitMarker("Ultima visita", Modifier.padding(horizontal = 16.dp))
            }
            items(feed.older, key = { "o-" + it.key }) { entry -> FeedRow(entry, faded = feed.fresh.isNotEmpty(), onPlayVideo) }
            item(key = "radio") { RadioRow(onOpenAll = { onOpenRoute(Routes.RADIO) }) }
            // In fondo le righe fisse (1.479): quello che è tuo e non è una novità.
            item(key = "rows") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (spotifyOn) {
                        val liked = library.data?.liked?.size ?: 0
                        ListRow(
                            Icons.Filled.Favorite, TileAccent.NEUTRAL, "Brani che ti piacciono",
                            if (liked > 0) "$liked brani, playlist e album salvati" else "Playlist e album salvati",
                        ) { onOpenRoute(Routes.SPOTIFY_FEED) }
                    } else {
                        ListRow(Icons.Outlined.GraphicEq, TileAccent.NEUTRAL, "Collega Spotify", "Mix, uscite e la tua musica qui") {
                            onOpenRoute(Routes.login(MediaService.SPOTIFY))
                        }
                    }
                    if (youTubeOn) {
                        ListRow(Icons.Outlined.ThumbUp, TileAccent.NEUTRAL, "Mi piace", "I video che ti sono piaciuti") { onOpenRoute(Routes.YOUTUBE_LIKED) }
                        ListRow(Icons.Outlined.History, TileAccent.NEUTRAL, "Cronologia", "I video che hai visto") { onOpenRoute(Routes.YOUTUBE_FEED) }
                    } else {
                        ListRow(Icons.Outlined.OndemandVideo, TileAccent.NEUTRAL, "Collega YouTube", "I video dei canali che segui qui") {
                            onOpenRoute(Routes.login(MediaService.YOUTUBE))
                        }
                    }
                    ListRow(
                        Icons.Outlined.Mic, TileAccent.NEUTRAL, "I tuoi podcast",
                        if (followedCount > 0) "$followedCount che segui" else "Scegline qualcuno da seguire",
                    ) { onOpenRoute(Routes.PODCAST) }
                }
            }
        }
    }
    // Sopra Media la barra resta in basso: l'ultima voce della coda le sta sopra.
    QueuePopup(open = queueOpen, onClose = { queueOpen = false }, bottomExtra = bottomPadding.calculateBottomPadding())
    }
}

/** Le storie (1.479): foto tonde in fila, anello blu se ha pubblicato dopo l'ultima visita, sotto nome e quando. */
@Composable
private fun StoriesRow(stories: List<com.platinum20.dashboard.media.MediaStory>, onOpen: (com.platinum20.dashboard.media.MediaStory) -> Unit) {
    val nowMs = remember { System.currentTimeMillis() }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(stories, key = { it.key }) { story ->
            Column(
                Modifier.width(72.dp).bouncyPress(0.94f).clickable { onOpen(story) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ring = if (story.fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(
                    Modifier
                        .size(68.dp)
                        .border(if (story.fresh) 2.5.dp else 1.dp, ring, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(story.title.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (story.image.isNotBlank()) {
                        AsyncImage(model = story.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(story.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(
                    com.platinum20.dashboard.media.MediaStories.age(story.latest, nowMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Una voce delle Novità: un video grande come nelle Iscrizioni, o un episodio in una riga con il tasto play. */
@Composable
private fun FeedRow(entry: com.platinum20.dashboard.media.FeedEntry, faded: Boolean, onPlayVideo: (String) -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp).alpha(if (faded) FADED_ALPHA else 1f)) {
        when (entry) {
            is com.platinum20.dashboard.media.FeedEntry.Video -> VideoCard(entry.video, onClick = { onPlayVideo(entry.video.watchUrl) })
            is com.platinum20.dashboard.media.FeedEntry.Podcast -> EpisodeRow(entry.episode)
        }
    }
}

/** Un episodio nel feed: copertina quadrata, titolo, podcast · durata · quando; tocco = parte. */
@Composable
private fun EpisodeRow(episode: com.platinum20.dashboard.media.podcast.Episode) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).bouncyPress(0.97f).clickable { PodcastStore.play(episode) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(72.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (episode.image.isNotBlank()) {
                AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                com.platinum20.dashboard.media.MediaStories.episodeLine(episode, System.currentTimeMillis()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = { PodcastStore.play(episode) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Ascolta")
        }
    }
}

/**
 * La card media, una sola in tutta l'app (Media e Oggi): stretta, una riga. Mostra quello che suona
 * adesso oppure, se nulla suona, l'ultima cosa lasciata a metà da far ripartire.
 */
@Composable
internal fun MediaNowCard(
    now: Playing,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    hero: Boolean = true,
    /** La coda dentro il blocco (1.475, Antonio): "Dopo: titolo · altri N", un tocco la apre. */
    next: String? = null,
    more: Int = 0,
    onQueue: (() -> Unit)? = null,
) {
    MediaRowCard(
        footer = if (next != null && onQueue != null) ({ QueueLine(next, more, onQueue) }) else null,
        hero = hero,
        label = (if (now.playing) "In riproduzione" else "In pausa") + " · " + now.source.label,
        title = now.title,
        detail = if (now.live) "● In diretta" else now.subtitle,
        progress = if (!now.live && now.durationMs > 0) now.progress else null,
        onClick = onOpen,
        onLongClick = { Playback.currentAsItem()?.let { SavedLinks.offer(SavedLink.Media(it)) } },
        modifier = modifier,
        thumb = {
            Box(Modifier.size(THUMB.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                IconTile(sourceIcon(now.source), sourceAccent(now.source), size = THUMB.dp, iconSize = (THUMB / 2).dp)
                if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.matchParentSize())
                else if (now.artwork.isNotBlank()) {
                    AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
        },
        action = {
            FilledIconButton(onClick = { Playback.toggle() }) {
                Icon(if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (now.playing) "Pausa" else "Riproduci")
            }
        },
    )
}

/** La stessa card quando nulla suona: si riprende dal punto esatto in cui eri rimasto. */
@Composable
internal fun MediaResumeCard(item: ResumeItem, onClick: () -> Unit, modifier: Modifier = Modifier, hero: Boolean = true) {
    MediaRowCard(
        hero = hero,
        label = if (item.unfinished) "Riprendi" else if (item.listened) "Ascolta di nuovo" else "Guarda di nuovo",
        title = item.title,
        detail = listOf(item.subtitle, Resume.progressLabel(item)).filter { it.isNotBlank() }.joinToString(" · "),
        progress = if (item.unfinished && item.durationMs > 0) item.progress else null,
        onClick = onClick,
        onLongClick = { SavedLinks.offer(SavedLink.Media(item)) },
        modifier = modifier,
        thumb = { ResumeThumb(item, THUMB) },
        action = {
            FilledIconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Riprendi")
            }
        },
    )
}

/** La copertina della card media: quadrata e piccola, perché la card resta stretta. */
private const val THUMB = 64

/** Il corpo della card media: copertina, tre righe di testo e un tasto; identico nei due stati. */
@Composable
private fun MediaRowCard(
    /** Il blocco colorato della pagina (in Media); in Home il blocco colorato è già Adesso, qui la card è normale. */
    hero: Boolean,
    label: String,
    title: String,
    detail: String,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    thumb: @Composable () -> Unit,
    action: @Composable () -> Unit,
    footer: (@Composable () -> Unit)? = null,
) {
    CanvasCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().bouncyPress(0.98f),
        shape = MaterialTheme.shapes.extraLarge,
        // Come "Adesso": il blocco colorato è quello che sta succedendo (o che riprende) ora. In Home no:
        // un solo blocco colorato per pagina, e lì è Adesso.
        colors = if (hero) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            thumb()
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1)
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                progress?.let {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            action()
        }
        footer?.invoke()
    }
}

/** L'ultima riga del blocco colorato: cosa viene dopo. Prende il colore del blocco, niente card a parte. */
@Composable
private fun QueueLine(next: String, more: Int, onClick: () -> Unit) {
    val ink = LocalContentColor.current
    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = ink.copy(alpha = 0.18f))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("Dopo: ")
                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) { append(next) }
                if (more > 0) append(" · altri $more")
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Apri la coda", modifier = Modifier.size(18.dp))
    }
}

/** "Continua": anteprima grande con la barra di dove eri, titolo e fonte. */
@Composable
private fun ContinueTile(item: ResumeItem, first: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(168.dp)
            .clip(MaterialTheme.shapes.large)
            .bouncyPress()
            .tapOrSave({ SavedLink.Media(item) }, onClick)
    ) {
        CaptionedCover(
            item.title,
            // Quanto manca lo dice la barra sulla copertina (1.475): sotto resta solo chi lo pubblica.
            item.subtitle,
            titleLines = MediaShelves.TITLE_LINES,
            textModifier = Modifier.padding(horizontal = 4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(94.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                IconTile(item.icon(), item.accent())
                if (item.image.isNotBlank()) {
                    AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                if (item.unfinished && item.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

/** Apre la cosa da riprendere dal punto lasciato: i video nel loro player, tutto il resto nel player unico. */
internal fun openResumeItem(
    context: android.content.Context,
    item: ResumeItem,
    /** Falso: si carica fermo e aspetta il play (aperto da una nota). */
    autoplay: Boolean = true,
    openRoute: (String) -> Unit,
) {
    openRoute(com.platinum20.dashboard.media.MediaLauncher.start(context, item, autoplay))
}

private fun ResumeItem.icon(): ImageVector = when (kind) {
    ResumeKind.VIDEO -> Icons.Outlined.PlayCircle
    ResumeKind.YOUTUBE -> Icons.Outlined.OndemandVideo
    ResumeKind.MUSIC -> Icons.Outlined.MusicNote
    ResumeKind.SPOTIFY -> Icons.Outlined.GraphicEq
    ResumeKind.RADIO -> Icons.Outlined.Radio
    ResumeKind.PODCAST -> Icons.Outlined.Mic
}

private fun ResumeItem.accent(): TileAccent = when (kind) {
    ResumeKind.VIDEO -> TileAccent.BLUE
    ResumeKind.YOUTUBE -> TileAccent.ORANGE
    ResumeKind.MUSIC, ResumeKind.SPOTIFY -> TileAccent.GREEN
    ResumeKind.RADIO, ResumeKind.PODCAST -> TileAccent.PURPLE
}

/** Si ascolta (musica, radio, podcast) o si guarda (video)? Decide "Ascolta di nuovo" o "Guarda di nuovo". */
private val ResumeItem.listened: Boolean
    get() = kind == ResumeKind.MUSIC || kind == ResumeKind.SPOTIFY || kind == ResumeKind.RADIO || kind == ResumeKind.PODCAST

/** Miniatura (YouTube, logo della radio) o, se manca, l'icona colorata della fonte. */
@Composable
private fun ResumeThumb(item: ResumeItem, size: Int) {
    Box(Modifier.size(size.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
        IconTile(item.icon(), item.accent(), size = size.dp, iconSize = (size / 2).dp)
        if (item.image.isNotBlank()) {
            AsyncImage(
                model = item.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp),
            )
        }
    }
}


/** Gli episodi usciti da poco che non hai ancora ascoltato (chiave della cronologia = id dell'episodio). */
internal fun newEpisodes(latest: List<com.platinum20.dashboard.media.podcast.Episode>, history: List<ResumeItem>): List<com.platinum20.dashboard.media.podcast.Episode> {
    val listened = history.filter { it.kind == ResumeKind.PODCAST }.map { it.key }.toSet()
    val now = System.currentTimeMillis()
    return latest.filter { com.platinum20.dashboard.media.MediaFresh.isNewEpisode(it, listened, now) }
}
