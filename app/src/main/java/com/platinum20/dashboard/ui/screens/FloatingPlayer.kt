package com.platinum20.dashboard.ui.screens

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
 * podcast): copertina (o il video stesso), titolo, play/pausa, chiudi e il tempo che scorre.
 * Si trascina dove si vuole (si aggancia al bordo più vicino) e si tocca per il player grande.
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
    // Sulle schermate dei player la bolla non serve: c'è già il player grande.
    if (currentRoute?.startsWith(com.platinum20.dashboard.Routes.PLAYER) == true) return
    if (now.isVideo && currentRoute?.startsWith("media/watch") == true) return

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val margin = with(density) { 12.dp.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        var bubbleW by remember { mutableIntStateOf(0) }
        var bubbleH by remember { mutableIntStateOf(0) }
        // Posizione in frazioni dello spazio, così resta sensata anche ruotando lo schermo.
        // Le frazioni sono Animatable: lasciata la bolla, una molla la porta al bordo con un rimbalzo.
        var savedX by rememberSaveable { mutableFloatStateOf(1f) }
        var savedY by rememberSaveable { mutableFloatStateOf(0.85f) }
        val fx = remember { Animatable(savedX) }
        val fy = remember { Animatable(savedY) }
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var dragging by remember { mutableStateOf(false) }
        // Comparsa: la bolla "salta fuori" invece di apparire di colpo.
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, Springs.bouncy()) }
        val pressScale by animateFloatAsState(if (dragging) 0.94f else 1f, Springs.snappy(), label = "drag")

        val rangeX = (maxW - bubbleW - 2 * margin).coerceAtLeast(1f)
        val rangeY = (maxH - bubbleH - 2 * margin).coerceAtLeast(1f)

        Surface(
            shape = MaterialTheme.shapes.large,
            // Bolla di vetro: si sposta sopra le pagine e le lascia intravedere sfocate.
            color = androidx.compose.ui.graphics.Color.Transparent,
            border = cardBorder(),
            modifier = Modifier
                .offset { IntOffset((margin + fx.value * rangeX).roundToInt(), (margin + fy.value * rangeY).roundToInt()) }
                .graphicsLayer {
                    val s = (0.6f + 0.4f * appear.value) * pressScale
                    scaleX = s
                    scaleY = s
                    alpha = appear.value.coerceIn(0f, 1f)
                }
                .onSizeChanged {
                    bubbleW = it.width
                    bubbleH = it.height
                }
                .glass(MaterialTheme.shapes.large)
                .pointerInput(rangeX, rangeY) {
                    val tracker = VelocityTracker()
                    // Posizione del dito tenuta qui, in modo sincrono: gli snapTo partono in coroutine.
                    var curX = 0f
                    var curY = 0f
                    detectDragGestures(
                        onDragStart = {
                            view.haptic(Haptic.GRAB)
                            dragging = true
                            tracker.resetTracking()
                            curX = fx.value
                            curY = fy.value
                        },
                        onDragCancel = { dragging = false },
                        // Lasciata la bolla, si aggancia al bordo verso cui la si lancia (o al più vicino).
                        onDragEnd = {
                            dragging = false
                            val velocity = tracker.calculateVelocity()
                            val vx = velocity.x / rangeX
                            val vy = velocity.y / rangeY
                            val targetX = when {
                                vx > 1.5f -> 1f
                                vx < -1.5f -> 0f
                                else -> if (curX < 0.5f) 0f else 1f
                            }
                            // Un lancio in verticale sposta un po' anche l'altezza, senza uscire dallo schermo.
                            val targetY = (curY + vy * 0.12f).coerceIn(0f, 1f)
                            savedX = targetX
                            savedY = targetY
                            view.haptic(Haptic.SNAP)
                            scope.launch { fx.animateTo(targetX, Springs.bouncy(), initialVelocity = vx) }
                            scope.launch { fy.animateTo(targetY, Springs.bouncy(), initialVelocity = vy) }
                        },
                    ) { change, drag ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        curX = (curX + drag.x / rangeX).coerceIn(-0.04f, 1.04f)
                        curY = (curY + drag.y / rangeY).coerceIn(0f, 1f)
                        scope.launch {
                            fx.snapTo(curX)
                            fy.snapTo(curY)
                        }
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
    Column(Modifier.width(300.dp)) {
        Row(
            Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { onExpand() }) }
                .padding(start = 6.dp, top = 6.dp, end = 2.dp, bottom = 4.dp),
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
            androidx.compose.material3.LinearProgressIndicator(
                progress = { now.progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp).height(2.dp),
                drawStopIndicator = {},
            )
        }
    }
}
