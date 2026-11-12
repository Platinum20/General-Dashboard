package com.platinum20.dashboard.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.weather.WeatherCodes
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import dev.chrisbanes.haze.hazeSource

/**
 * Insets per le schermate con la barra di navigazione in basso:
 * il fondo lo gestisce già la NavigationBar, quindi qui teniamo solo alto e lati.
 */
val topLevelInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

/** Molle comuni dell'app: un piccolo rimbalzo, mai lento. */
object Springs {
    /** Movimenti grandi (bolla del player, pagine): rimbalzo visibile ma breve. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    /** Ritorni piccoli (pressione su card e pulsanti): elastico e svelto. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
}

/**
 * La card si stringe appena alla pressione e torna con una molla.
 * Osserva i tocchi senza consumarli, quindi click e scorrimento funzionano come prima.
 * Si stringe solo dopo [PressRule.DELAY_MS] fermi: chi inizia a scorrere non vede le card tremare.
 * Un tocco rapido fa comunque un piccolo cenno. Dentro una card che si stringe, reagisce solo
 * l'elemento più interno sotto il dito (la riga, non anche la card intorno).
 */
fun Modifier.bouncyPress(
    pressedScale: Float = 0.96f,
    /**
     * La scala la applica chi chiama, solo a una parte (le righe del calendario: il pallino e il testo, non la
     * linea verticale, Antonio 2026-11-11). null = si stringe tutto l'elemento.
     */
    external: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? = null,
): Modifier = this.then(BouncyMark).composed {
    val own = remember { Animatable(1f) }
    val scale = external ?: own
    val scope = rememberCoroutineScope()
    fun to(target: Float) = scope.launch { scale.animateTo(target, if (target < 1f) PressRule.press() else PressRule.release()) }
    this
        .then(if (external != null) Modifier else Modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        })
        .pointerInput(pressedScale) {
            awaitEachGesture {
                // Main va dal più interno al più esterno: il primo che vede il tocco lo prende.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                if (!PressRule.claim(down.id.value)) return@awaitEachGesture
                val slop = viewConfiguration.touchSlop
                // Final arriva dopo la lista: se lei ha preso lo scorrimento qui si vede già consumato.
                fun outcome(change: PointerInputChange?): PressRule.Outcome =
                    PressRule.outcome(
                        lifted = change == null || !change.pressed,
                        moved = change != null && (change.position - down.position).getDistance() > slop,
                        taken = change != null && change.isConsumed && change.positionChanged(),
                    )
                val early = withTimeoutOrNull(PressRule.DELAY_MS) {
                    var result = PressRule.Outcome.HOLD
                    while (result == PressRule.Outcome.HOLD) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        result = outcome(event.changes.firstOrNull { it.id == down.id })
                    }
                    result
                }
                when (early) {
                    // Tocco rapido: un cenno breve, giù e su.
                    PressRule.Outcome.UP -> scope.launch {
                        scale.animateTo(pressedScale, PressRule.press())
                        scale.animateTo(1f, PressRule.release())
                    }
                    PressRule.Outcome.CANCEL -> Unit
                    else -> {
                        to(pressedScale)
                        var result = PressRule.Outcome.HOLD
                        while (result == PressRule.Outcome.HOLD) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            result = outcome(event.changes.firstOrNull { it.id == down.id })
                        }
                        to(1f)
                    }
                }
            }
        }
}

/** Le regole della pressione, senza Compose: per i test. */
internal object PressRule {
    /** Quanto aspettare fermi prima di stringere: sotto, è l'inizio di uno scorrimento o un tocco. */
    const val DELAY_MS = 80L

    enum class Outcome { HOLD, UP, CANCEL }

    private var claimed = -1L

    /** Il primo elemento che vede questo dito lo prende; gli altri (le card intorno) restano fermi. */
    fun claim(pointer: Long): Boolean = synchronized(this) {
        if (claimed == pointer) return false
        claimed = pointer
        true
    }

    /** Il dito si è alzato ([UP]), è partito uno scorrimento ([CANCEL]) o è ancora giù fermo ([HOLD]). */
    fun outcome(lifted: Boolean, moved: Boolean, taken: Boolean): Outcome = when {
        taken || moved -> Outcome.CANCEL
        lifted -> Outcome.UP
        else -> Outcome.HOLD
    }

    /** Scende svelto, senza rimbalzo. */
    fun <T> press() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow * 2.5f)

    /** Torna con una molla appena viva: niente oscillazioni oltre la misura. */
    fun <T> release() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
}

/** Segno lasciato da [bouncyPress]: CanvasCard non aggiunge la sua pressione se c'è già. */
private object BouncyMark : Modifier.Element

/**
 * Campo da scrivere subito: per gli elementi nuovi la tastiera si apre da sola,
 * senza un tocco in più (cattura veloce). Per le modifiche resta chiusa.
 */
fun Modifier.autoFocus(enabled: Boolean = true): Modifier = composed {
    val focus = remember { FocusRequester() }
    if (enabled) {
        LaunchedEffect(Unit) {
            // Un frame di attesa: nei dialog il campo si aggancia dopo la prima composizione.
            withFrameNanos { }
            runCatching { focus.requestFocus() }
        }
    }
    focusRequester(focus)
}

/** Tipi di vibrazione dell'app: brevi e leggere, una per ogni gesto. */
enum class Haptic { TICK, CONFIRM, TOGGLE_ON, TOGGLE_OFF, THRESHOLD, GRAB, SNAP, LONG_PRESS }

/**
 * Vibrazione di sistema: segue le impostazioni del telefono (se le vibrazioni al tocco
 * sono spente, non vibra). Sulle versioni vecchie di Android usa l'effetto più vicino.
 */
fun View.haptic(kind: Haptic) {
    val api = Build.VERSION.SDK_INT
    val constant = when (kind) {
        Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.CONFIRM -> if (api >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.TOGGLE_ON -> if (api >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.CLOCK_TICK
        Haptic.TOGGLE_OFF -> if (api >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
        Haptic.THRESHOLD -> if (api >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.GRAB -> if (api >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CLOCK_TICK
        Haptic.SNAP -> if (api >= 30) HapticFeedbackConstants.GESTURE_END else HapticFeedbackConstants.CLOCK_TICK
        Haptic.LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** La funzione da chiamare nei gesti: `val haptic = rememberHaptic(); haptic(Haptic.CONFIRM)`. */
@Composable
fun rememberHaptic(): (Haptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind: Haptic -> view.haptic(kind) } }
}

/** Interruttore con la sua vibrazione: diversa per acceso e spento. */
@Composable
fun hapticToggle(onChange: (Boolean) -> Unit): (Boolean) -> Unit {
    val haptic = rememberHaptic()
    val current by rememberUpdatedState(onChange)
    return remember(haptic) { { on: Boolean -> haptic(if (on) Haptic.TOGGLE_ON else Haptic.TOGGLE_OFF); current(on) } }
}

/** Trascina per aggiornare: un piccolo scatto quando la soglia viene superata e lasciando si aggiorna. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberHapticPullState(isRefreshing: Boolean): PullToRefreshState {
    val state = rememberPullToRefreshState()
    val view = LocalView.current
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state) {
        snapshotFlow { state.distanceFraction >= 1f }
            .distinctUntilChanged()
            .collect { past -> if (past && !refreshing) view.haptic(Haptic.THRESHOLD) }
    }
    return state
}

/**
 * Il cerchio del "trascina per aggiornare" solo quando l'aggiornamento parte dal gesto:
 * i caricamenti automatici hanno già il loro piccolo indicatore dentro le card.
 */
class PullRefresh(val showing: Boolean, val start: () -> Unit)

@Composable
fun rememberPullRefresh(busy: Boolean): PullRefresh {
    var pulled by remember { mutableStateOf(false) }
    val busyNow by rememberUpdatedState(busy)
    LaunchedEffect(pulled) {
        if (!pulled) return@LaunchedEffect
        // Aspetta che il caricamento parta (se non parte, il cerchio non resta acceso) e che finisca.
        withTimeoutOrNull(2_000) { snapshotFlow { busyNow }.first { it } }
        snapshotFlow { busyNow }.first { !it }
        pulled = false
    }
    return PullRefresh(showing = pulled, start = { pulled = true })
}

/** Tocco sulla scheda già aperta della barra in basso: le sezioni tornano in cima alla lista. */
object TabReselect {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

/** Lista di una sezione principale: toccando di nuovo la sua scheda torna in cima. */
@Composable
fun rememberReselectListState(route: String, initialIndex: Int = 0): LazyListState {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(state, route) {
        TabReselect.events.collect { if (it == route) state.animateScrollToItem(0) }
    }
    return state
}

/** Apre "Cerca ovunque": nelle sezioni si trascina giù dalla cima (qui l'azione per TalkBack). */
val LocalOpenSearch = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Apre le Impostazioni della sezione: si tiene premuta la sezione nella barra (qui l'azione per TalkBack). */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Le sezioni si aggiornano da sole: all'ingresso, al ritorno nell'app e poi ogni [everyMs]
 * finché restano aperte. Trascinare dall'alto apre la ricerca, non serve più per ricaricare.
 */
@Composable
fun AutoRefresh(everyMs: Long = 5 * 60_000L, refresh: suspend () -> Unit) {
    val current by rememberUpdatedState(refresh)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            while (true) {
                current()
                kotlinx.coroutines.delay(everyMs)
            }
        }
    }
}

/**
 * Intestazione delle sezioni principali (Oggi, News, Note, Media): una riga piccola sopra
 * (la data, "8 nuove dall'ultima visita") e il titolo grande. Niente pulsanti fissi: la ricerca
 * si apre trascinando dall'alto, le Impostazioni tenendo premuta una sezione della barra in basso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelBar(
    title: String,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Per le pagine aperte da una sezione (Impostazioni): la freccia indietro. */
    onBack: (() -> Unit)? = null,
    /** Senza fondo: per le pagine con un'immagine dietro (il player). */
    transparent: Boolean = false,
    /**
     * Forza la riga sottile (con animazione). Se non lo dice la pagina, segue [scrollBehavior]
     * (exitUntilCollapsed): il titolo rimpicciolisce col dito nei primi [TopBarCollapse.RANGE_DP] dp.
     */
    collapsed: Boolean? = null,
    /** Il titolo da ridotto, se diverso (la Home: "Martedì 10"). */
    collapsedTitle: String = title,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Si restringe col dito (1.435): la barra assorbe i primi 24 dp di scorrimento, quindi il contenuto
    // segue il dito senza scatti e il titolo rimpicciolisce in modo continuo, mai di colpo.
    val rangePx = with(density) { TopBarCollapse.RANGE_DP.dp.toPx() }
    val state = scrollBehavior?.state
    if (state != null) {
        androidx.compose.runtime.SideEffect { if (state.heightOffsetLimit != -rangePx) state.heightOffsetLimit = -rangePx }
    }
    val forced by androidx.compose.animation.core.animateFloatAsState(
        if (collapsed == true) 1f else 0f, Motion.settle(), label = "barCollapse",
    )
    val fraction = when {
        collapsed != null -> forced
        state != null -> TopBarCollapse.fraction(state.heightOffset, rangePx)
        else -> 0f
    }
    val barHeight = TopBarCollapse.height(fraction).dp
    val titleScale = TopBarCollapse.titleScale(fraction)
    TopAppBar(
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            }
        },
        title = {
            // Niente pulsanti, ma chi usa TalkBack trova ricerca e impostazioni tra le azioni del titolo.
            val openSearch = LocalOpenSearch.current.takeIf { onBack == null }
            val openSettings = LocalOpenSettings.current.takeIf { onBack == null }
            // Senza rete lo dice la riga sopra il titolo (1.402), e sparisce da sola quando la rete torna.
            val context = androidx.compose.ui.platform.LocalContext.current
            remember { com.platinum20.dashboard.net.Online.init(context) }
            val online by com.platinum20.dashboard.net.Online.online.collectAsState()
            val line = com.platinum20.dashboard.net.Online.subtitle(online, subtitle)
            Column(
                Modifier.semantics {
                    customActions = listOfNotNull(
                        openSearch?.let { CustomAccessibilityAction("Cerca ovunque") { it(); true } },
                        openSettings?.let { CustomAccessibilityAction("Impostazioni") { it(); true } },
                    )
                },
            ) {
                if (line != null && fraction < 1f) {
                    // Quando cambia ("3 nuove" → "5 nuove") la riga nuova sale e la vecchia svanisce.
                    AnimatedContent(
                        line.uppercase(),
                        modifier = Modifier
                            .collapseHeight { 1f - fraction }
                            .graphicsLayer { alpha = TopBarCollapse.subtitleAlpha(fraction) },
                        transitionSpec = {
                            (slideInVertically(tween(Motion.ENTER_MS, easing = Motion.Decelerate)) { it / 2 } +
                                fadeIn(tween(Motion.ENTER_MS, easing = Motion.Decelerate)))
                                .togetherWith(fadeOut(tween(Motion.EXIT_MS, easing = Motion.Accelerate)))
                        },
                        label = "subtitle",
                    ) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Un solo titolo che rimpicciolisce (niente dissolvenza tra due testi di misure diverse).
                Box(Modifier.collapseHeight { titleScale }.graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                }) {
                    val style = MaterialTheme.typography.headlineLarge
                    if (collapsedTitle == title) {
                        Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = 1f - fraction })
                        Text(collapsedTitle, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.graphicsLayer { alpha = fraction })
                    }
                }
            }
        },
        actions = {
            actions()
            Spacer(Modifier.width(8.dp))
        },
        expandedHeight = barHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
            scrolledContainerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
        ),
    )
}

/** Altezza misurata moltiplicata per [factor], contenuto ancorato in alto: per far restringere davvero la riga. */
private fun Modifier.collapseHeight(factor: () -> Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val h = (placeable.height * factor().coerceIn(0f, 1f)).roundToInt()
        layout(placeable.width, h) { placeable.placeRelative(0, 0) }
    },
)

/**
 * Come si restringe il titolo grande: logica pura, provata nei test. [fraction] va da 0 (grande) a 1 (riga sottile)
 * e segue lo scorrimento: le pagine usano exitUntilCollapsedScrollBehavior, la barra si riapre tornando in cima.
 */
object TopBarCollapse {
    const val EXPANDED_DP = 76
    const val COLLAPSED_DP = 52
    const val RANGE_DP = EXPANDED_DP - COLLAPSED_DP

    /** Da titleLarge (19 sp) a headlineLarge (30 sp). */
    const val SMALL_SCALE = 19f / 30f

    /** [heightOffset] è negativo quando la barra si sta chiudendo (come in TopAppBarState). */
    fun fraction(heightOffset: Float, rangePx: Float): Float =
        if (rangePx <= 0f) 0f else (-heightOffset / rangePx).coerceIn(0f, 1f)

    fun height(fraction: Float): Float = EXPANDED_DP - RANGE_DP * fraction.coerceIn(0f, 1f)

    fun titleScale(fraction: Float): Float = 1f - (1f - SMALL_SCALE) * fraction.coerceIn(0f, 1f)

    /** La riga sopra svanisce nella prima metà, prima che il titolo le arrivi addosso. */
    fun subtitleAlpha(fraction: Float): Float = (1f - fraction * 2f).coerceIn(0f, 1f)
}

/** Pulsante icona tondo con il bordo sottile: azioni nelle intestazioni delle sezioni. */
@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        border = cardBorder(),
        colors = IconButtonDefaults.outlinedIconButtonColors(containerColor = MaterialTheme.colorScheme.card),
        modifier = Modifier.padding(horizontal = 2.dp).size(40.dp).bouncyPress(0.9f),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/**
 * Le pagine come sorgente del vetro: da Android 13 per il vetro liquido, prima per Haze.
 */
fun Modifier.glassSource(hazeState: HazeState, backdrop: Backdrop): Modifier =
    if (Build.VERSION.SDK_INT >= 33) backdropSource(backdrop) else hazeSource(hazeState)

/**
 * Il contenuto delle pagine, da sfocare sotto gli elementi che galleggiano (vetro).
 * Lo fornisce MainActivity attorno alle schermate; null nelle anteprime e nelle activity a parte.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Vetro sfocato: la pagina sotto si intravede, sfocata e velata del colore delle card.
 * Velo leggero perché sotto si veda davvero la pagina. Da Android 12 la sfocatura è vera
 * (Haze di suo la accende solo da Android 13); prima resta un velo semitrasparente senza sfocatura.
 */
fun Modifier.glass(shape: Shape, color: Color? = null): Modifier = composed {
    val state = LocalHazeState.current
    // [color]: vetro colorato (il + della barra); altrimenti il velo del colore delle card.
    val card = color ?: MaterialTheme.colorScheme.card
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backdrop = LocalBackdrop.current
    val tint = card.copy(alpha = if (color != null) 0.78f else if (dark) 0.42f else 0.38f)
    if (backdrop != null && Build.VERSION.SDK_INT >= 33) {
        // Da Android 13 il vetro è "liquido": oltre a sfocare, piega la pagina sui bordi. Velo più leggero: si vede sotto.
        this.clip(shape).liquidGlass(backdrop, shape, card.copy(alpha = if (color != null) 0.82f else if (dark) 0.40f else 0.34f))
    } else if (state == null) {
        this.clip(shape).background(card)
    } else {
        this.clip(shape).hazeEffect(
            state,
            HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(tint),
                blurRadius = 14.dp,
                noiseFactor = 0.04f,
                fallbackTint = HazeTint(card.copy(alpha = if (color != null) 0.95f else 0.82f)),
            ),
        ) { blurEnabled = Build.VERSION.SDK_INT >= 31 }
    }
}

/** Colori delle icone nei quadratini (griglia Media, righe delle Impostazioni): chiaro e scuro. */
enum class TileAccent(val light: Long, val lightBg: Long, val dark: Long, val darkBg: Long) {
    BLUE(0xFF3550E8, 0xFFE3E8FF, 0xFF9AABFF, 0xFF252B4A),
    ORANGE(0xFFD9572B, 0xFFFCE5DA, 0xFFFF8F66, 0xFF3A241B),
    GREEN(0xFF1E8A57, 0xFFDDF3E7, 0xFF5FD39B, 0xFF1F3A2C),
    PURPLE(0xFF7A44C2, 0xFFEFE4FB, 0xFFD7A6FF, 0xFF33283F),
    NEUTRAL(0xFF5E5A52, 0xFFECE8DF, 0xFFC9C4BA, 0xFF2E2C28),
}

/**
 * Icona dentro un quadratino (1.402, Antonio): sempre inchiostro sul beige, qualunque [accent]. I colori
 * diversi per riga non volevano dire niente ed erano rumore; il colore resta solo per gli stati.
 */
@Composable
fun IconTile(icon: ImageVector, @Suppress("UNUSED_PARAMETER") accent: TileAccent, size: Dp = 40.dp, iconSize: Dp = 22.dp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val neutral = TileAccent.NEUTRAL
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(if (dark) neutral.darkBg else neutral.lightBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(iconSize))
    }
}

/** Il fondo delle card: bianco sulla carta, un grigio caldo appena più chiaro nel tema scuro. */
val ColorScheme.card: Color
    get() = if (background.luminance() < 0.5f) surfaceContainerLow else surfaceContainerLowest

/** Il bordo sottile che separa le card dallo sfondo, al posto dell'ombra. */
@Composable
fun cardBorder(): BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

/**
 * La card dell'app: fondo pieno, bordo sottile, nessuna ombra.
 * Con [colors] colorati (avvisi, evidenze) il bordo sparisce.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors? = null,
    /** Tenendo premuto: di solito [com.platinum20.dashboard.capture.SavedLinks.offer], "Salva nelle note". */
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val border = if (colors == null) cardBorder() else null
    // Ogni card entra a cascata con la pagina, sempre già alla sua grandezza: niente crescere o
    // rimpicciolire mentre arrivano i dati (Antonio le vuole ferme).
    // Le card che si toccano rispondono al dito, anche dove la schermata non l'ha chiesto.
    val press = if ((onClick != null || onLongClick != null) && !modifier.any { it === BouncyMark }) Modifier.bouncyPress(0.98f) else Modifier
    val modifier = modifier.entrance().then(press)
    if (onLongClick != null) {
        Card(
            modifier = modifier.clip(shape).combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick),
            shape = shape,
            colors = cardColors,
            border = border,
            content = content,
        )
    } else if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = cardColors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = cardColors, border = border, content = content)
    }
}

/** Etichetta di gruppo in maiuscoletto ("DA FARE · 3"), con un'azione testuale a destra. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.entrance().fillMaxWidth().heightIn(min = 36.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .bouncyPress(0.92f)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Un'etichetta che apre una pagina (1.361): inchiostro e freccia accanto (1.402: il blu è solo per "adesso"
 * e le azioni, la freccia dice già "si apre"), così si distingue da un'etichetta grigia come "Continua".
 */
@Composable
fun OpenableHeader(title: String, onOpen: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val haptic = rememberHaptic()
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .entrance()
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { haptic(Haptic.TICK); onOpen() }
            .heightIn(min = 36.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Niente peso sul titolo: il peso sta tutto nello spazio prima del tastino, che così va al bordo destro (1.493).
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = "Apri",
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        // A destra un tastino (Scopri in Media, 1.492): il titolo si apre lo stesso.
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Un blocco di Oggi: l'etichetta fuori, il contenuto in una card. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, action = action, onAction = onAction, trailing = trailing)
        Spacer(Modifier.height(4.dp))
        CanvasCard(Modifier.fillMaxWidth(), content = content)
    }
}

/**
 * Il titolo dentro una card della Home (1.404, punto 7 B): niente etichetta maiuscola fuori, la card è un
 * oggetto a sé col suo nome in alto. A destra un valore, se c'è (il totale del mese nel Calendario).
 */
@Composable
fun CardTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 16.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

/** Una card della Home col titolo dentro ([CardTitle]) invece dell'etichetta fuori (1.404). */
@Composable
fun CardSection(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    CanvasCard(modifier.fillMaxWidth(), onClick = onClick) {
        CardTitle(title, trailing = trailing)
        content()
    }
}

/** Campi di testo sulla carta: fondo della card e bordo sottile, l'accento solo quando si scrive. */
@Composable
fun canvasFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.card,
    focusedContainerColor = MaterialTheme.colorScheme.card,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    // I segnaposto nel grigio del testo (1.402): quello dei bordi era sotto il contrasto minimo, al sole non si leggeva.
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Una riga di un elenco lungo (LazyColumn) disegnata come parte di un'unica card (1.423, punto 8 A): la prima ha gli
 * angoli in alto, l'ultima in basso, in mezzo un separatore come [RowDivider]. Le righe vanno messe senza spazio fra loro.
 */
@Composable
fun Modifier.cardGroupItem(first: Boolean, last: Boolean, container: Color = MaterialTheme.colorScheme.card): Modifier {
    val border = MaterialTheme.colorScheme.outlineVariant
    val radius = 18.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (first) radius else 0.dp, topEnd = if (first) radius else 0.dp,
        bottomStart = if (last) radius else 0.dp, bottomEnd = if (last) radius else 0.dp,
    )
    return this.clip(shape).background(container).drawWithContent {
        drawContent()
        val sw = 1.dp.toPx()
        val r = androidx.compose.ui.geometry.CornerRadius(radius.toPx())
        val zero = androidx.compose.ui.geometry.CornerRadius.Zero
        // I lati in alto e in basso delle righe di mezzo cadono fuori: si vede un bordo solo, intorno a tutto.
        val rect = androidx.compose.ui.geometry.RoundRect(
            left = sw / 2, top = if (first) sw / 2 else -sw * 2,
            right = size.width - sw / 2, bottom = if (last) size.height - sw / 2 else size.height + sw * 2,
            topLeftCornerRadius = if (first) r else zero, topRightCornerRadius = if (first) r else zero,
            bottomRightCornerRadius = if (last) r else zero, bottomLeftCornerRadius = if (last) r else zero,
        )
        val path = androidx.compose.ui.graphics.Path().apply { addRoundRect(rect) }
        drawPath(path, border, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        if (!first) drawLine(border, Offset(16.dp.toPx(), sw / 2), Offset(size.width - 16.dp.toPx(), sw / 2), sw)
    }
}

/** Separatore tra le righe di una card. */
@Composable
fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** Chip di filtro: inchiostro pieno quando è scelto, bordo sottile altrimenti. */
@Composable
fun CanvasChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.card,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = MaterialTheme.colorScheme.surface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
        modifier = modifier.bouncyPress(0.94f),
    )
}

/**
 * Come [CanvasChip] ma prende tutta la larghezza che le danno (schede in parti uguali fino al bordo):
 * testo centrato che si rimpicciolisce quanto basta per stare su una riga, così non si scorre di lato.
 */
@Composable
fun CanvasTabChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    /**
     * Scelta tenue (accento chiaro invece dell'inchiostro pieno): per i filtri che non devono rubare
     * l'occhio a quello che filtrano (il segno OGGI del Calendario).
     */
    tonal: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val base = MaterialTheme.typography.labelLarge
    var style by remember(label, base) { mutableStateOf(base) }
    val on = if (tonal) colors.primary.copy(alpha = 0.14f).compositeOver(colors.card) else colors.onSurface
    val container by androidx.compose.animation.animateColorAsState(if (selected) on else colors.card, Motion.settle(), label = "chip")
    val content by androidx.compose.animation.animateColorAsState(
        if (!selected) colors.onSurfaceVariant else if (tonal) colors.primary else colors.surface, Motion.settle(), label = "chipText",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = when {
            selected && tonal -> BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
            selected -> null
            else -> BorderStroke(1.dp, colors.outlineVariant)
        },
        modifier = modifier.height(32.dp).bouncyPress(0.94f),
    ) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = style,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = { layout ->
                    if (layout.hasVisualOverflow && style.fontSize.value > 10f) style = style.copy(fontSize = (style.fontSize.value - 0.5f).sp)
                },
            )
        }
    }
}

/**
 * Il + della barra in basso apre la cattura veloce. Una sezione può sostituirlo con la sua azione
 * (in Note: la scheda aperta decide cosa aggiungere) finché resta a schermo.
 */
object PlusAction {
    val override = mutableStateOf<(() -> Unit)?>(null)
}

@Composable
fun ProvidePlusAction(action: (() -> Unit)?) {
    val current by rememberUpdatedState(action)
    DisposableEffect(action != null) {
        val handler: (() -> Unit)? = if (action != null) ({ current?.invoke() }) else null
        PlusAction.override.value = handler
        onDispose { if (PlusAction.override.value === handler) PlusAction.override.value = null }
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormatter = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ITALIAN)
private val yearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ITALIAN)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEE", java.util.Locale.ITALIAN)

/** Tempo trascorso a parole: "5 min fa", "3 h fa", poi "ieri 14:30", "lun 9:15", "12 ott". */
fun relativeTime(instant: Instant): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    if (minutes < 1) return "adesso"
    if (minutes < 60) return "$minutes min fa"
    val zone = ZoneId.systemDefault()
    val then = instant.atZone(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(then.toLocalDate(), java.time.LocalDate.now(zone))
    return when {
        minutes < 24 * 60 && days == 0L -> "${minutes / 60} h fa"
        days == 1L -> "ieri " + timeFormatter.format(then)
        days in 2..6 -> weekdayFormatter.format(then) + " " + timeFormatter.format(then)
        then.year == java.time.LocalDate.now(zone).year -> dayFormatter.format(then)
        else -> yearFormatter.format(then)
    }
}

fun weatherIcon(code: Int, isDay: Boolean = true): ImageVector = when (WeatherCodes.kind(code)) {
    WeatherCodes.Kind.CLEAR -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    WeatherCodes.Kind.PARTLY -> if (isDay) Icons.Outlined.WbCloudy else Icons.Outlined.Cloud
    WeatherCodes.Kind.CLOUDY, WeatherCodes.Kind.FOG -> Icons.Outlined.Cloud
    WeatherCodes.Kind.DRIZZLE -> Icons.Outlined.Grain
    WeatherCodes.Kind.RAIN -> Icons.Outlined.Umbrella
    WeatherCodes.Kind.SNOW -> Icons.Outlined.AcUnit
    WeatherCodes.Kind.STORM -> Icons.Outlined.Thunderstorm
}

/**
 * Il posto di un contenuto che sta arrivando: una forma grigia ferma, già della sua grandezza, al posto
 * della rotella. Quando arrivano i dati la card non cambia altezza (Antonio le vuole ferme).
 */
@Composable
fun Placeholder(width: Dp, height: Dp, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.extraSmall) {
    val tint = androidx.compose.material3.LocalContentColor.current
    Box(modifier.size(width, height).clip(shape).background(tint.copy(alpha = 0.1f)))
}

/** Come [Placeholder] ma lungo quanto lo spazio che ha (una riga di testo che arriverà). */
@Composable
fun PlaceholderLine(fraction: Float, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    val tint = androidx.compose.material3.LocalContentColor.current
    Box(modifier.fillMaxWidth(fraction).height(height).clip(MaterialTheme.shapes.extraSmall).background(tint.copy(alpha = 0.1f)))
}

/**
 * Una lista che sta arrivando (notizie, video): righe grigie ferme al posto della rotella in mezzo
 * allo schermo, così l'occhio è già dove compariranno le cose. [thumb] = con la miniatura a sinistra.
 */
@Composable
fun PlaceholderList(modifier: Modifier = Modifier, rows: Int = 6, thumb: Boolean = true, label: String = "Carico…") {
    val widths = listOf(0.86f, 0.7f, 0.78f, 0.62f, 0.82f, 0.68f)
    // Senza rete le righe grigie resterebbero lì per sempre: lo dice una riga sopra (1.402).
    val online by com.platinum20.dashboard.net.Online.online.collectAsState()
    if (!online) {
        Text(
            "Senza rete: riprovo quando torna",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
    ) {
        repeat(rows) { i ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                if (thumb) {
                    Placeholder(96.dp, 56.dp, shape = MaterialTheme.shapes.small)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
                    PlaceholderLine(widths[i % widths.size], 14.dp)
                    PlaceholderLine(widths[(i + 3) % widths.size] * 0.55f, 10.dp)
                }
            }
        }
    }
}

/**
 * Un numero che cambia (giga, minuti): scorrono in verticale solo le cifre cambiate, le altre
 * restano ferme (1.357). Le cifre si contano da destra, così "9,90 €" → "10,40 €" non fa scorrere tutto.
 */
@Composable
fun RollingText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = text }) {
        text.forEachIndexed { i, c ->
            androidx.compose.runtime.key(text.length - i) {
                AnimatedContent(
                    targetState = c,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(Motion.settle()) { if (up) it else -it } + fadeIn(Motion.settle())) togetherWith
                            (androidx.compose.animation.slideOutVertically(Motion.settle()) { if (up) -it else it } + fadeOut(Motion.settle()))
                    },
                    label = "roll",
                ) { ch ->
                    Text(ch.toString(), style = style, fontWeight = fontWeight, color = color, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Al posto della rotella per le attese piccole (dentro un tasto, sopra una copertina): un punto che pulsa
 * piano (1.357). Le liste che arrivano usano invece le forme grigie ([PlaceholderList]).
 */
@Composable
fun LoadingPulse(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: androidx.compose.ui.unit.Dp = 10.dp) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(700, easing = Motion.Standard),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = 0.4f + 0.6f * scale }
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = "Caricamento" },
    )
}

/**
 * I tre livelli di pulsante (1.402, Antonio): [PRIMARY] pieno blu = l'azione principale, una per pagina o
 * dialogo ("Salva", "Collega Spotify"); [TONAL] = azioni di riga ("Accedi", "Condividi"); [DANGER] = testo
 * rosso, per quello che toglie qualcosa ("Esci", "Elimina"). Niente link blu nelle righe.
 */
enum class ButtonLevel { PRIMARY, TONAL, DANGER }

/**
 * Il bottone di Canvas (1.357): una pillola tonale per le azioni dentro card e pagine ("Riprova", "Accedi",
 * "Guarda il video"). [primary] = pieno, solo per l'unica azione della pagina; [level] per i tre livelli.
 * Prima c'erano quattro tipi di bottone diversi per la stessa cosa.
 */
@Composable
fun CanvasButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    primary: Boolean = false,
    level: ButtonLevel = if (primary) ButtonLevel.PRIMARY else ButtonLevel.TONAL,
    enabled: Boolean = true,
) {
    val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        icon?.let {
            androidx.compose.material3.Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1)
    }
    when (level) {
        ButtonLevel.PRIMARY ->
            androidx.compose.material3.Button(onClick = onClick, enabled = enabled, modifier = modifier.bouncyPress(), shape = CircleShape, content = content)
        ButtonLevel.TONAL ->
            androidx.compose.material3.FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.bouncyPress(), shape = CircleShape, content = content)
        ButtonLevel.DANGER ->
            androidx.compose.material3.TextButton(
                onClick = onClick,
                enabled = enabled,
                modifier = modifier.bouncyPress(),
                shape = CircleShape,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                content = content,
            )
    }
}

/** La conferma di un dialogo ("Salva", "Collega"): piena, è l'azione principale; [danger] rossa ("Cancella"). */
@Composable
fun ConfirmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.bouncyPress(),
    shape = CircleShape,
    colors = if (danger) {
        androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        androidx.compose.material3.ButtonDefaults.buttonColors()
    },
    content = content,
)

/** L'alternativa di un dialogo ("Annulla", "OK" dei dialoghi che informano): solo testo, in inchiostro. */
@Composable
fun DismissButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = androidx.compose.material3.TextButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier,
    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
        contentColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    ),
    content = content,
)

/** Un'azione di riga che toglie qualcosa ("Esci"): testo rosso, il livello più basso dei tre. */
@Composable
fun DangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) = DismissButton(onClick = onClick, modifier = modifier, enabled = enabled, danger = true, content = content)
