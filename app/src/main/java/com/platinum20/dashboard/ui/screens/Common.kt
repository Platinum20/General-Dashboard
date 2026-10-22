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
import androidx.compose.foundation.layout.Column
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
