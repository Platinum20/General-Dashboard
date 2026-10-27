package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.platinum20.dashboard.media.PlaySource
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
 * stesso), titolo, indietro, play/pausa, avanti e il tempo che scorre. Si trascina e si aggancia solo
 * in alto o in basso (sopra la barra delle sezioni), si chiude lanciandola di lato; un tocco apre il player grande.
 */
@Composable
fun FloatingPlayer(
    currentRoute: String?,
    onExpand: (Playing) -> Unit,
    modifier: Modifier = Modifier,
    /** Solo per le prove: cosa mostrare e cosa fare chiudendo, al posto del player unico. */
    shown: Playing? = null,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    RadioStore.init(context)
    val playing by Playback.now.collectAsState()
    val now = shown ?: playing ?: return
    // Sulle schermate dei player la barra non serve: c'è già il player grande.
    // Solo il player: la coda ("media/player/queue") lo tiene, così si vede cosa suona mentre la sistemi.
    if (currentRoute == com.platinum20.dashboard.Routes.PLAYER) return
    if (currentRoute?.startsWith("media/watch") == true) return

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
        // Per chiudere: si porta di lato, come una notifica. Il dito la muove liberamente (di lato e
        // su e giù insieme), sotto compare la X dal lato che si scopre; oltre metà larghezza lasciandola si chiude.
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val x = remember { Animatable(0f) }
        val closeAt = maxW * 0.5f
        val reveal = (kotlin.math.abs(x.value) / closeAt).coerceIn(0f, 1f)
        var closeArmed by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        // Se dopo la chiusura qualcosa suona ancora (es. Spotify non risponde) la barra rientra.
        LaunchedEffect(closing) {
            if (closing) {
                kotlinx.coroutines.delay(1_500)
                closing = false
                x.snapTo(0f)
                appear.snapTo(0f)
                appear.animateTo(1f, Springs.bouncy())
            }
        }

        // La X dietro la barra, dal lato che si scopre: cresce e diventa rossa quando lasciando si chiude.
        if (reveal > 0f && barH > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { barH.toDp() })
                    .offset { IntOffset(0, (y.value * range).roundToInt()) },
                contentAlignment = if (x.value > 0) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .size(44.dp)
                        .graphicsLayer {
                            val sc = 0.6f + 0.4f * reveal
                            scaleX = sc
                            scaleY = sc
                            alpha = reveal
                        }
                        .clip(CircleShape)
                        .background(if (closeArmed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = if (closeArmed) "Lascia per chiudere" else null,
                        tint = if (closeArmed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = 90f * (1f - reveal) },
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            border = cardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x.value.roundToInt(), (y.value * range).roundToInt()) }
                .graphicsLayer {
                    val sc = (0.8f + 0.2f * appear.value) * pressScale
                    scaleX = sc
                    scaleY = sc
                    // Sbiadisce allontanandosi di lato.
                    alpha = appear.value.coerceIn(0f, 1f) * (1f - 0.6f * (kotlin.math.abs(x.value) / maxW).coerceIn(0f, 1f))
                }
                .onSizeChanged { barH = it.height }
                .glass(MaterialTheme.shapes.extraLarge)
                .pointerInput(range, maxW) {
                    val tracker = VelocityTracker()
                    var curY = 0f
                    var start = 0f
                    var dx = 0f
                    // Il dito si misura sullo schermo, non sulla barra (che si muove con lui):
                    // sulla barra la velocità risulterebbe sempre quasi zero e il lancio non partirebbe.
                    var travelled = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            view.haptic(Haptic.GRAB)
                            dragging = true
                            tracker.resetTracking()
                            curY = y.value
                            start = curY
                            dx = x.value
                            travelled = androidx.compose.ui.geometry.Offset.Zero
                        },
                        onDragCancel = {
                            dragging = false
                            closeArmed = false
                            scope.launch { x.animateTo(0f, Springs.bouncy()) }
                            scope.launch { y.animateTo(if (atTop) 0f else 1f, Springs.bouncy()) }
                        },
                        onDragEnd = {
                            dragging = false
                            val velocity = tracker.calculateVelocity()
                            val vx = velocity.x
                            // Chiude solo se portata oltre metà, o lanciata forte di lato quando è già ben avviata.
                            val fling = SwipeAway.fling(dx, vx, velocity.y, maxW)
                            if (closeArmed || fling) {
                                closeArmed = false
                                closing = true
                                view.haptic(Haptic.CONFIRM)
                                val dir = if (dx >= 0) 1f else -1f
                                scope.launch {
                                    x.animateTo(dir * maxW * 1.2f, androidx.compose.animation.core.tween(200), initialVelocity = vx)
                                    onClose?.invoke() ?: Playback.stop()
                                }
                                scope.launch { y.animateTo(if (atTop) 0f else 1f, Springs.bouncy()) }
                            } else {
                                scope.launch { x.animateTo(0f, Springs.bouncy(), initialVelocity = vx) }
                                // In alto o in basso: uno scatto o un piccolo spostamento verso l'altro lato basta per mandarla lì.
                                val v = velocity.y / range
                                val moved = curY - start
                                val top = when {
                                    v < -0.6f -> true
                                    v > 0.6f -> false
                                    moved < -0.1f -> true
                                    moved > 0.1f -> false
                                    else -> curY < 0.5f
                                }
                                view.haptic(Haptic.SNAP)
                                if (top == atTop) scope.launch { y.animateTo(if (top) 0f else 1f, Springs.bouncy(), initialVelocity = v) }
                                atTop = top
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, travelled)
                        // Di lato e su e giù insieme: si decide solo lasciandola.
                        dx += amount.x
                        curY = (curY + amount.y / range).coerceIn(-0.03f, 1.03f)
                        val armed = kotlin.math.abs(dx) > closeAt
                        if (armed != closeArmed) {
                            closeArmed = armed
                            view.haptic(if (armed) Haptic.THRESHOLD else Haptic.TICK)
                        }
                        val nx = dx
                        val ny = curY
                        scope.launch { x.snapTo(nx) }
                        scope.launch { y.snapTo(ny) }
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
    // Il tocco apre sempre quello che suona adesso: senza questo il gesto restava legato
    // alla prima cosa partita (es. un video chiuso) anche dopo aver messo una canzone.
    val expand by androidx.compose.runtime.rememberUpdatedState(onExpand)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .pointerInput(Unit) { detectTapGestures(onTap = { expand() }) }
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
                    if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
                    else if (now.artwork.isNotBlank()) {
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
            // Indietro, play/pausa, avanti (nella coda, per tutte le fonti); niente X: si chiude trascinandola.
            // In diretta (radio) avanti e indietro servono solo per muoversi nella coda.
            val queue by com.platinum20.dashboard.media.CanvasPlayer.queue.collectAsState()
            val skips = !now.live || queue.hasNext || queue.hasPrevious
            if (skips) {
                IconButton(onClick = { Playback.previous() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Indietro")
                }
            }
            IconButton(onClick = { Playback.toggle() }, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (now.playing) "Pausa" else "Riproduci",
                    modifier = Modifier.size(30.dp),
                )
            }
            if (skips) {
                IconButton(onClick = { Playback.next() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Avanti")
                }
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
