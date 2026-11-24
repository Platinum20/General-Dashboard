package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.notes.AttachedNotes
import com.platinum20.dashboard.notes.NoteNotify
import com.platinum20.dashboard.notes.NotesStore

/**
 * Il blocco "Appunti" sulle pagine di eventi, turni, treni e abitudini (idee 1 e 24, Antonio 2026-11-23): si scrive
 * qui dentro, con punti e voci da spuntare come nelle note. Gli appunti restano su questa cosa, non in Appunti.
 * [title] = il nome della cosa (la prima riga della nota, per trovarla cercando). Con [begin]/[end] (un evento) a
 * fine evento le voci rimaste aperte arrivano in una notifica (idea 28).
 *
 * [seed] = il testo che l'evento ha già nel calendario del telefono (2026-11-24, Antonio: "se hanno i dettagli
 * dell'evento finisce nella sezione appunti", "mettilo sempre alla fine", "come le altre note"): gli appunti sono una
 * nota come le altre, e il testo del calendario ci sta in fondo. Scrivi sopra; il calendario non si tocca.
 */
@Composable
internal fun AttachedNotesBlock(key: String, title: String, begin: Long = 0L, end: Long = 0L, label: String = "Appunti", seed: String = "") {
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val note = data.notes.firstOrNull { it.about == key }
    val body = AttachedNotes.body(note)
    val text = appuntiStart(body, seed).first.trim()
    AppuntiCard(
        stateKey = "attached-$key",
        value = body,
        tail = seed,
        label = label,
        headName = title,
        photos = note?.photos.orEmpty(),
        onCommit = { t ->
            NotesStore.saveAttached(key, title, t)
            if (end > 0) runCatching { NoteNotify.scheduleLeftovers(context, key, t, begin, end) }
        },
        // "APPUNTI ›" apre la nota intera (proposta 8): foto, tabelle, voce, Altro.
        onOpen = {
            val id = note?.id ?: NotesStore.saveAttached(key, title, text)
            if (id != null) inApp?.openRoute?.invoke(Routes.noteEdit(id))
        },
        onClear = if (note != null) ({ NotesStore.saveAttached(key, title, "") }) else null,
    )
}

/** Il testo del calendario che va in fondo agli appunti: niente per turni e treni col biglietto (già scritto sopra). */
internal fun appuntiSeed(description: String, shift: Boolean, ticket: Boolean): String = if (shift || ticket) "" else description

/**
 * Il testo con cui si aprono gli appunti e dove sta il cursore: quello scritto, se c'è; se no una riga vuota in cima per
 * scrivere e sotto, in fondo, il testo del calendario ([tail]).
 */
internal fun appuntiStart(body: String, tail: String): Pair<String, Int> = when {
    body.isNotBlank() -> body to body.length
    tail.isNotBlank() -> "\n" + tail.trim() to 0
    else -> "" to 0
}

/**
 * La card degli appunti, uguale su ogni pagina (2026-11-24, proposte 7-9): la faccia di una nota (niente icona, testo
 * grande come nell'editor, punti e spunte, le foto in fila piccola), il titolo che apre la nota intera ([onOpen]) e,
 * tenendola premuta, il fumetto con le sue scelte. Vuota è una riga sola, "Scrivi un appunto": toccandola diventa la
 * card con la tastiera aperta.
 */
@Composable
internal fun AppuntiCard(
    stateKey: String,
    value: String,
    label: String,
    headName: String,
    onCommit: (String) -> Unit,
    /** Il testo che sta in fondo finché non si scrive (il calendario dell'evento). */
    tail: String = "",
    photos: List<String> = emptyList(),
    onOpen: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val haptic = rememberHaptic()
    val context = LocalContext.current
    var writing by rememberSaveable(stateKey) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    if (value.isBlank() && tail.isBlank() && !writing && photos.isEmpty()) {
        CanvasCard(Modifier.fillMaxWidth().padding(top = 12.dp), onClick = { haptic(Haptic.TICK); writing = true }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Scrivi un appunto", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val start = appuntiStart(value, tail)
    // Quello che c'è senza aver scritto niente: finché il campo è uguale, non si salva niente.
    val baseline = start.first.trim()
    var field by rememberSaveable(stateKey, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(start.first, androidx.compose.ui.text.TextRange(start.second)))
    }
    // Il testo cambiato da fuori (la nota intera, una spunta dalla notifica): la card lo mostra appena torni.
    // Solo mentre non ci scrivi: se no un salvataggio appena arrivato riporterebbe indietro le ultime lettere.
    var typing by remember { mutableStateOf(false) }
    var seen by remember(stateKey) { mutableStateOf(value) }
    if (value != seen && !typing) {
        seen = value
        if (value.isNotBlank() && value.trim() != field.text.trim()) field = TextFieldValue(value)
    }
    val commit by rememberUpdatedState(onCommit)
    LaunchedEffect(field.text) {
        kotlinx.coroutines.delay(600)
        if (field.text.trim() != baseline && field.text.trim() != value.trim()) commit(field.text.trim())
    }
    LaunchedEffect(writing) { if (writing) runCatching { focus.requestFocus() } }
    CardSection(
        label,
        Modifier.padding(top = 12.dp),
        onClick = onOpen?.let { open -> { haptic(Haptic.TICK); open() } },
        onLongClick = {
            haptic(Haptic.LONG_PRESS)
            ActionBubble.show(
                BubbleHead(label, headName),
                *listOfNotNull(
                    onOpen?.let { open -> Slot.open { open() } },
                    Slot.copy { Clip.copy(context, label, field.text, "Appunti copiati") },
                    onClear?.let { clear -> Slot.clear(confirm = "Tocca ancora: gli appunti si cancellano") { clear(); field = TextFieldValue(""); writing = false } },
                ).toTypedArray(),
            )
        },
    ) {
        RichNoteField(
            value = field,
            onValueChange = { field = it },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            placeholder = "Scrivi un appunto…",
            focusRequester = focus,
            onFocusChange = { typing = it },
            sendToTodo = true,
            firstAir = false,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = if (photos.isEmpty()) 14.dp else 8.dp),
        )
        if (photos.isNotEmpty()) Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) {
            photos.take(4).forEach { path ->
                coil3.compose.AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Foto degli appunti",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}
