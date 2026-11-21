package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
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
 * podcast): dal 1.546 un riquadro 16:9 (circa il doppio del video di prima) con dentro il video o la
 * copertina e i tasti, uguale per tutte le fonti. Si trascina e si aggancia a uno dei quattro angoli
 * (in alto sotto il titolo, in basso sopra la barra delle sezioni); un tocco apre il player grande.
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
        val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // Il riquadro (1.546): misure fisse, 16:9.
        val boxW = with(density) { MiniTuck.BOX_W.dp.toPx() }
        val barH = with(density) { MiniTuck.BOX_H.dp.toPx() }
        val edgePad = with(density) { 16.dp.toPx() }
        SideEffect { MiniDock.barHeight = barH }
        // In alto sta sotto il titolo grande delle pagine.
        val topGap = with(density) { MiniTuck.TOP_GAP.dp.toPx() }.coerceAtMost((maxH - barH).coerceAtLeast(0f))
        val range = (maxH - barH - topGap).coerceAtLeast(1f)
        val magnetAt = MiniTuck.threshold(barH / 2f + screenBelow, density.density)
        val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(density).toFloat()
        // L'angolo: in alto o in basso, a destra o a sinistra; ricordato anche ruotando lo schermo. In basso a destra all'inizio.
        var atTop by rememberSaveable { mutableStateOf(MiniDock.atTop) }
        var atRight by rememberSaveable { mutableStateOf(MiniDock.atRight) }
        SideEffect { MiniDock.atRight = atRight }
        val base = if (atRight) maxW - boxW else 0f
        val y = remember { Animatable(if (atTop) 0f else 1f) }
        LaunchedEffect(atTop) {
            MiniDock.atTop = atTop
            y.animateTo(if (atTop) 0f else 1f, Motion.gesture())
        }
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        var dragging by remember { mutableStateOf(false) }
        // Comparsa: il riquadro "salta fuori" invece di apparire di colpo.
        // Arrivando dal player grande invece è già lì: il video ci è appena volato dentro.
        val appear = remember { Animatable(if (MiniDock.docking) 1f else 0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, Motion.lively()) }
        val pressScale by animateFloatAsState(if (dragging) 0.97f else 1f, Motion.lively(), label = "drag")
        // Spostamento dal suo angolo, in px.
        val x = remember { Animatable(0f) }
        // La calamita in fondo: quando lo prende scatta un po' giù da solo.
        var magnet by remember { mutableStateOf(false) }
        // Portato oltre il bordo: lasciandolo va nella linguetta (vibra come la calamita).
        var tuckArmed by remember { mutableStateOf(false) }
        val pull = remember { Animatable(0f) }
        var closing by remember { mutableStateOf(false) }
        val tucked = MiniDock.tucked
        // Se dopo la chiusura qualcosa suona ancora (es. Spotify non risponde) il riquadro rientra.
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
        // Riaperto dalla linguetta: esce dal suo bordo, nell'angolo di quel lato.
        // Solo [tucked] come chiave: con altre chiavi l'effetto si riavviava a metà e il riquadro restava mezzo fuori.
        var reopened by remember { mutableStateOf(false) }
        LaunchedEffect(tucked) {
            if (tucked || !reopened) return@LaunchedEffect
            reopened = false
            val right = MiniDock.tuckRight
            atRight = right
            x.snapTo(if (right) boxW + edgePad else -boxW - edgePad)
            appear.snapTo(0.85f)
            launch { appear.animateTo(1f, Motion.lively()) }
            x.animateTo(0f, Motion.lively())
        }
        // Rientrando in pagina dopo un ritiro il riquadro non resta mai spostato di lato.
        LaunchedEffect(tucked) { if (tucked) { magnet = false; pull.snapTo(0f) } }

        if (tucked) {
            val bar = barH
            val tabRange = range
            // Trascinabile (1.521): su e giù lungo il bordo, giù sulla X per chiuderla; tirata verso il centro
            // fa uscire il riquadro con una vibrazione (1.546, Antonio). Anche il tocco la riapre.
            val tabDx = remember { Animatable(0f) }
            val tabDy = remember { Animatable(0f) }
            var tabMagnet by remember { mutableStateOf(false) }
            var tabArmed by remember { mutableStateOf(false) }
            var tabClosing by remember { mutableStateOf(false) }
            val tabBelow = (MiniDock.tabFrac * tabRange + tabDy.value - tabRange).coerceAtLeast(0f)
            val tabNear = (tabBelow / magnetAt).coerceIn(0f, 1f)
            val tabPx = with(density) { MiniTuck.TAB_W.dp.toPx() }
            MiniTab(
                now = now,
                right = MiniDock.tuckRight,
                yPx = { topGap + MiniDock.tabFrac * tabRange + bar / 2f + tabDy.value + pull.value },
                xPx = { tabDx.value },
                maxW = maxW,
                height = MiniTuck.BOX_H.dp,
                gestures = Modifier.pointerInput(maxW, tabRange, magnetAt) {
                    val tracker = VelocityTracker()
                    var travelled = androidx.compose.ui.geometry.Offset.Zero
                    val inward = { if (MiniDock.tuckRight) -tabDx.value else tabDx.value }
                    detectDragGestures(
                        onDragStart = {
                            view.haptic(Haptic.GRAB)
                            tracker.resetTracking()
                            travelled = androidx.compose.ui.geometry.Offset.Zero
                        },
                        onDragCancel = {
                            tabMagnet = false
                            tabArmed = false
                            scope.launch { pull.animateTo(0f, Motion.lively()) }
                            scope.launch { tabDx.animateTo(0f, Motion.gesture()) }
                            scope.launch { tabDy.animateTo(0f, Motion.gesture()) }
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            tabArmed = false
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
                            } else if (MiniTuck.pulledOut(inward(), density.density)) {
                                // Tirata fuori: il riquadro esce dal suo lato, nell'angolo più vicino in verticale.
                                val baseY = MiniDock.tabFrac * tabRange
                                val frac = MiniTuck.tabCorner((baseY + tabDy.value) / tabRange, v.y / tabRange)
                                MiniDock.tabFrac = frac
                                atTop = frac < 0.5f
                                view.haptic(Haptic.CONFIRM)
                                reopened = true
                                MiniDock.tucked = false
                            } else {
                                val oldLeft = if (MiniDock.tuckRight) maxW - tabPx else 0f
                                val center = oldLeft + tabPx / 2f + tabDx.value
                                val toRight = MiniTuck.tabSide(center, v.x, maxW)
                                val newLeft = if (toRight) maxW - tabPx else 0f
                                val baseY = MiniDock.tabFrac * tabRange
                                // Solo in alto o in basso, come il riquadro (1.522, Antonio): niente posizioni a metà bordo.
                                val frac = MiniTuck.tabCorner((baseY + tabDy.value) / tabRange, v.y / tabRange)
                                // Stesso punto sullo schermo, misurato dal nuovo posto: poi scivola lì.
                                val nx = oldLeft + tabDx.value - newLeft
                                val ny = baseY + tabDy.value - frac * tabRange
                                scope.launch {
                                    tabDx.snapTo(nx)
                                    tabDy.snapTo(ny)
                                    MiniDock.tuckRight = toRight
                                    MiniDock.tabFrac = frac
                                    // Riaprendola, il riquadro torna dalla stessa metà dello schermo.
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
                        // Tirata abbastanza verso il centro: la piccola vibrazione dice che lasciandola esce.
                        val armed = !held && MiniTuck.pulledOut(if (MiniDock.tuckRight) -nx else nx, density.density)
                        if (armed != tabArmed) {
                            tabArmed = armed
                            view.haptic(if (armed) Haptic.THRESHOLD else Haptic.TICK)
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
        val shape = RoundedCornerShape(16.dp)

        Surface(
            shape = shape,
            color = Color.Transparent,
            border = cardBorder(),
            modifier = Modifier
                .size(MiniTuck.BOX_W.dp, MiniTuck.BOX_H.dp)
                .offset { IntOffset((base + x.value).roundToInt(), (topGap + y.value * range + pull.value).roundToInt()) }
                .graphicsLayer {
                    val sc = (0.8f + 0.2f * appear.value) * pressScale * (1f - 0.12f * (pull.value / (MiniTuck.PULL_DP * density.density)).coerceIn(0f, 1f))
                    scaleX = sc
                    scaleY = sc
                    // Sbiadisce uscendo dallo schermo di lato.
                    alpha = (if (MiniDock.docking) 0f else 1f) * appear.value.coerceIn(0f, 1f) *
                        (1f - 0.6f * MiniTuck.offscreen(base + x.value, boxW, maxW))
                }
                .clip(shape)
                .pointerInput(range, maxW, atRight) {
                    val tracker = VelocityTracker()
                    var curY = 0f
                    var start = 0f
                    var dx = 0f
                    // Il dito si misura sullo schermo, non sul riquadro (che si muove con lui):
                    // sul riquadro la velocità risulterebbe sempre quasi zero e il lancio non partirebbe.
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
                            val oldBase = if (atRight) maxW - boxW else 0f
                            when (val drop = MiniTuck.drop(magnet, oldBase + dx, boxW, maxW, vx, fromRight = atRight)) {
                                MiniTuck.Drop.CLOSE -> {
                                    closing = true
                                    view.haptic(Haptic.CONFIRM)
                                    // Giù, fuori dallo schermo, tirato dalla calamita.
                                    val out = 1f + (maxH + barH) / range
                                    scope.launch {
                                        y.animateTo(out, Motion.fling((out - y.value) * range, velocity.y.coerceAtLeast(0f)))
                                        onClose?.invoke() ?: Playback.stop()
                                    }
                                }
                                MiniTuck.Drop.TUCK_LEFT, MiniTuck.Drop.TUCK_RIGHT -> {
                                    view.haptic(Haptic.CONFIRM)
                                    val right = drop == MiniTuck.Drop.TUCK_RIGHT
                                    scope.launch { y.animateTo(if (atTop) 0f else 1f, Motion.gesture()) }
                                    scope.launch {
                                        // Fuori dal bordo, alla velocità del dito: lì salta fuori la linguetta.
                                        val target = (if (right) maxW + edgePad else -boxW - edgePad) - oldBase
                                        x.animateTo(target, Motion.fling(kotlin.math.abs(target - x.value), vx))
                                        MiniDock.tuckRight = right
                                        MiniDock.tabFrac = if (atTop) 0f else 1f
                                        MiniDock.tucked = true
                                    }
                                }
                                MiniTuck.Drop.LEFT, MiniTuck.Drop.RIGHT -> {
                                    val right = drop == MiniTuck.Drop.RIGHT
                                    val newBase = if (right) maxW - boxW else 0f
                                    scope.launch {
                                        // Stesso punto sullo schermo, misurato dal nuovo angolo: poi ci scivola.
                                        x.snapTo(oldBase + x.value - newBase)
                                        atRight = right
                                        x.animateTo(0f, Motion.gesture(), initialVelocity = vx)
                                    }
                                    // In alto o in basso: uno scatto o un piccolo spostamento verso l'altro lato basta per mandarlo lì.
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
                        // Di lato e su e giù insieme: si decide solo lasciandolo.
                        dx += amount.x
                        curY = (curY + amount.y / range).coerceIn(-0.03f, bottom)
                        val side = MiniTuck.offEdge((if (atRight) maxW - boxW else 0f) + dx, boxW, maxW) != 0
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

        CloseMark(near, magnet, screenBelow, navInset)
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
 * La linguetta sul bordo (1.546): sottile, alta quanto il riquadro, sul filo del bordo, con uno
 * spicchio della copertina. Un tocco o tirarla verso il centro riapre il riquadro; trascinata su e giù cambia
 * angolo, giù sulla X chiude.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.MiniTab(
    now: Playing,
    right: Boolean,
    yPx: () -> Float,
    xPx: () -> Float,
    maxW: Float,
    height: androidx.compose.ui.unit.Dp,
    gestures: Modifier,
    onOpen: () -> Unit,
) {
    val density = LocalDensity.current
    val open by androidx.compose.runtime.rememberUpdatedState(onOpen)
    // Entra scivolando dal bordo e, riaprendo, ci rientra prima che il riquadro ne esca (1.521).
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
    val tabW = MiniTuck.TAB_W.dp
    // Tutta dentro lo schermo, sul filo del riquadro (Antonio, 1.513: mai mezza fuori). Fuori dal suo
    // contenitore non riceverebbe i tocchi.
    val shape = RoundedCornerShape(12.dp)
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.92f else 1f, Motion.lively(), label = "tab")
    // I tocchi su un riquadro semplice (senza scala), il disegno dentro.
    Box(
        Modifier
            .size(tabW, height)
            .offset {
                val w = with(density) { tabW.toPx() }
                val h = with(density) { height.toPx() }
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
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Fuori dal bordo quando show = 0: scivola dentro e fuori dal suo lato.
                    translationX = (1f - show.value) * size.width * 1.2f * (if (right) 1f else -1f)
                    scaleY = (0.85f + 0.15f * show.value) * pressScale
                    alpha = show.value.coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
            else if (now.artwork.isNotBlank()) {
                AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            // La maniglia: dice che si tira. Blu mentre suona (blu = adesso).
            Box(
                Modifier
                    .size(width = 3.dp, height = 26.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (now.playing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)),
            )
        }
    }
}

/**
 * Il contenuto del riquadro (1.546, Antonio): lo stesso per tutto. Il video lo riempie; con musica, radio e
 * podcast c'è la copertina nitida su sé stessa sfocata, con titolo. I tre tasti stanno sempre nello stesso
 * punto, in basso al centro: sul video compaiono al tocco e spariscono dopo [MiniTuck.CONTROLS_MS].
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MiniPlayer(now: Playing, onExpand: () -> Unit) {
    // Il tocco apre sempre quello che suona adesso: senza questo il gesto restava legato
    // alla prima cosa partita (es. un video chiuso) anche dopo aver messo una canzone.
    val expand by androidx.compose.runtime.rememberUpdatedState(onExpand)
    val always = MiniTuck.controlsAlways(now.isVideo, now.playing)
    var asked by remember { mutableStateOf(false) }
    // Ogni tocco su un tasto fa ripartire il conto alla rovescia.
    var poke by remember { mutableIntStateOf(0) }
    val controls = always || asked
    LaunchedEffect(asked, poke, always) {
        if (asked && !always) {
            kotlinx.coroutines.delay(MiniTuck.CONTROLS_MS)
            asked = false
        }
    }
    val shownState = androidx.compose.runtime.rememberUpdatedState(controls)
    val videoState = androidx.compose.runtime.rememberUpdatedState(now.isVideo)
    Box(
        Modifier
            .fillMaxSize()
            .background(if (now.isVideo) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    when (MiniTuck.tap(videoState.value, shownState.value)) {
                        MiniTuck.Tap.SHOW_CONTROLS -> { asked = true; poke++ }
                        MiniTuck.Tap.EXPAND -> expand()
                    }
                })
            },
    ) {
        if (now.isVideo) {
            // Mentre il video vola qui dal player grande lo schermo resta a lui: si aggancia all'arrivo.
            if (!MiniDock.docking) {
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
            }
        } else {
            // Sfondo: la copertina sfocata (la sfocatura c'è da Android 12; prima la copertina scurita).
            if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
            else if (now.artwork.isNotBlank()) {
                AsyncImage(
                    model = now.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(24.dp),
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
            // La copertina nitida in alto a sinistra, il titolo accanto.
            Box(
                Modifier
                    .padding(start = 10.dp, top = 10.dp)
                    .size(50.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sourceIcon(now.source), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (now.source == PlaySource.SPEECH) SpeechCover(Modifier.fillMaxSize())
                else if (now.artwork.isNotBlank()) {
                    AsyncImage(model = now.artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.padding(start = 70.dp, top = 12.dp, end = 10.dp)) {
                Text(now.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(now.source.label, now.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (now.error != null) MaterialTheme.colorScheme.errorContainer else Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val fade by animateFloatAsState(if (controls) 1f else 0f, Motion.settle(), label = "tasti")
        if (fade > 0.01f) {
            // Il velo scuro in basso, perché i tasti bianchi si vedano su qualunque immagine.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.62f)
                    .graphicsLayer { alpha = fade }
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
            )
            if (now.isVideo) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.45f)
                        .graphicsLayer { alpha = fade }
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
                )
                Text(
                    now.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (now.error != null) MaterialTheme.colorScheme.errorContainer else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 10.dp, vertical = 8.dp).graphicsLayer { alpha = fade },
                )
            }
            // Indietro, play/pausa, avanti (nella coda, per tutte le fonti); niente X: si chiude trascinandolo.
            // In diretta (radio) avanti e indietro servono solo per muoversi nella coda.
            val queue by com.platinum20.dashboard.media.CanvasPlayer.queue.collectAsState()
            val skips = !now.live || queue.hasNext || queue.hasPrevious
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).graphicsLayer { alpha = fade },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (skips) {
                    IconButton(onClick = { poke++; Playback.previous() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Indietro", tint = Color.White)
                    }
                }
                IconButton(
                    onClick = { poke++; Playback.toggle() },
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Icon(
                        if (now.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (now.playing) "Pausa" else "Riproduci",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
                if (skips) {
                    IconButton(onClick = { poke++; Playback.next() }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Avanti", tint = Color.White)
                    }
                }
            }
        }
        // Il tempo che scorre, sottile sul bordo basso, sempre; in diretta niente barra. Resta (a zero) anche
        // mentre il brano dopo si carica, così dopo uno skip non sparisce.
        if (!now.live) {
            val position = livePosition(now)
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (now.durationMs > 0) (position.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
