package com.platinum20.dashboard.ui.screens

import coil3.compose.AsyncImage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import com.platinum20.dashboard.today.HomeResume
import com.platinum20.dashboard.media.Playback
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.media.VideoLibrary
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.MediaHistory
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.platinum20.dashboard.Routes
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.news.NewsHistory
import com.platinum20.dashboard.news.NewsItem
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.Tomorrow
import com.platinum20.dashboard.today.WeekReview
import com.platinum20.dashboard.today.Greeting
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.news.RecapTheme
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.calendar.CalendarAsk
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.today.Invites
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.weather.LocationProvider
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherState
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.widget.DashboardWidget
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Headphones
import com.platinum20.dashboard.news.RecapReader
import com.platinum20.dashboard.news.RecapSpeaker
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bottomPadding: PaddingValues,
    onOpenNews: () -> Unit,
    onOpenWeather: () -> Unit,
    onOpenRecap: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenNotes: () -> Unit,
    onCustomize: () -> Unit,
    onSearch: () -> Unit = {},
    onOpenAutoFill: () -> Unit = {},
) {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

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
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        TabReselect.events.collect { if (it == Routes.HOME) listState.animateScrollToItem(0) }
    }
    val layout by AppSettings.homeLayout.collectAsStateWithLifecycle()
    var calendarRefresh by remember { mutableStateOf(0) }
    val agenda = rememberAgenda(calendarRefresh)
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsStateWithLifecycle()
    // Ricalcola "Adesso" ogni minuto: "tra 25 min" deve scendere da solo.
    val minute by produceState(LocalDateTime.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = LocalDateTime.now()
        }
    }
    // Saluto e data seguono l'orologio: lasciando l'app aperta la notte, la mattina dice "Buongiorno".
    val greeting = Greeting.forHour(minute.hour)
    val today = remember(minute.toLocalDate()) {
        minute.toLocalDate().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }
    }
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val mediaHistory by MediaHistory.items.collectAsStateWithLifecycle()
    val videos by VideoLibrary.recent.collectAsStateWithLifecycle()
    val playingNow by Playback.now.collectAsStateWithLifecycle()
    val somethingPlaying = playingNow?.let { it.playing || it.loading } == true
    val resume = remember(minute, mediaHistory, videos, somethingPlaying) {
        HomeResume.pick(mediaHistory + videos.map(ResumeItem::fromVideo), System.currentTimeMillis(), somethingPlaying)
    }
    // Dalle 18: com'è domani. Dopo mezzanotte "domani" è il giorno appena iniziato.
    val tomorrowPlan = remember(minute, weather.forecast, agenda.today, agenda.tomorrow, notes) {
        Tomorrow.build(
            minute, weather.forecast,
            events = if (minute.hour >= Tomorrow.FROM_HOUR) agenda.tomorrow else agenda.today,
            tasks = notes.openTasks, countdowns = notes.countdowns, fixed = notes.fixedExpenses,
        )
    }
    // Dalla domenica alle 17 al lunedì a mezzogiorno: com'è andata la settimana, finché non si chiude.
    var weekSeen by remember { mutableStateOf(0) }
    val weekMonday = WeekReview.weekToReview(minute)?.takeIf { !WeekReviewSeen.isSeen(context, it) }
    // Gli impegni della settimana nuova si leggono solo quando la card c'è.
    val weekEvents by produceState(emptyMap<LocalDate, List<CalendarEvent>>(), weekMonday, agenda.granted, calendarRefresh) {
        value = if (weekMonday != null && agenda.granted) CalendarRepository.eventsFrom(context, weekMonday.plusWeeks(1), 7) else emptyMap()
    }
    val weekReview = remember(weekMonday, notes, weekSeen, weekEvents) {
        weekMonday?.let { monday ->
            WeekReview.build(
                monday, notes.tasks, notes.habits, notes.expenses, notes.fixedExpenses, notes.parcels, notes.countdowns,
                events = weekEvents,
            ).takeIf { !it.isEmpty }
        }
    }
    val briefing = remember(minute, weather.forecast, agenda.today, agenda.tomorrow, notes, tomorrowPlan) {
        Briefing.build(
            minute, weather.forecast, agenda.today, agenda.tomorrow, notes.openTasks, notes.countdowns,
            habits = notes.habits, parcels = notes.parcels, expenses = notes.expenses, budgetCents = notes.budgetCents,
            fixedExpenses = notes.fixedExpenses, tomorrowCard = tomorrowPlan != null,
        )
    }
    // Azioni rapide di "Adesso": per qualche secondo si può annullare, come in Note.
    val haptic = rememberHaptic()
    var briefUndo by remember { mutableStateOf<BriefUndo?>(null) }
    LaunchedEffect(briefUndo) {
        if (briefUndo != null) {
            delay(5_000)
            briefUndo = null
        }
    }
    var habitIds by remember { mutableStateOf<List<Long>?>(null) }
    habitIds?.let { ids ->
        HabitsTodayDialog(
            habits = notes.habits.filter { it.id in ids },
            today = minute.toLocalDate(),
            onToggle = { haptic(Haptic.CONFIRM); NotesStore.toggleHabit(it.id, minute.toLocalDate()) },
            onDismiss = { habitIds = null },
        )
    }
    // Il widget mostra le stesse righe, con l'orario esatto perché si aggiorna solo ogni ora.
    LaunchedEffect(weather.forecast, agenda.today, agenda.tomorrow, notes) {
        if (agenda.granted && agenda.today == null) return@LaunchedEffect
        val lines = Briefing.build(LocalDateTime.now(), weather.forecast, agenda.today, agenda.tomorrow, notes.openTasks, notes.countdowns, max = 6, relative = false,
            habits = notes.habits, parcels = notes.parcels, expenses = notes.expenses, budgetCents = notes.budgetCents,
            fixedExpenses = notes.fixedExpenses,
        )
        DashboardWidget.saveBriefing(context, lines)
    }
    val agendaOff by CalendarAsk.off.collectAsStateWithLifecycle()
    AutoFill.init(context)
    val autoFillEnabled by AutoFill.enabled.collectAsStateWithLifecycle()
    val autoFillDismissed by AutoFill.introDismissed.collectAsStateWithLifecycle()
    // Una card compare solo se ha qualcosa da dire.
    val cards = layout.visible.filter { card ->
        when (card) {
            // Senza permesso l'agenda non occupa la cima: l'invito sta in fondo (InvitesRow).
            HomeCard.RESUME -> resume != null
            else -> true
        }
    }

    // Una card della pagina; la lista la divide per mettere sotto la prima le cose trovate da sole.
    val cardContent: @Composable (HomeCard) -> Unit = { card ->
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
                briefing = briefing,
                onBriefAction = { line ->
                    haptic(Haptic.CONFIRM)
                    if (line.action == BriefAction.HABITS) habitIds = line.targetIds
                    else BriefActions.perform(line, notes, minute.toLocalDate())?.let { briefUndo = it }
                },
                undo = briefUndo,
                dayCardShown = tomorrowPlan?.let { it.day == minute.toLocalDate() } == true,
                onUndo = {
                    haptic(Haptic.TICK)
                    briefUndo?.undo?.invoke()
                    briefUndo = null
                },
                onOpenBrief = { kind ->
                    when (kind) {
                        BriefKind.EVENT -> inApp?.openCalendar?.invoke()
                        BriefKind.TASK, BriefKind.DATE, BriefKind.HABIT, BriefKind.PARCEL, BriefKind.MONEY -> onOpenNotes()
                        BriefKind.RAIN, BriefKind.WEATHER -> onOpenWeather()
                        BriefKind.FREE -> Unit
                    }
                },
            )
            HomeCard.DAY -> DayCard(agenda, onOpenNotes = onOpenNotes, tomorrowCard = tomorrowPlan != null)
            HomeCard.RESUME -> resume?.let { item ->
                ResumeCard(item) {
                    haptic(Haptic.TICK)
                    openResumeItem(context, item) { route -> inApp?.openRoute?.invoke(route) }
                }
            }
            HomeCard.RECAP -> RecapCard(
                state = recap,
                onOpenTheme = {
                    NewsHistory.init(context)
                    NewsHistory.markRead(it.headline.link)
                    onOpenArticle(it.headline)
                },
                onOpenAll = onOpenRecap,
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            // Il saluto e la data fanno da titolo: niente card in più che ripete il meteo.
            TopLevelBar(
                title = greeting,
                subtitle = today,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        // Si aggiorna da sola (meteo, notizie del riepilogo, agenda); trascinare dall'alto apre la ricerca.
        AutoRefresh {
            if (hasLocation || WeatherStore.manualPlace(context) != null) WeatherStore.refresh(context)
            runCatching { NewsStore.load() }
            calendarRefresh++
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = inner.calculateTopPadding()),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // La prima card (di solito Adesso), poi le cose trovate da sole in notifiche e SMS, poi le altre.
            // Le card che compaiono, spariscono o cambiano posto scivolano invece di saltare.
            items(cards.take(1), key = { it.name }) { Box(Modifier.animateItem()) { cardContent(it) } }
            tomorrowPlan?.let { plan ->
                item(key = "tomorrow") {
                    TomorrowCard(
                        plan = plan,
                        onOpenWeather = onOpenWeather,
                        onOpenCalendar = { inApp?.openCalendar?.invoke() },
                        onOpenNotes = onOpenNotes,
                    )
                }
            }
            weekReview?.let { review ->
                item(key = "week") {
                    WeekReviewCard(
                        review = review,
                        onOpenNotes = onOpenNotes,
                        onOpenCalendar = { inApp?.openCalendar?.invoke() },
                        onDismiss = {
                            haptic(Haptic.TICK)
                            WeekReviewSeen.markSeen(context, review.monday)
                            weekSeen++
                        },
                    )
                }
            }
            item(key = "autofill") { AutoFillCard(onOpenSettings = onOpenAutoFill) }
            items(cards.drop(1), key = { it.name }) { Box(Modifier.animateItem()) { cardContent(it) } }
            item(key = "invites") {
                InvitesRow(
                    invites = Invites.pending(
                        agendaVisible = !(agendaOff ?: CalendarAsk.isOff(context)),
                        calendarGranted = agenda.granted,
                        autoFillEnabled = autoFillEnabled,
                        autoFillDismissed = autoFillDismissed,
                    ),
                    onAllowCalendar = agenda.requestPermission,
                    onOpenAutoFill = onOpenAutoFill,
                    onDismissAutoFill = { AutoFill.dismissIntro() },
                )
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
            .onFailure { if (it !is CancellationException) error = it.userMessage("Notizie non disponibili.") }
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
    briefing: List<BriefLine>,
    onBriefAction: (BriefLine) -> Unit,
    undo: BriefUndo?,
    onUndo: () -> Unit,
    onOpenBrief: (BriefKind) -> Unit,
    /** true se sotto c'è la card "Oggi · …" della notte: allora qui niente massime, minime e pioggia. */
    dayCardShown: Boolean = false,
) {
    val forecast = state.forecast
    // "Adesso" è l'unico blocco colorato della pagina (colore del tema, Material You compreso): l'occhio parte da qui.
    CanvasCard(
        onClick = { if (forecast != null) onClick() else if (!hasPermission) onRequestPermission() else onRetry() },
        modifier = Modifier.fillMaxWidth().bouncyPress(0.98f),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        WeatherSummary(state, hasPermission, onRequestPermission, onRetry, dayCardShown)
        if (briefing.isNotEmpty() || undo != null) {
            Column(Modifier.padding(bottom = 10.dp)) {
                briefing.forEach { line -> BriefRow(line, onAction = { onBriefAction(line) }) { onOpenBrief(line.kind) } }
                undo?.let { u ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            u.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onUndo, modifier = Modifier.bouncyPress()) {
                            Text("Annulla", color = LocalContentColor.current)
                        }
                    }
                }
            }
        }
    }
}

/**
 * L'arancio di ciò che è urgente in "Adesso": fisso, non preso dal tema, così resta arancio anche
 * con Material You (dove il terziario viene dallo sfondo del telefono). Scuro col tema chiaro e
 * chiaro col tema scuro, sempre con contrasto almeno 4,5:1 sul blocco colorato.
 */
internal object Urgent {
    val onLight = Color(0xFFA63C17)
    val onDark = Color(0xFFFF9466)

    fun color(darkTheme: Boolean): Color = if (darkTheme) onDark else onLight

    /** Rapporto di contrasto WCAG tra due colori (1..21). */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }
}

/** Una riga di "Adesso": icona, testo, in evidenza se è urgente. */
@Composable
private fun BriefRow(line: BriefLine, onAction: () -> Unit, onClick: () -> Unit) {
    val icon = when (line.kind) {
        BriefKind.EVENT -> Icons.Outlined.Event
        BriefKind.RAIN -> Icons.Outlined.Umbrella
        BriefKind.TASK -> Icons.Outlined.TaskAlt
        BriefKind.DATE -> Icons.Outlined.Celebration
        BriefKind.WEATHER -> line.weatherCode?.let { weatherIcon(it) } ?: Icons.Outlined.WbSunny
        BriefKind.HABIT -> Icons.Outlined.Autorenew
        BriefKind.PARCEL -> Icons.Outlined.LocalShipping
        BriefKind.MONEY -> Icons.Outlined.Euro
        BriefKind.FREE -> Icons.Outlined.CheckCircle
    }
    val content = LocalContentColor.current
    val urgent = Urgent.color(darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val color = if (line.urgent) urgent else content.copy(alpha = 0.7f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (line.kind != BriefKind.FREE) Modifier.bouncyPress(0.98f) else Modifier)
            .clickable(enabled = line.kind != BriefKind.FREE, onClick = onClick)
            .padding(start = 18.dp, end = if (line.action != null) 12.dp else 18.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(12.dp))
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.urgent) content else content.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        line.action?.let { action ->
            Spacer(Modifier.size(8.dp))
            BriefActionButton(action, onClick = onAction)
        }
    }
}

@Composable
private fun WeatherSummary(
    state: WeatherState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    dayCardShown: Boolean,
) {
    val forecast = state.forecast
    when {
        forecast != null -> WeatherNowHeader(forecast, state.loading, dayCardShown)
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

/** "Riprendi": copertina, titolo e punto da cui riparte; un tocco e riparte da lì. */
@Composable
private fun ResumeCard(item: ResumeItem, onClick: () -> Unit) {
    CanvasCard(
        onClick = onClick,
        onLongClick = { SavedLinks.offer(SavedLink.Media(item)) },
        modifier = Modifier.fillMaxWidth().bouncyPress(0.98f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 72.dp, height = 48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.image.isNotBlank()) {
                    AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
                if (item.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Riprendi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                HomeResume.detail(item).takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.size(8.dp))
            Icon(Icons.Filled.PlayArrow, contentDescription = "Riprendi", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun RecapCard(
    state: RecapUiState,
    onOpenTheme: (RecapTheme) -> Unit,
    onOpenAll: () -> Unit,
) {
    Section(
        title = "Il punto del giorno",
        action = if (state.themes.isNotEmpty()) "Tutti" else null,
        onAction = onOpenAll,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            when {
                state.themes.isNotEmpty() -> {
                    state.themes.take(4).forEachIndexed { index, theme ->
                        if (index > 0) RowDivider()
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
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Il titolo vero si legge come in un giornale: niente terna di parole chiave sopra.
        Text(
            theme.headline.title,
            style = MaterialTheme.typography.titleSmall,
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
        // Sta dentro "Adesso", sul fondo scuro: colori presi dal contenuto della card.
        val content = LocalContentColor.current
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f))
        }
        Spacer(Modifier.size(8.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}
