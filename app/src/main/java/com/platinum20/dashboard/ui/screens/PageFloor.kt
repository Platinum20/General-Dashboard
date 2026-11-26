package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import kotlin.math.hypot

/**
 * L'alone dietro le pagine (1.646, Antonio: mockup A+B in ui-rinnovo/AB): tre macchie morbide coi colori del tema
 * (con Material You quelli dello sfondo del telefono), che quando piove si raffreddano in grigio ardesia.
 * Ferme: niente animazioni, quindi niente consumo. Spento in Impostazioni › Aspetto resta il fondo pieno di sempre.
 */
object Glow {
    /** Una macchia: centro e colore. Il centro è in frazioni dello schermo, il raggio è mezza diagonale. */
    data class Blob(val x: Float, val y: Float, val color: Color)

    private val RAIN_DARK = Color(0xFF3A4250)
    private val RAIN_LIGHT = Color(0xFFCDD6E0)

    /** Le macchie per questi colori: in alto a sinistra il colore principale, a destra il terzo, in basso il secondo. */
    fun blobs(primary: Color, tertiary: Color, secondary: Color, dark: Boolean, rainy: Boolean): List<Blob> {
        fun tone(c: Color, alpha: Float): Color {
            val base = if (rainy) lerp(c, if (dark) RAIN_DARK else RAIN_LIGHT, 0.8f) else c
            return base.copy(alpha = alpha)
        }
        val a = if (dark) 0.62f else 0.55f
        return listOf(
            Blob(0.10f, 0.04f, tone(primary, a)),
            Blob(0.96f, 0.36f, tone(tertiary, a * 0.8f)),
            Blob(0.18f, 0.92f, tone(secondary, a * 0.7f)),
        )
    }

    fun blobs(scheme: ColorScheme, rainy: Boolean): List<Blob> = blobs(
        scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer,
        dark = scheme.background.luminance() < 0.5f, rainy = rainy,
    )

    /** Piove adesso? Basta il codice del meteo attuale (pioggia, pioviggine, temporale). */
    fun rainy(code: Int?): Boolean =
        code != null && WeatherCodes.kind(code) in setOf(WeatherCodes.Kind.DRIZZLE, WeatherCodes.Kind.RAIN, WeatherCodes.Kind.STORM)
}

/**
 * Il fondo di una pagina: il colore dell'app e, se acceso, l'alone. Disegnato nelle coordinate dello schermo, così
 * pagina, barra del titolo e una card che cresce nella sua pagina mostrano lo stesso alone senza giunte.
 * Le pagine lo mettono sul loro Scaffold (con containerColor trasparente) al posto del fondo pieno.
 */
fun Modifier.pageFloor(): Modifier = composed {
    val on by AppSettings.glow.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val bg = scheme.background
    val code = WeatherStore.state.collectAsState().value.forecast?.current?.code
    val rainy = Glow.rainy(code)
    val blobs = remember(scheme, rainy, on) { if (on) Glow.blobs(scheme, rainy) else emptyList() }
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = config.screenWidthDp * density.density
    val screenH = config.screenHeightDp * density.density
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .then(if (blobs.isEmpty()) Modifier else Modifier.onGloballyPositioned { pos = it.positionInWindow() })
        .drawBehind {
            drawRect(bg)
            if (blobs.isEmpty()) return@drawBehind
            val w = maxOf(screenW, size.width)
            val h = maxOf(screenH, size.height)
            val radius = hypot(w, h) * 0.5f
            for (b in blobs) {
                val center = Offset(b.x * w - pos.x, b.y * h - pos.y)
                drawRect(Brush.radialGradient(listOf(b.color, b.color.copy(alpha = 0f)), center = center, radius = radius))
            }
        }
}

/** Una pagina senza Scaffold: il fondo con l'alone a tutto schermo. */
@androidx.compose.runtime.Composable
fun PageFloor(content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(Modifier.fillMaxSize().pageFloor()) { content() }
}
