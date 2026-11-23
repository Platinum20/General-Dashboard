package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NoteSmart
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.shifts.ShiftStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Le pagine che legano le note al resto (2026-11-23): l'etichetta (#casa), la persona dei turni (@Marco)
 * e il blocco "Citato in" in fondo alle pagine di eventi, turni, treni, promemoria, pacchi e documenti.
 */

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
private fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun hm(ms: Long) = dayOf(ms).let { "%d:%02d".format(it.hour, it.minute) }

/** Una riga Canvas: icona a sinistra, nome + una riga, valore a destra. Tieni premuto = le stesse scelte. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LinkRow(icon: ImageVector, title: String, line: String, right: String = "", onOpen: () -> Unit) {
    val haptic = rememberHaptic()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = {
                    haptic(Haptic.LONG_PRESS)
                    ActionBubble.show(BubbleHead("Citato in", title), Slot.open { onOpen() })
                },
            ) { haptic(Haptic.TICK); onOpen() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotBlank()) Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (right.isNotEmpty()) Text(right, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** La seconda riga di una nota: la prima riga di testo dopo il titolo, senza segni. */
private fun Note.secondLine(): String =
    text.lines().drop(1).map { com.platinum20.dashboard.notes.NoteFormat.plain(it).trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

/**
 * "Citato in": le note che hanno un link "@" verso questa cosa. Non c'è niente se nessuna nota la cita.
 * [urls] = gli indirizzi con cui si può citare (es. la pagina dell'evento).
 */
@Composable
internal fun CitedIn(urls: List<String>, modifier: Modifier = Modifier) {
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val notes = remember(data, urls) { NoteSmart.citing(data, urls) }
    if (notes.isEmpty()) return
    val inApp = LocalInAppNav.current
    CardSection("Citato in", modifier = modifier) {
        notes.forEach { n ->
            LinkRow(Icons.AutoMirrored.Outlined.Notes, n.title.ifEmpty { "Nota" }, NoteSmart.citeLine(n, urls).let { if (it.isEmpty()) n.secondLine() else "“$it”" }) {
                inApp?.openRoute?.invoke(Routes.noteEdit(n.id))
            }
        }
    }
}

/** La pagina di un'etichetta: tutte le note e le cose da fare con #nome. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(name: String, onBack: () -> Unit) {
    NotesStore.init(LocalContext.current)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val (notes, tasks) = remember(data, name) { NoteSmart.tagged(data, name) }
    val inApp = LocalInAppNav.current
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val count = notes.size + tasks.size
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "#$name", subtitle = if (count == 1) "1 cosa con questa etichetta" else "$count cose con questa etichetta", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 160.dp)) {
            if (tasks.isNotEmpty()) item("t") {
                CardSection("Da fare e date", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    tasks.forEach { t ->
                        LinkRow(
                            if (t.dueAt != null) Icons.Outlined.Event else Icons.Outlined.TaskAlt,
                            com.platinum20.dashboard.notes.NoteFormat.plain(t.text),
                            if (t.done) "Fatta" else t.notes.lines().firstOrNull().orEmpty(),
                            t.dueAt?.let { dayOf(it).format(dayFmt) }.orEmpty(),
                        ) { inApp?.openRoute?.invoke(Routes.noteItem(ItemKind.TASK, t.id)) }
                    }
                }
            }
            if (notes.isNotEmpty()) item("n") {
                CardSection("Note", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    notes.forEach { n ->
                        LinkRow(Icons.AutoMirrored.Outlined.Notes, n.title.ifEmpty { "Nota" }, n.secondLine(), relativeTime(Instant.ofEpochMilli(n.updatedAt))) {
                            inApp?.openRoute?.invoke(Routes.noteEdit(n.id))
                        }
                    }
                }
            }
            if (count == 0) item("e") {
                Text("Nessuna nota con #$name. Scrivilo in una nota e la trovi qui.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

/** La pagina di una persona dei turni: i prossimi turni insieme, gli ultimi, e le note che la citano. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(name: String, onBack: () -> Unit) {
    NotesStore.init(LocalContext.current)
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val shifts by ShiftStore.shifts.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val together = remember(shifts, name) { shifts.filter { s -> s.mates.any { NoteSmart.firstName(it).equals(name, ignoreCase = true) } } }
    val next = together.filter { it.end >= now }.sortedBy { it.start }
    val past = together.filter { it.end < now }.sortedByDescending { it.start }.take(5)
    val url = com.platinum20.dashboard.notes.NoteFormat.APP_LINK + Routes.person(name)
    val notes = remember(data, name) {
        (NoteSmart.citing(data, listOf(url)) + data.notes.filter { n -> n.archivedAt == null && Regex("(?<![\\p{L}])${Regex.escape(name)}(?![\\p{L}])", RegexOption.IGNORE_CASE).containsMatchIn(n.text) })
            .distinctBy { it.id }
    }
    val inApp = LocalInAppNav.current
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = name,
                subtitle = if (next.isEmpty()) "Nessun turno insieme in vista" else if (next.size == 1) "1 turno insieme in arrivo" else "${next.size} turni insieme in arrivo",
                scrollBehavior = bar, onBack = onBack,
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = PaddingValues(bottom = 160.dp)) {
            fun shiftRows(title: String, list: List<com.platinum20.dashboard.shifts.Shift>) = item(title) {
                CardSection(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    list.forEach { s ->
                        LinkRow(Icons.Outlined.Work, s.title, "${hm(s.start)}–${hm(s.end)} · " + s.mates.joinToString(", ") { NoteSmart.firstName(it) }, dayOf(s.start).format(dayFmt)) {
                            inApp?.openRoute?.invoke(Routes.calendarEvent(s.eventId, s.start, s.end))
                        }
                    }
                }
            }
            if (next.isNotEmpty()) shiftRows("Prossimi turni insieme", next)
            if (notes.isNotEmpty()) item("n") {
                CardSection("Nelle note", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    notes.forEach { n ->
                        LinkRow(Icons.AutoMirrored.Outlined.Notes, n.title.ifEmpty { "Nota" }, n.secondLine()) { inApp?.openRoute?.invoke(Routes.noteEdit(n.id)) }
                    }
                }
            }
            if (past.isNotEmpty()) shiftRows("Ultimi turni insieme", past)
        }
    }
}
