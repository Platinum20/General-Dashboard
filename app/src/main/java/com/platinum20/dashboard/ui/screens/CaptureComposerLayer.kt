package com.platinum20.dashboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick as semanticsClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    // Un velo scuro (1.532): mentre si scrive la pagina sotto non si legge più e non confonde; toccando fuori si chiude.
    AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(tween(Motion.EXIT_MS)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = CaptureHints.VEIL_ALPHA))
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
            val chosen = CaptureComposer.chosen
            CaptureComposer.close()
            if (value.isNotEmpty()) {
                haptic(Haptic.CONFIRM)
                scope.launch {
                    when (val outcome = VoiceCapture.save(context, value, chosen)) {
                        VoiceCapture.Outcome.NeedsDialog -> needsDialog(value)
                        // Calendario senza giorno: si chiede solo quando, nel fumetto (1.536), non più il dialogo.
                        is VoiceCapture.Outcome.NeedsWhen -> CaptureWhenAsk.askDay(context, scope, outcome.text)
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

/** Le due strade per allegare: fotocamera o galleria. */
private class PhotoPick(val camera: () -> Unit, val gallery: () -> Unit)

/**
 * La graffetta: scatta o scegli una foto, Canvas la legge e la scrive nella barra. Si controlla e
 * si invia come sempre.
 */
@Composable
private fun rememberPhotoAttach(onReading: (Boolean) -> Unit): PhotoPick {
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
    val inApp = LocalInAppNav.current
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        // Un video del telefono si guarda nel player unico (1.475: "Sul telefono" non sta più in Video).
        if (uri != null && context.contentResolver.getType(uri)?.startsWith("video/") == true) {
            CaptureComposer.attaching = false
            CaptureComposer.close()
            runCatching { com.platinum20.dashboard.media.MediaLauncher.playVideo(context, uri) }
                .onSuccess { route -> inApp?.openRoute?.invoke(route) }
                .onFailure { CaptureFeedback.tell(context, "Non riesco ad aprire questo video") }
        } else readInto(uri, cameraFile = false)
    }
    // Le scelte stanno dentro il riquadro, come in Claude (1.635): niente fumetto sopra.
    fun go(start: () -> Unit) {
        CaptureComposer.attaching = true
        runCatching(start).onFailure { CaptureComposer.attaching = false }
    }
    return remember {
        PhotoPick(
            camera = { go { camera.launch(ReceiptReader.photoUri(context)) } },
            gallery = { go { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } },
        )
    }
}

/**
 * Il riquadro per scrivere (1.634, come nell'app Claude): in cima dove andrà (le chip), poi il campo che cresce col
 * testo fino a sei righe, sotto i tasti come in Claude: + per allegare a sinistra, microfono e Invia a destra.
 * Il microfono detta dentro il testo a mani libere.
 */
@Composable
internal fun CaptureComposerInput(modifier: Modifier = Modifier, onSend: () -> Unit) {
    val haptic = rememberHaptic()
    val text = CaptureComposer.text
    var reading by remember { mutableStateOf(false) }
    val attach = rememberPhotoAttach { reading = it }
    // Il + apre dentro il riquadro le scelte per allegare, come in Claude; ritoccato (ora è una ×) le chiude.
    var tray by remember { mutableStateOf(false) }
    val empty = text.text.isEmpty()
    // Il segnaposto è un esempio che cambia: insegna cosa si può scrivere senza occupare una riga in più.
    var exampleAt by remember { mutableStateOf(0) }
    LaunchedEffect(empty, reading) {
        while (empty && !reading) {
            delay(CaptureHints.EXAMPLE_MS)
            exampleAt += 1
        }
    }
    // Misure dell'app Claude (Antonio, 1.636): riquadro alto, testo più grande staccato dai tasti, tasti pieni.
    Column(modifier.padding(start = 10.dp, end = 10.dp, top = 18.dp, bottom = 10.dp)) {
        CaptureDestinationChips(text.text, Modifier.padding(start = 6.dp, bottom = 8.dp))
        Box(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 22.dp), contentAlignment = Alignment.TopStart) {
            val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp)
            if (empty) {
                val hint = if (reading) "Leggo la foto…" else CaptureHints.example(exampleAt)
                Crossfade(hint, animationSpec = tween(Motion.EXIT_MS), label = "esempio") { shown ->
                    Text(shown, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Cresce col testo fino a sei righe, poi scorre dentro.
            BasicTextField(
                value = text,
                onValueChange = { CaptureComposer.text = it },
                textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus()
                    .semantics { contentDescription = "Scrivi" },
            )
        }
        AnimatedVisibility(
            visible = tray,
            enter = fadeIn(Motion.lively()) + expandVertically(Motion.lively(), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(Motion.EXIT_MS)) + shrinkVertically(Motion.settle(), shrinkTowards = Alignment.Bottom),
        ) {
            // Come "Aggiungi contesto" in Claude (Antonio, 1.635): la X e il titolo, sotto le tessere col bordo.
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 14.dp)) {
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.align(Alignment.CenterStart)) {
                        ComposerKey(Icons.Rounded.Close, "Chiudi", plain = true) { haptic(Haptic.TICK); tray = false }
                    }
                    Text("Allega", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    TrayTile(Icons.Outlined.PhotoCamera, "Fotocamera", Modifier.weight(1f)) { haptic(Haptic.TICK); tray = false; attach.camera() }
                    TrayTile(Icons.Outlined.PhotoLibrary, "Foto", Modifier.weight(1f)) { haptic(Haptic.TICK); tray = false; attach.gallery() }
                }
            }
        }
        // I tasti come nell'app Claude (1.636, Antonio): la graffetta per allegare a sinistra, a destra solo la freccia. Il microfono
        // non c'è: tenendo premuta la freccia si detta (i gesti li legge la barra in MainActivity, che resta sotto il dito).
        // Niente X: si chiude toccando fuori o con Indietro.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            // La graffetta (Antonio, 1.636): un + a sinistra sembrava il + di destra che si spostava.
            ComposerKey(Icons.Rounded.AttachFile, if (tray) "Chiudi allegati" else "Allega") { if (!reading) { haptic(Haptic.TICK); tray = !tray } }
            Spacer(Modifier.weight(1f))
            ComposerKey(Icons.Rounded.ArrowUpward, "Inserisci", main = true, touch = false) { onSend() }
        }
    }
}

/** Grande come il play di Riprendi e l'ingranaggio (Antonio, 1.636): tutti i tasti tondi uguali. */
internal const val COMPOSER_KEY_DP = 40

/** Un tasto tondo del riquadro: grigio, o blu pieno per Invia (blu = azione). */
@Composable
internal fun ComposerKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    main: Boolean = false,
    enabled: Boolean = true,
    /** Solo il contorno (il + per allegare, come in Claude). */
    outlined: Boolean = false,
    /** Solo l'icona, senza fondo (il microfono accanto a Invia). */
    plain: Boolean = false,
    /** Rotazione dell'icona in gradi (il + che diventa ×). */
    iconTurn: Float = 0f,
    /** Da 0 a 1: quanto diventa rosso (il vocale trascinato verso il cestino). */
    danger: Float = 0f,
    /** False: il tocco lo legge chi sta sopra (la freccia, che tenuta premuta detta); resta solo per l'accessibilità. */
    touch: Boolean = true,
    onClick: () -> Unit,
) {
    val base = when {
        main && enabled -> MaterialTheme.colorScheme.primary
        main -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        outlined || plain -> Color.Transparent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val bg = if (danger > 0f) androidx.compose.ui.graphics.lerp(base, MaterialTheme.colorScheme.error, danger) else base
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(COMPOSER_KEY_DP.dp)
            .bouncyPress(0.88f)
            .clip(CircleShape)
            .background(bg)
            .then(if (outlined) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier)
            .then(if (touch) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .semantics {
                contentDescription = label
                if (!touch) semanticsClick(label = label) { onClick(); true }
            },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                danger > 0.5f -> MaterialTheme.colorScheme.onError
                main -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = iconTurn },
        )
    }
}

/** Una scelta per allegare dentro il riquadro: icona sopra, nome sotto, come le tessere di Claude. */
@Composable
private fun TrayTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .bouncyPress(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

/**
 * Sopra la barra del "+", mentre si scrive (1.532, Antonio scelta A): dove andrà, come fila di chip. Canvas accende da
 * sola quella giusta; toccandone un'altra si corregge (vince fino a quando si chiude). Niente bolla trasparente sopra
 * la pagina: prima l'anteprima copriva il Calendario e sembrava rotta. Dettando non c'è (si salva appena si lascia).
 */
@Composable
internal fun CaptureDestinationChips(text: String, modifier: Modifier = Modifier) {
    val haptic = rememberHaptic()
    val row = remember(text) { com.platinum20.dashboard.capture.CapturePreview.of(text) }
    val auto = remember(text, row) { row?.let { com.platinum20.dashboard.capture.CaptureDestination.of(it.mode, text) } }
    // L'ultima scelta resta mentre la fila sparisce, così non si svuota a metà animazione.
    var last by remember { mutableStateOf(auto) }
    if (auto != null) last = auto
    AnimatedVisibility(
        visible = auto != null,
        enter = fadeIn(Motion.lively()) + expandVertically(Motion.lively(), expandFrom = Alignment.Bottom),
        exit = fadeOut(tween(Motion.EXIT_MS)) + shrinkVertically(Motion.settle(), shrinkTowards = Alignment.Bottom),
        modifier = modifier,
    ) {
        val lit = CaptureComposer.chosen ?: last ?: return@AnimatedVisibility
        // Nel Calendario la chip dice anche il giorno capito ("Calendario · Domani"): si vede subito se ha letto bene.
        val day = row?.takeIf { lit == com.platinum20.dashboard.capture.CaptureDestination.CALENDAR && CaptureComposer.chosen == null }
            ?.detail?.substringBefore(" · ")?.takeIf { it.isNotBlank() && !it.startsWith("Scrivi") }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            items(com.platinum20.dashboard.capture.CaptureDestination.entries.size) { i ->
                val d = com.platinum20.dashboard.capture.CaptureDestination.entries[i]
                val on = d == lit
                androidx.compose.material3.FilterChip(
                    selected = on,
                    onClick = {
                        haptic(Haptic.TICK)
                        // Toccando quella già scelta da Canvas si torna all'automatico.
                        CaptureComposer.chosen = if (d == auto) null else d
                    },
                    label = { Text(if (on && day != null) "${d.label} · $day" else d.label, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.card,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = on,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.bouncyPress(0.94f).semantics { contentDescription = if (on) "Va in ${d.label}" else "Metti in ${d.label}" },
                )
            }
        }
    }
}
