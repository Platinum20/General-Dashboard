package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.notes.AttachedNotes
import com.platinum20.dashboard.notes.NoteNotify
import com.platinum20.dashboard.notes.NotesStore

/**
 * Il blocco "Appunti" sulle pagine di eventi, turni, treni e abitudini (idee 1 e 24, Antonio 2026-11-23): si scrive
 * qui dentro, con punti e voci da spuntare come nelle note. Gli appunti restano su questa cosa, non in Appunti.
 * [title] = il nome della cosa (la prima riga della nota, per trovarla cercando). Con [begin]/[end] (un evento) a
 * fine evento le voci rimaste aperte arrivano in una notifica (idea 28).
 */
@Composable
internal fun AttachedNotesBlock(key: String, title: String, begin: Long = 0L, end: Long = 0L, label: String = "Appunti") {
    val context = LocalContext.current
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val note = data.notes.firstOrNull { it.about == key }
    val body = AttachedNotes.body(note)
    // Il titolo dentro la card, come "Citato in" subito sopra: due card uguali, non un titolo che galleggia (2026-11-23).
    CardSection(label, modifier = Modifier.padding(top = 12.dp)) {
        DetailRichRow("attached-$key", Icons.AutoMirrored.Outlined.Notes, body, "Tocca per scrivere…") { text ->
            NotesStore.saveAttached(key, title, text)
            if (end > 0) runCatching { NoteNotify.scheduleLeftovers(context, key, text, begin, end) }
        }
    }
}
