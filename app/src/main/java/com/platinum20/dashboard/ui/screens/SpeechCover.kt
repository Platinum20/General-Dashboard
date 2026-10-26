package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Blue = Color(0xFF3550E8)
private val Night = Color(0xFF141C5C)
private val Orange = Color(0xFFD9572B)

/**
 * La copertina del punto del giorno letto ad alta voce: i colori dell'app (blu e arancione),
 * un "sole" che sorge e, se c'è spazio, il titolo con la data di oggi. Si adatta da 40dp a tutto schermo.
 */
@Composable
fun SpeechCover(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.background(Brush.linearGradient(listOf(Blue, Night), start = Offset.Zero, end = Offset.Infinite)),
    ) {
        val large = maxWidth >= 140.dp
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension
            // Il sole arancione che spunta in alto a destra, con due aloni.
            val sun = Offset(size.width * 0.82f, size.height * 0.2f)
            drawCircle(Orange.copy(alpha = 0.18f), radius = r * 0.42f, center = sun)
            drawCircle(Orange.copy(alpha = 0.35f), radius = r * 0.28f, center = sun)
            drawCircle(Orange, radius = r * 0.16f, center = sun)
            // Onde della voce in basso.
            for (i in 0 until 3) {
                drawCircle(
                    Color.White.copy(alpha = 0.06f + 0.03f * i),
                    radius = r * (0.9f - 0.2f * i),
                    center = Offset(size.width * 0.1f, size.height * 1.05f),
                )
            }
        }
        if (large) {
            val date = remember {
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                    .replaceFirstChar { it.uppercase() }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(maxWidth * 0.08f)) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(maxWidth * 0.12f))
                Text(
                    "Il punto\ndel giorno",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (maxWidth.value * 0.11f).sp,
                    lineHeight = (maxWidth.value * 0.12f).sp,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = (maxWidth.value * 0.05f).sp, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(maxWidth * 0.5f))
            }
        }
    }
}
