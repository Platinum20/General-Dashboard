package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.layout.findRootCoordinates
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
 * Dal 1.508 ([MiniTuck]): lanciato di lato (o scorrendo una pagina) si ritira in una linguetta sul bordo,
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

    // Quanto schermo resta sotto il suo riquadro (barra delle sezioni compresa, se c'è): la calamita e la X
    // stanno sul fondo dello schermo, non sulla barra, così si chiude anche nelle pagine senza (1.521).
    var screenBelow by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .onGloballyPositioned { c ->
                val root = c.findRootCoordinates()
                screenBelow = (root.size.height - c.boundsInRoot().bottom).coerceAtLeast(0f)
            }
    ) {
        val density = LocalDensity.current
        val maxH = constraints.maxHeight.toFloat()
        var barH by remember { mutableIntStateOf(0) }
        val range = (maxH - barH).coerceAtLeast(1f)
        val magnetAt = MiniTuck.threshold(barH / 2f + screenBelow, density.density)
        val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density).toFloat()
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
        // Portato oltre metà di lato: lasciandolo va nella linguetta (vibra come la calamita).
        var tuckArmed by remember { mutableStateOf(false) }
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
        // Solo [tucked] come chiave: con altre chiavi l'effetto si riavviava a metà e la barra restava mezza fuori.
        var reopened by remember { mutableStateOf(false) }
        LaunchedEffect(tucked) {
            if (tucked || !reopened) return@LaunchedEffect
            reopened = false
            // Esce dal bordo dove si era nascosto, scorrendo fino al suo posto (1.521).
            x.snapTo((if (MiniDock.tuckRight) 1f else -1f) * maxW * 0.9f)
            appear.snapTo(0.85f)
            launch { appear.animateTo(1f, Motion.lively()) }
            x.animateTo(0f, Motion.lively())
        }
        // Rientrando in pagina dopo un ritiro (anche dallo scorrimento) la barra non resta mai spostata di lato.
        LaunchedEffect(tucked) { if (tucked) { magnet = false; pull.snapTo(0f) } }

        if (tucked) {
            val guess = with(density) { 70.dp.toPx() }
            val bar = if (barH > 0) barH.toFloat() else guess
            val tabRange = (maxH - bar).coerceAtLeast(1f)
            val tabPx = with(density) { 56.dp.toPx() }
            // Trascinabile in ogni direzione (1.521, Antonio): su e giù lungo il bordo, direttamente sull'altro
            // bordo, o giù sulla X per chiuderla. Il tocco la riapre.
            val tabDx = remember { Animatable(0f) }
            val tabDy = remember { Animatable(0f) }
            var tabMagnet by remember { mutableStateOf(false) }
            var tabClosing by remember { mutableStateOf(false) }
            val tabBelow = (MiniDock.tabFrac * tabRange + tabDy.value - tabRange).coerceAtLeast(0f)
            val tabNear = (tabBelow / magnetAt).coerceIn(0f, 1f)
            MiniTab(
                now = now,
                right = MiniDock.tuckRight,
                yPx = { MiniDock.tabFrac * tabRange + bar / 2f + tabDy.value + pull.value },
                xPx = { tabDx.value },
                maxW = maxW,
                gestures = Modifier.pointerInput(maxW, tabRange, magnetAt) {
                    val tracker = VelocityTracker()
                    var travelled = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            view.haptic(Haptic.GRAB)
                            tracker.resetTracking()
                            travelled = androidx.compose.ui.geometry.Offset.Zero
                        },
                        onDragCancel = {
                            tabMagnet = false
                            scope.launch { pull.animateTo(0f, Motion.lively()) }
                            scope.launch { tabDx.animateTo(0f, Motion.gesture()) }
                            scope.launch { tabDy.animateTo(0f, Motion.gesture()) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            if (tabMagnet) {
                                tabClosing = true
                                view.haptic(Haptic.CONFIRM)
                                scope.launch {
                                    tabDy.animateTo(tabDy.value + maxH, Motion.fling(maxH, v.y.coerceAtLeast(0f)))
                                    onClose?.invoke() ?: Playback.stop()
                                    // Se qualcosa suona ancora la linguetta torna al suo posto.
                                    kotlinx.coroutines.delay(1_500)
                                    tabClosing = false
                                    tabMagnet = false
                                    pull.snapTo(0f)
                                    tabDy.snapTo(0f)
                                }
                            } else {
                                val oldLeft = if (MiniDock.tuckRight) maxW - tabPx else 0f
                                val center = oldLeft + tabPx / 2f + tabDx.value
                                val toRight = MiniTuck.tabSide(center, v.x, maxW)
                                val newLeft = if (toRight) maxW - tabPx else 0f
                                val baseY = MiniDock.tabFrac * tabRange
                                val frac = ((baseY + tabDy.value) / tabRange).coerceIn(0f, 1f)
                                // Stesso punto sullo schermo, misurato dal nuovo posto: poi scivola lì.
                                val nx = oldLeft + tabDx.value - newLeft
                                val ny = baseY + tabDy.value - frac * tabRange
                                scope.launch {
                                    tabDx.snapTo(nx)
                                    tabDy.snapTo(ny)
                                    MiniDock.tuckRight = toRight
                                    MiniDock.tabFrac = frac
                                    // Riaprendola, la barra torna dalla stessa metà dello schermo.
                                    atTop = frac < 0.5f
                                    view.haptic(Haptic.SNAP)
                                    launch { tabDx.animateTo(0f, Motion.gesture(), initialVelocity = v.x) }
                                    tabDy.animateTo(0f, Motion.gesture(), initialVelocity = v.y)
                                }
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        travelled += amount
                        tracker.addPosition(change.uptimeMillis, travelled)
                        val baseY = MiniDock.tabFrac * tabRange
                        val ny = (tabDy.value + amount.y).coerceIn(-baseY, tabRange - baseY + 2f * magnetAt)
                        val nx = tabDx.value + amount.x
                        val held = MiniTuck.magnet((baseY + ny - tabRange).coerceAtLeast(0f), magnetAt)
                        if (held != tabMagnet) {
                            tabMagnet = held
                            view.haptic(if (held) Haptic.THRESHOLD else Haptic.TICK)
                            scope.launch { pull.animateTo(if (held) MiniTuck.PULL_DP * density.density else 0f, Motion.lively()) }
                        }
                        scope.launch { tabDx.snapTo(nx) }
                        scope.launch { tabDy.snapTo(ny) }
                    }
                },
                onOpen = {
                    view.haptic(Haptic.CONFIRM)
                    reopened = true
                    MiniDock.tucked = false
                },
            )
            CloseMark(tabNear, tabMagnet, screenBelow, navInset)
            return@BoxWithConstraints
        }

        // La X rossa in fondo al centro, sopra la barra delle sezioni (il mini player si disegna sopra lo Scaffold):
        // compare man mano che lo porti giù e cresce quando la calamita lo prende.
        val below = (y.value - 1f).coerceAtLeast(0f) * range
        val near = (below / magnetAt).coerceIn(0f, 1f)

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
                            tuckArmed = false
                            scope.launch { pull.animateTo(0f, Motion.lively()) }
                            scope.launch { x.animateTo(0f, Motion.gesture()) }
                            scope.launch { y.animateTo(if (atTop) 0f else 1f, Motion.gesture()) }
                        },
                        onDragEnd = {
                            dragging = false
                            tuckArmed = false
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
                                    view.haptic(Haptic.CONFIRM)
                                    val dir = if (dx >= 0) 1f else -1f
                                    scope.launch { y.animateTo(if (atTop) 0f else 1f, Motion.gesture()) }
                                    scope.launch {
                                        // Fino al bordo, alla velocità del dito: lì salta fuori la linguetta.
                                        x.animateTo(dir * maxW * 0.9f, Motion.fling(maxW * 0.9f - kotlin.math.abs(x.value), vx))
                                        MiniDock.tuckRight = dir > 0f
                                        MiniDock.tabFrac = if (atTop) 0f else 1f
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
                        val side = SwipeAway.armed(dx, maxW)
                        if (side != tuckArmed && !magnet) {
                            tuckArmed = side
                            view.haptic(if (side) Haptic.THRESHOLD else Haptic.TICK)
                        }
                        val held = MiniTuck.magnet((curY - 1f).coerceAtLeast(0f) * range, magnetAt)
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

        if (barH > 0) CloseMark(near, magnet, screenBelow, navInset)
    }
}

/** La X rossa sul fondo dello schermo: compare man mano che lo porti giù e cresce quando la calamita lo prende. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CloseMark(near: Float, magnet: Boolean, screenBelow: Float, navInset: Float) {
    if (near <= 0f) return
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    // Il centro della X sul fondo dello schermo, appena sopra i tasti di sistema (22 dp di raggio + 6 di margine).
                    .offset { IntOffset(0, (screenBelow - navInset - 28.dp.toPx() + 22.dp.toPx()).roundToInt()) }
                    .size(44.dp)
                    .graphicsLayer {
                        val sc = 0.6f + 0.4f * near + if (magnet) 0.15f else 0f
                        scaleX = sc
                        scaleY = sc
                        alpha = near
                    }
                    .clip(CircleShape)
                    // Rossa da subito (rosso = elimina): dice che lì sotto si chiude; più piena quando la calamita lo prende.
                    .background(MaterialTheme.colorScheme.error.copy(alpha = if (magnet) 1f else 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = if (magnet) "Lascia per chiudere" else "Portalo qui per chiudere",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.graphicsLayer { rotationZ = 90f * (1f - near) },
                )
            }
}

/**
 * La linguetta sul bordo: la copertina tonda con l'anello del tempo che scorre, tutta dentro lo schermo.
 * Un tocco riapre il mini player; trascinata si sposta (lungo il bordo, sull'altro bordo o giù per chiudere).
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MiniTab(
    now: Playing,
    right: Boolean,
    yPx: () -> Float,
    xPx: () -> Float,
    maxW: Float,
    gestures: Modifier,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
    // Entra scivolando dal bordo e, riaprendo, ci rientra prima che la barra ne esca (1.521).
    val show = remember { Animatable(0f) }
    LaunchedEffect(Unit) { show.animateTo(1f, Motion.lively()) }
    val tabScope = androidx.compose.runtime.rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    val leave: () -> Unit = {
        if (!leaving) {
            leaving = true
            tabScope.launch {
                show.animateTo(0f, Motion.settle())
                open()
            }
        }
    }
    // Tonda e tutta dentro lo schermo, allineata al bordo della barra: niente mezza fuori (Antonio, 1.513).
    val tabW = 56.dp
    val tabH = 56.dp
    val shape = CircleShape
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.lively(), label = "tab")
    // I tocchi su un riquadro semplice (senza vetro né scala), il disegno dentro.
    Box(
        Modifier
            .size(tabW, tabH)
            .offset {
                val w = with(density) { tabW.toPx() }
                val h = with(density) { tabH.toPx() }
                val left = if (right) maxW - w else 0f
                IntOffset((left + xPx()).roundToInt(), (yPx() - h / 2f).roundToInt())
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "Mini player: ${now.title}. Tocca per riaprirlo"
            }
            .then(gestures)
            // Un vero clic (non solo un tocco a mano): così lo apre anche TalkBack.
            .clickable(interactionSource = press, indication = null, onClickLabel = "Riapri") { leave() },
    ) {
    Surface(
        shape = shape,
        color = Color.Transparent,
        border = cardBorder(),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Fuori dal bordo quando show = 0: scivola dentro e fuori dal suo lato.
                translationX = (1f - show.value) * size.width * 1.2f * (if (right) 1f else -1f)
                scaleX = (0.7f + 0.3f * show.value) * pressScale
                scaleY = (0.7f + 0.3f * show.value) * pressScale
                alpha = show.value.coerceIn(0f, 1f)
            }
            .glass(shape),
    ) {
        Box(contentAlignment = Alignment.Center) {
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
