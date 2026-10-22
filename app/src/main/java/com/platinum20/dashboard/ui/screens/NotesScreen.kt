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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.platinum20.dashboard.notes.Note
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
import com.platinum20.dashboard.capture.CaptureDialog
import com.platinum20.dashboard.capture.CaptureMode
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.foundation.border

/** Le schede della sezione Note, nell'ordine in cui si usano di più. */
enum class NotesTabs(val label: String, val addLabel: String?) {
    TASKS("Da fare", null),
    HABITS("Abitudini", "Nuova abitudine"),
    MONEY("Spese", null),
    NOTES("Note", "Nuova nota"),
    PARCELS("Pacchi", "Nuovo pacco"),
    DATES("Date", "Nuova data"),
}

/**
 * Dove far scorrere la fila di pillole perché quella aperta stia intera con un margine:
 * null se è già tutta in vista (così la fila non si muove e non lascia pezzi tagliati).
 * Le posizioni sono in pixel dall'inizio della fila, [scroll] è lo scorrimento attuale.
 */
internal fun stripScrollTarget(chipStart: Int, chipEnd: Int, viewport: Int, scroll: Int, margin: Int): Int? = when {
    viewport <= 0 -> null
    chipStart - margin < scroll -> (chipStart - margin).coerceAtLeast(0)
    chipEnd + margin > scroll + viewport -> chipEnd + margin - viewport
    else -> null
}

/**
 * Le sei schede delle Note come pillole, uguali ai filtri delle News: stanno in una riga che scorre
 * e la riga si sposta da sola per tenere in vista la scheda attiva.
 */
@Composable
private fun NotesTabStrip(selected: NotesTabs, onSelect: (NotesTabs) -> Unit) {
    val haptic = rememberHaptic()
    val scroll = rememberScrollState()
    val chipBounds = remember { mutableStateMapOf<NotesTabs, IntRange>() }
    var viewport by remember { mutableStateOf(0) }
    val margin = with(LocalDensity.current) { 16.dp.roundToPx() }
    LaunchedEffect(selected, viewport) {
        val bounds = chipBounds[selected] ?: return@LaunchedEffect
        // Le posizioni delle pillole non contano i 16 dp di bordo della fila: li aggiungo.
        stripScrollTarget(bounds.first + margin, bounds.last + margin, viewport, scroll.value, margin)
            ?.let { scroll.animateScrollTo(it.coerceIn(0, scroll.maxValue)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { viewport = it.width }
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotesTabs.entries.forEach { t ->
            CanvasChip(
                selected = selected == t,
                onClick = { if (selected != t) haptic(Haptic.TICK); onSelect(t) },
                label = t.label,
                modifier = Modifier.onGloballyPositioned {
                    val x = it.positionInParent().x.toInt()
                    chipBounds[t] = x..(x + it.size.width)
                },
            )
        }
    }
}

/** Sezione "Note": promemoria, abitudini, spese, note libere, pacchi e date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bottomPadding: PaddingValues,
    onOpenNote: (Long?) -> Unit,
) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(NotesTabs.TASKS) }
    var adding by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
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

    // Il + della barra in basso aggiunge alla scheda aperta: promemoria, spesa, nota, pacco…
    ProvidePlusAction { if (tab == NotesTabs.NOTES) onOpenNote(null) else adding = true }
    val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val overdue = data.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < System.currentTimeMillis() }
    val dueToday = data.openTasks.count { (it.dueAt ?: Long.MAX_VALUE) < endOfDay } - overdue

    Scaffold(
        contentWindowInsets = topLevelInsets,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding.calculateBottomPadding()))
        },
        topBar = {
            Column {
                TopLevelBar(
                    title = "Note",
                    subtitle = listOfNotNull(
                        dueToday.takeIf { it > 0 }?.let { "$it oggi" },
                        overdue.takeIf { it > 0 }?.let { if (it == 1) "1 scaduta" else "$it scadute" },
                    ).joinToString(" · ").ifEmpty { "Cattura e scadenze" },
                    actions = {
                        if (!searching) {
                            RoundIconButton(Icons.Outlined.Search, "Cerca in tutte le Note") { searching = true }
                        }
                        if (!searching && tab == NotesTabs.TASKS && data.tasks.any { it.done }) {
                            RoundIconButton(Icons.Outlined.DeleteSweep, "Elimina completati") {
                                val done = data.tasks.filter { it.done }
                                NotesStore.clearDone()
                                offerUndo(done, if (done.size == 1) "Eliminato 1 completato" else "Eliminati ${done.size} completati")
                            }
                        }
                    },
                )
                if (!searching) NotesTabStrip(selected = tab, onSelect = { tab = it })
            }
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
                onOpenTab = { searching = false; tab = it },
            )
            return@Scaffold
        }
        when (tab) {
            NotesTabs.TASKS -> TasksTab(data.tasks, padding, onDeleted = { offerUndo(listOf(it), "Promemoria eliminato") })
            NotesTabs.HABITS -> HabitsTab(data.habits, padding, onDeleted = { h ->
                offerUndo("Abitudine eliminata") { NotesStore.restoreHabit(h) }
            })
            NotesTabs.MONEY -> ExpensesTab(data, padding, onDeleted = { e ->
                offerUndo("Spesa eliminata") { NotesStore.restoreExpense(e) }
            })
            NotesTabs.NOTES -> NotesTab(data.notes, padding, onOpenNote)
            NotesTabs.PARCELS -> ParcelsTab(data.parcels, padding, onDeleted = { p ->
                offerUndo("Pacco eliminato") { NotesStore.restoreParcel(p) }
            })
            NotesTabs.DATES -> DatesTab(data.upcoming, padding, onDeleted = { c ->
                offerUndo("Data eliminata") { NotesStore.restoreCountdown(c) }
            })
        }
    }
    if (adding) {
        val close = { adding = false }
        when (tab) {
            NotesTabs.HABITS -> HabitDialog(habit = null, onDismiss = close)
            NotesTabs.PARCELS -> ParcelDialog(parcel = null, onDismiss = close)
            NotesTabs.DATES -> CountdownDialog(countdown = null, onDismiss = close)
            NotesTabs.TASKS -> CaptureDialog(initial = "", initialMode = CaptureMode.TASK, onClose = close)
            NotesTabs.MONEY -> CaptureDialog(initial = "", initialMode = CaptureMode.EXPENSE, onClose = close)
            NotesTabs.NOTES -> adding = false
        }
    }
}

/** Conti alla rovescia: compleanni, viaggi e scadenze, dal più vicino. */
@Composable
private fun DatesTab(upcoming: List<Countdown>, padding: PaddingValues, onDeleted: (Countdown) -> Unit) {
    var editing by remember { mutableStateOf<Countdown?>(null) }
    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(upcoming, key = { it.id }) { item ->
            val days = item.daysLeft()
            CanvasCard(
                onClick = { editing = item },
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
        if (upcoming.isEmpty()) {
            item { EmptyHint("Nessuna data. Aggiungi un compleanno, un viaggio o una scadenza con il pulsante +.") }
        }
    }
    editing?.let { CountdownDialog(countdown = it, onDismiss = { editing = null }, onDeleted = onDeleted) }
}

@Composable
private fun TasksTab(tasks: List<TaskItem>, padding: PaddingValues, onDeleted: (TaskItem) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val haptic = rememberHaptic()
    val add = {
        if (NotesStore.addTask(draft) != null) haptic(Haptic.CONFIRM)
        draft = ""
    }
    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Es. domani alle 9 chiamare il dentista") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(onClick = add) { Icon(Icons.Outlined.Add, contentDescription = "Aggiungi") }
                    }
                },
                shape = MaterialTheme.shapes.large,
                colors = canvasFieldColors(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            QuickPreview(draft, Modifier.padding(start = 12.dp, bottom = 8.dp))
        }
        // Raggruppati per quando vanno fatti: prima ciò che è scaduto o in scadenza oggi.
        val now = System.currentTimeMillis()
        val endOfDay = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val open = tasks.filter { !it.done }.sortedBy { it.dueAt ?: Long.MAX_VALUE }
        val groups = listOf(
            "Scaduti" to open.filter { it.dueAt != null && it.dueAt < now },
            "Oggi" to open.filter { it.dueAt != null && it.dueAt in now until endOfDay },
            "Prossimi giorni" to open.filter { it.dueAt != null && it.dueAt >= endOfDay },
            "Senza scadenza" to open.filter { it.dueAt == null },
        ).filter { it.second.isNotEmpty() }
        val done = tasks.filter { it.done }
        // Ogni gruppo è una card con le sue righe; con un gruppo solo l'intestazione non dice niente.
        groups.forEach { (label, list) ->
            item(key = "group-$label") {
                TaskGroup(label.takeIf { groups.size > 1 }, list, onDeleted, error = label == "Scaduti")
            }
        }
        if (done.isNotEmpty()) {
            item(key = "group-done") { TaskGroup("Completati", done, onDeleted) }
        }
        if (tasks.isEmpty()) {
            item { EmptyHint("Nessun promemoria. Scrivi come parli: \"venerdì alle 18 palestra\", \"ogni mese il 5 affitto\", \"tra 20 minuti forno\". Giorno, ora e ripetizione si impostano da soli.") }
        }
    }
}

/** Abitudini di ogni giorno: un tocco per spuntare oggi, la serie di giorni di fila, l'ultima settimana. */
@Composable
private fun HabitsTab(habits: List<Habit>, padding: PaddingValues, onDeleted: (Habit) -> Unit) {
    var editing by remember { mutableStateOf<Habit?>(null) }
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (habits.isNotEmpty()) {
            item {
                val done = habits.count { it.isDone(today) }
                SectionHeader(if (done == habits.size) "Tutte fatte oggi" else "Oggi $done su ${habits.size}")
            }
        }
        items(habits, key = { it.id }) { habit ->
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
                            line.text,
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
        if (habits.isEmpty()) {
            item {
                EmptyHint(
                    "Nessuna abitudine. Aggiungi con + quello che vuoi fare ogni giorno (bere 2 litri d'acqua, " +
                        "camminare, leggere): la spunti e vedi quanti giorni di fila tieni. La sera, in Oggi, " +
                        "ti ricorda quelle che mancano."
                )
            }
        }
    }
    editing?.let { HabitDialog(habit = it, onDismiss = { editing = null }, onDeleted = onDeleted) }
}

@Composable
private fun TaskGroup(label: String?, tasks: List<TaskItem>, onDeleted: (TaskItem) -> Unit, error: Boolean = false) {
    Column(Modifier.padding(bottom = 8.dp)) {
        if (label != null) {
            SectionHeader(label, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
@Composable
private fun NotesTab(notes: List<Note>, padding: PaddingValues, onOpenNote: (Long?) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val sorted = notes
        .filter { query.isBlank() || it.text.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val haptic = rememberHaptic()
    LazyColumn(state = rememberReselectListState(Routes.NOTES), contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Con poche note la ricerca è solo ingombro: compare quando la lista si allunga.
        if (notes.size >= SEARCH_FROM || query.isNotEmpty()) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Cerca nelle note") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cancella ricerca")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors = canvasFieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        items(sorted, key = { it.id }) { note ->
            CanvasCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyPress()
                    .combinedClickable(
                        onClick = { onOpenNote(note.id) },
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
        if (notes.isEmpty()) {
            item { EmptyHint("Nessuna nota. Tocca + per scriverne una; tieni premuto una nota per fissarla in alto.") }
        } else if (sorted.isEmpty()) {
            item { EmptyHint("Nessuna nota contiene \"${query.trim()}\".") }
        }
    }
}

/** Riga sotto il titolo di una data: "12 marzo · compie 30 anni" o "· ogni anno". */
fun Countdown.dateLine(): String = longDate(nextDate()) + when {
    !yearly -> ""
    else -> yearsAtNext()?.let { " · compie $it anni" } ?: " · ogni anno"
}

/** Da quante note in su compare il campo di ricerca. */
private const val SEARCH_FROM = 6

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
