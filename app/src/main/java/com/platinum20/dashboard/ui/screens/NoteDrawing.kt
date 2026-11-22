package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Un tratto del disegno: i punti toccati, il colore e lo spessore. */
internal class Stroke2(val points: List<Offset>, val color: Color, val width: Float)

/** I colori della penna: nero, blu, rosso, arancio (gli stessi significati del resto di Canvas non servono qui). */
private val PEN_COLORS = listOf(Color(0xFF1B1B1B), Color(0xFF2F6FED), Color(0xFFD93A3A), Color(0xFFF08A24))

/**
 * Il disegno a mano (1.588): un foglio bianco a tutto schermo, si disegna col dito. "Fatto" lo mette nella nota come foto,
 * X chiude senza salvare, la freccia toglie l'ultimo tratto.
 */
@Composable
internal fun NoteDrawingLayer(onDone: (List<Stroke2>, IntSize) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val strokes = remember { mutableStateListOf<Stroke2>() }
    val current = remember { mutableStateListOf<Offset>() }
    var color by remember { mutableStateOf(PEN_COLORS.first()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 4.dp.toPx() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            RoundIconButton(Icons.Outlined.Close, "Chiudi senza salvare") { onClose() }
            PEN_COLORS.forEach { c ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { color = c },
                )
            }
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.AutoMirrored.Outlined.Undo, "Togli l'ultimo tratto", enabled = strokes.isNotEmpty()) { strokes.removeLastOrNull() }
            CanvasButton("Fatto", onClick = { if (strokes.isEmpty()) onClose() else onDone(strokes.toList(), size) }, icon = Icons.Outlined.Check, primary = true)
        }
        Spacer(Modifier.size(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current.clear(); current += it },
                        onDragEnd = {
                            if (current.isNotEmpty()) strokes += Stroke2(current.toList(), color, widthPx)
                            current.clear()
                        },
                        onDragCancel = { current.clear() },
                    ) { change, _ -> current += change.position }
                },
        ) {
            fun draw(points: List<Offset>, c: Color, w: Float) {
                if (points.isEmpty()) return
                if (points.size == 1) { drawCircle(c, w / 2, points.first()); return }
                val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            strokes.forEach { draw(it.points, it.color, it.width) }
            draw(current.toList(), color, widthPx)
        }
    }
}

/** Il disegno diventa un PNG (fondo bianco) fra le foto delle note; null se non si riesce a scrivere. */
internal suspend fun saveDrawing(context: Context, strokes: List<Stroke2>, size: IntSize): String? = withContext(Dispatchers.IO) {
    runCatching {
        val w = size.width.coerceAtLeast(1)
        val h = size.height.coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        strokes.forEach { s ->
            paint.color = s.color.toArgb()
            paint.strokeWidth = s.width
            if (s.points.size == 1) {
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(s.points[0].x, s.points[0].y, s.width / 2, paint)
                paint.style = android.graphics.Paint.Style.STROKE
            } else {
                val path = android.graphics.Path()
                path.moveTo(s.points[0].x, s.points[0].y)
                s.points.drop(1).forEach { path.lineTo(it.x, it.y) }
                canvas.drawPath(path, paint)
            }
        }
        val file = File(context.filesDir, "note-photos/${System.currentTimeMillis()}-disegno.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        file.absolutePath
    }.getOrNull()
}
