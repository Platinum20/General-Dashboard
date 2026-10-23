package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.HourlyPoint
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.luminance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit, onPickPlace: () -> Unit) {
    val context = LocalContext.current
    WeatherStore.init(context)
    val state by WeatherStore.state.collectAsStateWithLifecycle()
    val pull = rememberPullRefresh(state.loading)
    LaunchedEffect(Unit) { WeatherStore.refresh(context) }

    Scaffold(
        topBar = {
            TopLevelBar(
                title = "Meteo",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onPickPlace) {
                        Icon(Icons.Outlined.EditLocationAlt, contentDescription = "Cambia località")
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { WeatherStore.refresh(context, force = true) }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Aggiorna")
                        }
                    }
                },
            )
        },
    ) { inner ->
        val forecast = state.forecast
        if (forecast == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.loading) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        state.error ?: "Meteo non disponibile.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    // Senza posizione l'uscita più rapida è scegliere una città: il pulsante è subito qui.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPickPlace, modifier = Modifier.bouncyPress()) { Text("Scegli località") }
                        Button(onClick = { WeatherStore.refresh(context, force = true) }, modifier = Modifier.bouncyPress()) { Text("Riprova") }
                    }
                }
            }
        } else PullToRefreshBox(
            isRefreshing = pull.showing,
            state = rememberHapticPullState(pull.showing),
            onRefresh = {
                pull.start()
                WeatherStore.refresh(context, force = true)
            },
            modifier = Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()),
        ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { CurrentSection(forecast) }
            forecast.rainAlert?.let { alert ->
                item {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Umbrella, contentDescription = null)
                            Spacer(Modifier.size(12.dp))
                            Text(alert, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (state.error != null) {
                item {
                    Text(
                        "Ultimo aggiornamento non riuscito: ${state.error}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item { DetailsGrid(forecast) }
            item { SectionTitle("Prossime 24 ore") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(forecast.hourly) { HourCell(it) }
                }
            }
            item { SectionTitle("Prossimi 7 giorni") }
            item {
                val low = forecast.daily.minOfOrNull { it.min }?.takeIf { !it.isNaN() } ?: 0.0
                val high = forecast.daily.maxOfOrNull { it.max }?.takeIf { !it.isNaN() } ?: 1.0
                CanvasCard(shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        forecast.daily.forEach { DayRow(it, low, high) }
                    }
                }
            }
            item {
                Text(
                    "Dati meteo: Open-Meteo.com · aggiornati ${relativeTime(java.time.Instant.ofEpochMilli(forecast.fetchedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        }
    }
}

@Composable
private fun CurrentSection(forecast: Forecast) {
    val c = forecast.current
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        // Il blocco colorato come "Adesso" in Oggi.
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(" " + forecast.place, style = MaterialTheme.typography.titleSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.temperature.asTemp(), style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                Icon(weatherIcon(c.code, c.isDay), contentDescription = null, modifier = Modifier.size(56.dp))
            }
            Text(WeatherCodes.description(c.code), style = MaterialTheme.typography.titleLarge)
            forecast.today?.let {
                Text(
                    "Percepita ${c.apparent.asTemp()} · Max ${it.max.asTemp()} · Min ${it.min.asTemp()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DetailsGrid(forecast: Forecast) {
    val c = forecast.current
    val today = forecast.today
    val time = DateTimeFormatter.ofPattern("HH:mm")
    val cells = listOfNotNull(
        Triple(Icons.Outlined.Opacity, "Umidità", "${c.humidity}%"),
        Triple(Icons.Outlined.Air, "Vento", "${c.windSpeed.roundToInt()} km/h ${WeatherCodes.windDirection(c.windDirection)}"),
        today?.let { Triple(Icons.Outlined.Umbrella, "Pioggia oggi", "${it.precipitationProbability}% · ${"%.1f".format(Locale.ITALIAN, it.precipitationSum)} mm") },
        forecast.air?.let { Triple(Icons.Outlined.Masks, "Qualità dell'aria", "${it.label} (${it.europeanAqi})") },
        Triple(Icons.Outlined.Compress, "Pressione", if (c.pressure.isNaN()) "–" else "${c.pressure.roundToInt()} hPa"),
        today?.let { Triple(Icons.Outlined.WbSunny, "Indice UV", if (it.uvMax.isNaN()) "–" else "%.0f".format(Locale.ITALIAN, it.uvMax)) },
        today?.let {
            Triple(
                Icons.Outlined.WbTwilight,
                "Alba · Tramonto",
                "${it.sunrise?.format(time) ?: "–"} · ${it.sunset?.format(time) ?: "–"}",
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // L'ultimo da solo prende tutta la riga: niente buco accanto ad "Alba · Tramonto".
                row.forEach { (icon, label, value) -> DetailCell(icon, label, value, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DetailCell(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
        modifier = modifier,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text, Modifier.padding(top = 8.dp))
}

@Composable
private fun HourCell(point: HourlyPoint) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
        border = cardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).width(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(point.time.format(DateTimeFormatter.ofPattern("HH")), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Icon(weatherIcon(point.code, point.isDay), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(point.temperature.asTemp(), style = MaterialTheme.typography.titleMedium)
            Text(
                if (point.precipitationProbability > 0) "${point.precipitationProbability}%" else " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DayRow(day: DailyPoint, low: Double, high: Double) {
    val name = when (day.date) {
        LocalDate.now() -> "Oggi"
        LocalDate.now().plusDays(1) -> "Domani"
        else -> day.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        Icon(weatherIcon(day.code), contentDescription = WeatherCodes.description(day.code), tint = MaterialTheme.colorScheme.primary)
        Text(
            if (day.precipitationProbability > 0) " ${day.precipitationProbability}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(44.dp),
        )
        Text(day.min.asTemp(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TempBar(day.min, day.max, low, high, Modifier.weight(1f).padding(horizontal = 10.dp))
        Text(day.max.asTemp(), style = MaterialTheme.typography.bodyLarge)
    }
}

/** Barra stile "app meteo": mostra dove cadono minima e massima del giorno rispetto alla settimana. */
@Composable
private fun TempBar(min: Double, max: Double, low: Double, high: Double, modifier: Modifier) {
    val range = (high - low).takeIf { it > 0 } ?: 1.0
    val start = ((min - low) / range).toFloat().coerceIn(0f, 1f)
    val end = ((max - low) / range).toFloat().coerceIn(start, 1f)
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(Modifier.fillMaxSize()) {
            if (start > 0f) Spacer(Modifier.weight(start))
            Box(
                Modifier
                    .weight((end - start).coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            if (end < 1f) Spacer(Modifier.weight(1f - end))
        }
    }
}
