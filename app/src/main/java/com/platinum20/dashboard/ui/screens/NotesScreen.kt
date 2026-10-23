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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.key
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.capture.captureTarget
import com.platinum20.dashboard.capture.CaptureDialog
import com.platinum20.dashboard.capture.CaptureMode
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

/**
 * In "In arrivo" date e pacchi stanno in una lista sola, dal più vicino: prima quello che arriva
 * (le date e i pacchi senza data in fondo), poi i pacchi già consegnati.
 */
internal data class ComingRow(val key: String, val day: LocalDate?, val countdown: Countdown? = null, val parcel: Parcel? = null)

internal fun comingRows(upcoming: List<Countdown>, parcels: List<Parcel>, today: LocalDate = LocalDate.now()): List<ComingRow> {
    val open = upcoming.map { ComingRow("date-${it.id}", it.nextDate(today), countdown = it) } +
        parcels.filter { !it.delivered }.map { ComingRow("parcel-${it.id}", it.expected, parcel = it) }
    val delivered = parcels.filter { it.delivered }.sortedByDescending { it.deliveredOn }
        .map { ComingRow("parcel-${it.id}", it.deliveredOn, parcel = it) }
    return open.sortedBy { it.day ?: LocalDate.MAX } + delivered
}

/** Le pillole in cima alle Note: mostrano una parte sola della pagina (di nuovo per tornare a tutto). */
enum class NotesSection { TASKS, HABITS, COMING, NOTES, MONEY }

/**
 * Le pillole con quello che c'è in ciascuna parte ("Da fare · 3", "Abitudini · 1/2", "Spese · 115,90 €"):
 * solo le parti che hanno qualcosa, così ogni pillola dice qualcosa e porta da qualche parte.
 */
internal fun notesChips(data: com.platinum20.dashboard.notes.NotesData, today: LocalDate = LocalDate.now()): List<Pair<NotesSection, String>> {
    val open = data.tasks.count { !it.done }
    val coming = data.upcoming.size + data.parcels.count { !it.delivered }
    val month = com.platinum20.dashboard.notes.Expenses.total(data.expenses, java.time.YearMonth.from(today))
    return listOfNotNull(
        data.tasks.takeIf { it.isNotEmpty() }?.let { NotesSection.TASKS to if (open > 0) "Da fare · $open" else "Da fare" },
        data.habits.takeIf { it.isNotEmpty() }?.let { h -> NotesSection.HABITS to "Abitudini · ${h.count { it.isDone(today) }}/${h.size}" },
        (coming > 0 || data.parcels.isNotEmpty()).takeIf { it }?.let { NotesSection.COMING to if (coming > 0) "In arrivo · $coming" else "In arrivo" },
        data.notes.takeIf { it.isNotEmpty() }?.let { NotesSection.NOTES to "Note · ${it.size}" },
        (data.expenses.isNotEmpty() || data.fixedExpenses.isNotEmpty() || data.budgetCents != null).takeIf { it }
            ?.let { NotesSection.MONEY to "Spese · ${com.platinum20.dashboard.notes.Expenses.format(month)}" },
    )
}

/** Quante note si vedono prima di "Tutte le note": le fissate e le ultime toccate. */
internal const val NOTES_PREVIEW = 4

/**
 * Sezione "Note", in una pagina sola: in cima si scrive e il tipo (promemoria, nota, spesa, abitudine,
 * data, pacco, evento) lo sceglie Canvas da solo; sotto, tutto in ordine di quando conta.
 * Le pillole in alto riassumono ogni parte e la mostrano da sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bottomPadding: PaddingValues,
    onOpenNote: (Long?) -> Unit,
) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var section by rememberSaveable { mutableStateOf<NotesSection?>(null) }
    var allNotes by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Eliminare è immediato, ma per qualche secondo si può tornare indietro.
    fun offerUndo(message: String, restore: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Annulla", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) restore()
        }
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
    val coming = comingItems(
        data.upcoming,
        data.parcels,
        onDateDeleted = { c -> offerUndo("Data eliminata") { NotesStore.restoreCountdown(c) } },
        onParcelDeleted = { p -> offerUndo("Pacco eliminato") { NotesStore.restoreParcel(p) } },
    )
    var proposingHabit by remember { mutableStateOf<HabitIdea?>(null) }
    var proposingDate by remember { mutableStateOf<DateIdea?>(null) }
    proposingHabit?.let { HabitDialog(habit = null, idea = it, onDismiss = { proposingHabit = null }) }
    proposingDate?.let { CountdownDialog(countdown = null, idea = it, onDismiss = { proposingDate = null }) }
    var importingContacts by remember { mutableStateOf(false) }
    if (importingContacts) {
        ContactImportDialog(data.countdowns, onDismiss = { importingContacts = false }) { added ->
            if (added.isNotEmpty()) {
                val ids = added.map { it.id }.toSet()
                offerUndo(if (added.size == 1) "Importata 1 data" else "Importate ${added.size} date") { NotesStore.removeCountdowns(ids) }
            }
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

    // La lampadina: esempi da aggiungere con un tocco (compleanni dalla rubrica, abitudini, date).
    var showingIdeas by remember { mutableStateOf(false) }
    val habitIdeas = HabitIdeas.suggest(data.habits, max = 5, until = Int.MAX_VALUE)
    val dateIdeas = DateIdeas.suggest(data.countdowns, max = 4, until = Int.MAX_VALUE)
    val contacts = showContactImport(data.countdowns)
    val ideaRows = listOfNotNull(("Compleanni dai contatti" to "Importa dalla rubrica, una volta sola").takeIf { contacts }) +
        habitIdeas.map { it.title to "Abitudine · ${it.frequency.lowercase()}" } +
        dateIdeas.map { it.title.trim() to if (it.yearly) "Data · ogni anno" else "Data" }
    if (showingIdeas) {
        CanvasDialog(
            onDismissRequest = { showingIdeas = false },
            title = { Text("Idee da aggiungere") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IdeasSection(ideaRows, header = false) { i ->
                        showingIdeas = false
                        val offset = if (contacts) 1 else 0
                        when {
                            i < offset -> importingContacts = true
                            i - offset < habitIdeas.size -> proposingHabit = habitIdeas[i - offset]
                            else -> proposingDate = dateIdeas[i - offset - habitIdeas.size]
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showingIdeas = false }) { Text("Chiudi") } },
        )
    }

    // Con una pillola accesa, "indietro" torna a tutta la pagina.
    androidx.activity.compose.BackHandler(enabled = section != null && !searching) { section = null }
    val chips = notesChips(data)
    // Se la parte scelta si svuota (es. ultimo pacco eliminato) si torna a tutto.
    LaunchedEffect(chips.map { it.first }) { if (section != null && chips.none { it.first == section }) section = null }

    Scaffold(
        contentWindowInsets = topLevelInsets,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding.calculateBottomPadding()))
        },
        topBar = { Column {
            TopLevelBar(
                title = "Note",
                subtitle = listOfNotNull(
                    dueToday.takeIf { it > 0 }?.let { "$it oggi" },
                    overdue.takeIf { it > 0 }?.let { if (it == 1) "1 scaduta" else "$it scadute" },
                ).joinToString(" · ").ifEmpty { "Cattura e scadenze" },
                actions = {
                    if (!searching && data.tasks.any { it.done }) {
                        RoundIconButton(Icons.Outlined.DeleteSweep, "Elimina completati") {
                            val done = data.tasks.filter { it.done }
                            NotesStore.clearDone()
                            offerUndo(done, if (done.size == 1) "Eliminato 1 completato" else "Eliminati ${done.size} completati")
                        }
                    }
                },
            )
            if (!searching) {
                val haptic = rememberHaptic()
                // Le pillole scorrono; a destra, fissi, i due tastini: scontrino e idee.
                Row(Modifier.fillMaxWidth().padding(end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (chips.size >= 2) chips.forEach { (s, label) ->
                            CanvasChip(
                                selected = section == s,
                                onClick = { haptic(Haptic.TICK); section = if (section == s) null else s },
                                label = label,
                            )
                        }
                    }
                    if (readingReceipt) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    } else {
                        RoundIconButton(Icons.AutoMirrored.Outlined.ReceiptLong, "Fotografa uno scontrino") {
                            runCatching { receiptPhoto.launch(ReceiptReader.photoUri(context)) }
                        }
                    }
                    if (ideaRows.isNotEmpty()) {
                        RoundIconButton(Icons.Outlined.Lightbulb, "Idee da aggiungere") { showingIdeas = true }
                    }
                }
            }
        } },
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
            ExpensesTab(data, padding, onDeleted = { e -> offerUndo("Spesa eliminata") { NotesStore.restoreExpense(e) } })
            return@Scaffold
        }
        fun show(part: NotesSection) = section == null || section == part
        val onTaskDeleted: (TaskItem) -> Unit = { offerUndo(listOf(it), "Promemoria eliminato") }
        // Raggruppati per quando vanno fatti: prima ciò che è scaduto o in scadenza oggi.
        val now = System.currentTimeMillis()
        val open = data.tasks.filter { !it.done }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        val urgent = listOf(
            "Scaduti" to open.filter { it.dueAt != null && it.dueAt < now },
            "Oggi" to open.filter { it.dueAt != null && it.dueAt in now until endOfDay },
        ).filter { it.second.isNotEmpty() }
        val later = listOf(
            "Prossimi giorni" to open.filter { it.dueAt != null && it.dueAt >= endOfDay },
            "Senza scadenza" to open.filter { it.dueAt == null },
        ).filter { it.second.isNotEmpty() }
        val done = data.tasks.filter { it.done }
        val notes = data.notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        val shownNotes = if (allNotes || section == NotesSection.NOTES) notes else notes.take(NOTES_PREVIEW)
        val empty = data.tasks.isEmpty() && data.notes.isEmpty() && data.habits.isEmpty() &&
            data.upcoming.isEmpty() && data.parcels.isEmpty() && data.expenses.isEmpty()

        LazyColumn(state = listState, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (empty) {
                item(key = "empty") {
                    EmptyHint(
                        "Tocca + e scrivi come parli: Canvas capisce cos'è. \"venerdì alle 18 palestra\" è un promemoria, " +
                            "\"12,50 pizza\" una spesa, \"ogni giorno leggere\" un'abitudine, \"compleanno di Marta 12 marzo\" " +
                            "una data. Lo scontrino si fotografa col tastino in alto, la lampadina ha qualche idea."
                    )
                }
            }
            // Ogni gruppo è una card con le sue righe.
            if (show(NotesSection.TASKS)) urgent.forEach { (label, list) ->
                item(key = "group-$label") { TaskGroup(label, list, onTaskDeleted, error = label == "Scaduti") }
            }
            if (show(NotesSection.HABITS)) habitRows()
            if (show(NotesSection.COMING)) coming()
            if (show(NotesSection.TASKS)) later.forEach { (label, list) ->
                item(key = "group-$label") { TaskGroup(label, list, onTaskDeleted) }
            }
            if (notes.isNotEmpty() && show(NotesSection.NOTES)) {
                item(key = "notes-header") {
                    SectionHeader(
                        if (notes.size > NOTES_PREVIEW) "Note · ${notes.size}" else "Note",
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).captureTarget(CaptureMode.NOTE),
                        action = when {
                            notes.size <= NOTES_PREVIEW || section == NotesSection.NOTES -> null
                            allNotes -> "Meno"
                            else -> "Tutte"
                        },
                        onAction = { allNotes = !allNotes },
                    )
                }
                items(shownNotes, key = { "note-${it.id}" }) { note ->
                    NoteCard(note, onOpen = { onOpenNote(note.id) }, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            if (section == null && (data.expenses.isNotEmpty() || data.fixedExpenses.isNotEmpty() || data.budgetCents != null)) {
                item(key = "expenses") { ExpensesSummary(data, onOpen = { section = NotesSection.MONEY }) }
            }
            if (done.isNotEmpty() && show(NotesSection.TASKS)) {
                item(key = "group-done") { Column(Modifier.padding(top = 12.dp)) { TaskGroup("Completati", done, onTaskDeleted) } }
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

/** "In arrivo": date e pacchi in una lista sola, dal più vicino. Tiene i dialoghi per modificarli. */
@Composable
private fun comingItems(
    upcoming: List<Countdown>,
    parcels: List<Parcel>,
    onDateDeleted: (Countdown) -> Unit,
    onParcelDeleted: (Parcel) -> Unit,
): LazyListScope.() -> Unit {
    var editingDate by remember { mutableStateOf<Countdown?>(null) }
    var editingParcel by remember { mutableStateOf<Parcel?>(null) }
    editingDate?.let { CountdownDialog(countdown = it, onDismiss = { editingDate = null }, onDeleted = onDateDeleted) }
    editingParcel?.let { ParcelDialog(it, onDismiss = { editingParcel = null }, onDeleted = onParcelDeleted) }
    val rows = comingRows(upcoming, parcels)
    return {
        if (rows.isNotEmpty()) {
            item(key = "coming-header") {
                SectionHeader(
                    "In arrivo",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        .captureTarget(CaptureMode.DATE).captureTarget(CaptureMode.PARCEL),
                )
            }
        }
        items(rows, key = { it.key }) { row ->
            Box(Modifier.padding(bottom = 4.dp)) {
                row.countdown?.let { DateCard(it, onClick = { editingDate = it }) }
                row.parcel?.let { ParcelCard(it, onClick = { editingParcel = it }) }
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
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when (days) {
                        0L -> "Oggi"
                        1L -> "Domani"
                        else -> "$days"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (days > 1) {
                    Text("giorni", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
        if (habits.isNotEmpty()) {
            item(key = "habits-header") {
                val done = habits.count { it.isDone(today) }
                SectionHeader(
                    if (done == habits.size) "Abitudini · tutte fatte oggi" else "Abitudini · oggi $done su ${habits.size}",
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).captureTarget(CaptureMode.HABIT),
                )
            }
        }
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
            SectionHeader(label, modifier = Modifier.captureTarget(CaptureMode.TASK), color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
        }
        CanvasCard(Modifier.fillMaxWidth().animateContentSize(Springs.snappy())) {
            tasks.forEachIndexed { index, task ->
                key(task.id) {
                    if (index > 0) RowDivider()
                    TaskRow(task, onDeleted)
                }
            }
        }
    }
}

/** Una riga di Da fare: casella e testo. Scorrendo verso sinistra si elimina (con Annulla). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(task: TaskItem, onDeleted: (TaskItem) -> Unit) {
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
    }
    }
    if (editing) TaskEditDialog(task, onDismiss = { editing = false }, onDeleted = onDeleted)
}

@OptIn(ExperimentalFoundationApi::class)
/** Una nota nella lista: titolo, anteprima e quando è stata toccata. Tenendo premuto si fissa in alto. */
@Composable
private fun NoteCard(note: Note, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    CanvasCard(
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { haptic(Haptic.LONG_PRESS); NotesStore.togglePin(note.id) },
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
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
