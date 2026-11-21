package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
import androidx.compose.material.icons.outlined.Checklist as ChecklistIcon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.Dictation
import com.platinum20.dashboard.capture.PhotoAttach
import com.platinum20.dashboard.notes.Checklist
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotePhotoFiles
import com.platinum20.dashboard.notes.NotePhotos
import com.platinum20.dashboard.notes.NoteText
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.ReceiptReader
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Editor di una nota lunga (1.529, Antonio scelta B di /mnt/project-files/editor-note/): fatto come le altre schede.
 * In alto Indietro e una riga sola (quando, parole, foto); il titolo; le foto in fila sotto il titolo (tocca = grande,
 * tieni premuto = fumetto, tieni premuto e trascina = ordine); il testo. In basso "Aggiungi" a sinistra (foto, scatta,
 * detta, lista, titoletto; tenuto premuto = foto subito) e "Altro" a destra. Grassetto e gli altri formati compaiono solo
 * con del testo scelto. Si salva da sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long?, onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf(noteId) }
    val first = remember { NoteText.split(noteId?.let { NotesStore.note(it)?.text }.orEmpty()) }
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(first.first)) }
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(first.second)) }
    val text = NoteText.join(title.text, body.text)
    var deleted by remember { mutableStateOf(false) }
    val data by NotesStore.data.collectAsStateWithLifecycle()
    val current = id?.let { now -> data.notes.firstOrNull { it.id == now } }
    val pinned = current?.pinned ?: false
    val photos = current?.photos.orEmpty()
    // Quando è stata scritta e toccata l'ultima volta, prima di aprirla adesso.
    val opened = remember { noteId?.let { NotesStore.note(it) } }
    var viewer by remember { mutableStateOf<Int?>(null) }
    var arranging by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }

    fun save(next: String = NoteText.join(title.text, body.text)) {
        id = NotesStore.saveNote(id, next) ?: id
    }

    fun setText(next: String) {
        val (t, b) = NoteText.split(next)
        title = TextFieldValue(t)
        body = TextFieldValue(b)
        save(next)
    }

    DisposableEffect(Unit) {
        onDispose { if (!deleted) NotesStore.saveNote(id, NoteText.join(title.text, body.text)) }
    }

    // ---- Foto ----
    // Le note con la stessa foto due volte (prima della 1.552 si poteva) tornano con una sola.
    LaunchedEffect(noteId) {
        val nid = noteId ?: return@LaunchedEffect
        val now = NotesStore.note(nid)?.photos.orEmpty()
        if (now.size < 2) return@LaunchedEffect
        val keep = NotePhotos.unique(now, NotePhotoFiles.fingerprints(now))
        if (keep != now) NotesStore.editNotePhotos(nid) { list -> list.filter { it in keep } }
    }
    suspend fun attach(picked: List<String>) {
        // La stessa foto scelta di nuovo non si aggiunge: il suo file copiato si butta.
        val prints = NotePhotoFiles.fingerprints(photos + picked)
        val paths = NotePhotos.unique(photos + picked, prints).filter { it in picked && it !in photos }
        (picked - paths.toSet() - photos.toSet()).forEach { java.io.File(it).delete() }
        if (paths.isEmpty()) return
        val nid = id
        if (nid == null || NotesStore.note(nid) == null) {
            // Nota nuova: nasce con la prima foto (una nota con foto resta anche senza testo).
            val note = NotesStore.addPhotoNote(text, paths.first())
            id = note.id
            if (paths.size > 1) NotesStore.editNotePhotos(note.id) { NotePhotos.add(it, paths.drop(1)) }
        } else {
            NotesStore.editNotePhotos(nid) { NotePhotos.add(it, paths) }
        }
        haptic(Haptic.CONFIRM)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) scope.launch { attach(uris.mapNotNull { PhotoAttach.keep(context, it) }) }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { PhotoAttach.keep(context, ReceiptReader.photoUri(context))?.let { attach(listOf(it)) } }
    }
    fun pickPhotos() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    fun removePhoto(path: String) {
        val nid = id ?: return
        val before = NotesStore.note(nid) ?: return
        val index = before.photos.indexOf(path)
        haptic(Haptic.TOGGLE_OFF)
        NotesStore.editNotePhotos(nid) { NotePhotos.remove(it, path) }
        CaptureFeedback.tell(context, "Foto tolta", Icons.Outlined.Delete, "Annulla") {
            // Una nota fatta solo di quella foto è sparita con lei: torna tutta.
            if (NotesStore.note(nid) == null) NotesStore.restoreNote(before)
            else NotesStore.editNotePhotos(nid) { NotePhotos.restore(it, path, index) }
        }
    }
    fun rotatePhoto(path: String) {
        val nid = id ?: return
        scope.launch {
            val turned = NotePhotoFiles.rotate(context, path)
            if (turned == null) CaptureFeedback.tell(context, "Non riesco a girare la foto")
            else NotesStore.editNotePhotos(nid) { NotePhotos.replace(it, path, turned) }
        }
    }
    fun photoActions(path: String, open: Boolean): Array<BubbleAction> {
        val index = photos.indexOf(path)
        val nid = id
        return listOfNotNull(
            if (open) BubbleAction(Icons.Outlined.Fullscreen, "Apri a tutto schermo") { viewer = index.coerceAtLeast(0) } else null,
            if (index > 0 && nid != null) BubbleAction(Icons.Outlined.Star, "Metti per prima", "diventa la copertina nel Calendario") {
                haptic(Haptic.CONFIRM)
                NotesStore.editNotePhotos(nid) { NotePhotos.toFront(it, path) }
            } else null,
            BubbleAction(Icons.Outlined.Rotate90DegreesCw, "Ruota") { rotatePhoto(path) },
            BubbleAction(Icons.Outlined.Share, "Condividi la foto") { NotePhotoFiles.share(context, "", listOf(path)) },
            BubbleAction(Icons.Outlined.Delete, "Togli dalla nota", "con Annulla", danger = true) { removePhoto(path) },
        ).toTypedArray()
    }

    // ---- Dettatura: il testo va dove c'è il cursore (in fondo alla lista se è una lista) ----
    val dictation = remember { Dictation(context) }
    DisposableEffect(Unit) { onDispose { dictation.cancel() } }
    fun startDictation() {
        haptic(Haptic.TOGGLE_ON)
        dictation.start { heard ->
            if (heard.isNullOrBlank()) return@start
            val (next, cursor) = NoteText.insert(body.text, body.selection.end, heard)
            body = TextFieldValue(next, TextRange(cursor))
            save()
        }
        dictation.lock()
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startDictation() }
    fun dictate() {
        when {
            dictation.listening -> { haptic(Haptic.TOGGLE_OFF); dictation.release() }
            !dictation.hasPermission() -> micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            else -> startDictation()
        }
    }

    /**
     * Punto, voce da spuntare o titoletto da "Aggiungi" (1.555): la riga del cursore cambia; senza cursore nel testo
     * se ne aggiunge una nuova in fondo.
     */
    fun lineKind(kind: NoteLines.Kind) {
        haptic(Haptic.TICK)
        if (bodyFocused) {
            val e = NoteLines.setKind(body.text, body.selection.min, body.selection.max, kind)
            body = TextFieldValue(e.text, TextRange(e.start, e.end))
        } else {
            val prefix = when (kind) {
                NoteLines.Kind.BULLET -> NoteLines.BULLET
                NoteLines.Kind.CHECK -> NoteLines.CHECK
                NoteLines.Kind.HEADING -> NoteLines.HEADING
                NoteLines.Kind.TEXT -> ""
            }
            val base = body.text.trimEnd('\n')
            val next = if (base.isEmpty()) prefix else base + "\n" + prefix
            body = TextFieldValue(next, TextRange(next.length))
        }
        save()
        bodyFocus.requestFocus()
    }

    /** "Tutto fatto": l'ultima voce spuntata. Niente cambia da solo, si possono togliere le fatte. */
    fun allDone() {
        CaptureFeedback.tell(context, "Tutto fatto", Icons.Outlined.TaskAlt, "Togli le fatte") {
            body = TextFieldValue(NoteLines.removeDone(body.text))
            save()
        }
    }

    fun add() {
        haptic(Haptic.LONG_PRESS)
        ActionBubble.show(
            *listOfNotNull(
                BubbleAction(Icons.Outlined.AddPhotoAlternate, "Foto", "dalla galleria, anche più insieme") { pickPhotos() },
                BubbleAction(Icons.Outlined.PhotoCamera, "Scatta una foto") { camera.launch(ReceiptReader.photoUri(context)) },
                BubbleAction(Icons.Outlined.Mic, "Detta", "il testo va dove c'è il cursore") { dictate() },
                BubbleAction(Icons.AutoMirrored.Outlined.FormatListBulleted, "Elenco puntato") { lineKind(NoteLines.Kind.BULLET) },
                BubbleAction(Icons.Outlined.ChecklistIcon, "Lista da spuntare") { lineKind(NoteLines.Kind.CHECK) },
                BubbleAction(Icons.Outlined.Title, "Titoletto") { lineKind(NoteLines.Kind.HEADING) },
            ).toTypedArray(),
        )
    }

    fun more() {
        haptic(Haptic.LONG_PRESS)
        ActionBubble.show(
            *listOfNotNull(
                id?.takeIf { current != null }?.let { nid ->
                    BubbleAction(
                        if (pinned) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                        if (pinned) "Togli da in alto" else "Fissa in alto",
                    ) {
                        haptic(if (pinned) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                        NotesStore.togglePin(nid)
                    }
                },
                // Per le liste (1.555): le fatte in fondo, via, o tutte da rifare (la valigia).
                if (!NoteLines.hasDone(body.text)) null else BubbleAction(Icons.Outlined.VerticalAlignBottom, "Fatte in fondo", "in ogni lista le spuntate scendono") {
                    body = TextFieldValue(NoteLines.doneToBottom(body.text))
                    save()
                },
                if (!NoteLines.hasDone(body.text)) null else BubbleAction(Icons.Outlined.RadioButtonUnchecked, "Togli tutte le spunte", "per rifare la stessa lista") {
                    body = TextFieldValue(NoteLines.uncheckAll(body.text))
                    save()
                },
                if (!NoteLines.hasDone(body.text)) null else BubbleAction(Icons.Outlined.RemoveDone, "Togli le fatte", "con Annulla") {
                    val before = body.text
                    body = TextFieldValue(NoteLines.removeDone(before))
                    save()
                    CaptureFeedback.tell(context, "Fatte tolte", Icons.Outlined.Delete, "Annulla") {
                        body = TextFieldValue(before)
                        save()
                    }
                },
                if (text.isBlank()) null else BubbleAction(Icons.Outlined.ContentCopy, "Copia il testo") {
                    Clip.copy(context, "Nota", NoteFormat.plain(text), "Testo copiato")
                },
                if (text.isBlank() && photos.isEmpty()) null else BubbleAction(
                    Icons.Outlined.Share, "Condividi", if (photos.isEmpty()) "" else "testo e foto insieme",
                ) { NotePhotoFiles.share(context, NoteFormat.plain(text), photos) },
                BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true) {
                    deleted = true
                    id?.let { nid ->
                        NotesStore.note(nid)?.let(DeletedNote::put)
                        NotesStore.deleteNote(nid)
                    }
                    onBack()
                },
            ).toTypedArray(),
        )
    }

    val meta = when {
        dictation.listening -> dictation.partial.ifBlank { "Ti ascolto…" }
        arranging -> "Trascina per cambiare l'ordine"
        else -> listOfNotNull(
            if (opened == null) "Nuova nota" else NoteText.moment(opened.createdAt, opened.updatedAt),
            if (pinned) "fissata" else null,
            Checklist.progressLabel(text) ?: NoteText.stats(text),
            NotePhotos.label(photos.size),
        ).joinToString(" · ")
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 24.dp, top = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                    // La riga sopra il titolo, come nelle altre schede; mentre detti dice quello che sta capendo.
                    Text(
                        meta.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dictation.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                    )
                }
            },
            bottomBar = {
                val place = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .miniPlayerFloor()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                // Mentre si scrive, al posto dei due tasti: la barra delle righe (1.555).
                if (richBarShown()) {
                    RichToolbar(place)
                } else {
                    // Come in DetailPage: tasto tondo a sinistra col suo nome, "Altro" a destra.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = place) {
                        if (dictation.listening) {
                            HoldRoundButton(Icons.Outlined.Stop, "Ferma", tint = MaterialTheme.colorScheme.primary, onClick = { dictate() })
                        } else {
                            HoldRoundButton(Icons.Outlined.Add, "Aggiungi", onClick = { add() }, onLongClick = { haptic(Haptic.LONG_PRESS); pickPhotos() })
                        }
                        Text(
                            if (dictation.listening) "Ferma" else "Aggiungi",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        RoundIconButton(Icons.Outlined.MoreHoriz, "Altro") { more() }
                    }
                }
            },
        ) { inner ->
            @OptIn(ExperimentalLayoutApi::class)
            val imeOpen = WindowInsets.isImeVisible
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = 26.sp)
            val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
            ) {
                BasicTextField(
                    value = title,
                    onValueChange = { v ->
                        // Invio (o un testo incollato su più righe) nel titolo: il resto scende nel testo.
                        val cut = v.text.indexOf('\n')
                        if (cut < 0) {
                            title = v
                        } else {
                            val rest = v.text.substring(cut + 1)
                            title = TextFieldValue(v.text.substring(0, cut))
                            val joined = if (body.text.isEmpty()) rest else rest + "\n" + body.text
                            body = TextFieldValue(joined, TextRange(rest.length))
                            bodyFocus.requestFocus()
                        }
                        // Salvataggio continuo: niente perso se l'app viene chiusa.
                        save()
                    },
                    textStyle = titleStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    decorationBox = { field ->
                        Box {
                            if (title.text.isEmpty()) Text("Titolo", style = titleStyle, color = faint)
                            field()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .autoFocus(enabled = noteId == null),
                )
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.size(14.dp))
                    PhotoStrip(
                        photos = photos,
                        onOpen = { viewer = it },
                        onMenu = { path -> haptic(Haptic.LONG_PRESS); ActionBubble.show(*photoActions(path, open = true)) },
                        onMove = { from, to -> id?.let { nid -> NotesStore.editNotePhotos(nid) { NotePhotos.move(it, from, to) } } },
                        onArranging = { arranging = it },
                        onAdd = { pickPhotos() },
                    )
                }
                Spacer(Modifier.size(12.dp))
                RichNoteField(
                    value = body,
                    onValueChange = { v ->
                        body = v
                        save()
                    },
                    textStyle = bodyStyle,
                    placeholder = "Scrivi qui…",
                    focusRequester = bodyFocus,
                    onFocusChange = { bodyFocused = it },
                    sendToTodo = true,
                    onAllDone = { allDone() },
                    // Con la tastiera aperta niente altezza minima: se no la pagina scorre per mostrarla tutta e titolo e foto
                    // finiscono fuori dallo schermo (1.559).
                    minHeight = if (imeOpen) 0.dp else 320.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        }

        // Le foto a tutto schermo, sopra l'editor: di lato fra le foto, giù per chiudere.
        viewer?.let { start ->
            if (photos.isEmpty()) {
                LaunchedEffect(Unit) { viewer = null }
            } else {
                PhotoViewer(
                    photos = photos,
                    start = start.coerceIn(0, photos.size - 1),
                    onClose = { viewer = null },
                    onRemove = { path -> removePhoto(path) },
                    onMore = { path -> haptic(Haptic.LONG_PRESS); ActionBubble.show(*photoActions(path, open = false)) },
                )
            }
        }
    }
}

/** Quante foto si scelgono al massimo in una volta dalla galleria. */
private const val MAX_PICK = 20

/** Il tasto tondo delle schede, che sa anche il tieni premuto (qui: "Aggiungi" tenuto = foto subito). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldRoundButton(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(40.dp)
            .bouncyPress(0.9f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.card)
            .border(cardBorder(), CircleShape)
            .combinedClickable(onClickLabel = label, onLongClick = onLongClick, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * Le foto in fila sotto il titolo. Tocca = a tutto schermo; tieni premuto e lascia = fumetto; tieni premuto e trascina
 * = cambia l'ordine (le altre si fanno da parte, una vibrazione a ogni posto). L'ultima casella aggiunge altre foto.
 */
@Composable
private fun PhotoStrip(
    photos: List<String>,
    onOpen: (Int) -> Unit,
    onMenu: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onArranging: (Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        // Tre foto riempiono giuste la larghezza fra i margini della pagina (1.552): niente foto tagliata dal bordo.
        PhotoRow(photos, (maxWidth - PHOTO_GAP * 2) / 3, onOpen, onMenu, onMove, onArranging, onAdd)
    }
}

private val PHOTO_GAP = 8.dp

@Composable
private fun PhotoRow(
    photos: List<String>,
    tile: androidx.compose.ui.unit.Dp,
    onOpen: (Int) -> Unit,
    onMenu: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onArranging: (Boolean) -> Unit,
    onAdd: () -> Unit,
) {
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    val gap = PHOTO_GAP
    val step = with(density) { (tile + gap).toPx() }
    val slop = with(density) { 12.dp.toPx() }
    val current by rememberUpdatedState(photos)
    // I gesti restano accesi fra una foto e l'altra: leggono sempre le azioni di adesso.
    val open by rememberUpdatedState(onOpen)
    val menu by rememberUpdatedState(onMenu)
    val move by rememberUpdatedState(onMove)
    val arrange by rememberUpdatedState(onArranging)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(-1) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = dragFrom == null),
    ) {
        photos.forEachIndexed { i, path ->
            key(path) {
                val from = dragFrom
                val to = from?.let { NotePhotos.target(it, dragX, step, photos.size) }
                val shift = if (from != null && to != null) NotePhotos.shift(i, from, to) else 0
                val slide by animateFloatAsState(shift * step, Motion.settle(), label = "fila")
                val held = from == i
                val lift by animateFloatAsState(if (held) 1.06f else 1f, Motion.lively(), label = "presa")
                Box(
                    Modifier
                        .size(tile)
                        .zIndex(if (held) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (held) dragX else slide
                            scaleX = lift
                            scaleY = lift
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .pointerInput(path) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // 0 = tenuto premuto, 1 = lasciato subito (tocco), 2 = scorrimento della fila.
                                var outcome = 0
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    outcome = if (waitForUpOrCancellation() != null) 1 else 2
                                }
                                val index = current.indexOf(path)
                                when (outcome) {
                                    1 -> open(index)
                                    0 -> {
                                        haptic(Haptic.GRAB)
                                        dragFrom = index
                                        dragX = 0f
                                        lastTarget = index
                                        var moved = 0f
                                        drag(down.id) { change ->
                                            val dx = change.positionChange().x
                                            change.consume()
                                            dragX += dx
                                            moved += abs(dx)
                                            if (moved > slop) arrange(true)
                                            val t = NotePhotos.target(index, dragX, step, current.size)
                                            if (t != lastTarget) {
                                                lastTarget = t
                                                haptic(Haptic.TICK)
                                            }
                                        }
                                        val t = NotePhotos.target(index, dragX, step, current.size)
                                        dragFrom = null
                                        dragX = 0f
                                        arrange(false)
                                        when {
                                            moved <= slop -> menu(path)
                                            t != index -> {
                                                haptic(Haptic.SNAP)
                                                move(index, t)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    NotePhoto(path, Modifier.fillMaxSize(), crop = true)
                }
            }
        }
        // "+ Foto": aggiunge dalla galleria, anche più d'una.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .size(width = 84.dp, height = tile)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.card)
                .border(cardBorder(), RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Aggiungi foto", onClick = onAdd),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Foto", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Le foto della nota a tutto schermo: di lato fra le foto, giù per chiudere; in basso "Togli" e "Altro". */
@Composable
private fun PhotoViewer(
    photos: List<String>,
    start: Int,
    onClose: () -> Unit,
    onRemove: (String) -> Unit,
    onMore: (String) -> Unit,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start) { photos.size }
    val density = LocalDensity.current
    val closeAt = with(density) { 120.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val back by animateFloatAsState(pull, Motion.gesture(), label = "chiudi")
    val page = pager.currentPage.coerceIn(0, photos.size - 1)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (1f - abs(back) / (closeAt * 3)).coerceIn(0.4f, 1f)))
            // Assorbe i tocchi: sotto c'è l'editor.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        HorizontalPager(
            state = pager,
            key = { photos.getOrElse(it) { "" } },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = back }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (abs(pull) > closeAt) onClose() else pull = 0f },
                        onDragCancel = { pull = 0f },
                    ) { change, dy ->
                        change.consume()
                        pull += dy
                    }
                },
        ) { i ->
            coil3.compose.AsyncImage(
                model = java.io.File(photos[i]),
                contentDescription = "Foto ${i + 1} di ${photos.size}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 24.dp, top = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Chiudi", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("${page + 1} di ${photos.size}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            RoundIconButton(Icons.Outlined.Close, "Togli") { photos.getOrNull(page)?.let(onRemove) }
            Text(
                "Togli",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(photos.size.coerceAtMost(10)) { i ->
                    Box(Modifier.size(width = if (i == page) 16.dp else 6.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == page) 0.9f else 0.35f)))
                }
            }
            Spacer(Modifier.width(12.dp).weight(1f))
            RoundIconButton(Icons.Outlined.MoreHoriz, "Altro") { photos.getOrNull(page)?.let(onMore) }
        }
    }
}
