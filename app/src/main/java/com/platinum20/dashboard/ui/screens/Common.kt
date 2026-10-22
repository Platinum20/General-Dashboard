package com.platinum20.dashboard.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** Tocco sulla scheda già aperta della barra in basso: le sezioni tornano in cima alla lista. */
object TabReselect {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
}

/** Apre le Impostazioni: le sezioni principali la usano per l'icona in alto a destra. */
val LocalOpenSettings = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Barra in alto delle sezioni principali (Oggi, News, Note, Media): stessa altezza e stile ovunque,
 * con le azioni della sezione e, per ultima, l'ingranaggio delle Impostazioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopLevelBar(
    title: String,
    subtitle: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val openSettings = LocalOpenSettings.current
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        actions = {
            actions()
            if (openSettings != null) {
                IconButton(onClick = openSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Impostazioni")
                }
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

fun relativeTime(instant: Instant): String {
    val minutes = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "adesso"
        minutes < 60 -> "$minutes min fa"
        minutes < 24 * 60 -> "${minutes / 60} h fa"
        else -> dateFormatter.format(instant.atZone(ZoneId.systemDefault()))
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
