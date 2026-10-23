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

/** Sezione Media: player video, YouTube e Spotify con il blocco pubblicità. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    bottomPadding: PaddingValues,
    onOpenVideo: () -> Unit,
    onOpenService: (MediaService) -> Unit,
    onOpenRadio: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    YouTubeAuth.init(context)
    SpotifyAuth.init(context)
    val youTubeLinked by YouTubeAuth.connected.collectAsState()
    val spotifyLinked by SpotifyAuth.connected.collectAsState()
    val open by MediaWebViews.open.collectAsState()
    val tracks by NowPlaying.tracks.collectAsState()
    val sleepAt by NowPlaying.sleepAt.collectAsState()
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()
    val youTubeNow by YtPlayer.now.collectAsState()
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val history by MediaHistory.items.collectAsState()
    val videos by VideoLibrary.recent.collectAsState()
    val resumable = remember(history, videos) { Resume.pick(history + videos.map(ResumeItem::fromVideo)) }
    val inApp = LocalInAppNav.current
    val openResume: (ResumeItem) -> Unit = { item -> openResumeItem(item) { route -> inApp?.openRoute?.invoke(route) } }
    // Quando nulla suona, la prima cosa è l'ultima cosa vista o ascoltata.
    val hero = resumable.firstOrNull()?.takeIf { tracks.isEmpty() && radio == null && youTubeNow == null }
    val recents = resumable.filter { it != hero }.take(4)
    // Al primo uso (niente da riprendere) qualche radio da far partire subito.
    val firstUse = hero == null && recents.isEmpty() && tracks.isEmpty() && radio == null
    val popular by RadioStore.popular.collectAsState()
    val recentStations by RadioStore.recent.collectAsState()
    val favoriteStations by RadioStore.favorites.collectAsState()
    val starters = if (firstUse) MediaStarters.stations(popular.data.orEmpty(), recentStations, favoriteStations) else emptyList()
    LaunchedEffect(firstUse) { if (firstUse) RadioStore.loadPopular() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            TopLevelBar(
                title = "Media",
                subtitle = when {
                    tracks.values.any { it.playing } -> "In riproduzione"
                    radio != null -> "In ascolto: ${radio?.station?.name}"
                    tracks.isNotEmpty() -> "Riprendi da dove eri"
                    else -> "Video, musica e radio"
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            state = rememberReselectListState(Routes.MEDIA),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            tracks.values.forEach { track ->
                item(key = "playing-${track.service.name}") {
                    NowPlayingCard(
                        track = track,
                        onOpen = { onOpenService(track.service) },
                        onCommand = { NowPlaying.command(track.service, it) },
                    )
                }
            }
            if (tracks.isNotEmpty()) {
                item(key = "sleep") { SleepTimerRow(sleepAt = sleepAt, onSet = NowPlaying::setSleepTimer) }
            }
            hero?.let { last ->
                item(key = "resume") { ResumeCard(last, onClick = { openResume(last) }) }
            }
            // Le quattro fonti in una griglia 2×2: il colore dell'icona le distingue a colpo d'occhio.
            item(key = "grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaTile(
                            icon = Icons.Outlined.PlayCircle,
                            accent = TileAccent.BLUE,
                            title = "Video",
                            subtitle = "File e link, riprendi da dove eri",
                            onClick = onOpenVideo,
                        )
                        MediaTile(
                            icon = Icons.Outlined.OndemandVideo,
                            accent = TileAccent.ORANGE,
                            title = MediaService.YOUTUBE.label,
                            subtitle = if (youTubeLinked) "Iscrizioni, playlist e Mi piace" else "Collega l'account per il feed",
                            onClick = { onOpenService(MediaService.YOUTUBE) },
                            playing = MediaService.YOUTUBE in open,
                            onStop = { MediaWebViews.release(MediaService.YOUTUBE) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaTile(
                            icon = Icons.Outlined.GraphicEq,
                            accent = TileAccent.GREEN,
                            title = MediaService.SPOTIFY.label,
                            subtitle = if (spotifyLinked) "Per te, playlist e libreria" else "Collega l'account per il feed",
                            onClick = { onOpenService(MediaService.SPOTIFY) },
                            playing = MediaService.SPOTIFY in open,
                            onStop = { MediaWebViews.release(MediaService.SPOTIFY) },
                        )
                        MediaTile(
                            icon = Icons.Outlined.Radio,
                            accent = TileAccent.PURPLE,
                            title = "Radio",
                            subtitle = radio?.let { "In ascolto: ${it.station.name}" } ?: "Stazioni italiane e del mondo",
                            onClick = onOpenRadio,
                            playing = radio != null,
                            onStop = { RadioStore.stop() },
                        )
                    }
                }
            }
            if (recents.isNotEmpty()) {
                item(key = "recent") {
                    Section("Recenti") {
                        recents.forEachIndexed { index, recent ->
                            if (index > 0) RowDivider()
                            ResumeRow(recent, onClick = { openResume(recent) })
                        }
                    }
                }
            }
            if (starters.isNotEmpty()) {
                item(key = "starters") {
                    Section("Per iniziare", action = "Tutte le radio", onAction = onOpenRadio) {
                        starters.forEachIndexed { index, station ->
                            if (index > 0) RowDivider()
                            StarterRow(station, onClick = { RadioStore.play(station) })
                        }
                    }
                }
            }
            // Il blocco pubblicità si regola nelle Impostazioni: qui solo una riga che dice se è attivo.
            item(key = "adblock") {
                val adBlock by AppSettings.adBlock.collectAsState()
                Text(
                    if (adBlock) "Pubblicità bloccata nei player · Impostazioni" else "Blocco pubblicità spento · Impostazioni",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onOpenSettings)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.MediaTile(
    icon: ImageVector,
    accent: TileAccent,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    playing: Boolean = false,
    onStop: () -> Unit = {},
) {
    CanvasCard(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 128.dp).bouncyPress(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, accent)
                Spacer(Modifier.weight(1f))
                if (playing) {
                    // Chiude il player rimasto aperto in sottofondo.
                    IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Chiudi player", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (playing) "Aperto · $subtitle" else subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Apre la cosa da riprendere: il video dal punto lasciato, la radio subito in ascolto. */
internal fun openResumeItem(item: ResumeItem, openRoute: (String) -> Unit) {
    when (item.kind) {
        ResumeKind.VIDEO -> openRoute(Routes.video(item.key))
        ResumeKind.YOUTUBE -> openRoute(Routes.youtubeWatch(Resume.youtubeLink(item)))
        ResumeKind.RADIO -> {
            item.station?.let(RadioStore::play)
            openRoute(Routes.RADIO)
        }
    }
}

private fun ResumeItem.icon(): ImageVector = when (kind) {
    ResumeKind.VIDEO -> Icons.Outlined.PlayCircle
    ResumeKind.YOUTUBE -> Icons.Outlined.OndemandVideo
    ResumeKind.RADIO -> Icons.Outlined.Radio
}

private fun ResumeItem.accent(): TileAccent = when (kind) {
    ResumeKind.VIDEO -> TileAccent.BLUE
    ResumeKind.YOUTUBE -> TileAccent.ORANGE
    ResumeKind.RADIO -> TileAccent.PURPLE
}

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
                    if (item.unfinished) "Riprendi" else if (item.kind == ResumeKind.RADIO) "Ascolta di nuovo" else "Guarda di nuovo",
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

@Composable
private fun ResumeRow(item: ResumeItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ResumeThumb(item, 40)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOf(item.subtitle, Resume.progressLabel(item)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

/** Una radio di "Per iniziare": logo, nome e genere, un tocco la fa partire. */
@Composable
private fun StarterRow(station: RadioStation, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationLogo(station, 40)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                station.tags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString(" · ").ifBlank { "Radio" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = "Ascolta ${station.name}", tint = MaterialTheme.colorScheme.primary)
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

/** Mini player: cosa sta suonando, con i comandi, anche quando la pagina non è a schermo. */
@Composable
private fun NowPlayingCard(track: Track, onOpen: () -> Unit, onCommand: (PlayerCommand) -> Unit) {
    CanvasCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().bouncyPress(),
        shape = MaterialTheme.shapes.extraLarge,
        // Come "Adesso" in Oggi: il blocco colorato è ciò che sta succedendo ora.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (track.artworkUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (track.playing) "In riproduzione su ${track.service.label}" else "In pausa su ${track.service.label}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (track.artist.isNotBlank()) {
                    Text(track.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = { onCommand(PlayerCommand.PREVIOUS) }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Indietro")
            }
            FilledIconButton(onClick = { onCommand(PlayerCommand.TOGGLE) }) {
                Icon(
                    if (track.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (track.playing) "Pausa" else "Riproduci",
                )
            }
            IconButton(onClick = { onCommand(PlayerCommand.NEXT) }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Avanti")
            }
        }
    }
}

private val sleepOptions = listOf(15 to "15 min", 30 to "30 min", 60 to "1 ora", 90 to "1 ora e mezza")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Timer di spegnimento: mette in pausa la musica dopo il tempo scelto. */
@Composable
private fun SleepTimerRow(sleepAt: Long?, onSet: (Int?) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                sleepAt?.let { "Si ferma alle " + clock.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
                    ?: "Timer di spegnimento",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            sleepOptions.forEach { (minutes, label) ->
                CanvasChip(selected = false, onClick = { onSet(minutes) }, label = label)
            }
            if (sleepAt != null) {
                CanvasChip(selected = true, onClick = { onSet(null) }, label = "Annulla")
            }
        }
    }
}
