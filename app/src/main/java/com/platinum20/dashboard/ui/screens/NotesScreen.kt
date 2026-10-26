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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.border

/** Le pillole in cima alle Note, una per tipo: con scadenza, senza scadenza, spese (di nuovo per tornare a tutto). */
enum class NotesSection(val title: String) { DATED("Con scadenza"), UNDATED("Senza scadenza"), MONEY("Spese") }

/**
 * Le pillole con quanto c'è in ciascun tipo ("Con scadenza · 4", "Senza scadenza · 7", "Spese · 115,90 €"):
 * solo i tipi che hanno qualcosa, così ogni pillola dice qualcosa e porta da qualche parte.
 * Le abitudini stanno con le scadenze: si fanno oggi. [events] sono gli eventi del calendario in lista.
 */
internal fun notesChips(data: com.platinum20.dashboard.notes.NotesData, today: LocalDate = LocalDate.now(), events: Int = 0): List<Pair<NotesSection, String>> {
    val dated = data.tasks.count { !it.done && it.dueAt != null } + data.upcoming.size + data.parcels.count { !it.delivered } + events
    val undated = data.tasks.count { !it.done && it.dueAt == null } + data.notes.size
    val month = com.platinum20.dashboard.notes.Expenses.total(data.expenses, java.time.YearMonth.from(today))
    return listOfNotNull(
        (dated > 0 || data.habits.isNotEmpty()).takeIf { it }?.let { NotesSection.DATED to if (dated > 0) "Con scadenza · $dated" else "Con scadenza" },
        (undated > 0).takeIf { it }?.let { NotesSection.UNDATED to "Senza scadenza · $undated" },
        (data.expenses.isNotEmpty() || data.fixedExpenses.isNotEmpty() || data.budgetCents != null).takeIf { it }
            ?.let { NotesSection.MONEY to "Spese · ${com.platinum20.dashboard.notes.Expenses.format(month)}" },
    )
}

/**
 * La pagina delle Note: i tre tipi, uno per card, con le prime cose di ognuno.
 * Toccando una card si apre il tipo intero (le scadenze divise per giorno, le spese per mese).
 */
@Composable
private fun NotesOverview(
    data: com.platinum20.dashboard.notes.NotesData,
    timeline: List<Pair<DueGroup, List<DueRow>>>,
    padding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpen: (NotesSection) -> Unit,
) {
    val month = java.time.YearMonth.now()
    val dated = timeline.flatMap { (group, rows) -> rows.map { group to it } }
    val habitsLeft = data.habits.count { !it.isDone(LocalDate.now()) }
    val undatedTasks = data.tasks.filter { !it.done && it.dueAt == null }
    val notes = data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val total = com.platinum20.dashboard.notes.Expenses.total(data.expenses, month)
    val budget = data.budgetCents
    LazyColumn(state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "dated") {
            NotesCategoryCard(
                title = NotesSection.DATED.title,
                icon = Icons.Outlined.Event,
                accent = TileAccent.BLUE,
                count = (dated.size + data.habits.size).takeIf { it > 0 }?.toString(),
                lines = dated.take(3).map { (group, row) -> group.label to row.title() } +
                    listOfNotNull(habitsLeft.takeIf { it > 0 }?.let { "Oggi" to if (it == 1) "1 abitudine da fare" else "$it abitudini da fare" }),
                empty = "Promemoria, date, pacchi ed eventi, in ordine di giorno",
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
                empty = "Le cose da fare prima o poi e gli appunti",
                modifier = Modifier.animateItem().captureTarget(CaptureMode.NOTE),
                onClick = { onOpen(NotesSection.UNDATED) },
            )
        }
        item(key = "money") {
            NotesCategoryCard(
                title = NotesSection.MONEY.title,
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                accent = TileAccent.GREEN,
                count = com.platinum20.dashboard.notes.Expenses.format(total).takeIf { total > 0 || budget != null },
                lines = listOfNotNull(
                    month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } to
                        "${data.expenses.count { java.time.YearMonth.from(it.date) == month }} spese",
                    budget?.let { b ->
                        "Budget" to if (total > b) "superato di ${com.platinum20.dashboard.notes.Expenses.format(total - b)}"
                        else "restano ${com.platinum20.dashboard.notes.Expenses.format(b - total)}"
                    },
                ).takeIf { data.expenses.isNotEmpty() || budget != null }.orEmpty(),
                empty = "Scrivi \"12,50 pizza\" con il + o fotografa uno scontrino",
                modifier = Modifier.animateItem().captureTarget(CaptureMode.EXPENSE),
                onClick = { onOpen(NotesSection.MONEY) },
            )
        }
    }
}

/** Il titolo di una cosa con scadenza, qualunque sia. */
private fun DueRow.title(): String =
    task?.text ?: countdown?.title ?: parcel?.title ?: event?.title ?: ""

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
    val scope = rememberCoroutineScope()

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
    var editingDate by remember { mutableStateOf<Countdown?>(null) }
    var editingParcel by remember { mutableStateOf<Parcel?>(null) }
    editingDate?.let { CountdownDialog(countdown = it, onDismiss = { editingDate = null }, onDeleted = { c -> offerUndo("Data eliminata") { NotesStore.restoreCountdown(c) } }) }
    editingParcel?.let { ParcelDialog(it, onDismiss = { editingParcel = null }, onDeleted = { p -> offerUndo("Pacco eliminato") { NotesStore.restoreParcel(p) } }) }
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
    // Lo scontrino fotografato apre lo stesso pannello del +, già scritto, da controllare e salvare.
    var receiptText by remember { mutableStateOf<String?>(null) }
    receiptText?.let { CaptureDialog(initial = it, initialMode = CaptureMode.TASK, onClose = { receiptText = null }) }
    val listState = rememberReselectListState(Routes.NOTES)

    // Foto dello scontrino.
    var readingReceipt by remember { mutableStateOf(false) }
    val receiptPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (!ok) return@rememberLauncherForActivityResult
        readingReceipt = true
        scope.launch {
            val guess = ReceiptReader.read(context, ReceiptReader.photoUri(context))
            readingReceipt = false
            if (guess != null) receiptText = guess.captureText
            else android.widget.Toast.makeText(context, "Non trovo il totale: riprova con lo scontrino dritto e ben illuminato", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Con una pillola accesa, "indietro" torna a tutta la pagina.
    androidx.activity.compose.BackHandler(enabled = section != null && !searching) { section = null }
    // Ogni pagina (i tre tipi, o uno solo) parte dalla cima.
    LaunchedEffect(section) { runCatching { listState.scrollToItem(0) } }
    val timeline = dueGroups(data.tasks, data.upcoming, data.parcels, events)

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
                onOpenExpenses = { searching = false; section = NotesSection.MONEY },
            )
            return@Scaffold
        }
        if (section == NotesSection.MONEY) {
            // Le spese del mese, il budget e le fisse, con il loro campo ("12,50 pizza", "ogni mese il 5 affitto 650").
            ExpensesTab(
                data,
                padding,
                onDeleted = { e -> offerUndo("Spesa eliminata") { NotesStore.restoreExpense(e) } },
                readingReceipt = readingReceipt,
                onReceipt = { runCatching { receiptPhoto.launch(ReceiptReader.photoUri(context)) } },
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
        val delivered = data.parcels.filter { it.delivered }.sortedByDescending { it.deliveredOn }
        val notes = data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        val shownNotes = if (allNotes || section == NotesSection.UNDATED) notes else notes.take(NOTES_PREVIEW)
        val empty = data.tasks.isEmpty() && data.notes.isEmpty() && data.habits.isEmpty() &&
            data.upcoming.isEmpty() && data.parcels.isEmpty() && data.expenses.isEmpty() && events.isEmpty()
        // Le abitudini si fanno oggi: stanno sotto "Oggi", anche quando oggi non scade nient'altro.
        val groups = if (data.habits.isNotEmpty() && timeline.none { it.first == DueGroup.TODAY }) {
            (timeline + (DueGroup.TODAY to emptyList())).sortedBy { it.first.ordinal }
        } else timeline
        val today = LocalDate.now()

        LazyColumn(state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (empty) {
                item(key = "empty") {
                    EmptyHint(
                        "Tocca + e scrivi come parli: Canvas capisce da solo se ha una scadenza. \"venerdì alle 18 palestra\", " +
                            "\"compleanno di Marta 12 marzo\" o un pacco Amazon finiscono in Con scadenza, con i giorni che mancano; " +
                            "un'idea senza data in Senza scadenza; \"12,50 pizza\" nelle Spese."
                    )
                }
            }
            // Con scadenza: una lista sola per giorno, tutte con quanto manca a destra.
            if (show(NotesSection.DATED)) groups.forEachIndexed { index, (group, rows) ->
                item(key = "due-${group.name}") {
                    // Il pallino del + atterra sul primo giorno che si vede.
                    val target = if (index == 0) Modifier.captureTarget(CaptureMode.TASK).captureTarget(CaptureMode.DATE)
                        .captureTarget(CaptureMode.PARCEL).captureTarget(CaptureMode.EVENT) else Modifier
                    SectionHeader(
                        group.label,
                        modifier = Modifier.animateItem().padding(top = if (index == 0) 0.dp else 12.dp, bottom = 4.dp).then(target)
                            .then(if (group == DueGroup.TODAY) Modifier.captureTarget(CaptureMode.HABIT) else Modifier),
                        color = if (group == DueGroup.LATE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rows, key = { it.key }) { row ->
                    // Spuntate, spostate o cancellate: le righe scivolano al loro posto invece di saltare.
                    Box(Modifier.animateItem().padding(bottom = 4.dp)) {
                        row.task?.let { TaskDueCard(it, row.day, late = group == DueGroup.LATE, onDeleted = onTaskDeleted) }
                        row.countdown?.let { DateCard(it, onClick = { editingDate = it }) }
                        row.parcel?.let { ParcelCard(it, onClick = { editingParcel = it }) }
                        row.event?.let { EventCard(it, row.day, onClick = { onOpenEvent(it) }) }
                    }
                }
                if (group == DueGroup.TODAY) habitRows()
            }
            // Senza scadenza: le cose da fare prima o poi e gli appunti.
            if (show(NotesSection.UNDATED) && (undatedTasks.isNotEmpty() || notes.isNotEmpty())) {
                item(key = "undated-header") {
                    SectionHeader(
                        "Senza scadenza",
                        modifier = Modifier.padding(top = if (section == null && groups.isNotEmpty()) 12.dp else 0.dp, bottom = 4.dp)
                            .captureTarget(CaptureMode.NOTE),
                        action = when {
                            notes.size <= NOTES_PREVIEW || section == NotesSection.UNDATED -> null
                            allNotes -> "Meno note"
                            else -> "Tutte le note · ${notes.size}"
                        },
                        onAction = { allNotes = !allNotes },
                    )
                }
                if (undatedTasks.isNotEmpty()) {
                    item(key = "undated-tasks") { Box(Modifier.animateItem()) { TaskGroup(null, undatedTasks, onTaskDeleted) } }
                }
                items(shownNotes, key = { "note-${it.id}" }) { note ->
                    NoteCard(
                        note,
                        onOpen = { onOpenNote(note.id) },
                        onDelete = {
                            NotesStore.deleteNote(note.id)
                            offerUndo("Nota eliminata") { NotesStore.restoreNote(note) }
                        },
                        modifier = Modifier.animateItem().padding(bottom = 4.dp))
                }
            }
            if (section == null && (data.expenses.isNotEmpty() || data.fixedExpenses.isNotEmpty() || data.budgetCents != null)) {
                item(key = "expenses") { ExpensesSummary(data, onOpen = { section = NotesSection.MONEY }) }
            }
            // In fondo quello che è già fatto: promemoria spuntati e pacchi arrivati.
            if (show(NotesSection.DATED) && (done.isNotEmpty() || delivered.isNotEmpty())) {
                item(key = "done-header") {
                    SectionHeader(
                        "Completati",
                        modifier = Modifier.animateItem().padding(top = 12.dp, bottom = 4.dp),
                        action = if (done.isNotEmpty()) "Elimina" else null,
                        onAction = {
                            NotesStore.clearDone()
                            offerUndo(done, if (done.size == 1) "Eliminato 1 completato" else "Eliminati ${done.size} completati")
                        },
                    )
                }
                if (done.isNotEmpty()) item(key = "group-done") { Box(Modifier.animateItem()) { TaskGroup(null, done, onTaskDeleted) } }
                items(delivered, key = { "parcel-${it.id}" }) { p ->
                    Box(Modifier.animateItem().padding(bottom = 4.dp)) { ParcelCard(p, onClick = { editingParcel = p }) }
                }
            }
        }
    }
}

/** La riga delle spese in fondo alle Note: il totale del mese e il budget; si tocca per i dettagli. */
@Composable
private fun ExpensesSummary(data: com.platinum20.dashboard.notes.NotesData, onOpen: () -> Unit) {
    val month = java.time.YearMonth.now()
    val total = com.platinum20.dashboard.notes.Expenses.total(data.expenses, month)
    val budget = data.budgetCents
    Column(Modifier.padding(top = 12.dp)) {
        SectionHeader("Spese", modifier = Modifier.padding(bottom = 4.dp).captureTarget(CaptureMode.EXPENSE))
        CanvasCard(onClick = onOpen, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().bouncyPress(0.98f)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(com.platinum20.dashboard.notes.Expenses.format(total), style = MaterialTheme.typography.headlineSmall)
                    if (budget != null) {
                        val over = total > budget
                        Text(
                            if (over) "Budget superato di ${com.platinum20.dashboard.notes.Expenses.format(total - budget)}"
                            else "Restano ${com.platinum20.dashboard.notes.Expenses.format(budget - total)} del budget",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("Dettagli", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
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
    var editing by remember { mutableStateOf<Habit?>(null) }
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    editing?.let { HabitDialog(habit = it, onDismiss = { editing = null }, onDeleted = onDeleted) }
    return {
        items(habits, key = { "habit-${it.id}" }) { habit ->
            val doneToday = habit.isDone(today)
            val line = habit.statusLine(LocalDateTime.now())
            CanvasCard(
                onClick = { editing = habit },
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
    var editing by remember { mutableStateOf(false) }
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
                .clickable { editing = true }
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
    if (editing) TaskEditDialog(task, onDismiss = { editing = false }, onDeleted = onDeleted)
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
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .autoFocus(enabled = noteId == null),
        )
    }
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
        CalendarRepository.eventsFrom(context, LocalDate.now(), EVENT_DAYS).values.flatten().also { last = it }
}
