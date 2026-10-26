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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onOpenExpenses: () -> Unit,
    /** Un pacco si vede nelle Note, in "In arrivo": dentro le Note basta chiudere la ricerca. */
    onOpenParcels: () -> Unit = onClose,
    placeholder: String = "Cerca in promemoria, note, spese, pacchi…",
    /** Gruppi in più (la ricerca ovunque di Oggi): restituisce quanti risultati ha aggiunto. */
    extra: LazyListScope.(query: String) -> Int = { 0 },
    /** Sotto il campo (i filtri di Cerca ovunque). */
    header: (@Composable () -> Unit)? = null,
    /** False per nascondere i risultati delle Note (un filtro diverso è acceso). */
    showNotes: Boolean = true,
    /** Avvisa a ogni lettera: chi cerca anche in rete parte da qui. */
    onQuery: (String) -> Unit = {},
    /** Quanti risultati per gruppo prima di "Mostra altri" (Cerca ovunque ne mostra pochi per fonte). */
    perGroup: Int = Int.MAX_VALUE,
) {
    var more by remember { mutableStateOf(emptySet<SearchKind>()) }
    var query by rememberSaveable { mutableStateOf("") }
    // Ogni risultato si apre a schermo intero, come gli eventi del calendario.
    val openItem = rememberOpenItem()
    BackHandler(onBack = onClose)
    LaunchedEffect(query) { onQuery(query) }
    val hits = if (showNotes) NotesSearch.search(data, query) else emptyList()

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
        if (header != null) item(key = "header") { header() }
        hits.groupBy { it.kind }.forEach { (kind, group) ->
            item(key = kind.name) {
                Section(title = "${kind.label} · ${group.size}") {
                    val shown = if (kind in more) group else group.take(perGroup)
                    shown.forEachIndexed { i, hit ->
                        if (i > 0) RowDivider()
                        HitRow(hit, data) {
                            when (hit.kind) {
                                SearchKind.NOTE -> onOpenNote(hit.id)
                                SearchKind.EXPENSE -> openItem(ItemKind.EXPENSE, hit.id)
                                SearchKind.PARCEL -> openItem(ItemKind.PARCEL, hit.id)
                                SearchKind.TASK -> openItem(ItemKind.TASK, hit.id)
                                SearchKind.DATE -> openItem(ItemKind.DATE, hit.id)
                                SearchKind.HABIT -> openItem(ItemKind.HABIT, hit.id)
                            }
                        }
                    }
                    if (kind !in more && group.size > perGroup) {
                        RowDivider()
                        TextButton(onClick = { more = more + kind }, modifier = Modifier.fillMaxWidth()) {
                            Text("Mostra altri ${group.size - perGroup}")
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
