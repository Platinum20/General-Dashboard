package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.WindowInsets
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
