package com.platinum20.dashboard.ui.screens

import coil3.compose.AsyncImage
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import com.platinum20.dashboard.today.HomeResume
import com.platinum20.dashboard.today.Upcoming
import com.platinum20.dashboard.today.UpcomingItem
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
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
import com.platinum20.dashboard.today.Greeting
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.news.NewsFeed
import com.platinum20.dashboard.news.NewsStories
import com.platinum20.dashboard.news.reader.ArticleVerifier
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.mail.GmailAuth
import com.platinum20.dashboard.mail.GmailImap
import com.platinum20.dashboard.mail.MailStore
import com.platinum20.dashboard.calendar.CalendarAsk
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.today.Invites
import com.platinum20.dashboard.settings.HomeCard
import com.platinum20.dashboard.weather.LocationProvider
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherState
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.widget.NewsWidget
import com.platinum20.dashboard.widget.Widgets
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
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
    onOpenArticle: (NewsItem) -> Unit,
    onOpenNotes: () -> Unit,
    onCustomize: () -> Unit,
    onSearch: () -> Unit = {},
    onOpenAutoFill: () -> Unit = {},
    onOpenNote: (Long) -> Unit = {},
) {
    val context = LocalContext.current
    val openItem = rememberOpenItem()
    val inApp = LocalInAppNav.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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

    val news = rememberNewsCardState()
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
    // La data è il titolo, il saluto la riga piccola sopra (1.402): la data serve, il saluto no.
    val today = remember(minute.toLocalDate()) { Greeting.dateTitle(minute.toLocalDate()) }
    MediaHistory.init(context)
    VideoLibrary.init(context)
    val mediaHistory by MediaHistory.items.collectAsStateWithLifecycle()
    val videos by VideoLibrary.recent.collectAsStateWithLifecycle()
    val playingNow by Playback.now.collectAsStateWithLifecycle()
    val somethingPlaying = playingNow?.let { it.playing || it.loading } == true
    val resume = remember(minute, mediaHistory, videos, somethingPlaying) {
        HomeResume.pick(mediaHistory + videos.map(ResumeItem::fromVideo), System.currentTimeMillis(), somethingPlaying)
    }
    // "Prossimi giorni": la settimana da domani, in un posto solo.
    val weekAhead by produceState(emptyMap<LocalDate, List<CalendarEvent>>(), minute.toLocalDate(), agenda.granted, calendarRefresh) {
        value = if (agenda.granted) CalendarRepository.eventsFrom(context, minute.toLocalDate().plusDays(1), Upcoming.DAYS) else emptyMap()
    }
    // I turni di Creators Pro: con la Home aperta si rileggono spesso, e ogni cambio ridisegna il calendario.
    remember { com.platinum20.dashboard.shifts.ShiftStore.init(context) }
    val shifts by com.platinum20.dashboard.shifts.ShiftStore.shifts.collectAsStateWithLifecycle()
    val workPay by com.platinum20.dashboard.shifts.ShiftStore.pay.collectAsStateWithLifecycle()
    LaunchedEffect(shifts, workPay) { calendarRefresh++ }
    AutoRefresh(everyMs = com.platinum20.dashboard.shifts.ShiftStore.STALE_MS) {
        val work = com.platinum20.dashboard.shifts.ShiftStore
        if (work.linked.value && !work.expired.value && work.isStale()) com.platinum20.dashboard.shifts.CreatorsLink.refresh(context)
    }
    // Il rinnovo dell'offerta SIM va fra i prossimi giorni (Antonio).
    remember { com.platinum20.dashboard.sim.SimStore.init(context) }
    val sim by com.platinum20.dashboard.sim.SimStore.status.collectAsStateWithLifecycle()
    val simLinked by com.platinum20.dashboard.sim.SimStore.linked.collectAsStateWithLifecycle()
    val simRenewal = sim?.renewal?.takeIf { simLinked }
    // Il calendario unico (1.314): oggi in cima con le note senza data, poi i prossimi giorni.
    val calendar = remember(minute, agenda.today, weekAhead, notes, simRenewal) {
        dueDays(
            notes.tasks, notes.parcels, agenda.today.orEmpty() + weekAhead.values.flatten(),
            simRenewal = simRenewal, withToday = true, notes = notes.notes,
        )
    }
    val briefing = remember(minute, weather.forecast, agenda.today, agenda.tomorrow, notes) {
        Briefing.build(
            minute, weather.forecast, agenda.today, agenda.tomorrow, notes.openItems,
            habits = notes.habits, parcels = notes.parcels,
            withTomorrow = false, wholeDay = true, max = Int.MAX_VALUE,
        ).filter { it.kind in ADESSO_KINDS }.take(Briefing.CARD_MAX)
    }
    // Azioni rapide di "Adesso": per qualche secondo si può annullare, come in Note.
    val haptic = rememberHaptic()
    var habitIds by remember { mutableStateOf<List<Long>?>(null) }
    habitIds?.let { ids ->
        HabitsTodayDialog(
            habits = notes.habits.filter { it.id in ids },
            today = minute.toLocalDate(),
            onToggle = { haptic(Haptic.CONFIRM); NotesStore.toggleHabit(it.id, minute.toLocalDate()) },
            onDismiss = { habitIds = null },
        )
    }
    // I widget ricalcolano Adesso e il calendario da soli: qui solo il segnale che gli eventi sono cambiati.
    LaunchedEffect(agenda.today, agenda.tomorrow) {
        if (agenda.today != null) Widgets.refresh(context)
    }
    val agendaOff by CalendarAsk.off.collectAsStateWithLifecycle()
    AutoFill.init(context)
    val autoFillEnabled by AutoFill.enabled.collectAsStateWithLifecycle()
    val autoFillDismissed by AutoFill.introDismissed.collectAsStateWithLifecycle()
    // Gli accessi scaduti finiscono nella riga Impostazioni in fondo (punto 21 D).
    com.platinum20.dashboard.shifts.ShiftStore.init(context)
    val shiftsExpired by com.platinum20.dashboard.shifts.ShiftStore.expired.collectAsStateWithLifecycle()
    MailStore.init(context)
    com.platinum20.dashboard.docs.DocumentsStore.init(context)
    val docs by com.platinum20.dashboard.docs.DocumentsStore.docs.collectAsStateWithLifecycle()
    val gmailOAuth by GmailAuth.connected.collectAsStateWithLifecycle()
    val gmailImap by GmailImap.linked.collectAsStateWithLifecycle()
    val gmailConnected = gmailOAuth || gmailImap
    // Le cose senza scadenza (da fare, note, foto, salvati): la card "Per dopo", fuori dal Calendario.
    val loose = remember(calendar, notes, minute) { looseItems(calendar.filter { it.rows.isNotEmpty() }, notes.notes, minute.toLocalDate()).pending() }
    val archived = remember(notes) { notes.tasks.any { it.tickable && it.done } }
    // Una card compare solo se ha qualcosa da dire.
    val cards = layout.visible.filter { card ->
        when (card) {
            // Senza permesso l'agenda non occupa la cima: l'invito sta in fondo (InvitesRow).
            // La card media resta anche quando c'è il mini player: allora mostra quello che suona.
            HomeCard.RESUME -> resume != null || playingNow != null
            HomeCard.MAIL -> gmailConnected
            HomeCard.DOCS -> docs.isNotEmpty()
            // Vuota non c'è: compare quando hai qualcosa per dopo.
            // Compare anche vuota se c'è l'archivio: si apre da qui.
            HomeCard.LATER -> loose.isNotEmpty() || archived
            // Niente di nuovo da leggere: la card News sparisce invece di dire che sei in pari (punto 20 A).
            HomeCard.RECAP -> news.loading || news.error != null || news.feed.fresh.isNotEmpty()
            else -> true
        }
    }

    // Una card della pagina, nel posto scelto in Personalizza.
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
                    else BriefActions.perform(line, notes, minute.toLocalDate())?.let { u ->
                        // L'Annulla è il fumetto di vetro in fondo, come ovunque: la card Adesso resta ferma.
                        com.platinum20.dashboard.capture.CaptureFeedback.show(
                            com.platinum20.dashboard.capture.CaptureNotice(
                                com.platinum20.dashboard.capture.CaptureMode.TASK, u.message, undo = { u.undo() },
                                icon = Icons.Outlined.TaskAlt, fly = false,
                            ),
                        )
                    }
                },
                onOpenBrief = { line ->
                    // La riga di una cosa sola apre la sua scheda (Antonio); le righe che ne riassumono tante, la pagina.
                    val id = line.openId
                    val event = line.event
                    when {
                        event != null -> inApp?.openEvent?.invoke(event)
                        id != null && (line.kind == BriefKind.TASK || line.kind == BriefKind.DATE) -> openItem(ItemKind.TASK, id)
                        id != null && line.kind == BriefKind.PARCEL -> openItem(ItemKind.PARCEL, id)
                        else -> when (line.kind) {
                            BriefKind.EVENT -> inApp?.openCalendar?.invoke()
                            BriefKind.MONEY -> inApp?.openRoute?.invoke(Routes.SIM)
                            BriefKind.TASK, BriefKind.DATE, BriefKind.HABIT, BriefKind.PARCEL -> onOpenNotes()
                            BriefKind.RAIN, BriefKind.WEATHER -> onOpenWeather()
                            BriefKind.FREE -> Unit
                        }
                    }
                },
            )
            HomeCard.DAY -> CalendarCard(
                groups = calendar,
                today = minute.toLocalDate(),
                habits = notes.habits,
                onOpenAll = {
                    haptic(Haptic.TICK)
                    onOpenNotes()
                },
            )
            // La stessa card di Media: quello che suona adesso oppure l'ultima cosa da riprendere.
            HomeCard.RESUME -> playingNow?.let { playing ->
                MediaNowCard(playing, hero = false, onOpen = {
                    haptic(Haptic.TICK)
                    inApp?.openRoute?.invoke(Routes.PLAYER)
                })
            } ?: resume?.let { item ->
                MediaResumeCard(item, hero = false, onClick = {
                    haptic(Haptic.TICK)
                    openResumeItem(context, item) { route -> inApp?.openRoute?.invoke(route) }
                })
            }
            HomeCard.LATER -> LaterCard(loose, onOpenArchive = {
                haptic(Haptic.TICK)
                inApp?.openRoute?.invoke(Routes.LATER_ARCHIVE)
            })
            HomeCard.SIM -> ContiCard(onOpenSim = { inApp?.openRoute?.invoke(Routes.SIM) })
            HomeCard.DOCS -> DocumentsCard(onOpen = { id ->
                haptic(Haptic.TICK)
                inApp?.openRoute?.invoke(Routes.document(id))
            })
            HomeCard.MAIL -> FromMailCard(onOpenMail = { id -> inApp?.openRoute?.invoke(Routes.mail(id)) })
            HomeCard.RECAP -> NewsCard(
                state = news,
                onOpenArticle = {
                    NewsHistory.init(context)
                    NewsHistory.markRead(it.link)
                    onOpenArticle(it)
                },
                onOpenNews = onOpenNews,
            )
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = topLevelInsets,
        topBar = {
            // La data fa da titolo, il saluto sta piccolo sopra; scorrendo resta solo "Martedì 10".
            TopLevelBar(
                title = today,
                subtitle = Greeting.subtitle(minute.hour, minute.toLocalDate()),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        // Si aggiorna da sola (meteo, notizie del riepilogo, agenda); trascinare dall'alto apre la ricerca.
        AutoRefresh {
            if (hasLocation || WeatherStore.manualPlace(context) != null) WeatherStore.refresh(context)
            runCatching { NewsStore.load() }
            if (MailStore.connected()) runCatching { MailStore.sync(context) }
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
            // Le card nell'ordine scelto in Personalizza, senza animazioni della lista: anche solo la sfumata (1.472)
            // lasciava la SIM sopra il fondo del Calendario quando questo cambiava altezza (Antonio, 2026-11-11 e 11-12).
            // Le card entrano già con la loro cascata ([entrance]).
            // Le cose da sistemare stanno in cima, sotto il titolo (1.528): una riga arancione, solo se ce ne sono.
            val invites = Invites.pending(
                agendaVisible = !(agendaOff ?: CalendarAsk.isOff(context)),
                calendarGranted = agenda.granted,
                autoFillEnabled = autoFillEnabled,
                autoFillDismissed = autoFillDismissed,
                shiftsExpired = shiftsExpired,
            )
            if (invites.isNotEmpty()) item(key = "invites") {
                InvitesRow(
                    invites = invites,
                    onAllowCalendar = agenda.requestPermission,
                    onOpenAutoFill = onOpenAutoFill,
                    onDismissAutoFill = { AutoFill.dismissIntro() },
                )
            }
            items(cards, key = { it.name }) { cardContent(it) }
        }
        }
    }
}

/** La card News in Home: le storie divise dal segno dell'ultima visita, come nella scheda. */
data class NewsCardState(
    val feed: NewsFeed = NewsFeed(emptyList(), emptyList(), null),
    val loading: Boolean = true,
    val error: String? = null,
    val retry: () -> Unit = {},
)

/**
 * Scarica le notizie (se non già in memoria), controlla quali si possono leggere e ne ricava le storie
 * nuove dall'ultima visita alle News. Solo articoli che il lettore sa aprire, come nella scheda.
 */
@Composable
fun rememberNewsCardState(): NewsCardState {
    val result by NewsStore.result.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        runCatching { NewsStore.load(force = attempt > 0) }
            .onFailure { if (it !is CancellationException) error = it.userMessage("Notizie non disponibili.") }
    }
    LaunchedEffect(result) { result?.let { NewsStore.verifyLatest(it.items) } }
    val context = LocalContext.current
    NewsHistory.init(context)
    val verification by ArticleVerifier.state.collectAsStateWithLifecycle()
    val hidden by AppSettings.hiddenNewsSources.collectAsStateWithLifecycle()
    val since by NewsHistory.lastVisitFlow.collectAsStateWithLifecycle()
    val readList by NewsHistory.read.collectAsStateWithLifecycle()
    val stories = rememberStories(result?.items.orEmpty(), verification.readable, hidden)
    val feed = remember(stories, since, readList) { NewsStories.split(stories, since, readList.toSet()) }
    // Il widget mostra le stesse storie nuove della Home.
    LaunchedEffect(feed) {
        val forWidget = NewsStories.forWidget(feed)
        if (forWidget.isNotEmpty()) NewsWidget.save(context, forWidget, feed.fresh.size)
    }
    return NewsCardState(
        feed = feed,
        loading = feed.isEmpty && error == null && (result == null || verification.pending > 0 || stories.isEmpty() && result?.items?.isNotEmpty() == true),
        error = error,
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
    onOpenBrief: (BriefLine) -> Unit,
) {
    val forecast = state.forecast
    // "Adesso" è l'unico blocco colorato della pagina (colore del tema, Material You compreso): l'occhio parte da qui.
    CanvasCard(
        onClick = { if (forecast != null) onClick() else if (!hasPermission) onRequestPermission() else onRetry() },
        modifier = Modifier.fillMaxWidth().sharedItem(SharedKeys.WEATHER, rounded = true).bouncyPress(0.98f),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        WeatherSummary(state, hasPermission, onRequestPermission, onRetry, alone = briefing.isEmpty() && forecast == null)
        // Le prossime ore dentro Adesso (1.528, Antonio scelta B): il meteo si legge senza aprire la pagina.
        if (forecast != null) {
            HourStrip(
                forecast.hourly,
                Modifier.padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = if (briefing.isEmpty()) 14.dp else 8.dp),
            )
        }
        if (briefing.isNotEmpty()) {
            Column(Modifier.padding(bottom = 10.dp)) {
                briefing.forEach { line -> BriefRow(line, onAction = { onBriefAction(line) }) { onOpenBrief(line) } }
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
    /** Nessuna riga sotto: stesso margine sopra e sotto, così il meteo sta al centro della card. */
    alone: Boolean,
) {
    val forecast = state.forecast
    when {
        forecast != null -> WeatherNowCompact(forecast, state.loading, bottom = if (alone) 14.dp else 6.dp)
        !hasPermission -> CardMessage(
            icon = Icons.Outlined.LocationOn,
            title = "Meteo",
            text = "Consenti la posizione per vedere il meteo del posto in cui sei.",
            action = "Consenti",
            onAction = onRequestPermission,
        )
        // Mentre cerca: la forma del meteo in grigio, già alla sua altezza, invece della rotella.
        state.loading || state.error == null -> Row(
            modifier = Modifier
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp)
                .semantics(mergeDescendants = true) { contentDescription = "Cerco il meteo della tua zona" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // La stessa forma dell'eroe compatto: icona, temperatura, una riga.
            Placeholder(30.dp, 30.dp, shape = CircleShape)
            Spacer(Modifier.size(10.dp))
            Placeholder(56.dp, 32.dp)
            Spacer(Modifier.size(12.dp))
            Placeholder(130.dp, 12.dp)
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

/**
 * La card News: quante storie nuove dall'ultima visita e le prime tre, una riga per fatto. Senza nuove resta
 * una riga sola che dice che sei in pari. Un tocco fuori dalle righe apre la scheda News.
 */
@Composable
private fun NewsCard(
    state: NewsCardState,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenNews: () -> Unit,
) {
    val context = LocalContext.current
    run {
        val feed = state.feed
        val shown = feed.fresh.take(3)
        // "25 nuove dalle 18:40" sta sulla riga del titolo, tutto a destra: una riga in meno (Antonio, 2026-11-10).
        CardSection("News", onClick = onOpenNews, trailing = {
            if (!feed.isEmpty) Text(
                NewsStories.homeLine(if (feed.since == null) shown.size else feed.fresh.size, feed.since, Instant.now(), ::newsClock),
                style = MaterialTheme.typography.labelMedium,
                color = if (shown.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            )
        }) {
            Column(Modifier.padding(bottom = 4.dp)) {
                when {
                    !feed.isEmpty -> {
                        shown.forEachIndexed { index, story ->
                            if (index > 0) RowDivider()
                            StoryRow(
                                story,
                                read = { false },
                                onOpen = onOpenArticle,
                                onLongClick = { ActionBubble.show(*storyActions(context, story) {}) },
                                expandable = false,
                                thumb = if (index == 0) 56.dp else 0.dp,
                                horizontal = 16.dp,
                            )
                        }
                    }
                    state.error != null -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(state.error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CanvasButton("Riprova", onClick = state.retry, modifier = Modifier.padding(top = 8.dp))
                    }
                    // Mentre raccoglie: tre righe grigie al posto delle storie, così la card non si allunga dopo.
                    else -> Column(
                        Modifier.semantics(mergeDescendants = true) { contentDescription = "Raccolgo le notizie" },
                    ) {
                        listOf(0.82f, 0.64f, 0.74f).forEachIndexed { index, w ->
                            if (index > 0) RowDivider()
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PlaceholderLine(w, 14.dp)
                                PlaceholderLine(w * 0.6f, 10.dp)
                            }
                        }
                    }
                }
            }
        }
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

/**
 * Che cosa resta in "Adesso" dalla 1.314: il meteo, la pioggia, le abitudini. Impegni, promemoria, date e pacchi
 * di oggi stanno in cima al calendario, un posto solo per ogni cosa (Antonio).
 */
// Le abitudini stanno nel Calendario, sotto OGGI (punto 19): in Adesso solo pioggia e meteo.
private val ADESSO_KINDS = setOf(BriefKind.RAIN, BriefKind.WEATHER)
