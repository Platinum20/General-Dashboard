package com.platinum20.dashboard.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.MediaWebViews
import com.platinum20.dashboard.media.NowPlaying
import com.platinum20.dashboard.media.PlayerCommand
import com.platinum20.dashboard.media.radio.RadioStore
import kotlin.math.roundToInt

/** Dove porta il tocco sul mini player. */
sealed interface FloatingTarget {
    data class Web(val service: MediaService) : FloatingTarget
    data object Radio : FloatingTarget
}

/**
 * Mini player a bolla sopra tutta l'app: il video di YouTube, oppure copertina e comandi di Spotify
 * o della radio. Si trascina dove si vuole (si aggancia al bordo più vicino), si tocca per aprire
 * il player completo, la X ferma la riproduzione.
 */
@Composable
fun FloatingPlayer(
    currentRoute: String?,
    onExpand: (FloatingTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    RadioStore.init(context)
    val tracks by NowPlaying.tracks.collectAsState()
    val open by MediaWebViews.open.collectAsState()
    val radio by RadioStore.now.collectAsState()

    // Sulle schermate dei player la bolla non serve: c'è già il player grande.
    val onWebPlayer = currentRoute?.startsWith("media/web") == true
    val onRadio = currentRoute?.startsWith("media/radio") == true
    val youTube = tracks[MediaService.YOUTUBE].takeIf { MediaService.YOUTUBE in open && !onWebPlayer }
    val spotify = tracks[MediaService.SPOTIFY].takeIf { MediaService.SPOTIFY in open && !onWebPlayer }
    val radioNow = radio.takeIf { !onRadio }

    if (youTube == null && spotify == null && radioNow == null) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val margin = with(density) { 12.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var bubbleW by remember { mutableIntStateOf(0) }
        var bubbleH by remember { mutableIntStateOf(0) }
        // Posizione in frazioni dello spazio, così resta sensata anche ruotando lo schermo.
        var fx by rememberSaveable { mutableFloatStateOf(1f) }
        var fy by rememberSaveable { mutableFloatStateOf(0.85f) }

        val rangeX = (maxW - bubbleW - 2 * margin).coerceAtLeast(1f)
        val rangeY = (maxH - bubbleH - 2 * margin).coerceAtLeast(1f)

        Surface(
            shape = MaterialTheme.shapes.large,
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .offset { IntOffset((margin + fx * rangeX).roundToInt(), (margin + fy * rangeY).roundToInt()) }
                .onSizeChanged {
                    bubbleW = it.width
                    bubbleH = it.height
                }
                .pointerInput(rangeX, rangeY) {
                    detectDragGestures(
                        // Lasciata la bolla, si aggancia al bordo sinistro o destro.
                        onDragEnd = { fx = if (fx < 0.5f) 0f else 1f },
                    ) { change, drag ->
                        change.consume()
                        fx = (fx + drag.x / rangeX).coerceIn(0f, 1f)
                        fy = (fy + drag.y / rangeY).coerceIn(0f, 1f)
                    }
                },
        ) {
            when {
                youTube != null -> YouTubeBubble(
                    playing = youTube.playing,
                    onExpand = { onExpand(FloatingTarget.Web(MediaService.YOUTUBE)) },
                )
                spotify != null -> AudioBubble(
                    image = spotify.artworkUrl,
                    title = spotify.title,
                    subtitle = spotify.artist.ifBlank { "Spotify" },
                    playing = spotify.playing,
                    onToggle = { NowPlaying.command(MediaService.SPOTIFY, PlayerCommand.TOGGLE) },
                    onClose = { MediaWebViews.release(MediaService.SPOTIFY) },
                    onExpand = { onExpand(FloatingTarget.Web(MediaService.SPOTIFY)) },
                )
                radioNow != null -> AudioBubble(
                    image = radioNow.station.logo,
                    title = radioNow.station.name,
                    subtitle = radioNow.track.ifBlank { "Radio" },
                    playing = radioNow.playing,
                    onToggle = { RadioStore.togglePause() },
                    onClose = { RadioStore.stop() },
                    onExpand = { onExpand(FloatingTarget.Radio) },
                )
            }
        }
    }
}

@Composable
private fun YouTubeBubble(playing: Boolean, onExpand: () -> Unit) {
    val context = LocalContext.current
    val webView = remember { MediaWebViews.obtain(context, MediaService.YOUTUBE) }
    val hostParent = remember { arrayOfNulls<android.view.ViewParent>(1) }
    DisposableEffect(Unit) {
        MediaWebViews.setMiniMode(MediaService.YOUTUBE, true)
        onDispose {
            MediaWebViews.setMiniMode(MediaService.YOUTUBE, false)
            MediaWebViews.detach(MediaService.YOUTUBE, hostParent[0])
        }
    }
    Column {
        Box(
            Modifier
                .size(width = 224.dp, height = 126.dp)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView
                },
                update = { hostParent[0] = it.parent },
                modifier = Modifier.fillMaxSize(),
            )
            // Strato sopra la pagina: i tocchi aprono il player invece di finire nel sito.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { NowPlaying.command(MediaService.YOUTUBE, PlayerCommand.TOGGLE) }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pausa" else "Riproduci")
            }
            Text("YouTube", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { MediaWebViews.release(MediaService.YOUTUBE) }) {
                Icon(Icons.Filled.Close, contentDescription = "Chiudi")
            }
        }
    }
}

@Composable
private fun AudioBubble(
    image: String,
    title: String,
    subtitle: String,
    playing: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (image.isNotBlank()) {
                AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 150.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pausa" else "Riproduci")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "Chiudi")
        }
    }
}
