package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.media.Playing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Mini player unico sopra tutta l'app, per qualunque cosa suoni (YouTube, musica, Spotify, radio,
 * podcast): una barra di vetro larga quanto la barra in basso con il +, con copertina (o il video
 * stesso), titolo, play/pausa, chiudi e il tempo che scorre. Si trascina e si aggancia solo in alto
 * o in basso (sopra la barra delle sezioni); un tocco apre il player grande.
 */
@Composable
fun FloatingPlayer(
    currentRoute: String?,
    onExpand: (Playing) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    RadioStore.init(context)
    val playing by Playback.now.collectAsState()
    val now = playing ?: return
    // Sulle schermate dei player la barra non serve: c'è già il player grande.
    if (currentRoute?.startsWith(com.platinum20.dashboard.Routes.PLAYER) == true) return
    if (now.isVideo && currentRoute?.startsWith("media/watch") == true) return

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val maxH = constraints.maxHeight.toFloat()
        var barH by remember { mutableIntStateOf(0) }
        val range = (maxH - barH).coerceAtLeast(1f)
        // In alto o in basso, ricordato anche ruotando lo schermo; in basso all'inizio.
        var atTop by rememberSaveable { mutableStateOf(false) }
        val y = remember { Animatable(1f) }
        LaunchedEffect(atTop) { y.animateTo(if (atTop) 0f else 1f, Springs.bouncy()) }
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var dragging by remember { mutableStateOf(false) }
        // Comparsa: la barra "salta fuori" invece di apparire di colpo.
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, Springs.bouncy()) }
        val pressScale by animateFloatAsState(if (dragging) 0.97f else 1f, Springs.snappy(), label = "drag")

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            border = cardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (y.value * range).roundToInt()) }
                .graphicsLayer {
                    val sc = (0.8f + 0.2f * appear.value) * pressScale
                    scaleX = sc
                    scaleY = sc
                    alpha = appear.value.coerceIn(0f, 1f)
                }
                .onSizeChanged { barH = it.height }
                .glass(MaterialTheme.shapes.extraLarge)
                .pointerInput(range) {
                    val tracker = VelocityTracker()
                    var cur = 0f
                    var start = 0f
                    // Il dito si misura sullo schermo, non sulla barra (che si muove con lui):
                    // sulla barra la velocità risulterebbe sempre quasi zero e il lancio non partirebbe.
                    var travelled = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            view.haptic(Haptic.GRAB)
                            dragging = true
                            tracker.resetTracking()
                            cur = y.value
                            start = cur
                            travelled = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { y.animateTo(if (atTop) 0f else 1f, Springs.bouncy()) }
                        },
                        // Lasciata: uno scatto o un piccolo spostamento verso l'altro lato basta per mandarla lì.
                        onDragEnd = {
                            dragging = false
                            val v = tracker.calculateVelocity().y / range
                            val moved = cur - start
                            val top = when {
                                v < -0.6f -> true
                                v > 0.6f -> false
                                moved < -0.1f -> true
                                moved > 0.1f -> false
                                else -> cur < 0.5f
                            }
                            view.haptic(Haptic.SNAP)
                            if (top == atTop) scope.launch { y.animateTo(if (top) 0f else 1f, Springs.bouncy(), initialVelocity = v) }
                            atTop = top
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, androidx.compose.ui.geometry.Offset(0f, travelled))
                        cur = (cur + amount / range).coerceIn(-0.03f, 1.03f)
                        scope.launch { y.snapTo(cur) }
                    }
                },
        ) {
            MiniPlayer(now, onExpand = { onExpand(now) })
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MiniPlayer(now: Playing, onExpand: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
                .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Il video di YouTube si vede anche in piccolo; per il resto la copertina.
            val thumbWidth = if (now.isVideo) 84.dp else 48.dp
            Box(
                Modifier
                    .size(width = thumbWidth, height = 48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (now.isVideo) Color.Black else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (now.isVideo) {
                    AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                useController = false
                                player = YtPlayer.player(ctx)
                            }
                        },
                        update = { view -> YtPlayer.current()?.let { if (view.player !== it) view.player = it } },
                        onRelease = { it.player = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(sourceIcon(now.source), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (now.artwork.isNotBlank()) {
                        AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(now.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(now.source.label, now.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (now.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { Playback.toggle(context) }) {
                Icon(
                    if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (now.playing) "Pausa" else "Riproduci",
                )
            }
            IconButton(onClick = { Playback.stop(context) }) {
                Icon(Icons.Filled.Close, contentDescription = "Chiudi")
            }
        }
        // Il tempo che scorre, sottile sul bordo basso; in diretta niente barra.
        if (now.durationMs > 0 && !now.live) {
            val position = livePosition(now)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (position.toFloat() / now.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp).height(2.dp),
                drawStopIndicator = {},
            )
        }
    }
}
