package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.Routes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist as ChecklistIcon
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.OutlinedIconToggleButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.LocalDateTime
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.platinum20.dashboard.notes.DateIdeas
import com.platinum20.dashboard.notes.ReceiptReader
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import com.platinum20.dashboard.notes.HabitIdea
import com.platinum20.dashboard.notes.HabitIdeas
import com.platinum20.dashboard.notes.DateIdea
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.notes.Checklist
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.HabitReminder
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.capture.captureTarget
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeKind
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Podcasts
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.calendar.CalendarAsk
import androidx.lifecycle.repeatOnLifecycle
import com.platinum20.dashboard.capture.CaptureDialog
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureSave
import com.platinum20.dashboard.capture.CaptureResult
import com.platinum20.dashboard.notes.QuickParse
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.border

/** Apre le Note già su un tipo: la card Prossimi giorni di Oggi porta a tutto quello che arriva. */
object NotesNav {
    val request = kotlinx.coroutines.flow.MutableStateFlow<NotesSection?>(null)

    fun open(section: NotesSection) {
        request.value = section
    }
}

/**
 * Le due parti delle Note (1.187): "Prossimi giorni", tutto quello che arriva un giorno (con il Calendario
 * dentro, lo stesso nome della card di Oggi), e "Note", tutto quello senza data. Le spese stanno in Oggi.
 */
enum class NotesSection(val title: String) { DATED("Prossimi giorni"), UNDATED("Note") }

/**
 * Quanto c'è in ciascuna parte ("Prossimi giorni · 4", "Note · 7"):
 * solo i tipi che hanno qualcosa, così ogni pillola dice qualcosa e porta da qualche parte.
 * Le abitudini stanno con le scadenze: si fanno oggi. [events] sono gli eventi del calendario in lista.
 */
internal fun notesChips(data: com.platinum20.dashboard.notes.NotesData, today: LocalDate = LocalDate.now(), events: Int = 0): List<Pair<NotesSection, String>> {
    val dated = data.tasks.count { !it.done && it.dueAt != null } + data.upcoming.size + data.parcels.count { !it.delivered } + events
    val undated = data.tasks.count { !it.done && it.dueAt == null } + data.notes.size
    return listOfNotNull(
        (dated > 0 || data.habits.isNotEmpty()).takeIf { it }?.let { NotesSection.DATED to if (dated > 0) "Prossimi giorni · $dated" else "Prossimi giorni" },
        (undated > 0).takeIf { it }?.let { NotesSection.UNDATED to "Note · $undated" },
    )
}

/**
 * La pagina delle Note: i tre tipi, uno per card, con le prime cose di ognuno.
 * Toccando una card si apre il tipo intero (le scadenze divise per giorno, le spese per mese).
 */
@Composable
private fun NotesOverview(
    data: com.platinum20.dashboard.notes.NotesData,
    timeline: List<DueDay>,
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (NotesSection) -> Unit,
) {
    val dated = timeline.flatMap { d -> d.rows.map { d to it } }
    val habitsLeft = data.habits.count { !it.isDone(LocalDate.now()) }
    val undatedTasks = data.tasks.filter { !it.done && it.dueAt == null }
    val notes = data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.createdAt })
    LazyColumn(state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "dated") {
            NotesCategoryCard(
                title = NotesSection.DATED.title,
                icon = Icons.Outlined.Event,
                accent = TileAccent.BLUE,
                count = (dated.size + data.habits.size).takeIf { it > 0 }?.toString(),
                lines = dated.take(3).map { (day, row) -> day.shortLabel() to row.title() } +
                    listOfNotNull(habitsLeft.takeIf { it > 0 }?.let { "Oggi" to if (it == 1) "1 abitudine da fare" else "$it abitudini da fare" }),
                empty = "Impegni, promemoria, date, pacchi e addebiti, per giorno",
                modifier = Modifier.animateItem()
                    .captureTarget(CaptureMode.TASK).captureTarget(CaptureMode.DATE).captureTarget(CaptureMode.PARCEL)
                    .captureTarget(CaptureMode.EVENT).captureTarget(CaptureMode.HABIT),
                onClick = { onOpen(NotesSection.DATED) },
            )
        }
        item(key = "undated") {
            NotesCategoryCard(
                title = NotesSection.UNDATED.title,
                icon = Icons.Outlined.EditNote,
                accent = TileAccent.ORANGE,
                count = (undatedTasks.size + notes.size).takeIf { it > 0 }?.toString(),
                lines = (undatedTasks.map { "Da fare" to it.text } + notes.map { "Nota" to it.text.lineSequence().first().trim() }).take(3),
                empty = "Tutto quello senza data: cose da fare e appunti",
                modifier = Modifier.animateItem().captureTarget(CaptureMode.NOTE),
                onClick = { onOpen(NotesSection.UNDATED) },
            )
        }
    }
}

/** Il titolo di una cosa con scadenza, qualunque sia. */
private fun DueRow.title(): String =
    task?.text ?: countdown?.title ?: parcel?.title ?: event?.title ?: charge?.let { "Addebito: ${it.text.ifEmpty { it.category.label }}" } ?: ""

/** Una card della pagina Note: icona, nome del tipo, quanto c'è a destra e le prime righe sotto. */
@Composable
private fun NotesCategoryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: TileAccent,
    count: String?,
    lines: List<Pair<String, String>>,
    empty: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CanvasCard(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, accent, size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (count != null) {
                    Text(count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (lines.isEmpty()) {
                Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else lines.forEach { (label, text) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Quante note si vedono prima di "Tutte le note": le fissate e le ultime toccate. */
internal const val NOTES_PREVIEW = 4

/**
 * Sezione "Note": tre card, una per tipo (con scadenza, senza scadenza, spese), con le prime cose di ognuno.
 * Toccandone una si apre il tipo intero: le scadenze divise per giorno, le note, le spese del mese.
 * Si scrive con il + della barra e il tipo lo sceglie Canvas da solo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bottomPadding: PaddingValues,
    onOpenNote: (Long?) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit = {},
) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf<NotesSection?>(null) }
    var allNotes by rememberSaveable { mutableStateOf(false) }
    val inApp = LocalInAppNav.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        NotesNav.request.collect { wanted ->
            if (wanted != null) {
                section = wanted
                searching = false
                NotesNav.request.value = null
            }
        }
    }

    // Eliminare è immediato, ma per qualche secondo si può tornare indietro: lo stesso fumetto di vetro
    // in fondo delle conferme del +, chiudibile di lato, senza il pallino che vola.
    fun offerUndo(message: String, restore: () -> Unit) {
        CaptureFeedback.show(
            CaptureNotice(CaptureMode.NOTE, message, undo = { restore() }, icon = Icons.Outlined.Delete, fly = false),
        )
    }

    fun offerUndo(removed: List<TaskItem>, message: String) {
        if (removed.isNotEmpty()) offerUndo(message) { NotesStore.restoreTasks(removed) }
    }

    // Una nota eliminata dal suo editor: l'"Annulla" compare qui, appena si torna alla lista.
    LaunchedEffect(Unit) {
        DeletedNote.take()?.let { note -> offerUndo("Nota eliminata") { NotesStore.restoreNote(note) } }
    }

    val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overdue = data.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < System.currentTimeMillis() }
    val dueToday = data.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < endOfDay } - overdue

    val habitRows = habitItems(data.habits, onDeleted = { h -> offerUndo("Abitudine eliminata") { NotesStore.restoreHabit(h) } })
    // Ogni cosa si apre a schermo intero, come gli eventi del calendario.
    val openItem = rememberOpenItem()
    val haptic = rememberHaptic()
    // Gli eventi del calendario sono cose con scadenza come le altre: si rileggono ogni volta che si torna qui,
    // ma la pagina parte da quelli già letti, così le card sono subito alla loro grandezza e non si allargano.
    var events by remember { mutableStateOf(NotesEvents.cached(context)) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            events = NotesEvents.load(context)
        }
    }
    // Per scrivere c'è il + della barra, uguale in tutta l'app: qui niente campo fisso in cima.
    val listState = rememberReselectListState(Routes.NOTES)


    // Con una pillola accesa, "indietro" torna a tutta la pagina.
    androidx.activity.compose.BackHandler(enabled = section != null && !searching) { section = null }
    // Ogni pagina (i tre tipi, o uno solo) parte dalla cima.
    LaunchedEffect(section) { runCatching { listState.scrollToItem(0) } }
    val timeline = dueDays(data.tasks, data.upcoming, data.parcels, events, data.fixedExpenses)
    // Sopra, scorrendo in su, il passato: cose fatte, pacchi arrivati, eventi finiti.
    val past = pastDays(data.tasks, data.parcels, events)
    // Dove comincia "adesso" dentro la card della linea del tempo: la pagina si apre lì, non dal passato.
    var nowOffset by remember(section) { mutableStateOf<Int?>(null) }
    var startedAtNow by remember(section) { mutableStateOf(false) }
    // Senza il permesso del calendario qui c'è l'invito (prima stava nella pagina Calendario).
    var calendarGranted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    val calendarAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarGranted = it
        if (it) scope.launch { events = NotesEvents.load(context) }
        else if (CalendarAsk.shouldHide(CalendarAsk.recordDenial(context))) hideAgenda(context)
    }
    val calendarOff by CalendarAsk.off.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = topLevelInsets,
        // Come le altre sezioni: solo il titolo in alto, tutto il resto scorre. Dentro un tipo, il titolo
        // è il tipo con la freccia per tornare ai tre.
        topBar = {
            TopLevelBar(
                title = section?.title ?: "Note",
                subtitle = if (section != null) null else listOfNotNull(
                    dueToday.takeIf { it > 0 }?.let { "$it oggi" },
                    overdue.takeIf { it > 0 }?.let { if (it == 1) "1 scaduta" else "$it scadute" },
                ).joinToString(" · ").ifEmpty { "Cattura e scadenze" },
                onBack = if (section != null) ({ section = null }) else null,
            )
        },
    ) { inner ->
        val padding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = inner.calculateTopPadding() + 12.dp,
            bottom = bottomPadding.calculateBottomPadding() + 24.dp,
        )
        if (searching) {
            NotesSearchPanel(
                data = data,
                padding = padding,
                onClose = { searching = false },
                onOpenNote = { onOpenNote(it) },
                onOpenExpenses = { searching = false; inApp?.openRoute?.invoke(Routes.EXPENSES) },
            )
            return@Scaffold
        }
        if (section == null) {
            // I tre tipi e basta: si tocca uno per vederlo tutto, diviso per giorno o per mese.
            NotesOverview(
                data = data,
                timeline = timeline,
                padding = padding,
                listState = listState,
                onOpen = { section = it },
            )
            return@Scaffold
        }
        fun show(part: NotesSection) = section == null || section == part
        val onTaskDeleted: (TaskItem) -> Unit = { offerUndo(listOf(it), "Promemoria eliminato") }
        val undatedTasks = data.tasks.filter { !it.done && it.dueAt == null }
        val done = data.tasks.filter { it.done }
        // Completati tiene solo quello che non sta già nel passato della linea del tempo.
        val inPast = past.flatMap { it.rows }.map { it.key }.toSet()
        val delivered = data.parcels.filter { it.delivered && "parcel-${it.id}" !in inPast }.sortedByDescending { it.deliveredOn }
        // I promemoria senza data spuntati restano in Note, in fondo; qui solo quelli con una data.
        val doneShown = done.filter { it.dueAt != null && "task-${it.id}" !in inPast }
        val doneUndated = done.filter { it.dueAt == null }
        // In ordine di inserimento, le più nuove in cima: la linea del tempo a destra segue questo ordine.
        val notes = data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.createdAt })
        val today = LocalDate.now()
        val empty = if (section == NotesSection.DATED) timeline.isEmpty() && past.isEmpty() && data.habits.isEmpty() && done.isEmpty() && delivered.isEmpty()
        else undatedTasks.isEmpty() && notes.isEmpty() && doneUndated.isEmpty()
        val groups = past + timeline
        val invite = section == NotesSection.DATED && !calendarGranted && !(calendarOff ?: CalendarAsk.isOff(context))
        val listPadding = padding

        // Si apre sempre da adesso: il passato sta sopra, scorrendo in su.
        val timelineIndex = (if (empty) 1 else 0) + (if (invite) 1 else 0)
        LaunchedEffect(section, nowOffset) {
            val offset = nowOffset
            if (section == NotesSection.DATED && offset != null && !startedAtNow) {
                startedAtNow = true
                runCatching { listState.scrollToItem(timelineIndex, offset) }
            }
        }
        // Fra adesso e passato una piccola resistenza, con una vibrazione.
        val barrier = rememberPastBarrier(listState, timelineIndex, if (section == NotesSection.DATED) nowOffset else null)
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.nestedScroll(barrier),
        ) {
            if (empty) {
                item(key = "empty") {
                    EmptyHint(
                        "Tocca + e scrivi come parli: Canvas capisce da solo se c'è una data. \"venerdì alle 18 palestra\", " +
                            "\"compleanno di Marta 12 marzo\" o un pacco Amazon finiscono in Prossimi giorni, con i giorni che mancano; " +
                            "un'idea senza data in Note."
                    )
                }
            }
            if (invite) {
                item(key = "calendar-invite") {
                    CalendarInvite(
                        onAllow = { calendarAsk.launch(android.Manifest.permission.READ_CALENDAR) },
                        onLater = { hideAgenda(context) },
                    )
                }
            }
            // Prossimi giorni: la stessa linea del tempo della card di Oggi, ma con tutto.
            if (show(NotesSection.DATED) && groups.isNotEmpty()) {
                item(key = "timeline") {
                    val entries = timelineEntries(groups, today)
                    val nowIndex = entries.indexOfFirst { !it.first.past }
                    // Sullo sfondo, senza card: la linea è la pagina.
                    Column(
                        Modifier.fillMaxWidth()
                            .captureTarget(CaptureMode.TASK).captureTarget(CaptureMode.DATE)
                            .captureTarget(CaptureMode.PARCEL).captureTarget(CaptureMode.EVENT),
                    ) {
                        entries.forEachIndexed { index, (entry, row) ->
                            // Il confine fra passato e adesso: la pagina si apre qui, e qui la lista fa resistenza.
                            if (index == nowIndex && index > 0) {
                                TimelineNowMarker(Modifier.onGloballyPositioned { nowOffset = it.positionInParent().y.roundToInt() })
                            }
                            TimelineRow(
                                entry,
                                start = 0.dp,
                                top = index > 0,
                                bottom = index < entries.lastIndex,
                                onClick = {
                                    row.task?.let { openItem(ItemKind.TASK, it.id) }
                                    row.countdown?.let { openItem(ItemKind.DATE, it.id) }
                                    row.parcel?.let { openItem(ItemKind.PARCEL, it.id) }
                                    row.event?.let { onOpenEvent(it) }
                                    row.charge?.let { openItem(ItemKind.FIXED, it.id) }
                                },
                                onIcon = row.task?.takeIf { !it.done }?.let { task ->
                                    {
                                        haptic(Haptic.CONFIRM)
                                        NotesStore.toggleTask(task.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // Le abitudini si fanno oggi: sotto la linea del tempo.
            if (show(NotesSection.DATED) && data.habits.isNotEmpty()) {
                item(key = "habits-header") {
                    SectionHeader("Abitudini", modifier = Modifier.animateItem().padding(top = 12.dp, bottom = 4.dp).captureTarget(CaptureMode.HABIT))
                }
                habitRows()
            }
            // Note: promemoria da spuntare e appunti sulla stessa linea del tempo di Prossimi giorni,
            // per quando li hai scritti, i più nuovi in cima.
            if (show(NotesSection.UNDATED) && (undatedTasks.isNotEmpty() || notes.isNotEmpty())) {
                item(key = "undated-timeline") {
                    val entries = undatedEntries(undatedTasks, notes, today)
                    Column(Modifier.fillMaxWidth().captureTarget(CaptureMode.NOTE)) {
                        entries.forEachIndexed { index, (entry, row) ->
                            TimelineRow(
                                entry,
                                start = 0.dp,
                                top = index > 0,
                                bottom = index < entries.lastIndex,
                                onClick = {
                                    row.task?.let { openItem(ItemKind.TASK, it.id) }
                                    row.note?.let { onOpenNote(it.id) }
                                },
                                onIcon = row.task?.let { task ->
                                    {
                                        haptic(Haptic.CONFIRM)
                                        NotesStore.toggleTask(task.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // Sotto, i promemoria senza data già spuntati.
            if (show(NotesSection.UNDATED) && doneUndated.isNotEmpty()) {
                item(key = "undated-done-header") {
                    SectionHeader(
                        "Completati",
                        modifier = Modifier.animateItem().padding(top = 12.dp, bottom = 4.dp),
                        action = "Elimina",
                        onAction = {
                            NotesStore.clearDone { it.dueAt == null }
                            offerUndo(doneUndated, if (doneUndated.size == 1) "Eliminato 1 completato" else "Eliminati ${doneUndated.size} completati")
                        },
                    )
                }
                item(key = "undated-done") { Box(Modifier.animateItem()) { TaskGroup(null, doneUndated, onTaskDeleted) } }
            }
            // In fondo quello che è già fatto: promemoria spuntati e pacchi arrivati.
            if (show(NotesSection.DATED) && (doneShown.isNotEmpty() || delivered.isNotEmpty())) {
                item(key = "done-header") {
                    SectionHeader(
                        "Completati",
                        modifier = Modifier.animateItem().padding(top = 12.dp, bottom = 4.dp),
                        action = if (doneShown.isNotEmpty()) "Elimina" else null,
                        onAction = {
                            NotesStore.clearDone { t -> doneShown.any { it.id == t.id } }
                            offerUndo(doneShown, if (doneShown.size == 1) "Eliminato 1 completato" else "Eliminati ${doneShown.size} completati")
                        },
                    )
                }
                if (doneShown.isNotEmpty()) item(key = "group-done") { Box(Modifier.animateItem()) { TaskGroup(null, doneShown, onTaskDeleted) } }
                items(delivered, key = { "parcel-${it.id}" }) { p ->
                    Box(Modifier.animateItem().padding(bottom = 4.dp)) { ParcelCard(p, onClick = { openItem(ItemKind.PARCEL, p.id) }) }
                }
            }
            // Spazio in fondo: l'ultima riga non finisce sotto la barra.
            item(key = "bottom-space") { Spacer(Modifier.height(72.dp)) }
        }
        }
    }
}

/** Un conto alla rovescia: compleanno, viaggio o scadenza, con i giorni che mancano. */
@Composable
private fun DateCard(item: Countdown, onClick: () -> Unit) {
    val days = item.daysLeft()
    CanvasCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().bouncyPress(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.dateLine(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val (big, small) = dueBadge(item.nextDate())
            DueBadge(big, small)
        }
    }
}

/**
 * Abitudini di ogni giorno: un tocco per spuntare oggi, la serie di giorni di fila, l'ultima settimana.
 * Tiene il dialogo per modificarle e restituisce le righe della lista (niente se non ce ne sono).
 */
@Composable
private fun habitItems(habits: List<Habit>, onDeleted: (Habit) -> Unit): LazyListScope.() -> Unit {
    val openItem = rememberOpenItem()
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    return {
        items(habits, key = { "habit-${it.id}" }) { habit ->
            val doneToday = habit.isDone(today)
            val line = habit.statusLine(LocalDateTime.now())
            CanvasCard(
                onClick = { openItem(ItemKind.HABIT, habit.id) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().bouncyPress(0.98f),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(habit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            line.text + HabitReminder.suffix(habit),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (line.urgent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            habit.lastDays(today).forEach { (day, done) ->
                                // Il pallino è piccolo, ma si tocca tutta la colonna (pallino e iniziale del giorno).
                                Column(
                                    Modifier
                                        .bouncyPress(0.9f)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            haptic(if (done) Haptic.TICK else Haptic.CONFIRM)
                                            NotesStore.toggleHabit(habit.id, day)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    // Oggi non ancora fatto: un anello, così si vede che manca.
                                    val missingToday = !done && day == today
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    done -> MaterialTheme.colorScheme.primary
                                                    missingToday -> Color.Transparent
                                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            )
                                            .then(
                                                if (missingToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                else Modifier
                                            ),
                                    )
                                    Text(
                                        day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.ITALIAN),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    // Da fare: cerchio vuoto con il bordo; fatta: pieno con l'accento e la spunta.
                    val toggle: (Boolean) -> Unit = {
                        haptic(if (it) Haptic.CONFIRM else Haptic.TICK)
                        NotesStore.toggleHabit(habit.id)
                    }
                    if (doneToday) {
                        FilledIconToggleButton(checked = true, onCheckedChange = toggle, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Check, contentDescription = "Fatta oggi")
                        }
                    } else {
                        OutlinedIconToggleButton(
                            checked = false,
                            onCheckedChange = toggle,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Segna fatta oggi" },
                        ) {}
                    }
                }
            }
        }
    }
}

/** "Idee da aggiungere": esempi tappabili (titolo e riga piccola) sotto una lista corta. */
@Composable
private fun IdeasSection(ideas: List<Pair<String, String>>, header: Boolean = true, onPick: (Int) -> Unit) {
    Column(Modifier.padding(top = if (header) 8.dp else 0.dp)) {
        if (header) {
            SectionHeader("Idee da aggiungere")
            Spacer(Modifier.height(4.dp))
        }
        CanvasCard(Modifier.fillMaxWidth()) {
            ideas.forEachIndexed { i, (title, detail) ->
                if (i > 0) RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(i) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.Add, contentDescription = "Aggiungi $title", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun TaskGroup(label: String?, tasks: List<TaskItem>, onDeleted: (TaskItem) -> Unit, error: Boolean = false) {
    Column(Modifier.padding(bottom = 8.dp)) {
        if (label != null) {
            SectionHeader(label, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
        }
        CanvasCard(Modifier.fillMaxWidth()) {
            tasks.forEachIndexed { index, task ->
                key(task.id) {
                    if (index > 0) RowDivider()
                    TaskRow(task, onDeleted)
                }
            }
        }
    }
}

/** Un promemoria con scadenza, nella sua card come le date e i pacchi: casella, testo e a destra quanto manca. */
@Composable
private fun TaskDueCard(task: TaskItem, day: LocalDate?, late: Boolean, onDeleted: (TaskItem) -> Unit) {
    CanvasCard(Modifier.fillMaxWidth()) {
        TaskRow(task, onDeleted) {
            val (big, small) = dueBadge(day, late = late)
            DueBadge(big, small, error = late, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        }
    }
}

/** Una riga di Da fare: casella e testo. Scorrendo verso sinistra si elimina (con Annulla). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(task: TaskItem, onDeleted: (TaskItem) -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val openItem = rememberOpenItem()
    val haptic = rememberHaptic()
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptic(Haptic.CONFIRM)
                NotesStore.deleteTask(task.id)
                onDeleted(task)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.card)
            .bouncyPress(0.98f)
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = {
            haptic(if (task.done) Haptic.TICK else Haptic.CONFIRM)
            NotesStore.toggleTask(task.id)
        })
        Column(
            Modifier
                .weight(1f)
                .clickable { openItem(ItemKind.TASK, task.id) }
                .padding(vertical = 6.dp)
        ) {
            Text(
                task.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                ),
                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (!task.done) task.dueAt?.let { DueText(it, task.repeat) }
        }
        trailing?.invoke()
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/**
 * Una nota nella lista: titolo, anteprima e quando è stata toccata. Tenendo premuto sale il fumetto
 * delle azioni (fissa in alto, condividi, elimina): per le cose di "Salva nelle note" è l'unico modo di gestirle,
 * perché il loro testo con l'indirizzo canvas://open non si mostra mai in un editor.
 */
@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    // Una nota di "Salva nelle note": l'icona dice cos'è e il tocco riapre la cosa.
    val saved = remember(note.text) { SavedLinks.find(note.text) }
    Box(modifier) {
    CanvasCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = {
                    haptic(Haptic.LONG_PRESS)
                    ActionBubble.show(
                        BubbleAction(
                            if (note.pinned) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                            if (note.pinned) "Togli da in alto" else "Fissa in alto",
                            detail = note.title,
                        ) { NotesStore.togglePin(note.id) },
                        BubbleAction(Icons.Outlined.Share, "Condividi") {
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, saved?.let(SavedLinks::shareText) ?: note.text)
                            context.startActivity(android.content.Intent.createChooser(share, null))
                        },
                        BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true, onClick = onDelete),
                    )
                },
            ),
    ) {
        Row(Modifier.padding(16.dp)) {
            // L'anteprima di quello che si è salvato: copertina, miniatura del video, foto dell'articolo.
            saved?.takeIf { it.image.isNotBlank() }?.let { SavedPreview(it, it.image) }
            // La foto allegata con la graffetta.
            if (note.photo.isNotEmpty()) NotePhoto(note.photo, Modifier.padding(end = 12.dp).size(width = 96.dp, height = 64.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    saved?.takeIf { it.image.isBlank() }?.let {
                        Icon(
                            savedIcon(it),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(20.dp),
                        )
                    }
                    Text(
                        note.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (saved != null) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (note.pinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Fissata",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Checklist.progressLabel(note.text)?.let { progress ->
                    Text(
                        progress,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (note.body.isNotBlank()) {
                    Text(
                        Checklist.preview(note.body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    relativeTime(Instant.ofEpochMilli(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    }
}

/**
 * La miniatura a sinistra di una nota salvata: quadrata per musica, radio e podcast, larga per
 * video e articoli. Sopra, la piccola icona che dice cos'è.
 */
@Composable
private fun SavedPreview(link: SavedLink, image: String) {
    val square = link is SavedLink.Media && link.item.kind in setOf(ResumeKind.MUSIC, ResumeKind.SPOTIFY, ResumeKind.RADIO, ResumeKind.PODCAST)
    Box(
        Modifier
            .padding(end = 12.dp)
            .size(width = if (square) 64.dp else 96.dp, height = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        coil3.compose.AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(
            savedIcon(link),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(3.dp)
                .size(14.dp),
        )
    }
}

private fun savedIcon(link: SavedLink) = when (link) {
    is SavedLink.Article -> Icons.AutoMirrored.Outlined.Article
    is SavedLink.Media -> when (link.item.kind) {
        ResumeKind.VIDEO, ResumeKind.YOUTUBE -> Icons.Outlined.SmartDisplay
        ResumeKind.MUSIC, ResumeKind.SPOTIFY -> Icons.Outlined.MusicNote
        ResumeKind.RADIO -> Icons.Outlined.Radio
        ResumeKind.PODCAST -> Icons.Outlined.Podcasts
    }
}

/** Riga sotto il titolo di una data: "12 marzo · compie 30 anni" o "· ogni anno". */
fun Countdown.dateLine(): String = longDate(nextDate()) + when {
    !yearly -> ""
    else -> yearsAtNext()?.let { " · compie $it anni" } ?: " · ogni anno"
}


@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.StickyNote2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** L'ultima nota eliminata dall'editor, finché la lista non offre "Annulla". */
private object DeletedNote {
    /** Oltre questo tempo non ha più senso proporre di recuperarla. */
    private const val VALID_MS = 15_000L
    private var note: Note? = null
    private var at = 0L

    fun put(removed: Note) {
        note = removed
        at = System.currentTimeMillis()
    }

    fun take(): Note? {
        val removed = note?.takeIf { System.currentTimeMillis() - at < VALID_MS }
        note = null
        return removed
    }
}

/** Editor di una nota: si salva da solo quando si esce. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    var id by remember { mutableStateOf(noteId) }
    var text by rememberSaveable { mutableStateOf(noteId?.let { NotesStore.note(it)?.text }.orEmpty()) }
    var deleted by remember { mutableStateOf(false) }
    // Le note con voci da spuntare si aprono come lista; la matita torna al testo.
    var checkMode by rememberSaveable { mutableStateOf(noteId != null && Checklist.hasItems(text)) }
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val pinned = id?.let { current -> data.notes.firstOrNull { it.id == current }?.pinned } ?: false
    val haptic = rememberHaptic()
    // Quando era stata toccata l'ultima volta, prima di aprirla adesso.
    val lastEdited = remember { noteId?.let { NotesStore.note(it)?.updatedAt } }

    DisposableEffect(Unit) {
        onDispose { if (!deleted) NotesStore.saveNote(id, text) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (noteId == null) "Nuova nota" else "Nota")
                        if (lastEdited != null) {
                            Text(
                                "Modificata " + relativeTime(Instant.ofEpochMilli(lastEdited)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (checkMode) {
                            checkMode = false
                        } else {
                            text = Checklist.toList(text)
                            id = NotesStore.saveNote(id, text) ?: id
                            checkMode = true
                        }
                    }) {
                        if (checkMode) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Modifica il testo")
                        } else {
                            Icon(Icons.Outlined.ChecklistIcon, contentDescription = "Lista")
                        }
                    }
                    // Fissa in alto: lo stesso della pressione lunga nella lista, ma visibile.
                    IconButton(onClick = {
                        id?.let {
                            haptic(if (pinned) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                            NotesStore.togglePin(it)
                        }
                    }, enabled = id != null) {
                        if (pinned) {
                            Icon(Icons.Filled.PushPinFilled, contentDescription = "Togli da in alto", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.PushPin, contentDescription = "Fissa in alto")
                        }
                    }
                    IconButton(onClick = {
                        val share = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, text)
                        context.startActivity(android.content.Intent.createChooser(share, null))
                    }, enabled = text.isNotBlank()) {
                        Icon(Icons.Outlined.Share, contentDescription = "Condividi")
                    }
                    IconButton(onClick = {
                        deleted = true
                        id?.let { noteId ->
                            NotesStore.note(noteId)?.let(DeletedNote::put)
                            NotesStore.deleteNote(noteId)
                        }
                        onBack()
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Elimina")
                    }
                },
            )
        },
    ) { inner ->
        if (checkMode) {
            ChecklistView(
                text = text,
                onChange = { next ->
                    text = next
                    id = NotesStore.saveNote(id, next) ?: id
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .imePadding(),
            )
            return@Scaffold
        }
        val photo = id?.let { current -> data.notes.firstOrNull { it.id == current }?.photo }.orEmpty()
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
        if (photo.isNotEmpty()) {
            NotePhoto(photo, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(max = 360.dp), crop = false)
        }
        TextField(
            value = text,
            onValueChange = {
                text = it
                // Salvataggio continuo: niente perso se l'app viene chiusa.
                id = NotesStore.saveNote(id, it) ?: id
            },
            placeholder = { Text("Scrivi… la prima riga fa da titolo") },
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.bodyLarge),
            visualTransformation = rememberTitleLine(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .autoFocus(enabled = noteId == null),
        )
        }
    }
}

/** La foto di una nota, dal file salvato nell'app: ritagliata nella lista, intera nell'editor. */
@Composable
private fun NotePhoto(path: String, modifier: Modifier, crop: Boolean = true) {
    coil3.compose.AsyncImage(
        model = java.io.File(path),
        contentDescription = "Foto",
        contentScale = if (crop) androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Dove finisce il titolo di una nota: la prima riga, come nella lista delle Note. */
internal fun noteTitleEnd(text: String): Int = text.indexOf('\n').let { if (it < 0) text.length else it }

/** La prima riga dell'editor più grande e in grassetto: si vede che è il titolo. */
@Composable
private fun rememberTitleLine(): VisualTransformation {
    val title = MaterialTheme.typography.titleLarge
    return remember(title) {
        VisualTransformation { text ->
            val end = noteTitleEnd(text.text)
            val styled = buildAnnotatedString {
                append(text)
                if (end > 0) addStyle(SpanStyle(fontSize = title.fontSize, fontWeight = FontWeight.SemiBold), 0, end)
            }
            TransformedText(styled, OffsetMapping.Identity)
        }
    }
}

/** Una nota-lista: righe da spuntare con un tocco e un campo per aggiungerne in fondo. */
@Composable
private fun ChecklistView(text: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    var draft by rememberSaveable { mutableStateOf("") }
    fun addDraft() {
        if (draft.isBlank()) return
        haptic(Haptic.CONFIRM)
        onChange(Checklist.add(text, draft))
        draft = ""
    }
    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        item(key = "title") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(
                    text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifEmpty { "Lista" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Checklist.progressLabel(text)?.let { progress ->
                    Text(progress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(Checklist.lines(text), key = { it.index }) { line ->
            when (line) {
                is Checklist.Item -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            haptic(if (line.done) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                            onChange(Checklist.toggle(text, line.index))
                        }
                        .semantics { contentDescription = (if (line.done) "Fatto: " else "Da fare: ") + line.text },
                ) {
                    Checkbox(checked = line.done, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                    Text(
                        line.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (line.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (line.done) TextDecoration.LineThrough else null,
                    )
                }
                is Checklist.Plain -> if (line.text.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(line.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
        item(key = "add") {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Aggiungi voce") },
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = canvasFieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addDraft() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

/** Gli ultimi eventi letti per la pagina Note: tornando sulla pagina ci sono già, senza comparire dopo. */
private object NotesEvents {
    private var last: List<CalendarEvent>? = null

    /** La prima volta li legge subito (una query locale di pochi millisecondi), poi usa quelli già letti. */
    fun cached(context: android.content.Context): List<CalendarEvent> =
        last ?: kotlinx.coroutines.runBlocking { load(context) }

    suspend fun load(context: android.content.Context): List<CalendarEvent> =
        // Anche gli ultimi [PAST_DAYS] giorni: il passato della linea del tempo.
        CalendarRepository.eventsFrom(context, LocalDate.now().minusDays(PAST_DAYS.toLong()), PAST_DAYS + EVENT_DAYS)
            .values.flatten().also { last = it }
}
