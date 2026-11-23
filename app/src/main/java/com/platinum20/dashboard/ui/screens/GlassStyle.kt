package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * IL vetro di Canvas, uno solo (1.606): barra, +, fumetti, coda, impostazioni video, finestre di dialogo.
 * Prima ognuno aveva i suoi numeri (velo 0,34 / 0,38 / 0,74, bordi grigi, bianchi o sfumati, veli dietro
 * 0,12 / 0,18 / 0,35) e a seconda del telefono o del pezzo il vetro sembrava un altro. Qui stanno tutti.
 */
object Glass {
    /** Quanto si sfoca la pagina sotto: uguale per vetro liquido, Haze e sfocatura di sistema dei dialoghi. */
    val BLUR = 16.dp

    /** Il velo del colore delle card sopra la sfocatura: abbastanza per leggere, poco per vedere sotto. */
    fun tint(dark: Boolean, colored: Boolean = false): Float = when {
        colored -> 0.82f
        dark -> 0.44f
        else -> 0.40f
    }

    /** Senza sfocatura (Android 11 e prima, risparmio energetico) il vetro si fa quasi pieno, se no non si legge. */
    fun solidTint(colored: Boolean = false): Float = if (colored) 0.96f else 0.92f

    /** Il velo scuro dietro a fumetti, coda e dialoghi: lo stesso ovunque. */
    fun scrim(dark: Boolean): Float = if (dark) 0.24f else 0.14f

    /** Il bordo: un filo di luce in alto che sfuma nel grigio delle card in basso. Uno per tutti. */
    fun rim(dark: Boolean, outline: Color, colored: Boolean = false): Brush = Brush.verticalGradient(
        if (colored) listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.08f))
        else listOf(Color.White.copy(alpha = if (dark) 0.22f else 0.70f), outline.copy(alpha = 0.6f)),
    )
}
