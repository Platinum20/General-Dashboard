package com.platinum20.dashboard.ui.screens

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
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.MediaStarters
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
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.EpisodeDate
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor

/**
 * Media: tre sezioni (Musica, Video, Radio e podcast) e sopra quello che suona adesso, nel player
 * unico. Sotto, quello che si riprende al volo: dove eri rimasto, nuovi episodi e le tue radio.
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
    RadioStore.init(context)
    PodcastStore.init(context)
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val spotifyLinked by SpotifyAuth.connected.collectAsState()
    val youTubeLinked by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val now by Playback.now.collectAsState()
    val history by MediaHistory.items.collectAsState()
    val videos by VideoLibrary.recent.collectAsState()
    val latest by PodcastStore.latest.collectAsState()
    val followed by PodcastStore.followed.collectAsState()
    val spotifyShows by PodcastStore.spotify.collectAsState()
    val podcastNow by PodcastStore.now.collectAsState()
    val radioNow by RadioStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    val recentStations by RadioStore.recent.collectAsState()
    val favoriteStations by RadioStore.favorites.collectAsState()
    val resumable = remember(history, videos) { Resume.pick(history + videos.map(ResumeItem::fromVideo)) }
    val inApp = LocalInAppNav.current
    val openResume: (ResumeItem) -> Unit = { item -> openResumeItem(context, item) { route -> inApp?.openRoute?.invoke(route) } }
    // Quando nulla suona, il blocco colorato è l'ultima cosa vista o ascoltata.
    val hero = resumable.firstOrNull()?.takeIf { now == null }
    val continueList = resumable.filter { it != hero }.take(12)
    val stations = remember(favoriteStations, recentStations) { (favoriteStations + recentStations).distinctBy { it.id }.take(10) }
    // Al primo uso (nessuna radio ascoltata) le più ascoltate, per partire con un tocco.
    val radioRow = stations.ifEmpty { MediaStarters.stations(popular.data.orEmpty(), recentStations, favoriteStations) }
    LaunchedEffect(stations.isEmpty()) { if (stations.isEmpty()) RadioStore.loadPopular() }
    LaunchedEffect(Unit) { PodcastStore.loadLatest() }
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
            now?.let { playing ->
                item(key = "now") {
                    NowCard(playing, onOpen = { onOpenPlayer(playing) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            hero?.let { last ->
                item(key = "resume") {
                    Box(Modifier.padding(horizontal = 16.dp)) { ResumeCard(last, onClick = { openResume(last) }) }
                }
            }
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
            if (continueList.isNotEmpty()) {
                item(key = "continue-h") { SectionHeader("Continua", Modifier.padding(horizontal = 16.dp)) }
                item(key = "continue") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueList, key = { "${it.kind}-${it.key}" }) { item -> ContinueTile(item, onClick = { openResume(item) }) }
                    }
                }
            }
            val episodes = latest.data.orEmpty().take(3)
            if (episodes.isNotEmpty()) {
                item(key = "episodes") {
                    Section(
                        "Nuovi episodi",
                        action = "Tutti",
                        onAction = { onOpenListen(ListenTab.PODCAST) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        episodes.forEachIndexed { index, episode ->
                            if (index > 0) RowDivider()
                            EpisodeMiniRow(episode, playing = podcastNow?.episode?.id == episode.id && podcastNow?.playing == true)
                        }
                    }
                }
            }
            if (radioRow.isNotEmpty()) {
                item(key = "radio-h") {
                    SectionHeader(
                        if (stations.isEmpty()) "Radio per iniziare" else "Le tue radio",
                        Modifier.padding(horizontal = 16.dp),
                        action = "Tutte",
                        onAction = { onOpenListen(ListenTab.RADIO) },
                    )
                }
                item(key = "radio") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(radioRow, key = { it.id }) { station ->
                            StationBubble(station, playing = radioNow?.station?.id == station.id && radioNow?.playing == true) {
                                if (radioNow?.station?.id == station.id) RadioStore.togglePause() else RadioStore.play(station)
                            }
                        }
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

/** Il blocco colorato di Media: quello che suona adesso, da qualunque fonte, con i comandi. */
@Composable
private fun NowCard(now: Playing, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    CanvasCard(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().bouncyPress(0.98f),
        shape = MaterialTheme.shapes.extraLarge,
        // Come "Adesso" in Oggi: il blocco colorato è ciò che sta succedendo ora.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = if (now.isVideo) 112.dp else 72.dp, height = 72.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    IconTile(sourceIcon(now.source), sourceAccent(now.source))
                    if (now.artwork.isNotBlank()) {
                        AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    }
                }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (now.playing) "IN RIPRODUZIONE" else "IN PAUSA") + " · " + now.source.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                    Text(now.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (now.subtitle.isNotBlank()) {
                        Text(now.subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    when {
                        now.live -> Text("● In diretta", style = MaterialTheme.typography.labelMedium)
                        now.durationMs > 0 -> Column {
                            LinearProgressIndicator(
                                progress = { now.progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${clockLabel(now.positionMs)} / ${clockLabel(now.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                FilledIconButton(onClick = { Playback.toggle(context) }) {
                    Icon(if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (now.playing) "Pausa" else "Riproduci")
                }
                if (now.canSkip) {
                    IconButton(onClick = { Playback.next(context) }) { Icon(Icons.Filled.SkipNext, contentDescription = "Avanti") }
                }
            }
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
            .clickable(onClick = onClick)
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

/** Un episodio nuovo: copertina, titolo e podcast; un tocco lo fa partire (o lo mette in pausa). */
@Composable
private fun EpisodeMiniRow(episode: Episode, playing: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable { if (playing) PodcastStore.togglePause() else PodcastStore.play(episode) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            IconTile(Icons.Outlined.Mic, TileAccent.PURPLE, size = 44.dp)
            if (episode.image.isNotBlank()) {
                AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(
                    episode.podcastTitle,
                    EpisodeDate.label(episode.publishedAt, java.time.ZonedDateTime.now()),
                    if (episode.audioUrl.startsWith("spotify:")) "Spotify" else "",
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pausa" else "Ascolta",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Una radio nella fila: logo tondo e nome, un tocco la fa partire. */
@Composable
private fun StationBubble(station: RadioStation, playing: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.medium)
            .bouncyPress()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .padding(if (playing) 3.dp else 0.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            StationLogo(station, 64)
        }
        Spacer(Modifier.height(6.dp))
        Text(station.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Apre la cosa da riprendere dal punto lasciato: i video nel loro player, tutto il resto nel player unico. */
internal fun openResumeItem(
    context: android.content.Context,
    item: ResumeItem,
    openRoute: (String) -> Unit,
    /** Falso: si carica fermo e aspetta il play (aperto da una nota). */
    autoplay: Boolean = true,
) {
    val from = if (item.unfinished) item.positionMs else 0
    when (item.kind) {
        ResumeKind.VIDEO -> openRoute(Routes.video(item.key))
        ResumeKind.YOUTUBE -> {
            // Caricato prima di aprire la schermata: lì il video già nel player non riparte da solo.
            if (!autoplay) YtPlayer.play(context, Resume.youtubeLink(item), autoplay = false)
            openRoute(Routes.youtubeWatch(Resume.youtubeLink(item)))
        }
        ResumeKind.MUSIC -> {
            YtPlayer.play(context, Resume.youtubeLink(item), audioOnly = true, autoplay = autoplay)
            openRoute(Routes.PLAYER)
        }
        ResumeKind.SPOTIFY -> {
            com.platinum20.dashboard.media.spotify.SpotifyPlayer.resume(context, item.key, item.link, from, autoplay)
            openRoute(Routes.PLAYER)
        }
        ResumeKind.RADIO -> {
            item.station?.let { RadioStore.play(it, autoplay) }
            openRoute(Routes.PLAYER)
        }
        ResumeKind.PODCAST -> {
            PodcastStore.init(context)
            PodcastStore.play(
                Episode(
                    id = item.key, title = item.title, audioUrl = item.link, publishedAt = 0,
                    durationSec = item.durationMs / 1000, description = "", image = item.image,
                    podcastTitle = item.subtitle, feedUrl = item.feed,
                ),
                autoplay,
            )
            openRoute(Routes.PLAYER)
        }
    }
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

/** "Riprendi": il blocco colorato di Media quando nulla suona, con il punto in cui eri rimasto. */
@Composable
private fun ResumeCard(item: ResumeItem, onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().bouncyPress(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ResumeThumb(item, 64)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.unfinished) "Riprendi" else if (item.listened) "Ascolta di nuovo" else "Guarda di nuovo",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOf(item.subtitle, Resume.progressLabel(item)).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.unfinished && item.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            FilledIconButton(onClick = onClick) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Riprendi")
            }
        }
    }
}

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

