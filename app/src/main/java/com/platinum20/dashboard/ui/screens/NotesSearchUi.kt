package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesSearch
import com.platinum20.dashboard.notes.SearchHit
import com.platinum20.dashboard.notes.SearchKind
import java.time.Instant

/**
 * Cerca in tutte le Note: un campo e i risultati divisi per tipo. Promemoria, abitudini e date
 * si modificano qui; una nota si apre nell'editor; spese e pacchi portano alla loro scheda.
 */
@Composable
fun NotesSearchPanel(
    data: NotesData,
    padding: PaddingValues,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenTab: (NotesTabs) -> Unit,
    placeholder: String = "Cerca in promemoria, note, spese, pacchi…",
    /** Gruppi in più (la ricerca ovunque di Oggi): restituisce quanti risultati ha aggiunto. */
    extra: LazyListScope.(query: String) -> Int = { 0 },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<SearchHit?>(null) }
    BackHandler(onBack = onClose)
    val hits = NotesSearch.search(data, query)

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "field") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { if (query.isEmpty()) onClose() else query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = if (query.isEmpty()) "Chiudi ricerca" else "Cancella ricerca")
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = canvasFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus(),
            )
        }
        hits.groupBy { it.kind }.forEach { (kind, group) ->
            item(key = kind.name) {
                Section(title = "${kind.label} · ${group.size}") {
                    group.forEachIndexed { i, hit ->
                        if (i > 0) RowDivider()
                        HitRow(hit, data) {
                            when (hit.kind) {
                                SearchKind.NOTE -> onOpenNote(hit.id)
                                SearchKind.EXPENSE -> onOpenTab(NotesTabs.MONEY)
                                SearchKind.PARCEL -> onOpenTab(NotesTabs.COMING)
                                else -> open = hit
                            }
                        }
                    }
                }
            }
        }
        val more = extra(query)
        if (query.isNotBlank() && hits.isEmpty() && more == 0) {
            item(key = "empty") {
                Text(
                    "Niente trovato per \"${query.trim()}\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    val close = { open = null }
    open?.let { hit ->
        when (hit.kind) {
            SearchKind.TASK -> data.tasks.firstOrNull { it.id == hit.id }?.let { TaskEditDialog(it, onDismiss = close) }
            SearchKind.HABIT -> data.habits.firstOrNull { it.id == hit.id }?.let { HabitDialog(it, onDismiss = close) }
            SearchKind.DATE -> data.countdowns.firstOrNull { it.id == hit.id }?.let { CountdownDialog(it, onDismiss = close) }
            else -> Unit
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, data: NotesData, onClick: () -> Unit) {
    val detail = when (hit.kind) {
        SearchKind.TASK -> data.tasks.firstOrNull { it.id == hit.id }?.let { if (it.done) "Completato" else "Da fare" }
        SearchKind.NOTE -> data.notes.firstOrNull { it.id == hit.id }?.let { "Modificata " + relativeTime(Instant.ofEpochMilli(it.updatedAt)) }
        SearchKind.EXPENSE -> data.expenses.firstOrNull { it.id == hit.id }?.let { "${Expenses.format(it.cents)} · ${it.category.label} · ${longDate(it.date)}" }
        SearchKind.PARCEL -> data.parcels.firstOrNull { it.id == hit.id }?.let { if (it.delivered) "Arrivato" else it.code.ifEmpty { "In arrivo" } }
        SearchKind.DATE -> data.countdowns.firstOrNull { it.id == hit.id }?.dateLine()
        SearchKind.HABIT -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(hit.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
