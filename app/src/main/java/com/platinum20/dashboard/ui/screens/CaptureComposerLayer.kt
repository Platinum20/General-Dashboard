package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.layout.heightIn
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
import com.platinum20.dashboard.capture.AUTO_HINT
import com.platinum20.dashboard.capture.CaptureComposer
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureGuess
import com.platinum20.dashboard.capture.CaptureKindTag
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.capture.CapturePreview
import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.notes.ReceiptReader
import android.net.Uri
import android.widget.Toast
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
    BackHandler(open) { CaptureComposer.close() }
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
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(tween(160)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
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
                        is VoiceCapture.Outcome.Saved ->
                            CaptureFeedback.show(CaptureNotice(outcome.mode, outcome.message, outcome.undo))
                    }
                }
            }
        }
    }
}

/**
 * La graffetta: scatta o scegli una foto, Canvas la legge e la scrive nella barra. Si controlla e
 * si invia come sempre (uno scontrino diventa una spesa). Prima stava dentro Spese.
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
            val found = PhotoAttach.read(context, uri)
            // La foto della fotocamera non si tiene: serviva solo a leggerla.
            if (cameraFile) runCatching { java.io.File(context.cacheDir, "receipts/scontrino.jpg").delete() }
            reading(false)
            CaptureComposer.attaching = false
            if (found != null) CaptureComposer.append(found)
            else Toast.makeText(context, "Non trovo niente da leggere in questa foto", Toast.LENGTH_LONG).show()
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
            BubbleAction(Icons.Outlined.PhotoCamera, "Scatta una foto", "Uno scontrino diventa una spesa") {
                go { camera.launch(ReceiptReader.photoUri(context)) }
            },
            BubbleAction(Icons.Outlined.PhotoLibrary, "Scegli una foto", "Canvas la legge e capisce cosa farne") {
                go { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            },
        )
    }
}

/**
 * Dentro la barra trasformata: la graffetta, il campo, la X per annullare e, mentre si scrive,
 * cosa diventerà. Il tasto invia è il + accanto, che intanto è diventato una freccia.
 */
@Composable
internal fun CaptureComposerInput(modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val text = CaptureComposer.text
    val mode = remember(text.text) { CaptureGuess.guess(text.text) }
    var reading by remember { mutableStateOf(false) }
    val attach = rememberPhotoAttach { reading = it }
    Column(modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        // Non una scelta ma una risposta: che cosa diventa e cosa ha capito Canvas.
        if (text.text.isNotBlank()) {
            Column(Modifier.padding(start = 14.dp, top = 6.dp, end = 10.dp)) {
                CaptureKindTag(mode)
                CapturePreview(mode, text.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            // Tutto a sinistra, come nelle chat: allega una foto.
            IconButton(onClick = { if (!reading) { haptic(Haptic.TICK); attach() } }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.AttachFile,
                    contentDescription = "Allega",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                val style = MaterialTheme.typography.bodyLarge
                if (text.text.isEmpty()) {
                    Text(if (reading) "Leggo la foto…" else AUTO_HINT, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { CaptureComposer.text = it },
                    textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 24.dp)
                        .autoFocus()
                        .semantics { contentDescription = "Scrivi" },
                )
            }
            // La X annulla: la nota non si salva.
            IconButton(onClick = { haptic(Haptic.TICK); CaptureComposer.close() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Annulla",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
