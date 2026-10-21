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
import com.platinum20.dashboard.media.AdBlocker
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
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
import com.platinum20.dashboard.settings.AppSettings

/** Sezione Media: player video, YouTube e Spotify con il blocco pubblicità. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    bottomPadding: PaddingValues,
    onOpenVideo: () -> Unit,
    onOpenService: (MediaService) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val loggedIn by Accounts.loggedIn.collectAsState()
    val open by MediaWebViews.open.collectAsState()
    val adBlock by AppSettings.adBlock.collectAsState()
    val blocked by AdBlocker.blocked.collectAsState()
    val tracks by NowPlaying.tracks.collectAsState()

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
            item {
                AdBlockCard(
                    enabled = adBlock,
                    blocked = blocked,
                    onToggle = {
                        AppSettings.setAdBlock(it)
                        // Le pagine aperte ripartono con la nuova impostazione.
                        MediaWebViews.releaseAll()
                    },
                )
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
                        subtitle = if (service in loggedIn) "Account collegato" else "Senza account · accedi da Impostazioni",
                        onClick = { onOpenService(service) },
                        playing = service in open,
                        onStop = { MediaWebViews.release(service) },
                        onLogin = if (service in loggedIn) null else onOpenSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdBlockCard(enabled: Boolean, blocked: Int, onToggle: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Blocco pubblicità", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "Attivo · $blocked richieste bloccate da quando hai aperto l'app" else "Disattivato",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
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
        modifier = Modifier.fillMaxWidth(),
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
