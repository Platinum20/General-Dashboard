package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.TaskItem
import java.time.Instant

/** Sezione "Note": promemoria da spuntare e note libere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    bottomPadding: PaddingValues,
    onOpenNote: (Long?) -> Unit,
) {
    val context = LocalContext.current
    NotesStore.init(context)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        contentWindowInsets = topLevelInsets,
        topBar = {
            Column {
                TopLevelBar(
                    title = "Note",
                    actions = {
                        if (tab == 0 && data.tasks.any { it.done }) {
                            IconButton(onClick = NotesStore::clearDone) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = "Elimina completati")
                            }
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Da fare") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Note") })
                }
            }
        },
        floatingActionButton = {
            if (tab == 1) {
                FloatingActionButton(
                    onClick = { onOpenNote(null) },
                    modifier = Modifier.padding(bottom = bottomPadding.calculateBottomPadding()),
                ) { Icon(Icons.Outlined.Add, contentDescription = "Nuova nota") }
            }
        },
    ) { inner ->
        val padding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = inner.calculateTopPadding() + 8.dp,
            bottom = bottomPadding.calculateBottomPadding() + 88.dp,
        )
        if (tab == 0) TasksTab(data.tasks, padding) else NotesTab(data.notes, padding, onOpenNote)
    }
}

@Composable
private fun TasksTab(tasks: List<TaskItem>, padding: PaddingValues) {
    var draft by rememberSaveable { mutableStateOf("") }
    val add = {
        NotesStore.addTask(draft)
        draft = ""
    }
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Nuovo promemoria") },
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
        }
        val open = tasks.filter { !it.done }
        val done = tasks.filter { it.done }
        items(open, key = { it.id }) { TaskRow(it) }
        if (done.isNotEmpty()) {
            item {
                Text(
                    "Completati",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(done, key = { it.id }) { TaskRow(it) }
        }
        if (tasks.isEmpty()) {
            item { EmptyHint("Nessun promemoria. Scrivi qui sopra e premi invio; toccane uno per aggiungere un avviso.") }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem) {
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { NotesStore.toggleTask(task.id) })
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
            if (!task.done) task.dueAt?.let { DueText(it) }
        }
        IconButton(onClick = { NotesStore.deleteTask(task.id) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) TaskEditDialog(task, onDismiss = { editing = false })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesTab(notes: List<Note>, padding: PaddingValues, onOpenNote: (Long?) -> Unit) {
    val sorted = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(sorted, key = { it.id }) { note ->
            ElevatedCard(
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenNote(note.id) },
                        onLongClick = { NotesStore.togglePin(note.id) },
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
