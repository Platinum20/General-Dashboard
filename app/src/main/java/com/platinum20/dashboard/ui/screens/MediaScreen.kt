package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.Routes
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
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
import androidx.compose.runtime.LaunchedEffect
import com.platinum20.dashboard.media.podcast.PodcastStore
import androidx.compose.material.icons.outlined.Mic
import com.platinum20.dashboard.media.PlayerCommand
import com.platinum20.dashboard.media.Track
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor

/**
 * Media: tre sezioni (Musica, Video, Radio e podcast) e sopra quello che suona adesso, nel player
 * unico. Sotto, quello che si riprende al volo: dove eri rimasto, la coda e "Continua". Le radio e
 * i podcast stanno nella loro pagina: qui nessun doppione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    bottomPadding: PaddingValues,
    onOpenMusic: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenListen: (ListenTab) -> Unit,
    onOpenPlayer: (Playing) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    YouTubeAuth.init(context)
    SpotifyAuth.init(context)
    PodcastStore.init(context)
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val spotifyLinked by SpotifyAuth.connected.collectAsState()
    // Il riproduttore di Spotify si prepara già da qui: al primo tocco la musica parte subito.
    LaunchedEffect(spotifyLinked) { if (spotifyLinked) com.platinum20.dashboard.media.spotify.SpotifyPlayer.start(context) }
    val youTubeLinked by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val now by Playback.now.collectAsState()
    val history by MediaHistory.items.collectAsState()
    val videos by VideoLibrary.recent.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val resumable = remember(history, videos) { Resume.pick(history + videos.map(ResumeItem::fromVideo)) }
    val inApp = LocalInAppNav.current
    val openResume: (ResumeItem) -> Unit = { item -> openResumeItem(context, item) { route -> inApp?.openRoute?.invoke(route) } }
    val queue by com.platinum20.dashboard.media.CanvasPlayer.queue.collectAsState()
    // Quando nulla suona, il blocco colorato è l'ultima cosa vista o ascoltata, di qualunque fonte:
    // un tocco la fa ripartire dal punto esatto in cui era rimasta (la coda resta sotto).
    val hero = resumable.firstOrNull()?.takeIf { now == null }
    val continueList = resumable.filter { it != hero }.take(12)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    val spotifyOn = spotifyLinked || MediaService.SPOTIFY in loggedIn
    val youTubeOn = youTubeLinked || MediaService.YOUTUBE in loggedIn
    val podcasts = (followed.size + spotifyShows.size)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            TopLevelBar(
                title = "Media",
                subtitle = when {
                    now?.playing == true -> "In riproduzione · ${now?.source?.label}"
                    now != null -> "In pausa · ${now?.source?.label}"
                    hero != null -> "Riprendi da dove eri"
                    else -> "Musica, video, radio e podcast"
                },
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
            // Le tre sezioni, una accanto all'altra: il colore dell'icona le distingue a colpo d'occhio.
            item(key = "sections") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTile(
                        icon = Icons.Outlined.GraphicEq,
                        accent = TileAccent.GREEN,
                        title = "Musica",
                        subtitle = if (spotifyOn) "Spotify e YouTube Music" else "YouTube Music",
                        active = now?.source == PlaySource.SPOTIFY || now?.source == PlaySource.MUSIC,
                        onClick = onOpenMusic,
                    )
                    SectionTile(
                        icon = Icons.Outlined.OndemandVideo,
                        accent = TileAccent.ORANGE,
                        title = "Video",
                        subtitle = if (youTubeOn) "YouTube e telefono" else "Telefono e link",
                        active = now?.source == PlaySource.VIDEO,
                        onClick = onOpenVideo,
                    )
                    SectionTile(
                        icon = Icons.Outlined.Mic,
                        accent = TileAccent.PURPLE,
                        title = "Radio e podcast",
                        subtitle = if (podcasts > 0) "$podcasts podcast" else "In diretta e on demand",
                        active = now?.source == PlaySource.RADIO || now?.source == PlaySource.PODCAST,
                        onClick = { onOpenListen(if (now?.source == PlaySource.RADIO || podcasts == 0) ListenTab.RADIO else ListenTab.PODCAST) },
                    )
                }
            }
            now?.let { playing ->
                item(key = "now") {
                    MediaNowCard(playing, onOpen = { onOpenPlayer(playing) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            hero?.let { last ->
                item(key = "resume") {
                    MediaResumeCard(last, onClick = { openResume(last) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            // La coda: cosa viene dopo, di qualunque fonte.
            if (queue.upcoming.isNotEmpty()) item(key = "queue") {
                QueuePeek(
                    onOpenQueue = { inApp?.openRoute?.invoke(Routes.QUEUE) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (continueList.isNotEmpty()) {
                item(key = "continue-h") { SectionHeader("Continua", Modifier.padding(horizontal = 16.dp)) }
                item(key = "continue") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueList, key = { "${it.kind}-${it.key}" }) { item -> ContinueTile(item, onClick = { openResume(item) }) }
                    }
                }
            }
        }
    }
}

/** Una delle tre sezioni: icona colorata, nome e cosa c'è dentro; con un puntino se suona da lì. */
@Composable
private fun RowScope.SectionTile(
    icon: ImageVector,
    accent: TileAccent,
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    CanvasCard(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 124.dp).bouncyPress(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, accent, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.weight(1f))
                if (active) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La card media, una sola in tutta l'app (Media e Oggi): stretta, una riga. Mostra quello che suona
 * adesso oppure, se nulla suona, l'ultima cosa lasciata a metà da far ripartire.
 */
@Composable
internal fun MediaNowCard(now: Playing, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    MediaRowCard(
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
internal fun MediaResumeCard(item: ResumeItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaRowCard(
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
    label: String,
    title: String,
    detail: String,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier,
    thumb: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    CanvasCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().bouncyPress(0.98f),
        shape = MaterialTheme.shapes.extraLarge,
        // Come "Adesso": il blocco colorato è quello che sta succedendo (o che riprende) ora.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
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
    }
}

/** "Continua": anteprima grande con la barra di dove eri, titolo e fonte. */
@Composable
private fun ContinueTile(item: ResumeItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(168.dp)
            .clip(MaterialTheme.shapes.large)
            .bouncyPress()
            .tapOrSave({ SavedLink.Media(item) }, onClick)
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
        Spacer(Modifier.height(6.dp))
        // Il testo sta un po' dentro: gli angoli arrotondati della tessera non devono mangiarne i bordi.
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 8.dp)) {
            // Sempre due righe, anche per i titoli corti: le tessere hanno tutte la stessa altezza e la pagina non salta.
            Text(item.title, style = MaterialTheme.typography.labelLarge, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.subtitle, Resume.progressLabel(item)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

