package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.platinum20.dashboard.weather.Freshness
import java.time.LocalDateTime
import kotlinx.coroutines.delay
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.today.Greeting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit, onPickPlace: () -> Unit) {
    val context = LocalContext.current
    WeatherStore.init(context)
    val state by WeatherStore.state.collectAsStateWithLifecycle()
    val pull = rememberPullRefresh(state.loading)
    LaunchedEffect(Unit) { WeatherStore.refresh(context) }

    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        // Il blocco Adesso della Home si allarga fino a diventare questa pagina (1.357).
        modifier = Modifier.sharedItem(SharedKeys.WEATHER, rounded = true).nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Meteo",
                scrollBehavior = barScroll,
                onBack = onBack,
            )
        },
    ) { inner ->
        val forecast = state.forecast
        // Mentre cerca (1.357): la forma del blocco colorato e delle righe, come in Adesso, invece della rotella.
        if (forecast == null && state.loading) {
            Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CanvasCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Placeholder(110.dp, 10.dp)
                        Placeholder(84.dp, 34.dp)
                        Placeholder(150.dp, 12.dp)
                    }
                }
                PlaceholderList(rows = 4, thumb = false, label = "Cerco il meteo")
            }
        } else if (forecast == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(inner).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (state.loading) {
                    LoadingPulse()
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
                        CanvasButton("Scegli località", onClick = onPickPlace)
                        CanvasButton("Riprova", onClick = { WeatherStore.refresh(context, force = true) })
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
                bottom = inner.calculateBottomPadding() + 16.dp + miniPlayerRoom(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Lo stesso blocco colorato di "Adesso" in Oggi: chi tocca la card ritrova la stessa cosa.
                CanvasCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    WeatherNowHeader(forecast, state.loading)
                    Text(
                        "Percepita ${forecast.current.apparent.asTemp()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.75f),
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = if (forecast.rainAlert == null) 16.dp else 10.dp),
                    )
                    // La pioggia sta dentro l'eroe (punto 45): un solo blocco colorato, la stessa frase di Adesso.
                    forecast.rainAlert?.let { alert -> HeroRainLine(alert) }
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

/** La riga della pioggia dentro l'eroe: stessa frase di RainAlert usata da Adesso, in arancio di attenzione. */
@Composable
private fun HeroRainLine(alert: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Umbrella, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(alert, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

/**
 * I dettagli in una card sola, una riga per voce (punto 45). Niente pressione né "pioggia oggi"
 * (la striscia delle ore la dice già); l'UV solo quando conta, da 3 in su.
 */
internal fun weatherDetails(forecast: Forecast): List<Pair<String, String>> {
    val c = forecast.current
    val today = forecast.today
    val time = DateTimeFormatter.ofPattern("HH:mm")
    return listOfNotNull(
        "Umidità" to "${c.humidity}%",
        "Vento" to "${c.windSpeed.roundToInt()} km/h ${WeatherCodes.windDirection(c.windDirection)}",
        forecast.air?.let { "Qualità dell'aria" to "${it.label} (${it.europeanAqi})" },
        today?.uvMax?.takeIf { !it.isNaN() && it >= 3.0 }?.let { "Indice UV" to "%.0f".format(Locale.ITALIAN, it) },
        today?.let { "Alba · Tramonto" to "${it.sunrise?.format(time) ?: "–"} · ${it.sunset?.format(time) ?: "–"}" },
    )
}

@Composable
private fun DetailsGrid(forecast: Forecast) {
    val icons = mapOf(
        "Umidità" to Icons.Outlined.Opacity,
        "Vento" to Icons.Outlined.Air,
        "Qualità dell'aria" to Icons.Outlined.Masks,
        "Indice UV" to Icons.Outlined.WbSunny,
        "Alba · Tramonto" to Icons.Outlined.WbTwilight,
    )
    val rows = weatherDetails(forecast)
    CanvasCard(shape = MaterialTheme.shapes.extraLarge) {
        Column {
            rows.forEachIndexed { i, (label, value) ->
                if (i > 0) RowDivider()
                DetailCell(icons[label] ?: Icons.Outlined.Opacity, label, value)
            }
        }
    }
}

@Composable
private fun DetailCell(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Icon(weatherIcon(point.code, point.isDay), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(point.temperature.asTemp(), style = MaterialTheme.typography.titleMedium)
            Text(
                if (point.precipitationProbability > 0) "${point.precipitationProbability}%" else " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Icon(weatherIcon(day.code), contentDescription = WeatherCodes.description(day.code), tint = MaterialTheme.colorScheme.onSurface)
        Text(
            if (day.precipitationProbability > 0) " ${day.precipitationProbability}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * La parte alta di "Adesso": luogo, temperatura grande, cielo con massima e minima, icona.
 * È la stessa nella card di Oggi e in cima alla schermata Meteo, così le due si riconoscono.
 * Sempre il meteo di adesso: domani e i giorni dopo si leggono nella schermata Meteo.
 */
@Composable
internal fun WeatherNowHeader(forecast: Forecast, loading: Boolean) {
    WeatherNowFull(forecast, loading)
}

/**
 * L'eroe compatto della Home (1.402): icona, temperatura e una riga di condizioni, tutto su una riga.
 * Niente "Adesso · Roma": la città si vede in Meteo. L'ora del dato compare solo quando è vecchio.
 * [bottom] = 14 dp quando sotto non c'è nessuna riga: la riga sta al centro della card (1.447).
 */
@Composable
internal fun WeatherNowCompact(forecast: Forecast, loading: Boolean, bottom: Dp = 6.dp) {
    val content = LocalContentColor.current
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottom),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            weatherIcon(forecast.current.code, forecast.current.isDay),
            contentDescription = WeatherCodes.description(forecast.current.code),
            modifier = Modifier.size(30.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            forecast.current.temperature.asTemp(),
            style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 40.sp),
        )
        Spacer(Modifier.size(12.dp))
        val today = forecast.today
        Text(
            Greeting.weatherLine(
                WeatherCodes.description(forecast.current.code),
                if (today != null) "${today.max.asTemp()} / ${today.min.asTemp()}" else "percepita ${forecast.current.apparent.asTemp()}",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = content.copy(alpha = 0.8f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        var slow by remember { mutableStateOf(false) }
        LaunchedEffect(loading) {
            slow = false
            if (loading) {
                delay(Freshness.SPINNER_MS)
                slow = true
            }
        }
        if (loading && !slow) {
            Spacer(Modifier.size(8.dp))
            LoadingPulse(color = content.copy(alpha = 0.6f), size = 8.dp)
        } else {
            val minute by produceState(LocalDateTime.now(), forecast.fetchedAt) {
                while (true) {
                    value = LocalDateTime.now()
                    delay(60_000L)
                }
            }
            Freshness.label(forecast.fetchedAt, minute)?.let { age ->
                Spacer(Modifier.size(8.dp))
                Text(age, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.6f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun WeatherNowFull(forecast: Forecast, loading: Boolean) {
    Row(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val content = LocalContentColor.current
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ADESSO · " + forecast.place.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
                    color = content.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // La rotella resta al massimo 10 secondi: poi si dice da quando è il dato che si vede.
                var slow by remember { mutableStateOf(false) }
                LaunchedEffect(loading) {
                    slow = false
                    if (loading) {
                        delay(Freshness.SPINNER_MS)
                        slow = true
                    }
                }
                if (loading && !slow) {
                    Spacer(Modifier.size(8.dp))
                    LoadingPulse(color = content.copy(alpha = 0.6f), size = 8.dp)
                } else {
                    val minute by produceState(LocalDateTime.now(), forecast.fetchedAt) {
                        while (true) {
                            value = LocalDateTime.now()
                            delay(60_000L)
                        }
                    }
                    Freshness.label(forecast.fetchedAt, minute)?.let { age ->
                        Text(
                            " · " + age.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold),
                            color = content.copy(alpha = 0.6f),
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                forecast.current.temperature.asTemp(),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 58.sp),
            )
            val today = forecast.today
            // L'avviso di pioggia delle prossime ore sta già sotto: qui solo la probabilità del giorno.
            val rain = today?.precipitationProbability?.takeIf { it >= 30 && forecast.rainAlert == null }?.let { "pioggia $it%" }
            Text(
                Greeting.weatherLine(
                    WeatherCodes.description(forecast.current.code),
                    if (today != null) "${today.max.asTemp()} / ${today.min.asTemp()}" else "percepita ${forecast.current.apparent.asTemp()}",
                    rain,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            weatherIcon(forecast.current.code, forecast.current.isDay),
            contentDescription = null,
            modifier = Modifier.padding(top = 4.dp).size(44.dp),
        )
    }
}
