package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.PlaySource
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun sourceIcon(source: PlaySource): ImageVector = when (source) {
    PlaySource.VIDEO -> Icons.Outlined.OndemandVideo
    PlaySource.MUSIC -> Icons.Outlined.MusicNote
    PlaySource.SPOTIFY -> Icons.Outlined.GraphicEq
    PlaySource.RADIO -> Icons.Outlined.Radio
    PlaySource.PODCAST -> Icons.Outlined.Mic
}

internal fun sourceAccent(source: PlaySource): TileAccent = when (source) {
    PlaySource.VIDEO -> TileAccent.ORANGE
    PlaySource.MUSIC, PlaySource.SPOTIFY -> TileAccent.GREEN
    PlaySource.RADIO, PlaySource.PODCAST -> TileAccent.PURPLE
}

/**
 * Il player grande, uguale per musica (Spotify e YouTube Music), radio e podcast: copertina,
 * barra del tempo, comandi e timer di spegnimento. I video di YouTube hanno il loro, con il video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, onWatchVideo: (String) -> Unit) {
    val context = LocalContext.current
    val now by Playback.now.collectAsState()
    val sleepAt by Playback.sleepAt.collectAsState()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    // Dietro a tutto la copertina sfocata, che sfuma nello sfondo: il player prende i colori di quello che suona.
    now?.artwork?.takeIf { it.isNotBlank() }?.let { art ->
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f).blur(72.dp).alpha(0.55f),
        )
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.7f).background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))
            )
        )
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopLevelBar(
                title = "In ascolto",
                subtitle = now?.source?.label?.uppercase(),
                onBack = onBack,
                transparent = true,
            )
        },
    ) { inner ->
        val current = now
        if (current == null) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Non sta suonando niente", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scegli musica, un video, una radio o un podcast da Media.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(current)
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = 420.dp)) {
                SourcePill(current.source)
                Spacer(Modifier.height(8.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    current.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                TimeBar(current)
                Spacer(Modifier.height(8.dp))
                Controls(current)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // La musica di YouTube suona in solo audio: da qui si passa al video, dallo stesso punto.
                    if (current.source == PlaySource.MUSIC) {
                        YtPlayer.now.value?.link?.let { link ->
                            OutlinedButton(onClick = { onWatchVideo(link) }, modifier = Modifier.bouncyPress()) { Text("Guarda il video") }
                        }
                    }
                    OutlinedButton(onClick = { Playback.stop(context) }, modifier = Modifier.bouncyPress()) { Text("Chiudi") }
                }
                Spacer(Modifier.height(20.dp))
                SleepTimer(sleepAt)
            }
        }
    }
    }
}

/** Da dove arriva quello che suona, con l'icona e il colore della sezione. */
@Composable
private fun SourcePill(source: PlaySource) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sourceIcon(source), contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(source.label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Artwork(now: Playing) {
    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .aspectRatio(1f)
            .shadow(24.dp, MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        IconTile(sourceIcon(now.source), sourceAccent(now.source), size = 96.dp, iconSize = 48.dp)
        if (now.artwork.isNotBlank()) {
            AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (now.loading) CircularProgressIndicator(Modifier.size(40.dp))
    }
}

@Composable
private fun TimeBar(now: Playing) {
    val context = LocalContext.current
    if (now.live) {
        Text(
            "● In diretta",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
        return
    }
    if (now.durationMs <= 0) return
    // Mentre si trascina la barra segue il dito; lasciata, il player salta lì.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = livePosition(now)
    Slider(
        value = dragging ?: (position.toFloat() / now.durationMs).coerceIn(0f, 1f),
        enabled = now.canSeek,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { Playback.seekTo(context, (it * now.durationMs).toLong()) }
            dragging = null
        },
    )
    Row {
        Text(clockLabel(dragging?.let { (it * now.durationMs).toLong() } ?: position), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.weight(1f))
        Text(clockLabel(now.durationMs), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun Controls(now: Playing) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val podcast = now.source == PlaySource.PODCAST
        if (now.canSkip || podcast) {
            IconButton(onClick = { Playback.previous(context) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (podcast) Icons.Filled.Replay10 else Icons.Filled.SkipPrevious,
                    contentDescription = if (podcast) "Indietro di 10 secondi" else "Indietro",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        FilledIconButton(
            onClick = { Playback.toggle(context) },
            modifier = Modifier.size(76.dp).bouncyPress(),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(
                if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (now.playing) "Pausa" else "Riproduci",
                modifier = Modifier.size(40.dp),
            )
        }
        if (now.canSkip || podcast) {
            IconButton(onClick = { Playback.next(context) }, modifier = Modifier.size(56.dp)) {
                Icon(
                    if (podcast) Icons.Filled.Forward30 else Icons.Filled.SkipNext,
                    contentDescription = if (podcast) "Avanti di 30 secondi" else "Avanti",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * Dove si è arrivati, che avanza da sola tra un aggiornamento della fonte e l'altro: la barra
 * scorre fluida anche quando la fonte dà la posizione di rado (Spotify su un altro dispositivo).
 */
@Composable
internal fun livePosition(now: Playing): Long {
    val latest by rememberUpdatedState(now)
    var anchor by remember { mutableStateOf(now.positionMs to System.currentTimeMillis()) }
    LaunchedEffect(now.positionMs, now.title, now.playing) { anchor = now.positionMs to System.currentTimeMillis() }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(now.playing) {
        while (latest.playing) {
            delay(250)
            clock = System.currentTimeMillis()
        }
    }
    val elapsed = if (now.playing && !now.loading) (clock - anchor.second).coerceAtLeast(0) else 0
    val end = now.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
    return (anchor.first + elapsed).coerceIn(0, end)
}

private val sleepOptions = listOf(15 to "15 min", 30 to "30 min", 60 to "1 ora", 90 to "1 ora e mezza")
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/** Timer di spegnimento: mette in pausa qualunque cosa suoni dopo il tempo scelto. */
@Composable
private fun SleepTimer(sleepAt: Long?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(
                sleepAt?.let { "Si ferma alle " + clock.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
                    ?: "Timer di spegnimento",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            sleepOptions.forEach { (minutes, label) ->
                CanvasChip(selected = false, onClick = { Playback.setSleepTimer(minutes) }, label = label)
            }
            if (sleepAt != null) CanvasChip(selected = true, onClick = { Playback.setSleepTimer(null) }, label = "Annulla")
        }
    }
}
