package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.CaptureHints
import com.platinum20.dashboard.capture.icon
import com.platinum20.dashboard.settings.AppSettings
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import com.platinum20.dashboard.capture.CaptureComposer
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.docs.DocumentsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.ReceiptReader
import com.platinum20.dashboard.capture.CaptureMode
import android.net.Uri
import com.platinum20.dashboard.capture.VoiceCapture
import kotlinx.coroutines.launch

/**
 * Il + toccato: la barra in basso diventa il campo per scrivere (vedi [CaptureComposerInput]).
 * Qui il velo sotto la barra: toccando fuori, Indietro o chiudendo la tastiera la nota si annulla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.CaptureComposerVeil() {
    val open = CaptureComposer.isOpen
    BubbleBack(open) { CaptureComposer.close() }
    // Il primo "indietro" lo usa la tastiera per chiudersi: anche quello annulla la nota.
    val imeVisible = WindowInsets.isImeVisible
    var imeShown by remember { mutableStateOf(false) }
    // Mentre si detta la tastiera si chiude da sola: quello non annulla niente.
    val dictating = CaptureComposer.dictating
    val attaching = CaptureComposer.attaching
    LaunchedEffect(imeVisible, open, dictating, attaching) {
        if (!open || attaching) imeShown = false
        else if (imeVisible) imeShown = true
        else if (imeShown && !dictating) CaptureComposer.close()
    }
    // Un velo leggero: toccando fuori dalla barra si chiude.
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(tween(Motion.EXIT_MS)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.10f))
                .pointerInput(Unit) { detectTapGestures { CaptureComposer.close() } },
        )
    }
}

/** Invia quello che c'è nella barra: si salva da solo nel tipo giusto, il dialogo solo se manca qualcosa. */
@Composable
internal fun rememberComposerSend(onNeedsDialog: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val needsDialog by rememberUpdatedState(onNeedsDialog)
    return remember {
        {
            val value = CaptureComposer.text.text.trim()
            CaptureComposer.close()
            if (value.isNotEmpty()) {
                haptic(Haptic.CONFIRM)
                scope.launch {
                    when (val outcome = VoiceCapture.save(context, value)) {
                        VoiceCapture.Outcome.NeedsDialog -> needsDialog(value)
                        is VoiceCapture.Outcome.Answered -> com.platinum20.dashboard.capture.CaptureAsk.show(context, outcome.answer)
                        is VoiceCapture.Outcome.Saved -> {
                            AppSettings.countCaptureSave()
                            CaptureFeedback.show(CaptureNotice(outcome.mode, outcome.message, outcome.undo))
                        }
                    }
                }
            }
        }
    }
}

/**
 * La graffetta: scatta o scegli una foto, Canvas la legge e la scrive nella barra. Si controlla e
 * si invia come sempre.
 */
@Composable
private fun rememberPhotoAttach(onReading: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reading by rememberUpdatedState(onReading)
    fun readInto(uri: Uri?, cameraFile: Boolean) {
        if (uri == null) { CaptureComposer.attaching = false; return }
        reading(true)
        scope.launch {
            DocumentsStore.init(context)
            val waiting = DocumentsStore.waitingBack()
            val found = PhotoAttach.decide(PhotoAttach.rows(context, uri), waitingBack = waiting != null)
            val isDoc = found is PhotoAttach.Found.Document || found is PhotoAttach.Found.Back
            // Se diventa una nota la foto si tiene, copiata nell'app; quella della fotocamera in cache no.
            val kept = when {
                isDoc -> {
                    val kind = (found as? PhotoAttach.Found.Document)?.scan?.kind ?: waiting?.kind ?: com.platinum20.dashboard.docs.DocKind.OTHER
                    DocumentsStore.keep(context, uri, kind)
                }
                found is PhotoAttach.Found.Photo -> PhotoAttach.keep(context, uri)?.let { DocumentsStore.Kept(it, null) }
                else -> null
            }
            if (cameraFile) runCatching { java.io.File(context.cacheDir, "receipts/scontrino.jpg").delete() }
            reading(false)
            CaptureComposer.attaching = false
            when {
                found is PhotoAttach.Found.Text -> CaptureComposer.append(found.text)
                isDoc && kept != null -> {
                    CaptureComposer.close()
                    val notice = if (found is PhotoAttach.Found.Back && waiting != null) {
                        DocumentsStore.addBack(waiting.id, kept, found.scan)
                        CaptureNotice(CaptureMode.NOTE, "Retro aggiunto a ${waiting.title}", undo = { DocumentsStore.removeBack(waiting.id) }, icon = Icons.Outlined.Badge)
                    } else {
                        val doc = DocumentsStore.addFront(kept, (found as PhotoAttach.Found.Document).scan)
                        CaptureNotice(CaptureMode.NOTE, "${doc.title} in Documenti · ora il retro", undo = { DocumentsStore.delete(doc.id) }, icon = Icons.Outlined.Badge)
                    }
                    CaptureFeedback.show(notice)
                }
                kept != null -> {
                    // Quello già scritto nella barra fa da titolo della nota.
                    val title = CaptureComposer.text.text.trim().ifEmpty { "Foto" }
                    val note = NotesStore.addPhotoNote(listOf(title, (found as PhotoAttach.Found.Photo).text).filter { it.isNotBlank() }.joinToString("\n"), kept.path)
                    CaptureComposer.close()
                    CaptureFeedback.show(CaptureNotice(CaptureMode.NOTE, "Foto salvata nelle note", undo = { NotesStore.deleteNote(note.id) }))
                }
            }
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        readInto(if (ok) ReceiptReader.photoUri(context) else null, cameraFile = true)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        readInto(uri, cameraFile = false)
    }
    // Il fumetto sta in basso: la tastiera si chiude per lasciarlo vedere, senza annullare la nota.
    val keyboard = LocalSoftwareKeyboardController.current
    var choosing by remember { mutableStateOf(false) }
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(ActionBubble.actions == null) {
        if (ActionBubble.actions == null && choosing) {
            choosing = false
            // Chiuso senza scegliere: si torna a scrivere.
            if (!launched) CaptureComposer.attaching = false
        }
    }
    fun go(start: () -> Unit) {
        launched = true
        runCatching(start).onFailure { CaptureComposer.attaching = false }
    }
    return {
        CaptureComposer.attaching = true
        launched = false
        choosing = true
        keyboard?.hide()
        ActionBubble.show(
            BubbleAction(Icons.Outlined.PhotoCamera, "Scatta una foto", "Scontrino, documento, biglietto: capisce da sé") {
                go { camera.launch(ReceiptReader.photoUri(context)) }
            },
            BubbleAction(Icons.Outlined.PhotoLibrary, "Scegli una foto", "Canvas la legge e capisce cosa farne") {
                go { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            },
        )
    }
}

/**
 * Dentro la barra trasformata, sempre alta quanto il +: la graffetta, il campo (gli esempi fanno da
 * segnaposto), la X e, tutto a destra mentre si scrive, il simbolo di cosa diventerà. Il tasto
 * invia è il + accanto, che intanto è diventato una freccia.
 */
@Composable
internal fun CaptureComposerInput(modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val text = CaptureComposer.text
    val mode = remember(text.text) { CaptureGuess.guess(text.text) }
    var reading by remember { mutableStateOf(false) }
    val attach = rememberPhotoAttach { reading = it }
    val empty = text.text.isEmpty()
    // Il segnaposto è un esempio che cambia: insegna cosa si può scrivere senza occupare una riga in più.
    var exampleAt by remember { mutableStateOf(0) }
    LaunchedEffect(empty, reading) {
        while (empty && !reading) {
            delay(CaptureHints.EXAMPLE_MS)
            exampleAt += 1
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(CaptureHints.BAR_DP.dp).padding(horizontal = 6.dp),
    ) {
        // Tutto a sinistra, come nelle chat: allega una foto.
        IconButton(onClick = { if (!reading) { haptic(Haptic.TICK); attach() } }, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.AttachFile,
                contentDescription = "Allega",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            val style = MaterialTheme.typography.bodyLarge
            if (empty) {
                val hint = if (reading) "Leggo la foto…" else CaptureHints.example(exampleAt)
                Crossfade(hint, animationSpec = tween(Motion.EXIT_MS), label = "esempio") { shown ->
                    Text(shown, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Una riga sola visibile: il testo lungo o a capo scorre dentro, la barra non cresce mai.
            BasicTextField(
                value = text,
                onValueChange = { CaptureComposer.text = it },
                textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus()
                    .semantics { contentDescription = "Scrivi" },
            )
        }
        // La X: con del testo lo cancella, a campo vuoto chiude senza salvare.
        IconButton(
            onClick = {
                haptic(Haptic.TICK)
                if (empty) CaptureComposer.close() else CaptureComposer.text = TextFieldValue("")
            },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = if (empty) "Annulla" else "Cancella il testo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        // Tutto a destra, appena si scrive: il simbolo di cosa diventerà, che cambia mentre si scrive.
        AnimatedVisibility(
            visible = !empty,
            enter = fadeIn(Motion.lively()) + expandHorizontally(Motion.lively(), expandFrom = Alignment.Start) + scaleIn(Motion.lively(), initialScale = 0.6f),
            exit = fadeOut(tween(Motion.EXIT_MS)) + shrinkHorizontally(Motion.settle(), shrinkTowards = Alignment.Start),
        ) {
            val summary = remember(text.text, mode) { CaptureHints.summary(mode, text.text) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).semantics { contentDescription = summary ?: mode.label },
            ) {
                Crossfade(mode, animationSpec = tween(Motion.EXIT_MS), label = "tipo") { shown ->
                    Icon(shown.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}
