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
import com.platinum20.dashboard.weather.WeatherText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(onBack: () -> Unit, onPickPlace: () -> Unit) {
    val context = LocalContext.current
    WeatherStore.init(context)
    val state by WeatherStore.state.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val pull = rememberPullRefresh(state.loading)
    LaunchedEffect(Unit) { WeatherStore.refresh(context) }

    val barScroll = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior(androidx.compose.material3.rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.pageFloor(), containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // Il blocco Adesso della Home si allarga fino a diventare questa pagina (1.357).
        modifier = Modifier.sharedItem(SharedKeys.WEATHER, rounded = true).nestedScroll(barScroll.nestedScrollConnection),
        topBar = {
            // Il titolo è la città: toccandola si cambia (revisione Meteo 2026-11-22, al posto di "ADESSO · ROMA").
            TopLevelBar(
                title = state.forecast?.place ?: "Meteo",
                subtitle = if (state.forecast != null) "Meteo" else null,
                scrollBehavior = barScroll,
                onBack = onBack,
                onTitleClick = onPickPlace.takeIf { state.forecast != null },
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
                bottom = listBottom(inner.calculateBottomPadding() + 16.dp + miniPlayerRoom()),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Il dato vecchio lo dice in cima, con "Aggiorna" accanto (al posto della riga rossa).
            item {
                val now by produceState(System.currentTimeMillis(), forecast.fetchedAt) {
                    while (true) {
                        value = System.currentTimeMillis()
                        delay(60_000L)
                    }
                }
                val stale = !state.loading && WeatherText.isStale(forecast.fetchedAt, now, state.error != null)
                val age = Freshness.label(forecast.fetchedAt, LocalDateTime.now())
                StatusLine(
                    LinkStatus(
                        if (stale) LinkStatus.Kind.STALE else LinkStatus.Kind.OK,
                        if (stale) age?.let { WeatherText.staleLine(it) } ?: "Aggiornamento non riuscito" else null,
                    ),
                    action = "Aggiorna",
                    onAction = { WeatherStore.refresh(context, force = true) },
                    start = 4.dp,
                )
            }
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
                    // La pioggia sta dentro l'eroe (punto 45): un solo blocco colorato, la stessa frase di Adesso.
                    forecast.rainAlert?.let { alert -> HeroRainLine(alert) }
                    // La curva delle prossime 24 ore con massima, minima e pioggia (1.528 scelta B, numeri 2026-11-22).
                    TempCurve(forecast, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
                }
            }
            // Prima i giorni, poi i dettagli come tessere (1.528, scelta B): la pagina sta quasi in uno schermo.
            item {
                val low = forecast.daily.minOfOrNull { it.min }?.takeIf { !it.isNaN() } ?: 0.0
                val high = forecast.daily.maxOfOrNull { it.max }?.takeIf { !it.isNaN() } ?: 1.0
                // Un giorno aperto alla volta: tocco = le sue ore sotto, nella stessa card (2026-11-22).
                var open by rememberSaveable { mutableStateOf<String?>(null) }
                CanvasCard(shape = MaterialTheme.shapes.extraLarge) {
                    CardTitle("Prossimi giorni")
                    Column(Modifier.padding(bottom = 8.dp)) {
                        forecast.daily.forEach { day ->
                            val key = day.date.toString()
                            val hours = WeatherText.dayHours(forecast.allHourly, day.date)
                            DayRow(day, low, high, expanded = open == key, canOpen = hours.isNotEmpty()) {
                                haptic(Haptic.TICK)
                                open = if (open == key) null else key
                            }
                            AnimatedVisibility(
                                open == key,
                                enter = fadeIn(Motion.settle()) + expandVertically(Motion.settle()),
                                exit = fadeOut(Motion.settle()) + shrinkVertically(Motion.settle()),
                            ) { DayHours(day, hours) }
                        }
                    }
                }
            }
            item { DetailTiles(weatherDetails(forecast)) }
            item {
                // Solo la fonte: da quando è il dato lo dice la riga in cima, quando conta.
                Text(
                    "Dati: Open-Meteo.com",
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
 * I dettagli come tessere, due per riga (1.528; prima una card a righe, punto 45). Niente pressione né "pioggia oggi"
 * (la curva la dice già); l'UV solo quando conta, da 3 in su. Sotto ogni numero una parola che lo spiega (2026-11-22).
 */
internal fun weatherDetails(forecast: Forecast): List<WeatherDetail> {
    val c = forecast.current
    val today = forecast.today
    val time = DateTimeFormatter.ofPattern("HH:mm")
    return listOfNotNull(
        WeatherDetail("Umidità", "${c.humidity}%", WeatherText.humidity(c.humidity)),
        WeatherDetail("Vento", "${c.windSpeed.roundToInt()} km/h", WeatherText.wind(c.windSpeed, c.windDirection)),
        forecast.air?.let { WeatherDetail("Qualità dell'aria", it.label, "Indice europeo ${it.europeanAqi}") },
        today?.uvMax?.takeIf { !it.isNaN() && it >= 3.0 }?.let {
            WeatherDetail("Indice UV", "%.0f".format(Locale.ITALIAN, it), WeatherText.uv(it))
        },
        today?.let {
            WeatherDetail(
                "Alba · Tramonto",
                "${it.sunrise?.format(time) ?: "–"} · ${it.sunset?.format(time) ?: "–"}",
                WeatherText.daylight(it.sunrise, it.sunset),
            )
        },
    )
}

/** Le ore di un giorno aperto (6, 9, 12…) e una frase: per sapere a che ora piove mercoledì. */
@Composable
private fun DayHours(day: DailyPoint, hours: List<HourlyPoint>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${p.time.hour}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(weatherIcon(p.code, p.isDay), contentDescription = null, modifier = Modifier.padding(vertical = 3.dp).size(18.dp))
                    Text(p.temperature.asTemp(), style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (p.precipitationProbability >= 20) "${p.precipitationProbability}%" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            WeatherText.dayPhrase(day),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun DayRow(day: DailyPoint, low: Double, high: Double, expanded: Boolean, canOpen: Boolean, onClick: () -> Unit) {
    val name = when (day.date) {
        LocalDate.now() -> "Oggi"
        LocalDate.now().plusDays(1) -> "Domani"
        else -> day.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.ITALIAN)).replaceFirstChar { it.uppercase() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpen, onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
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
        // La freccia dice che il giorno si apre; girata quando è aperto.
        val turn by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f, Motion.settle(), label = "dayArrow")
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = if (expanded) "Chiudi" else "Apri le ore",
            tint = MaterialTheme.colorScheme.outline.copy(alpha = if (canOpen) 1f else 0f),
            modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsLayer { rotationZ = turn },
        )
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
 * [bottom] = 14 dp quando sotto non c'è nessuna riga: la riga sta al centro della card (1.443).
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
        // Cielo sopra, "max 19° · min 10°" sotto (2026-11-22); la sera la minima di stanotte, domani sta in una riga.
        val now = LocalDateTime.now()
        val second = when {
            now.hour >= com.platinum20.dashboard.today.Briefing.EVENING_HOUR ->
                WeatherText.tonightMin(forecast.hourly, now)?.let { "stanotte min ${it.asTemp()}" }
            today != null -> WeatherText.range(today.max, today.min)
            else -> null
        } ?: "percepita ${forecast.current.apparent.asTemp()}"
        Column(Modifier.weight(1f)) {
            Text(
                WeatherCodes.description(forecast.current.code),
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                second,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val content = LocalContentColor.current
        Column(Modifier.weight(1f)) {
            // Niente "ADESSO · ROMA": la città è il titolo della pagina; il dato vecchio lo dice la riga in cima.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    forecast.current.temperature.asTemp(),
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 58.sp),
                )
                // La rotella resta al massimo 10 secondi.
                var slow by remember { mutableStateOf(false) }
                LaunchedEffect(loading) {
                    slow = false
                    if (loading) {
                        delay(Freshness.SPINNER_MS)
                        slow = true
                    }
                }
                if (loading && !slow) {
                    Spacer(Modifier.size(10.dp))
                    LoadingPulse(color = content.copy(alpha = 0.6f), size = 8.dp)
                }
            }
            val today = forecast.today
            // L'avviso di pioggia delle prossime ore sta già sotto: qui solo la probabilità del giorno.
            val rain = today?.precipitationProbability?.takeIf { it >= 30 && forecast.rainAlert == null }?.let { "pioggia $it%" }
            // Percepita accanto al cielo; massima e minima sono scritte sulla curva.
            Text(
                Greeting.weatherLine(
                    WeatherCodes.description(forecast.current.code),
                    "percepita ${forecast.current.apparent.asTemp()}",
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
            modifier = Modifier.size(48.dp),
        )
    }
}
