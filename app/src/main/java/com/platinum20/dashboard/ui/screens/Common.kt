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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
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
 */
fun Modifier.bouncyPress(pressedScale: Float = 0.96f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Springs.snappy(), label = "press")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                // Se parte uno scorrimento l'evento viene consumato e la card torna subito com'era.
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
}

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
fun rememberReselectListState(route: String): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(state, route) {
        TabReselect.events.collect { if (it == route) state.animateScrollToItem(0) }
    }
    return state
}

/** Apre le Impostazioni: le sezioni principali la usano per l'icona in alto a destra. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Apre "Cerca ovunque": la lente sta in alto in ogni sezione, sempre nello stesso posto. */
val LocalOpenSearch = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Intestazione delle sezioni principali (Oggi, News, Note, Media): una riga piccola sopra
 * (la data, "8 nuove dall'ultima visita") e il titolo grande. A destra le azioni della sezione
 * e, per ultimi, la lente di Cerca ovunque e l'ingranaggio delle Impostazioni in un cerchio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelBar(
    title: String,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    /** Per le pagine aperte da una sezione (Impostazioni): freccia indietro al posto dell'ingranaggio. */
    onBack: (() -> Unit)? = null,
    /** Senza fondo: per le pagine con un'immagine dietro (il player). */
    transparent: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openSettings = LocalOpenSettings.current.takeIf { onBack == null }
    val openSearch = LocalOpenSearch.current.takeIf { onBack == null }
    TopAppBar(
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            }
        },
        title = {
            Column {
                if (subtitle != null) {
                    Text(
                        subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        actions = {
            actions()
            // Nelle sezioni in alto a destra solo due cerchi, uguali ovunque: cerca e impostazioni.
            if (openSearch != null) {
                RoundIconButton(Icons.Outlined.Search, "Cerca ovunque", onClick = openSearch)
            }
            if (openSettings != null) {
                RoundIconButton(Icons.Outlined.Settings, "Impostazioni", onClick = openSettings)
            }
            Spacer(Modifier.width(8.dp))
        },
        expandedHeight = 76.dp,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
            scrolledContainerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
        ),
        scrollBehavior = scrollBehavior,
    )
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

/** Icona dentro un quadratino colorato. */
@Composable
fun IconTile(icon: ImageVector, accent: TileAccent, size: Dp = 40.dp, iconSize: Dp = 22.dp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(Color(if (dark) accent.darkBg else accent.lightBg)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color(if (dark) accent.dark else accent.light), modifier = Modifier.size(iconSize))
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
@Composable
fun CanvasCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardColors = colors ?: CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val border = if (colors == null) cardBorder() else null
    if (onClick != null) {
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
        modifier = modifier.fillMaxWidth().heightIn(min = 36.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
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

/** Campi di testo sulla carta: fondo della card e bordo sottile, l'accento solo quando si scrive. */
@Composable
fun canvasFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.card,
    focusedContainerColor = MaterialTheme.colorScheme.card,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

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
