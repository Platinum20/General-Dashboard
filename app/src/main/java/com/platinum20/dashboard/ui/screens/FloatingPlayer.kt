package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
 * in alto o in basso (sopra la barra delle sezioni); un tocco apre il player grande.
 * Dal 1.503 ([MiniTuck]): lanciato di lato (o scorrendo una pagina) si ritira in una linguetta sul bordo,
 * che si riapre con un tocco o tirandola; si chiude portandolo giù, sotto lo schermo, dove la calamita lo prende.
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
    // Dove starebbe la barra, misurato sempre (anche sul player grande): lì vola il video quando lo chiudi.
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .onGloballyPositioned { MiniDock.frame = it.boundsInRoot() }
    )
    val playing by Playback.now.collectAsState()
    val now = shown ?: playing
    // Finito tutto, o dal player grande: si riparte dalla barra intera (il video ci vola dentro).
    val onPlayer = currentRoute == com.platinum20.dashboard.Routes.PLAYER || currentRoute?.startsWith("media/watch") == true
    if (now == null || onPlayer) {
        SideEffect { MiniDock.tucked = false }
        return
    }

    // Ritirato, la linguetta tocca il bordo dello schermo: niente margine ai lati, se no la sua parte
    // esterna starebbe fuori dal riquadro e non prenderebbe i tocchi.
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(horizontal = if (MiniDock.tucked) 0.dp else 16.dp, vertical = 6.dp)
    ) {
        val density = LocalDensity.current
        val maxH = constraints.maxHeight.toFloat()
        var barH by remember { mutableIntStateOf(0) }
        val range = (maxH - barH).coerceAtLeast(1f)
        // In alto o in basso, ricordato anche ruotando lo schermo; in basso all'inizio.
        var atTop by rememberSaveable { mutableStateOf(MiniDock.atTop) }
        val y = remember { Animatable(if (atTop) 0f else 1f) }
        LaunchedEffect(atTop) {
            MiniDock.atTop = atTop
            y.animateTo(if (atTop) 0f else 1f, Motion.gesture())
        }
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var dragging by remember { mutableStateOf(false) }
        // Comparsa: la barra "salta fuori" invece di apparire di colpo.
        // Arrivando dal player grande invece è già lì: il video ci è appena volato dentro.
        val appear = remember { Animatable(if (MiniDock.docking) 1f else 0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, Motion.lively()) }
        val pressScale by animateFloatAsState(if (dragging) 0.97f else 1f, Motion.lively(), label = "drag")
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val x = remember { Animatable(0f) }
        // La calamita in fondo: quando lo prende scatta un po' giù da solo.
        var magnet by remember { mutableStateOf(false) }
        val pull = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }
        val tucked = MiniDock.tucked
        // Se dopo la chiusura qualcosa suona ancora (es. Spotify non risponde) la barra rientra.
        LaunchedEffect(closing) {
            if (closing) {
                kotlinx.coroutines.delay(1_500)
                closing = false
                magnet = false
                pull.snapTo(0f)
                x.snapTo(0f)
                y.snapTo(if (atTop) 0f else 1f)
                appear.snapTo(0f)
                appear.animateTo(1f, Motion.lively())
            }
        }
        // Riaperta dalla linguetta: rientra dal suo lato e salta fuori.
        var reopened by remember { mutableStateOf(false) }
        LaunchedEffect(tucked, reopened) {
            if (!tucked && reopened) {
                reopened = false
                x.snapTo((if (MiniDock.tuckRight) 1f else -1f) * maxW * 0.5f)
                launch { x.animateTo(0f, Motion.lively()) }
                appear.snapTo(0.6f)
                appear.animateTo(1f, Motion.lively())
            }
        }

        if (tucked) {
            val guess = with(density) { 70.dp.toPx() }
            val bar = if (barH > 0) barH.toFloat() else guess
            MiniTab(
                now = now,
                right = MiniDock.tuckRight,
                yPx = { y.value * (maxH - bar).coerceAtLeast(1f) + bar / 2f },
                maxW = maxW,
                onOpen = {
                    view.haptic(Haptic.SNAP)
                    reopened = true
                    MiniDock.tucked = false
                },
            )
            return@BoxWithConstraints
        }

        // La calamita, in fondo al centro: compare man mano che lo porti giù e diventa rossa quando lo prende.
        val below = (y.value - 1f).coerceAtLeast(0f) * range
        val near = (below / (MiniTuck.MAGNET_DP * density.density)).coerceIn(0f, 1f)

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = Color.Transparent,
            border = cardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(x.value.roundToInt(), (y.value * range + pull.value).roundToInt()) }
                .graphicsLayer {
                    val sc = (0.8f + 0.2f * appear.value) * pressScale * (1f - 0.12f * (pull.value / (MiniTuck.PULL_DP * density.density)).coerceIn(0f, 1f))
                    scaleX = sc
                    scaleY = sc
                    // Sbiadisce allontanandosi di lato.
                    alpha = (if (MiniDock.docking) 0f else 1f) * appear.value.coerceIn(0f, 1f) * (1f - 0.6f * (kotlin.math.abs(x.value) / maxW).coerceIn(0f, 1f))
                }
                .onSizeChanged {
                    barH = it.height
                    MiniDock.barHeight = it.height.toFloat()
                }
                .glass(MaterialTheme.shapes.extraLarge)
                .pointerInput(range, maxW) {
                    val tracker = VelocityTracker()
                    var curY = 0f
                    var start = 0f
                    var dx = 0f
                    // Il dito si misura sullo schermo, non sulla barra (che si muove con lui):
                    // sulla barra la velocità risulterebbe sempre quasi zero e il lancio non partirebbe.
                    var travelled = androidx.compose.ui.geometry.Offset.Zero
                    // Fin dove si può portare giù: oltre la calamita, sotto lo schermo.
                    val bottom = 1f + 2f * MiniTuck.MAGNET_DP * density.density / range
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
                            magnet = false
                            scope.launch { pull.animateTo(0f, Motion.lively()) }
                            scope.launch { x.animateTo(0f, Motion.gesture()) }
                            scope.launch { y.animateTo(if (atTop) 0f else 1f, Motion.gesture()) }
                        },
                        onDragEnd = {
                            dragging = false
                            val velocity = tracker.calculateVelocity()
                            val vx = velocity.x
                            when (MiniTuck.release(magnet, dx, vx, velocity.y, maxW)) {
                                MiniTuck.Release.CLOSE -> {
                                    closing = true
                                    view.haptic(Haptic.CONFIRM)
                                    // Giù, fuori dallo schermo, tirato dalla calamita.
                                    val out = 1f + (maxH + barH) / range
                                    scope.launch {
                                        y.animateTo(out, Motion.fling((out - y.value) * range, velocity.y.coerceAtLeast(0f)))
                                        onClose?.invoke() ?: Playback.stop()
                                    }
                                }
                                MiniTuck.Release.TUCK_LEFT, MiniTuck.Release.TUCK_RIGHT -> {
                                    view.haptic(Haptic.SNAP)
                                    val dir = if (dx >= 0) 1f else -1f
                                    scope.launch { y.animateTo(if (atTop) 0f else 1f, Motion.gesture()) }
                                    scope.launch {
                                        x.animateTo(dir * maxW * 1.1f, Motion.fling(maxW * 1.1f - kotlin.math.abs(x.value), vx))
                                        MiniDock.tuckRight = dir > 0f
                                        MiniDock.tucked = true
                                    }
                                }
                                MiniTuck.Release.STAY -> {
                                    scope.launch { x.animateTo(0f, Motion.gesture(), initialVelocity = vx) }
                                    // In alto o in basso: uno scatto o un piccolo spostamento verso l'altro lato basta per mandarla lì.
                                    val v = velocity.y / range
                                    val moved = curY.coerceAtMost(1f) - start
                                    val top = when {
                                        v < -0.6f -> true
                                        v > 0.6f -> false
                                        moved < -0.1f -> true
                                        moved > 0.1f -> false
                                        else -> curY < 0.5f
                                    }
                                    view.haptic(Haptic.SNAP)
                                    if (top == atTop) scope.launch { y.animateTo(if (top) 0f else 1f, Motion.gesture(), initialVelocity = v) }
                                    atTop = top
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, travelled)
                        // Di lato e su e giù insieme: si decide solo lasciandola.
                        dx += amount.x
                        curY = (curY + amount.y / range).coerceIn(-0.03f, bottom)
                        val held = MiniTuck.magnet((curY - 1f).coerceAtLeast(0f) * range, density.density)
                        if (held != magnet) {
                            magnet = held
                            // La calamita: vibra e lo tira giù di scatto; risalendo lo lascia andare.
                            view.haptic(if (held) Haptic.THRESHOLD else Haptic.TICK)
                            scope.launch { pull.animateTo(if (held) MiniTuck.PULL_DP * density.density else 0f, Motion.lively()) }
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

        if (near > 0f && barH > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, (barH / 2f + MiniTuck.PULL_DP * density.density).roundToInt()) }
                    .size(44.dp)
                    .graphicsLayer {
                        val sc = 0.6f + 0.4f * near + if (magnet) 0.15f else 0f
                        scaleX = sc
                        scaleY = sc
                        alpha = near
                    }
                    .clip(CircleShape)
                    .background(if (magnet) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (magnet) "Lascia per chiudere" else null,
                    tint = if (magnet) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer { rotationZ = 90f * (1f - near) },
                )
            }
        }
    }
}

/**
 * La linguetta sul bordo: la copertina tonda con l'anello del tempo che scorre, mezza dentro lo schermo.
 * Un tocco o tirarla verso il centro riapre il mini player.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MiniTab(
    now: Playing,
    right: Boolean,
    yPx: () -> Float,
    maxW: Float,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
    val show = remember { Animatable(0f) }
    LaunchedEffect(Unit) { show.animateTo(1f, Motion.lively()) }
    val tabW = 58.dp
    val tabH = 56.dp
    val shape = if (right) androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    else androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    // I tocchi su un riquadro semplice (senza vetro né scala), il disegno dentro.
    Box(
        Modifier
            .size(tabW, tabH)
            .offset {
                val w = with(density) { tabW.toPx() }
                val h = with(density) { tabH.toPx() }
                val left = if (right) maxW - w else 0f
                IntOffset(left.roundToInt(), (yPx() - h / 2f).roundToInt())
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "Mini player: ${now.title}. Tocca per riaprirlo"
            }
            .pointerInput(right) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (MiniTuck.pulledOut(dx, right, density.density)) open() },
                ) { change, amount ->
                    change.consume()
                    dx += amount
                }
            }
            // Un vero clic (non solo un tocco a mano): così lo apre anche TalkBack.
            .clickable(interactionSource = null, indication = null, onClickLabel = "Riapri") { open() },
    ) {
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = cardBorder(),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 0.6f + 0.4f * show.value
                scaleY = 0.6f + 0.4f * show.value
                alpha = show.value.coerceIn(0f, 1f)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (right) 1f else 0f, 0.5f)
            }
            .glass(shape),
    ) {
        Box(
            Modifier.padding(start = if (right) 7.dp else 0.dp, end = if (right) 0.dp else 7.dp),
            contentAlignment = if (right) Alignment.CenterStart else Alignment.CenterEnd,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sourceIcon(now.source), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
                    else if (now.artwork.isNotBlank()) {
                        AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                // L'anello: il tempo che scorre (pieno in diretta), blu = sta suonando.
                val position = livePosition(now)
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { if (now.live) 1f else if (now.durationMs > 0) (position.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                    color = if (now.playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                )
            }
        }
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
                // Mentre il video vola qui dal player grande lo schermo resta a lui: si aggancia all'arrivo.
                if (now.isVideo && !MiniDock.docking) {
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
                } else if (!now.isVideo) {
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
        // Il tempo che scorre, sottile sul bordo basso; in diretta niente barra. Resta (a zero) anche
        // mentre il brano dopo si carica, così dopo uno skip non sparisce.
        if (!now.live) {
            val position = livePosition(now)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (now.durationMs > 0) (position.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp).height(2.dp),
                drawStopIndicator = {},
            )
        }
    }
}
