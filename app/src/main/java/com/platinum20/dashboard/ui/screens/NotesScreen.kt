package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilledIconToggleButton
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.notes.Countdown
import com.platinum20.dashboard.notes.Habit
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

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
 * Le sei schede delle Note come chip, come i filtri delle News: stanno meglio in larghezza
 * di sei tab e la scelta resta sempre visibile (la lista scorre da sola fino al chip attivo).
 */
@Composable
private fun NotesTabChips(selected: NotesTabs, onSelect: (NotesTabs) -> Unit) {
    val listState = rememberLazyListState()
    val haptic = rememberHaptic()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected.ordinal - 1).coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(NotesTabs.entries) { t ->
            FilterChip(
                selected = selected == t,
                onClick = { if (selected != t) haptic(Haptic.TICK); onSelect(t) },
                label = { Text(t.label) },
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Eliminare è immediato, ma per qualche secondo si può tornare indietro.
    fun offerUndo(removed: List<TaskItem>, message: String) {
        if (removed.isEmpty()) return
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = "Annulla", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) NotesStore.restoreTasks(removed)
        }
    }

    Scaffold(
        contentWindowInsets = topLevelInsets,
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = bottomPadding.calculateBottomPadding()))
        },
        topBar = {
            Column {
                TopLevelBar(
                    title = "Note",
                    actions = {
                        if (tab == NotesTabs.TASKS && data.tasks.any { it.done }) {
                            IconButton(onClick = {
                                val done = data.tasks.filter { it.done }
                                NotesStore.clearDone()
                                offerUndo(done, if (done.size == 1) "Eliminato 1 completato" else "Eliminati ${done.size} completati")
                            }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Elimina completati")
                            }
                        }
                    },
                )
                NotesTabChips(selected = tab, onSelect = { tab = it })
            }
        },
        floatingActionButton = {
            // Promemoria e spese si scrivono nel campo in alto: il + serve alle altre schede.
            val label = tab.addLabel
            if (label != null) {
                FloatingActionButton(
                    onClick = { if (tab == NotesTabs.NOTES) onOpenNote(null) else adding = true },
                    modifier = Modifier.padding(bottom = bottomPadding.calculateBottomPadding()).bouncyPress(0.9f),
                ) { Icon(Icons.Outlined.Add, contentDescription = label) }
            }
        },
    ) { inner ->
        val padding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = inner.calculateTopPadding() + 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 88.dp,
        )
        when (tab) {
            NotesTabs.TASKS -> TasksTab(data.tasks, padding, onDeleted = { offerUndo(listOf(it), "Promemoria eliminato") })
            NotesTabs.HABITS -> HabitsTab(data.habits, padding)
            NotesTabs.MONEY -> ExpensesTab(data, padding)
            NotesTabs.NOTES -> NotesTab(data.notes, padding, onOpenNote)
            NotesTabs.PARCELS -> ParcelsTab(data.parcels, padding)
            NotesTabs.DATES -> DatesTab(data.upcoming, padding)
        }
    }
    if (adding) {
        val close = { adding = false }
        when (tab) {
            NotesTabs.HABITS -> HabitDialog(habit = null, onDismiss = close)
            NotesTabs.PARCELS -> ParcelDialog(parcel = null, onDismiss = close)
            NotesTabs.DATES -> CountdownDialog(countdown = null, onDismiss = close)
            else -> adding = false
        }
    }
}

/** Conti alla rovescia: compleanni, viaggi e scadenze, dal più vicino. */
@Composable
private fun DatesTab(upcoming: List<Countdown>, padding: PaddingValues) {
    var editing by remember { mutableStateOf<Countdown?>(null) }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(upcoming, key = { it.id }) { item ->
            val days = item.daysLeft()
            ElevatedCard(
                onClick = { editing = item },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().bouncyPress(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            longDate(item.nextDate()) + if (item.yearly) " · ogni anno" else "",
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
    editing?.let { CountdownDialog(countdown = it, onDismiss = { editing = null }) }
}

@Composable
private fun TasksTab(tasks: List<TaskItem>, padding: PaddingValues, onDeleted: (TaskItem) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val haptic = rememberHaptic()
    val add = {
        if (NotesStore.addTask(draft) != null) haptic(Haptic.CONFIRM)
        draft = ""
    }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        groups.forEach { (label, list) ->
            // Con un gruppo solo l'intestazione non dice niente.
            if (groups.size > 1) item(key = "group-$label") { GroupLabel(label, error = label == "Scaduti") }
            items(list, key = { it.id }) { TaskRow(it, onDeleted) }
        }
        if (done.isNotEmpty()) {
            item { GroupLabel("Completati") }
            items(done, key = { it.id }) { TaskRow(it, onDeleted) }
        }
        if (tasks.isEmpty()) {
            item { EmptyHint("Nessun promemoria. Scrivi come parli: \"venerdì alle 18 palestra\", \"ogni mese il 5 affitto\", \"tra 20 minuti forno\". Giorno, ora e ripetizione si impostano da soli.") }
        }
    }
}

/** Abitudini di ogni giorno: un tocco per spuntare oggi, la serie di giorni di fila, l'ultima settimana. */
@Composable
private fun HabitsTab(habits: List<Habit>, padding: PaddingValues) {
    var editing by remember { mutableStateOf<Habit?>(null) }
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (habits.isNotEmpty()) {
            item {
                val done = habits.count { it.isDone(today) }
                Text(
                    if (done == habits.size) "Tutte fatte oggi" else "Oggi $done su ${habits.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
        items(habits, key = { it.id }) { habit ->
            val doneToday = habit.isDone(today)
            val streak = habit.streak(today)
            ElevatedCard(
                onClick = { editing = habit },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().bouncyPress(0.98f),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(habit.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (streak) {
                                0 -> "Inizia oggi"
                                1 -> "1 giorno"
                                else -> "$streak giorni di fila"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            habit.lastDays(today).forEach { (day, done) ->
                                // Il pallino è piccolo, ma si tocca tutta la colonna (pallino e iniziale del giorno).
                                Column(
                                    Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            haptic(if (done) Haptic.TICK else Haptic.CONFIRM)
                                            NotesStore.toggleHabit(habit.id, day)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (done) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
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
                    FilledIconToggleButton(
                        checked = doneToday,
                        onCheckedChange = {
                            haptic(if (it) Haptic.CONFIRM else Haptic.TICK)
                            NotesStore.toggleHabit(habit.id)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = if (doneToday) "Fatta oggi" else "Segna fatta oggi")
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
    editing?.let { HabitDialog(habit = it, onDismiss = { editing = null }) }
}

@Composable
private fun GroupLabel(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun TaskRow(task: TaskItem, onDeleted: (TaskItem) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        IconButton(onClick = {
            NotesStore.deleteTask(task.id)
            onDeleted(task)
        }) {
            Icon(Icons.Outlined.Close, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) TaskEditDialog(task, onDismiss = { editing = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesTab(notes: List<Note>, padding: PaddingValues, onOpenNote: (Long?) -> Unit) {
    val sorted = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    val haptic = rememberHaptic()
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sorted, key = { it.id }) { note ->
            ElevatedCard(
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
                    if (note.body.isNotBlank()) {
                        Text(
                            note.body,
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
        }
    }
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

/** Editor di una nota: si salva da solo quando si esce. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    var id by remember { mutableStateOf(noteId) }
    var text by rememberSaveable { mutableStateOf(noteId?.let { NotesStore.note(it)?.text }.orEmpty()) }
    var deleted by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { if (!deleted) NotesStore.saveNote(id, text) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "Nuova nota" else "Nota") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
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
                        id?.let(NotesStore::deleteNote)
                        onBack()
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Elimina")
                    }
                },
            )
        },
    ) { inner ->
        TextField(
            value = text,
            onValueChange = {
                text = it
                // Salvataggio continuo: niente perso se l'app viene chiusa.
                id = NotesStore.saveNote(id, it) ?: id
            },
            placeholder = { Text("Scrivi… la prima riga fa da titolo") },
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.bodyLarge),
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
                .imePadding(),
        )
    }
}
