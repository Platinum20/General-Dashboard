package com.platinum20.dashboard.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.news.RecapTheme
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.weather.LocationProvider
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherState
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.widget.DashboardWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bottomPadding: PaddingValues,
    onOpenNews: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenNotes: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenService: (MediaService) -> Unit,
    onCustomize: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..12 -> "Buongiorno"
            in 13..17 -> "Buon pomeriggio"
            in 18..23 -> "Buonasera"
            else -> "Buonanotte"
        }
    }
    val today = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }
    }

    WeatherStore.init(context)
    val weather by WeatherStore.state.collectAsStateWithLifecycle()
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasLocation = result.values.any { it }
        if (hasLocation) WeatherStore.refresh(context, force = true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
        if (hasLocation) WeatherStore.refresh(context)
    }

    val recap = rememberRecapState()
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()
    var calendarRefresh by remember { mutableStateOf(0) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            // Il saluto e la data fanno da titolo: niente card in più che ripete il meteo.
            TopLevelBar(
                title = greeting,
                subtitle = today,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onCustomize) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Personalizza")
                    }
                },
            )
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = weather.loading || recap.refreshing,
            onRefresh = {
                if (hasLocation || WeatherStore.manualPlace(context) != null) WeatherStore.refresh(context, force = true)
                recap.retry()
                calendarRefresh++
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(layout.visible, key = { it.name }) { card ->
                when (card) {
                    HomeCard.WEATHER -> WeatherCard(
                        state = weather,
                        hasPermission = hasLocation,
                        onRequestPermission = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                            )
                        },
                        onRetry = { WeatherStore.refresh(context, force = true) },
                        onClick = onOpenWeather,
                    )
                    HomeCard.CALENDAR -> CalendarCard(refreshKey = calendarRefresh)
                    HomeCard.TASKS -> TasksCard(onOpenNotes = onOpenNotes)
                    HomeCard.COUNTDOWN -> CountdownCard()
                    HomeCard.RECAP -> RecapCard(
                        state = recap,
                        onOpenTheme = { onOpenArticle(it.headline) },
                        onOpenAll = onOpenRecap,
                    )
                    HomeCard.MODULES -> Shortcuts(
                        onOpenWeather = onOpenWeather,
                        onOpenRecap = onOpenRecap,
                        onOpenVideo = onOpenVideo,
                        onOpenService = onOpenService,
                    )
                }
            }
            item {
                TextButton(
                    onClick = onCustomize,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Personalizza questa schermata")
                }
            }
        }
        }
    }
}

data class RecapUiState(
    val themes: List<RecapTheme> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val refreshing: Boolean = false,
    val retry: () -> Unit = {},
)

/** Scarica le notizie (se non già in memoria) e ne ricava i temi del giorno. */
@Composable
fun rememberRecapState(): RecapUiState {
    val result by NewsStore.result.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        error = null
        refreshing = attempt > 0
        runCatching { NewsStore.load(force = attempt > 0) }
            .onFailure { if (it !is CancellationException) error = it.message ?: "Notizie non disponibili." }
        refreshing = false
    }
    val context = LocalContext.current
    val themes by produceState(emptyList<RecapTheme>(), result) {
        value = result?.let { r -> withContext(Dispatchers.Default) { NewsRecap.build(r.items) } } ?: emptyList()
        // Il widget mostra gli stessi temi della Dashboard.
        if (value.isNotEmpty()) DashboardWidget.saveRecap(context, value)
    }
    return RecapUiState(
        themes = themes,
        loading = result == null && error == null,
        error = error,
        refreshing = refreshing,
        retry = { attempt++ },
    )
}

@Composable
private fun WeatherCard(
    state: WeatherState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onClick: () -> Unit,
) {
    val forecast = state.forecast
    ElevatedCard(
        onClick = { if (forecast != null) onClick() else if (!hasPermission) onRequestPermission() else onRetry() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        when {
            forecast != null -> Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    weatherIcon(forecast.current.code, forecast.current.isDay),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        WeatherCodes.description(forecast.current.code),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            " " + forecast.place,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val today = forecast.today
                    val alert = forecast.rainAlert
                    val rain = alert ?: today?.precipitationProbability?.takeIf { it >= 30 }?.let { "Pioggia $it%" }
                    if (rain != null) {
                        Text(
                            rain,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(6.dp))
                        }
                        Text(forecast.current.temperature.asTemp(), style = MaterialTheme.typography.headlineLarge)
                    }
                    val today = forecast.today
                    // Massima e minima su una riga sola, sotto la temperatura: non vanno più a capo.
                    Text(
                        if (today != null) "${today.max.asTemp()} / ${today.min.asTemp()}" else "Percepita ${forecast.current.apparent.asTemp()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            !hasPermission -> CardMessage(
                icon = Icons.Outlined.LocationOn,
                title = "Meteo",
                text = "Consenti la posizione per vedere il meteo del posto in cui sei.",
                action = "Consenti",
                onAction = onRequestPermission,
            )
            state.loading || state.error == null -> Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                Spacer(Modifier.size(16.dp))
                Text("Cerco il meteo della tua zona…", style = MaterialTheme.typography.bodyLarge)
            }
            else -> CardMessage(
                icon = Icons.Outlined.WbSunny,
                title = "Meteo non disponibile",
                text = state.error ?: "",
                action = "Riprova",
                onAction = onRetry,
            )
        }
    }
}

@Composable
private fun RecapCard(
    state: RecapUiState,
    onOpenTheme: (RecapTheme) -> Unit,
    onOpenAll: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(top = 6.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(12.dp))
                Text("Il punto del giorno", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.themes.isNotEmpty()) {
                    TextButton(onClick = onOpenAll) { Text("Tutti") }
                }
            }
            when {
                state.themes.isNotEmpty() -> {
                    Spacer(Modifier.height(4.dp))
                    state.themes.take(4).forEachIndexed { index, theme ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        RecapRow(theme, onClick = { onOpenTheme(theme) })
                    }
                }
                state.error != null -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = state.retry, modifier = Modifier.padding(top = 8.dp)) { Text("Riprova") }
                }
                else -> Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text("Raccolgo le notizie di oggi…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun RecapRow(theme: RecapTheme, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (theme.keywords.isNotEmpty()) {
            Text(
                theme.keywords.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            theme.headline.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (theme.items.size > 1) "${theme.items.size} articoli · ${theme.sourceCount} ${if (theme.sourceCount == 1) "fonte" else "fonti"}"
            else theme.headline.source.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardMessage(
    icon: ImageVector,
    title: String,
    text: String,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

/** Scorciatoie a ciò che non sta nella barra in basso: dettaglio meteo, punto del giorno e player. */
@Composable
private fun Shortcuts(
    onOpenWeather: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenService: (MediaService) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ShortcutTile(Icons.Outlined.WbSunny, "Meteo", onOpenWeather)
        ShortcutTile(Icons.Outlined.AutoAwesome, "Il punto", onOpenRecap)
        ShortcutTile(Icons.Outlined.PlayCircle, "Video", onOpenVideo)
        ShortcutTile(Icons.Outlined.OndemandVideo, "YouTube") { onOpenService(MediaService.YOUTUBE) }
        ShortcutTile(Icons.Outlined.GraphicEq, "Spotify") { onOpenService(MediaService.SPOTIFY) }
    }
}

@Composable
private fun RowScope.ShortcutTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(22.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
