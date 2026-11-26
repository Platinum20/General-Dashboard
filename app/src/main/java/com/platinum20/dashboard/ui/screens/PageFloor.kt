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

    /**
     * Le macchie, tutte dalla tinta del colore principale (mai dal terzo colore di Material You, che sul telefono di
     * Antonio è verde oliva: 1.647 virava al cachi). Al buio sono toni scuri, quasi neri come nel mockup A+B
     * (brace, vino, ambra spenta); di giorno pastelli leggeri. [wine] = il rosso scuro del tema per la macchia a destra.
     */
    fun blobs(primary: Color, wine: Color, dark: Boolean, rainy: Boolean, strong: Color = primary): List<Blob> {
        fun tone(c: Color, alpha: Float): Color {
            val base = if (rainy) lerp(c, if (dark) RAIN_DARK else RAIN_LIGHT, 0.8f) else c
            return base.copy(alpha = alpha)
        }
        val side = lerp(primary, wine, 0.5f)
        return if (dark) listOf(
            Blob(0.10f, 0.04f, tone(lerp(primary, Color.Black, 0.22f), 0.85f)),
            Blob(0.96f, 0.36f, tone(lerp(side, Color.Black, 0.45f), 0.75f)),
            Blob(0.18f, 0.92f, tone(lerp(primary, Color.Black, 0.5f), 0.6f)),
        ) else {
            // Di giorno il pastello da solo non si vedeva sulla carta e il vetro sembrava bianco pieno (Antonio, 1.648):
            // un po' del colore pieno del tema dà all'alone abbastanza colore da far leggere le card come vetro.
            val tint = lerp(primary, strong, 0.3f)
            listOf(
                Blob(0.10f, 0.04f, tone(tint, 0.6f)),
                Blob(0.96f, 0.36f, tone(lerp(tint, wine, 0.4f), 0.45f)),
                Blob(0.18f, 0.92f, tone(tint, 0.4f)),
            )
        }
    }

    fun blobs(scheme: ColorScheme, rainy: Boolean): List<Blob> = blobs(
        scheme.primaryContainer, scheme.errorContainer,
        dark = scheme.background.luminance() < 0.5f, rainy = rainy, strong = scheme.primary,
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
