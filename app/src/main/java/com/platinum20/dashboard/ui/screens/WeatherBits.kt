package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.HourlyPoint
import com.platinum20.dashboard.weather.asTemp
import java.time.format.DateTimeFormatter

/**
 * Logica pura del grafico del meteo (1.528, scelta B): dove cade ogni ora sulla curva e quanto è alta la sua barra
 * di pioggia, in frazioni 0..1, così il disegno e i test leggono gli stessi numeri.
 */
object WeatherChart {
    /** Altezza di ogni punto della curva: 0 = la più fredda (in basso), 1 = la più calda. Tutte uguali = a metà. */
    fun levels(temps: List<Double>): List<Float> {
        val clean = temps.filter { !it.isNaN() }
        if (clean.isEmpty()) return temps.map { 0.5f }
        val low = clean.min()
        val high = clean.max()
        val span = high - low
        return temps.map { t ->
            if (t.isNaN() || span < 0.5) 0.5f else ((t - low) / span).toFloat().coerceIn(0f, 1f)
        }
    }

    /** Altezza della barra di pioggia: la probabilità in frazione, niente barra sotto il 20% (rumore). */
    fun rain(probability: Int): Float = if (probability < 20) 0f else (probability / 100f).coerceIn(0f, 1f)

    /** Dove scrivere massima e minima sulla curva: il primo punto più caldo e il primo più freddo; null se piatta. */
    fun extremes(temps: List<Double>): Pair<Int, Int>? {
        val clean = temps.withIndex().filter { !it.value.isNaN() }
        if (clean.size < 2) return null
        val hi = clean.maxBy { it.value }
        val lo = clean.minBy { it.value }
        return if (hi.value - lo.value < 1.0) null else hi.index to lo.index
    }

    /** L'ora con la pioggia più probabile, per scriverne la percentuale sopra la barra: solo da 30% in su. */
    fun rainPeak(probabilities: List<Int>): Int? =
        probabilities.withIndex().filter { it.value >= 30 }.maxByOrNull { it.value }?.index

    /** Le etichette sotto il grafico: un'ora ogni [step], la prima detta "Ora". */
    fun labels(hours: List<HourlyPoint>, step: Int = 3): List<Pair<Int, String>> =
        hours.indices.filter { it % step == 0 }.map { i ->
            i to (if (i == 0) "Ora" else hours[i].time.format(DateTimeFormatter.ofPattern("H")))
        }
}

/**
 * La curva della temperatura delle prossime 24 ore con le barre della pioggia sotto, nel blocco colorato della
 * pagina Meteo (1.528, scelta B): la giornata a colpo d'occhio. Sotto, un'ora ogni tre con icona e gradi.
 */
@Composable
fun TempCurve(forecast: Forecast, modifier: Modifier = Modifier) {
    val hours = forecast.hourly.take(24)
    if (hours.size < 2) return
    val content = LocalContentColor.current
    val accent = content
    val rainColor = MaterialTheme.colorScheme.primary
    val levels = WeatherChart.levels(hours.map { it.temperature })
    val extremes = WeatherChart.extremes(hours.map { it.temperature })
    val peak = WeatherChart.rainPeak(hours.map { it.precipitationProbability })
    val measurer = rememberTextMeasurer()
    val tempStyle = MaterialTheme.typography.labelMedium.copy(color = content, fontWeight = FontWeight.SemiBold)
    val rainStyle = MaterialTheme.typography.labelSmall.copy(color = content.copy(alpha = 0.75f))
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(84.dp)
                .semantics { contentDescription = "Temperatura e pioggia delle prossime 24 ore" },
        ) {
            val step = size.width / (hours.size - 1)
            val top = 6.dp.toPx()
            val curveH = size.height * 0.5f
            // Le barre della pioggia dal fondo: alte quanto la probabilità.
            val barW = (step * 0.55f).coerceAtMost(8.dp.toPx())
            hours.forEachIndexed { i, h ->
                val f = WeatherChart.rain(h.precipitationProbability)
                if (f > 0f) {
                    val bh = (size.height * 0.45f) * f
                    drawRoundRect(
                        color = rainColor.copy(alpha = 0.55f),
                        topLeft = Offset(i * step - barW / 2, size.height - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            val path = Path()
            levels.forEachIndexed { i, l ->
                val x = i * step
                val y = top + (1f - l) * curveH
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Il pallino su "ora".
            drawCircle(accent, radius = 4.dp.toPx(), center = Offset(0f, top + (1f - levels[0]) * curveH))
            // Massima e minima scritte sulla curva, la percentuale sopra la barra più alta (revisione 2026-11-22).
            fun label(text: String, x: Float, y: Float, style: TextStyle) {
                val m = measurer.measure(text, style)
                val left = (x - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
                drawText(m, topLeft = Offset(left, y))
            }
            extremes?.let { (hi, lo) ->
                listOf(hi to true, lo to false).forEach { (i, high) ->
                    val x = i * step
                    val y = top + (1f - levels[i]) * curveH
                    if (i != 0) drawCircle(accent, radius = 3.dp.toPx(), center = Offset(x, y))
                    val text = hours[i].temperature.asTemp()
                    val h = measurer.measure(text, tempStyle).size.height
                    label(text, x, if (high) y - h - 3.dp.toPx() else y + 4.dp.toPx(), tempStyle)
                }
            }
            peak?.let { i ->
                val bh = (size.height * 0.45f) * WeatherChart.rain(hours[i].precipitationProbability)
                val text = "${hours[i].precipitationProbability}%"
                val h = measurer.measure(text, rainStyle).size.height
                label(text, i * step, size.height - bh - h - 1.dp.toPx(), rainStyle)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WeatherChart.labels(hours).forEach { (i, label) ->
                val p = hours[i]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.65f))
                    Icon(weatherIcon(p.code, p.isDay), contentDescription = null, modifier = Modifier.padding(vertical = 3.dp).size(16.dp))
                    Text(p.temperature.asTemp(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Le tessere che stanno sempre da sole su una riga, larghe come due (Antonio, 2026-11-22). */
internal val WIDE_DETAILS = setOf("Alba · Tramonto")

/**
 * Le righe delle tessere: due per riga, ma quelle in [WIDE_DETAILS] stanno da sole e una tessera rimasta sola
 * prende tutta la larghezza, così non resta mai mezza riga vuota.
 */
internal fun detailRows(details: List<WeatherDetail>): List<List<WeatherDetail>> {
    val wide = details.filter { it.label in WIDE_DETAILS }
    return details.filterNot { it.label in WIDE_DETAILS }.chunked(2) + wide.map { listOf(it) }
}

/** Una tessera: etichetta, valore e una parola che dice se è tanto o poco (revisione 2026-11-22). */
data class WeatherDetail(val label: String, val value: String, val note: String? = null)

/** I dettagli del meteo come tessere piccole, due per riga (1.528, scelta B): etichetta, valore, spiegazione. */
@Composable
internal fun DetailTiles(details: List<WeatherDetail>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        detailRows(details).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, value, note) ->
                    CanvasCard(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note != null) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
