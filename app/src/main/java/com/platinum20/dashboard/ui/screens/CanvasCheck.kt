package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LA spunta di Canvas (Antonio, 2026-11-22: "quadrati stile Notion ovunque"): quadrato con gli angoli
 * arrotondati, vuoto = solo il bordo, fatto = pieno con l'accento e la ✓ bianca. Usarla per ogni cosa da
 * spuntare (Da fare, abitudini, calendario), mai più cerchi. [border] = colore del bordo da vuota
 * (grigio di solito, arancio quando è in ritardo).
 */
@Composable
fun CanvasCheck(
    done: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    border: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fill: Color = MaterialTheme.colorScheme.primary,
    check: Color = MaterialTheme.colorScheme.onPrimary,
    description: String? = null,
    onToggle: (() -> Unit)? = null,
) {
    val corner = size * 0.24f
    // Aprendo la pagina la spunta è già com'è; toccandola si disegna con la molla viva.
    val drawn = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) { drawn.animateTo(if (done) 1f else 0f, Motion.lively()) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .then(if (onToggle != null) Modifier.clickable(role = Role.Checkbox, onClick = onToggle) else Modifier)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val p = drawn.value.coerceIn(0f, 1f)
            val s = 1.6.dp.toPx()
            if (p < 1f) drawRoundRect(
                border.copy(alpha = border.alpha * (1f - p)),
                topLeft = Offset(s / 2, s / 2),
                size = Size(this.size.width - s, this.size.height - s),
                cornerRadius = CornerRadius(corner.toPx() - s / 2),
                style = Stroke(s),
            )
            if (p > 0f) {
                // Il quadrato si riempie dal centro, poi la ✓ si disegna dal primo tratto (2026-11-24, proposta 1).
                val w = this.size.width
                val k = 0.55f + 0.45f * drawn.value.coerceAtLeast(0f)
                val side = w * k
                drawRoundRect(
                    fill.copy(alpha = fill.alpha * p),
                    topLeft = Offset((w - side) / 2, (w - side) / 2),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(corner.toPx() * k),
                )
                val path = Path().apply {
                    moveTo(w * 0.27f, w * 0.52f)
                    lineTo(w * 0.43f, w * 0.67f)
                    lineTo(w * 0.73f, w * 0.35f)
                }
                val part = if (p >= 1f) path else Path().also { out ->
                    val m = PathMeasure().apply { setPath(path, false) }
                    m.getSegment(0f, m.length * p, out, true)
                }
                drawPath(part, check, style = Stroke(w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
