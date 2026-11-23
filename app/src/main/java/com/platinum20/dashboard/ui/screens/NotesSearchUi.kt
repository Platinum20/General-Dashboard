package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
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
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesSearch
import com.platinum20.dashboard.notes.SearchHit
import com.platinum20.dashboard.notes.SearchKind
import java.time.Instant

/**
 * Cerca in tutte le Note: un campo e i risultati divisi per tipo. Promemoria, abitudini e date
 * si modificano qui; una nota si apre nell'editor; i pacchi portano alla loro scheda.
 */
@Composable
fun NotesSearchPanel(
    data: NotesData,
    padding: PaddingValues,
    onClose: () -> Unit,
    onOpenNote: (Long) -> Unit,
    /** Un pacco si vede nelle Note, in "In arrivo": dentro le Note basta chiudere la ricerca. */
    onOpenParcels: () -> Unit = onClose,
    placeholder: String = "Cerca in promemoria, note, pacchi…",
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
    /** Sotto i filtri quando il campo è vuoto (ricerche recenti, scorciatoie): può riempire il campo. */
    empty: (@Composable (setQuery: (String) -> Unit) -> Unit)? = null,
) {
    var more by remember { mutableStateOf(emptySet<SearchKind>()) }
    var query by rememberSaveable { mutableStateOf("") }
    // Ogni risultato si apre a schermo intero, come gli eventi del calendario.
    val openItem = rememberOpenItem()
    val inApp = LocalInAppNav.current
    BackHandler(onBack = onClose)
    LaunchedEffect(query) { onQuery(query) }
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { com.platinum20.dashboard.notes.NotePhotoText.init(context) }
    val photoText by com.platinum20.dashboard.notes.NotePhotoText.texts.collectAsState()
    val hits = if (showNotes) NotesSearch.search(data, query, photoText) else emptyList()

    LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "field") {
            CanvasSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus(),
            )
        }
        if (header != null) item(key = "header") { header() }
        // Le parti compaiono e spariscono scivolando, non a scatti, mentre si scrive.
        if (empty != null && query.isBlank()) item(key = "empty-hints") { Box(Modifier.animateItem()) { empty { query = it } } }
        hits.groupBy { it.kind }.forEach { (kind, group) ->
            item(key = kind.name) {
                Section(title = "${kind.label} · ${group.size}", modifier = Modifier.animateItem()) {
                    val shown = if (kind in more) group else group.take(perGroup)
                    shown.forEachIndexed { i, hit ->
                        HitRow(hit, data, query) {
                            when (hit.kind) {
                                // Trovata in una riga del testo: la nota si apre lì, con la parola colorata (idea 9).
                                SearchKind.NOTE -> if (hit.line != null && !hit.inPhoto && inApp != null &&
                                    data.notes.firstOrNull { it.id == hit.id }?.let { com.platinum20.dashboard.capture.SavedLinks.find(it.text) } == null
                                ) inApp.openRoute(com.platinum20.dashboard.Routes.noteEdit(hit.id, query.trim())) else onOpenNote(hit.id)
                                SearchKind.PARCEL -> openItem(ItemKind.PARCEL, hit.id)
                                SearchKind.TASK -> openItem(ItemKind.TASK, hit.id)
                                SearchKind.DATE -> openItem(ItemKind.TASK, hit.id)
                                SearchKind.HABIT -> openItem(ItemKind.HABIT, hit.id)
                            }
                        }
                    }
                    if (kind !in more && group.size > perGroup) {
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
private fun HitRow(hit: SearchHit, data: NotesData, query: String, onClick: () -> Unit) {
    val detail = when (hit.kind) {
        SearchKind.TASK -> data.tasks.firstOrNull { it.id == hit.id }?.let { if (it.done) "Completato" else "Da fare" }
        SearchKind.NOTE -> data.notes.firstOrNull { it.id == hit.id }?.let { "Modificata " + relativeTime(Instant.ofEpochMilli(it.updatedAt)) }
        SearchKind.PARCEL -> data.parcels.firstOrNull { it.id == hit.id }?.let { if (it.delivered) "Arrivato" else it.code.ifEmpty { "In arrivo" } }
        SearchKind.DATE -> data.tasks.firstOrNull { it.id == hit.id }?.dateLine()
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
            // Nelle note la riga dove sta la parola, con la parola in evidenza (2026-11-23, idee 9 e 30).
            val line = hit.line
            if (line != null) {
                Text(
                    foundLine(line, query, hit.inPhoto, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Password wifi: mare2024!" con "wifi" in blu e in grassetto; dalla foto: Nella foto: "…". */
private fun foundLine(line: String, query: String, inPhoto: Boolean, accent: androidx.compose.ui.graphics.Color) =
    androidx.compose.ui.text.buildAnnotatedString {
        val prefix = if (inPhoto) "Nella foto: \u201C" else ""
        append(prefix)
        append(line)
        if (inPhoto) append("\u201D")
        NotesSearch.matchRange(line, query)?.let { r ->
            addStyle(
                androidx.compose.ui.text.SpanStyle(color = accent, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                prefix.length + r.first,
                prefix.length + r.last + 1,
            )
        }
    }
