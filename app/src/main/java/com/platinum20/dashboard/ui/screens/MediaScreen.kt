package com.platinum20.dashboard.ui.screens

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
import androidx.compose.material3.ElevatedCard
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
    val adBlock by AppSettings.adBlock.collectAsState()
    val blocked by AdBlocker.blocked.collectAsState()
    val adBlockStatus by AdBlocker.status.collectAsState()
    val tracks by NowPlaying.tracks.collectAsState()
    val sleepAt by NowPlaying.sleepAt.collectAsState()
    RadioStore.init(context)
    val radio by RadioStore.now.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            TopLevelBar(title = "Media", scrollBehavior = scrollBehavior)
        },
    ) { inner ->
        LazyColumn(
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
            item {
                MediaCard(
                    icon = Icons.Outlined.PlayCircle,
                    title = "Video",
                    subtitle = "Video e musica del telefono o da un link, riprendi da dove eri rimasto",
                    onClick = onOpenVideo,
                )
            }
            MediaService.entries.forEach { service ->
                item(key = service.name) {
                    MediaCard(
                        icon = when (service) {
                            MediaService.YOUTUBE -> Icons.Outlined.OndemandVideo
                            MediaService.SPOTIFY -> Icons.Outlined.GraphicEq
                        },
                        title = service.label,
                        subtitle = when (service) {
                            MediaService.YOUTUBE ->
                                if (youTubeLinked) "Iscrizioni, playlist e Mi piace" else "Collega il tuo account per il feed"
                            MediaService.SPOTIFY ->
                                if (spotifyLinked) "Per te, playlist, libreria e ricerca" else "Collega il tuo account per il feed"
                        },
                        onClick = { onOpenService(service) },
                        playing = service in open,
                        onStop = { MediaWebViews.release(service) },
                    )
                }
            }
            item(key = "radio") {
                MediaCard(
                    icon = Icons.Outlined.Radio,
                    title = "Radio",
                    subtitle = radio?.let { "In ascolto: ${it.station.name}" } ?: "Le stazioni italiane e di tutto il mondo, in sottofondo",
                    onClick = onOpenRadio,
                    playing = radio != null,
                    onStop = { RadioStore.stop() },
                )
            }
            item(key = "adblock") {
                AdBlockCard(
                    enabled = adBlock,
                    blocked = blocked,
                    status = adBlockStatus,
                    onToggle = {
                        AppSettings.setAdBlock(it)
                        // Le pagine aperte ripartono con la nuova impostazione.
                        MediaWebViews.releaseAll()
                    },
                )
            }
        }
    }
}

@Composable
private fun AdBlockCard(enabled: Boolean, blocked: Int, status: AdBlocker.Status, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        // Stesso fondo delle altre card: lo stato lo dicono l'icona colorata e l'interruttore, senza un blocco di colore.
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Blocco pubblicità", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) "Attivo · $blocked richieste bloccate" else "Disattivato",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled) {
                    Text(
                        adBlockStatusText(status),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (enabled) {
                TextButton(onClick = { AdBlocker.updateNow() }, enabled = !status.updating) { Text("Aggiorna") }
            }
            Switch(checked = enabled, onCheckedChange = hapticToggle(onToggle))
        }
    }
}

/** "EasyList, EasyPrivacy e uBlock Origin · 163.784 regole · aggiornate il 22 ott". */
private fun adBlockStatusText(status: AdBlocker.Status): String {
    val parts = mutableListOf<String>()
    when {
        status.updating -> parts += "Aggiornamento in corso…"
        !status.ready -> parts += "Caricamento delle liste…"
        else -> parts += "%,d regole".format(Locale.ITALY, status.rules)
    }
    parts += status.updatedAt?.let {
        "aggiornate il " + SimpleDateFormat("d MMM", Locale.ITALIAN).format(Date(it))
    } ?: "liste incluse nell'app"
    status.lastError?.let { parts += it }
    return "EasyList, EasyPrivacy e uBlock Origin · " + parts.joinToString(" · ")
}

@Composable
private fun MediaCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    playing: Boolean = false,
    onStop: () -> Unit = {},
    onLogin: (() -> Unit)? = null,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().bouncyPress(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (playing || onLogin != null) {
                    Row {
                        if (playing) TextButton(onClick = onStop) { Text("Chiudi player") }
                        if (onLogin != null) TextButton(onClick = onLogin) { Text("Accedi") }
                    }
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Mini player: cosa sta suonando, con i comandi, anche quando la pagina non è a schermo. */
@Composable
private fun NowPlayingCard(track: Track, onOpen: () -> Unit, onCommand: (PlayerCommand) -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                FilterChip(selected = false, onClick = { onSet(minutes) }, label = { Text(label) })
            }
            if (sleepAt != null) {
                FilterChip(selected = true, onClick = { onSet(null) }, label = { Text("Annulla") })
            }
        }
    }
}
