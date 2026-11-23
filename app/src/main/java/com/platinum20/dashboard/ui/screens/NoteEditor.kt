package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import com.platinum20.dashboard.notes.NoteLists
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Replay
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.notes.NoteVersions
import com.platinum20.dashboard.notes.NotePdf
import com.platinum20.dashboard.notes.NoteAudio
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.filled.PushPin as PushPinFilled
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
import androidx.compose.ui.platform.testTag
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import kotlin.math.roundToInt

/**
 * Editor di una nota lunga (1.529, Antonio scelta B di /mnt/project-files/editor-note/): fatto come le altre schede.
 * In alto Indietro e una riga sola (quando, parole, foto); il titolo; le foto in fila sotto il titolo (tocca = grande,
 * tieni premuto = fumetto, tieni premuto e trascina = ordine); il testo. In basso "Aggiungi" a sinistra (foto, scatta,
 * detta; tenuto premuto = foto subito) e "Altro" a destra. Punti, spunte, titoletti e grassetto stanno solo nella barra
 * sopra la tastiera (1.577). Si salva da sola.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(noteId: Long?, find: String = "", onBack: () -> Unit) {
    val context = LocalContext.current
    NotesStore.init(context)
    com.platinum20.dashboard.notes.NoteSeek.init(context)
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
    // La pagina che scorre e cosa se ne vede sullo schermo (1.594): la riga in cui si scrive resta sopra la tastiera.
    val pageScroll = rememberScrollState()
    var viewTop by remember { mutableFloatStateOf(0f) }
    var tappedBelow by remember { mutableIntStateOf(0) }
    var viewBottom by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var barTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val lineRoom = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    val bodyFocus = remember { FocusRequester() }
    // "Cerca nella nota" (1.579): null = chiuso.
    // Aperta da Cerca ovunque (2026-11-23, idea 9): la ricerca nella nota parte già con la parola trovata.
    var finding by remember { mutableStateOf(find.takeIf { it.isNotBlank() }) }
    var findAt by remember { mutableIntStateOf(0) }
    val found = remember(body.text, finding) { finding?.let { NoteLines.matches(body.text, it) }.orEmpty() }
    BackHandler(enabled = finding != null) { finding = null }
    // L'indice dei titoli (1.586): il titolo toccato e un numero che cambia a ogni tocco.
    var reveal by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val inApp = LocalInAppNav.current
    // "Ricordamelo" sulla riga e il posto della nota (idee 16 e 11, NoteExtrasUi.kt).
    val extras = rememberNoteExtras()

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

    // ---- Le foto nel testo (1.591): dove c'è il cursore, non solo in cima ----
    val inlinePick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val paths = uris.mapNotNull { PhotoAttach.keep(context, it) }
            if (paths.isEmpty()) return@launch
            var t = body.text
            var at = body.selection.end.coerceIn(0, t.length)
            paths.forEach { p ->
                val e = NoteLines.insertBlock(t, at, NoteLines.imageLine(p))
                t = e.text
                at = e.start
            }
            body = TextFieldValue(t, TextRange(at))
            save()
            haptic(Haptic.CONFIRM)
        }
    }
    fun pickInline() = inlinePick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // ---- La copertina (1.590): una foto in cima alla nota ----
    val coverPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val nid = id
        if (uri != null && nid != null) scope.launch {
            PhotoAttach.keep(context, uri)?.let { path ->
                NotesStore.setNoteCover(nid, path)
                haptic(Haptic.CONFIRM)
            }
        }
    }
    fun pickCover() = coverPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    fun coverActions(nid: Long, now: String?): Array<BubbleAction> = listOfNotNull(
        BubbleAction(Icons.Outlined.Image, if (now == null) "Aggiungi una copertina" else "Cambia la copertina", "una foto in cima alla nota") { pickCover() },
        now?.let {
            BubbleAction(Icons.Outlined.Delete, "Togli la copertina", "con Annulla", danger = true) {
                NotesStore.setNoteCover(nid, null)
                CaptureFeedback.tell(context, "Copertina tolta", Icons.Outlined.Delete, "Annulla") { NotesStore.setNoteCover(nid, it) }
            }
        },
    ).toTypedArray()

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
            if (open) BubbleAction(Icons.Outlined.Fullscreen, "Apri a tutto schermo", "scorri di lato fra le foto") { viewer = index.coerceAtLeast(0) } else null,
            if (index > 0 && nid != null) BubbleAction(Icons.Outlined.Star, "Metti per prima", "diventa la copertina nel Calendario") {
                haptic(Haptic.CONFIRM)
                NotesStore.editNotePhotos(nid) { NotePhotos.toFront(it, path) }
            } else null,
            // Testo da una foto (1.588): quello che c'è scritto nella foto va in fondo alla nota.
            BubbleAction(Icons.Outlined.DocumentScanner, "Copia il testo nella nota", "quello scritto nella foto, in fondo") {
                scope.launch {
                    val rows = PhotoAttach.rows(context, android.net.Uri.fromFile(java.io.File(path)))
                    if (rows.isEmpty()) { CaptureFeedback.tell(context, "Nella foto non ho trovato testo"); return@launch }
                    val add = rows.joinToString("\n")
                    val next = if (body.text.isBlank()) add else body.text.trimEnd('\n') + "\n" + add
                    body = TextFieldValue(next, TextRange(next.length))
                    save()
                    haptic(Haptic.CONFIRM)
                    CaptureFeedback.tell(context, "Testo copiato nella nota", Icons.Outlined.DocumentScanner)
                }
            },
            BubbleAction(Icons.Outlined.Rotate90DegreesCw, "Ruota", "di un quarto di giro") { rotatePhoto(path) },
            BubbleAction(Icons.Outlined.Share, "Condividi la foto", "solo questa foto") { NotePhotoFiles.share(context, "", listOf(path)) },
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

    /** "Tutto fatto": l'ultima voce spuntata. Niente cambia da solo, si possono togliere le fatte. */
    fun allDone() {
        CaptureFeedback.tell(context, "Tutto fatto", Icons.Outlined.TaskAlt, "Togli le fatte") {
            body = TextFieldValue(NoteLines.removeDone(body.text))
            save()
        }
    }

    // ---- Disegno a mano (1.588) ----
    var drawing by remember { mutableStateOf(false) }
    fun draw() { haptic(Haptic.TICK); drawing = true }

    // ---- Audio nella nota (1.588): la riga-link va sotto la riga del cursore ----
    val recordingSince by NoteAudio.recordingSince.collectAsStateWithLifecycle()
    // Uscendo dalla nota mentre si registra, l'audio resta nella nota invece di perdersi.
    DisposableEffect(Unit) {
        onDispose {
            if (!deleted && NoteAudio.recordingSince.value != null) NoteAudio.stop()?.let { line ->
                val e = NoteText.insertLine(body.text, body.selection.end, line)
                body = TextFieldValue(e.first, TextRange(e.second))
                NotesStore.saveNote(id, NoteText.join(title.text, body.text))
            }
            NoteAudio.cancel(); NoteAudio.stopPlaying()
        }
    }
    fun startRecording() {
        if (NoteAudio.start(context)) haptic(Haptic.TOGGLE_ON) else CaptureFeedback.tell(context, "Il microfono non si apre")
    }
    val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) startRecording() }
    fun record() {
        when {
            recordingSince != null -> {
                haptic(Haptic.TOGGLE_OFF)
                val line = NoteAudio.stop()
                if (line == null) { CaptureFeedback.tell(context, "Audio troppo corto"); return }
                val e = NoteText.insertLine(body.text, body.selection.end, line)
                body = TextFieldValue(e.first, TextRange(e.second))
                save()
            }
            !dictation.hasPermission() -> recordPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            else -> startRecording()
        }
    }

    // ---- PDF (1.588) ----
    fun exportPdf() {
        save()
        scope.launch {
            val file = runCatching { NotePdf.make(context, title.text, body.text, photos) }.getOrNull()
            if (file == null) { CaptureFeedback.tell(context, "Il PDF non si è fatto"); return@launch }
            val saved = NotePdf.saveToDownloads(context, file)
            haptic(Haptic.CONFIRM)
            CaptureFeedback.tell(context, if (saved) "PDF nei Download" else "PDF pronto", Icons.Outlined.PictureAsPdf, "Condividi") {
                runCatching {
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(android.content.Intent.createChooser(send, title.text.ifBlank { "Nota" }).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
    }

    // ---- Versioni precedenti (1.588): il testo di quando si è aperta la nota resta come versione se lo si cambia ----
    NoteVersions.init(context)
    val openedText = remember { noteId?.let { NotesStore.note(it)?.text } }
    DisposableEffect(Unit) {
        onDispose {
            val nid = id
            if (nid != null && !deleted && openedText != null && openedText != NoteText.join(title.text, body.text)) NoteVersions.keep(nid, openedText)
        }
    }
    fun versions() {
        val nid = id ?: return
        val list = NoteVersions.of(nid)
        haptic(Haptic.LONG_PRESS)
        if (list.isEmpty()) { CaptureFeedback.tell(context, "Nessuna versione precedente: si tengono quando cambi la nota"); return }
        ActionBubble.show(
            *list.take(8).map { v ->
                BubbleAction(Icons.Outlined.History, NoteText.moment(v.at, v.at), NoteText.versionLine(v.text, text)) {
                    val before = text
                    setText(v.text)
                    CaptureFeedback.tell(context, "Versione rimessa", Icons.Outlined.History, "Annulla") { setText(before) }
                }
            }.toTypedArray(),
        )
    }

    // ---- Da elenco di brani a Spotify (1.606, idea 18) ----
    fun songsToSpotify(songs: List<String>) {
        val name = com.platinum20.dashboard.media.spotify.SpotifyPlaylists.cleanName(title.text.ifBlank { "Dalla nota" })
        fun withTracks(then: (List<com.platinum20.dashboard.media.spotify.SpItem>) -> Unit) {
            CaptureFeedback.tell(context, "Cerco ${songs.size} brani su Spotify…", Icons.AutoMirrored.Outlined.QueueMusic)
            scope.launch {
                val found = com.platinum20.dashboard.media.spotify.SpotifyStore.findTracks(songs)
                val tracks = found.data.orEmpty()
                when {
                    found.error != null && tracks.isEmpty() -> CaptureFeedback.tell(context, found.error)
                    tracks.isEmpty() -> CaptureFeedback.tell(context, "Nessun brano trovato su Spotify")
                    else -> then(tracks)
                }
            }
        }
        ActionBubble.show(
            BubbleAction(Icons.AutoMirrored.Outlined.PlaylistAdd, "Crea playlist \"$name\"", "va in Musica, la nota resta") {
                withTracks { tracks ->
                    scope.launch {
                        com.platinum20.dashboard.media.spotify.SpotifyStore.createPlaylistWith(name, tracks).fold(
                            onSuccess = { made ->
                                haptic(Haptic.CONFIRM)
                                CaptureFeedback.tell(context, "Playlist \"${made.title}\": ${tracks.size} su ${songs.size} brani", Icons.AutoMirrored.Outlined.QueueMusic, "Apri") {
                                    inApp?.openRoute(Routes.spotifyItem(made))
                                }
                            },
                            onFailure = { CaptureFeedback.tell(context, it.message ?: "Spotify non risponde, riprova") },
                        )
                    }
                }
            },
            BubbleAction(Icons.AutoMirrored.Outlined.QueueMusic, "Metti in coda", "partono dopo quello che ascolti") {
                withTracks { tracks ->
                    tracks.forEach { t ->
                        CanvasPlayer.add(ResumeItem(ResumeKind.SPOTIFY, t.uri, t.title, t.subtitle, t.image, durationMs = t.durationMs, at = System.currentTimeMillis()))
                    }
                    haptic(Haptic.CONFIRM)
                    CaptureFeedback.tell(context, "In coda: ${tracks.size} brani", Icons.AutoMirrored.Outlined.QueueMusic)
                }
            },
        )
    }

    fun add() {
        haptic(Haptic.LONG_PRESS)
        ActionBubble.show(
            *listOfNotNull(
                BubbleAction(Icons.Outlined.Image, "Foto nel testo", "dove c'è il cursore, larga quanto la nota") { pickInline() },
                BubbleAction(Icons.Outlined.AddPhotoAlternate, "Foto in cima", "dalla galleria, anche più insieme") { pickPhotos() },
                BubbleAction(Icons.Outlined.PhotoCamera, "Scatta una foto", "con la fotocamera, va subito nella nota") { camera.launch(ReceiptReader.photoUri(context)) },
                BubbleAction(Icons.Outlined.Mic, "Detta", "il testo va dove c'è il cursore") { dictate() },
                BubbleAction(Icons.Outlined.Draw, "Disegna", "a mano, va fra le foto") { draw() },
                BubbleAction(Icons.Outlined.GraphicEq, "Registra un audio", "si ascolta toccandolo nella nota") { record() },
            ).toTypedArray(),
        )
    }

    /** La riga "Emoji della nota" (1.586): una fila di emoji da toccare, e Togli se c'è già. */
    fun emojiAction(nid: Long, now: String?): BubbleAction = BubbleAction(
        Icons.Outlined.EmojiEmotions, "Emoji della nota", "si vede davanti al titolo, anche in Appunti",
        chips = NOTE_EMOJIS.map { e -> BubbleChip(e, selected = e == now) { NotesStore.setNoteEmoji(nid, if (e == now) null else e) } } +
            listOfNotNull(if (now != null) BubbleChip("Togli") { NotesStore.setNoteEmoji(nid, null) } else null),
    )

    fun more() {
        haptic(Haptic.LONG_PRESS)
        ActionBubble.show(
            *listOfNotNull(
                id?.takeIf { current != null }?.let { nid ->
                    BubbleAction(
                        if (pinned) Icons.Filled.PushPinFilled else Icons.Outlined.PushPin,
                        if (pinned) "Togli da in alto" else "Fissa in alto",
                        if (pinned) "torna in ordine con le altre" else "resta prima fra gli Appunti",
                    ) {
                        haptic(if (pinned) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                        NotesStore.togglePin(nid)
                    }
                },
                id?.takeIf { current != null }?.let { nid -> emojiAction(nid, current?.emoji) },
                extras.lineAction({ save(); id }, title.text, body.text, body.selection.end),
                extras.placeAction({ save(); id }, id),
                id?.takeIf { current != null }?.let { nid ->
                    BubbleAction(Icons.Outlined.Image, if (current?.cover == null) "Aggiungi una copertina" else "Copertina", "una foto in cima alla nota") {
                        if (current?.cover == null) pickCover() else ActionBubble.show(*coverActions(nid, current?.cover))
                    }
                },
                if (body.text.isBlank()) null else BubbleAction(Icons.Outlined.Search, "Cerca nella nota", "le parole trovate si colorano") {
                    findAt = 0
                    finding = ""
                },
                // Per le liste (1.555): le fatte in fondo, via, o tutte da rifare (la valigia).
                // "Ordina la lista" (1.586): dalla A alla Z, o prima quelle da fare (le spuntate scendono).
                if (!NoteLines.hasList(body.text)) null else BubbleAction(Icons.AutoMirrored.Outlined.Sort, "Ordina la lista", "dalla A alla Z, o prima quelle da fare") {
                    ActionBubble.show(
                        *listOfNotNull(
                            BubbleAction(Icons.Outlined.SortByAlpha, "Dalla A alla Z", "le fatte restano in fondo") {
                                val before = body.text
                                body = TextFieldValue(NoteLines.sortAlpha(before))
                                save()
                                CaptureFeedback.tell(context, "Lista in ordine", Icons.Outlined.SortByAlpha, "Annulla") {
                                    body = TextFieldValue(before)
                                    save()
                                }
                            },
                            if (!NoteLines.hasDone(body.text)) null else BubbleAction(Icons.Outlined.VerticalAlignBottom, "Prima quelle da fare", "le spuntate scendono, l'ordine resta") {
                                body = TextFieldValue(NoteLines.doneToBottom(body.text))
                                save()
                            },
                        ).toTypedArray(),
                    )
                },
                // "Ricomincia" (2026-11-23, idea 15): la valigia, la spesa del sabato, di nuovo pronte.
                if (!NoteLines.hasDone(body.text)) null else BubbleAction(
                    Icons.Outlined.Replay, "Ricomincia",
                    NoteLists.doneCount(text).let { if (it == 1) "toglie la spunta, il testo resta" else "toglie le $it spunte, il testo resta" },
                ) {
                    val before = body.text
                    body = TextFieldValue(NoteLines.uncheckAll(before))
                    save()
                    CaptureFeedback.tell(context, "Lista da rifare", Icons.Outlined.Replay, "Annulla") {
                        body = TextFieldValue(before)
                        save()
                    }
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
                // "Usa come modello" (1.586): una nota nuova uguale, con le spunte da rifare (la valigia, la spesa fissa).
                if (text.isBlank()) null else BubbleAction(Icons.Outlined.FileCopy, "Duplica", "una copia nuova, tutto da rifare") {
                    save()
                    val copy = NoteText.join(NoteText.copyTitle(title.text), NoteLines.uncheckAll(body.text))
                    val nid = NotesStore.saveNote(null, copy) ?: return@BubbleAction
                    current?.emoji?.let { NotesStore.setNoteEmoji(nid, it) }
                    inApp?.openRoute(Routes.noteEdit(nid))
                },
                // "Ascolta" (1.606, idea 29): la nota letta con la voce delle News, nel player come un podcast.
                if (body.text.isBlank()) null else BubbleAction(Icons.Outlined.Headphones, "Ascolta", "letta con la voce delle News") {
                    save()
                    val script = com.platinum20.dashboard.notes.NoteSpoken.script(title.text, body.text)
                    val name = title.text.ifBlank { "Nota" }
                    com.platinum20.dashboard.news.RecapSpeaker.play(
                        context, script, title = name, labels = script.map(com.platinum20.dashboard.news.RecapReader::chapterLabel),
                    )
                },
                // Un elenco di brani "Artista – Titolo" (1.606, idea 18): Canvas li cerca su Spotify.
                com.platinum20.dashboard.notes.NoteSpoken.songs(body.text).takeIf { it.isNotEmpty() }?.let { songs ->
                    BubbleAction(Icons.AutoMirrored.Outlined.QueueMusic, "Brani su Spotify", "${songs.size} righe: playlist o in coda") { songsToSpotify(songs) }
                },
                // Il widget Nota (2026-11-23, idea 8): questa nota sulla schermata del telefono.
                id?.takeIf { current != null && text.isNotBlank() }?.let { nid ->
                    BubbleAction(
                        Icons.Outlined.Widgets, "Mostra nel widget",
                        if (com.platinum20.dashboard.widget.NoteWidget.placed(context)) "il widget Nota mostra questa" else "poi aggiungi il widget Nota alla schermata",
                    ) {
                        save()
                        com.platinum20.dashboard.widget.NoteWidget.choose(context, nid)
                        CaptureFeedback.tell(context, "Nel widget Nota", Icons.Outlined.Widgets)
                    }
                },
                id?.takeIf { NoteVersions.of(it).isNotEmpty() }?.let { BubbleAction(Icons.Outlined.History, "Versioni precedenti", "rimetti com'era prima, con Annulla") { versions() } },
                if (text.isBlank() && photos.isEmpty()) null else BubbleAction(Icons.Outlined.PictureAsPdf, "Esporta in PDF", "testo e foto su fogli A4, nei Download") { exportPdf() },
                if (text.isBlank()) null else BubbleAction(Icons.Outlined.ContentCopy, "Copia il testo", "senza le foto") {
                    Clip.copy(context, "Nota", NoteFormat.plain(text), "Testo copiato")
                },
                if (text.isBlank() && photos.isEmpty()) null else BubbleAction(
                    Icons.Outlined.Share, "Condividi", if (photos.isEmpty()) "il testo, con le app del telefono" else "testo e foto insieme",
                ) { NotePhotoFiles.share(context, NoteFormat.plain(text), photos) },
                BubbleAction(Icons.Outlined.Delete, "Elimina", "con le sue foto", danger = true) {
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
        recordingSince != null -> "Registro l'audio… tocca Ferma"
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
                    finding?.let { query ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                            CanvasSearchField(
                                value = query,
                                onValueChange = { finding = it; findAt = 0 },
                                placeholder = "Cerca nella nota",
                                trailing = {
                                    if (query.isNotBlank()) Text(
                                        if (found.isEmpty()) "nessuna" else "${findAt + 1} di ${found.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                modifier = Modifier.weight(1f).autoFocus(),
                            )
                            Spacer(Modifier.size(6.dp))
                            RoundIconButton(Icons.Outlined.KeyboardArrowUp, "Quella prima") {
                                if (found.isNotEmpty()) { haptic(Haptic.TICK); findAt = (findAt - 1 + found.size) % found.size }
                            }
                            RoundIconButton(Icons.Outlined.KeyboardArrowDown, "Quella dopo") {
                                if (found.isNotEmpty()) { haptic(Haptic.TICK); findAt = (findAt + 1) % found.size }
                            }
                            RoundIconButton(Icons.Outlined.Close, "Chiudi la ricerca") { finding = null }
                        }
                    }
                    // La riga sopra il titolo, come nelle altre schede; mentre detti dice quello che sta capendo.
                    if (finding == null) Text(
                        meta.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dictation.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp, bottom = 10.dp),
                    )
                }
            },
            bottomBar = {
                val place = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .miniPlayerFloor()
                    // Il bordo alto della barra (sopra la tastiera): sotto lì il testo non si vede (1.594).
                    .onGloballyPositioned { barTop = it.boundsInRoot().top }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                // Mentre si scrive, al posto dei due tasti: la barra delle righe (1.555).
                if (richBarShown()) {
                    RichToolbar(place)
                } else {
                    // Come in DetailPage: tasto tondo a sinistra col suo nome, "Altro" a destra.
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = place) {
                        if (recordingSince != null) {
                            HoldRoundButton(Icons.Outlined.Stop, "Ferma", tint = MaterialTheme.colorScheme.primary, onClick = { record() })
                        } else if (dictation.listening) {
                            HoldRoundButton(Icons.Outlined.Stop, "Ferma", tint = MaterialTheme.colorScheme.primary, onClick = { dictate() })
                        } else {
                            HoldRoundButton(Icons.Outlined.Add, "Aggiungi", onClick = { add() }, onLongClick = { haptic(Haptic.LONG_PRESS); pickPhotos() })
                        }
                        Text(
                            if (dictation.listening || recordingSince != null) "Ferma" else "Aggiungi",
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
            val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
            val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .onGloballyPositioned { it.boundsInRoot().let { b -> viewTop = b.top; viewBottom = b.bottom } }
                    .verticalScroll(pageScroll)
                    .padding(vertical = 12.dp),
            ) {
                // La copertina (1.590): in cima, larga quanto la pagina; tocco o tieni premuto = cambiala o toglila.
                current?.cover?.let { path ->
                    coil3.compose.AsyncImage(
                        model = java.io.File(path),
                        contentDescription = "Copertina",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 14.dp)
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onLongClick = { id?.let { nid -> haptic(Haptic.LONG_PRESS); ActionBubble.show(*coverActions(nid, path)) } },
                            ) { id?.let { nid -> haptic(Haptic.TICK); ActionBubble.show(*coverActions(nid, path)) } },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.Top) {
                    // L'emoji della nota: a sinistra del titolo, sulla stessa riga; tocco o tieni premuto = cambiala.
                    current?.emoji?.let { e ->
                        Text(
                            e,
                            fontSize = 26.sp,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .combinedClickable(
                                    onClickLabel = "Cambia l'emoji",
                                    onLongClick = { id?.let { nid -> haptic(Haptic.LONG_PRESS); ActionBubble.show(emojiAction(nid, e)) } },
                                ) { id?.let { nid -> haptic(Haptic.TICK); ActionBubble.show(emojiAction(nid, e)) } },
                        )
                    }
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
                            .weight(1f)
                            .testTag("noteTitle")
                            .autoFocus(enabled = noteId == null),
                    )
                }
                if (photos.isNotEmpty()) {
                    Spacer(Modifier.size(14.dp))
                    PhotoStrip(
                        photos = photos,
                        onOpen = { viewer = it },
                        onMenu = { path -> haptic(Haptic.LONG_PRESS); ActionBubble.show(*photoActions(path, open = true)) },
                        onMove = { from, to -> id?.let { nid -> NotesStore.editNotePhotos(nid) { NotePhotos.move(it, from, to) } } },
                        onArranging = { arranging = it },
                        onAdd = { haptic(Haptic.TICK); pickPhotos() },
                        onAddMenu = { add() },
                    )
                }
                Spacer(Modifier.size(12.dp))
                // L'indice dei titoli (1.586): con almeno 3 titoli, in cima; tocchi e la nota scorre lì.
                val heads = remember(body.text) { NoteLines.headings(body.text) }
                if (heads.size >= 3 && finding == null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp),
                    ) {
                        heads.forEach { (at, name) ->
                            CanvasChip(selected = false, onClick = { haptic(Haptic.TICK); reveal = at to ((reveal?.second ?: 0) + 1) }, label = name.take(24))
                        }
                    }
                }
                // Tenendo premuto un punto del testo (1.606, idea 14): "Detta qui" nel menu, il testo entra lì mentre parli.
                WithTextExtras(listOf(TextToolbarExtra("Detta qui") { dictate() })) {
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
                    keepInView = { top, bottom, mode ->
                        pageScroll.keepLine(scope, top, bottom, mode, viewTop, minOf(viewBottom, barTop), lineRoom)
                    },
                    tappedBelow = tappedBelow,
                    sendToTodo = true,
                    noteId = id,
                    onAllDone = { allDone() },
                    find = finding,
                    findAt = findAt.coerceAtMost((found.size - 1).coerceAtLeast(0)),
                    revealAt = reveal,
                    slashExtras = listOf(
                        SlashItem(Icons.Outlined.Image, "Foto nel testo", "dove c'è il cursore") { pickInline() },
                        SlashItem(Icons.Outlined.AddPhotoAlternate, "Foto in cima", "dalla galleria") { pickPhotos() },
                        SlashItem(Icons.Outlined.PhotoCamera, "Scatta", "con la fotocamera") { camera.launch(ReceiptReader.photoUri(context)) },
                        SlashItem(Icons.Outlined.Mic, "Detta", "dove c'è il cursore") { dictate() },
                        SlashItem(Icons.Outlined.Draw, "Disegno", "a mano") { draw() },
                        SlashItem(Icons.Outlined.GraphicEq, "Vocale", "registra") { record() },
                    ),
                    // Con la tastiera aperta niente altezza minima: se no la pagina scorre per mostrarla tutta e titolo e foto
                    // finiscono fuori dallo schermo (1.559).
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .testTag("noteBody"),
                )
                }
                // Tocco sotto l'ultima riga (1.586): il cursore va in fondo e, se l'ultima riga è una voce, ne apre una nuova.
                // Con la tastiera aperta lo spazio è più basso: se no la pagina scorre e titolo e foto escono (1.559).
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Scrivendo (1.591) sotto c'è tanto spazio vuoto: la riga può salire in alto, mai sotto la tastiera.
                        .height(if (imeOpen && bodyFocused) (LocalConfiguration.current.screenHeightDp * 0.6f).dp else if (imeOpen) 120.dp else 320.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            val e = NoteLines.tapBelow(body.text)
                            val changed = e.text != body.text
                            body = TextFieldValue(e.text, TextRange(e.start))
                            if (changed) save()
                            tappedBelow++
                            bodyFocus.requestFocus()
                        },
                )
            }
        }

        // Le foto a tutto schermo, sopra l'editor: di lato fra le foto, giù per chiudere.
        if (drawing) {
            NoteDrawingLayer(
                onDone = { strokes, size ->
                    drawing = false
                    scope.launch { saveDrawing(context, strokes, size)?.let { attach(listOf(it)) } }
                },
                onClose = { drawing = false },
            )
        }
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

/** Le emoji da scegliere per una nota. */
private val NOTE_EMOJIS = listOf("📝", "🛒", "🧳", "🍝", "💊", "🏠", "💼", "🎁", "📚", "💡", "❤️", "⭐")

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
 * Le foto sotto il titolo (1.575): la prima grande, a destra due più piccole una sopra l'altra; scorrendo a destra le
 * altre a pagine 2x2. Tocca = a tutto schermo; tieni premuto e lascia = fumetto; tieni premuto e trascina = cambia
 * l'ordine (le altre si fanno da parte, una vibrazione a ogni posto). Per ultimo un riquadro col "+" (1.586): tocca =
 * foto dalla galleria, tieni premuto = anche scatta o detta. Si aggiungono anche da "Aggiungi" in basso.
 */
@Composable
private fun PhotoStrip(
    photos: List<String>,
    onOpen: (Int) -> Unit,
    onMenu: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onArranging: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onAddMenu: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        PhotoMosaic(photos, maxWidth, onOpen, onMenu, onMove, onArranging, onAdd, onAddMenu)
    }
}

private val PHOTO_GAP = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoMosaic(
    photos: List<String>,
    width: androidx.compose.ui.unit.Dp,
    onOpen: (Int) -> Unit,
    onMenu: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onArranging: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onAddMenu: () -> Unit,
) {
    val haptic = rememberHaptic()
    val density = LocalDensity.current
    // Un posto in più, l'ultimo, per il riquadro "+"; le foto si spostano solo fra i loro posti.
    val withAdd = with(density) { NotePhotos.slots(photos.size + 1, width.toPx(), PHOTO_GAP.toPx()) }
    val slots = withAdd.take(photos.size)
    val addSlot = withAdd.last()
    val slop = with(density) { 12.dp.toPx() }
    val current by rememberUpdatedState(photos)
    val places by rememberUpdatedState(slots)
    // I gesti restano accesi fra una foto e l'altra: leggono sempre le azioni di adesso.
    val open by rememberUpdatedState(onOpen)
    val menu by rememberUpdatedState(onMenu)
    val move by rememberUpdatedState(onMove)
    val arrange by rememberUpdatedState(onArranging)
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(-1) }
    fun targetOf(from: Int): Int {
        val s = places.getOrNull(from) ?: return from
        return NotePhotos.nearest(places, s.cx + dragX, s.cy + dragY)
    }
    val totalW = with(density) { NotePhotos.mosaicWidth(withAdd).toDp() }
    val totalH = with(density) { (slots.firstOrNull()?.size ?: 0f).toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState(), enabled = dragFrom == null),
    ) {
        Box(Modifier.size(width = totalW, height = totalH)) {
            photos.forEachIndexed { i, path ->
                key(path) {
                    val from = dragFrom
                    val to = from?.let { targetOf(it) }
                    val shift = if (from != null && to != null) NotePhotos.shift(i, from, to) else 0
                    val held = from == i
                    // Ogni foto va (animata) al posto che le tocca: anche la misura cambia fra grande e piccola.
                    val slot = slots.getOrNull(if (held) i else i + shift) ?: slots[i]
                    val x by animateFloatAsState(slot.x, Motion.settle(), label = "x")
                    val y by animateFloatAsState(slot.y, Motion.settle(), label = "y")
                    val side by animateFloatAsState(slot.size, Motion.settle(), label = "lato")
                    val lift by animateFloatAsState(if (held) 1.06f else 1f, Motion.lively(), label = "presa")
                    Box(
                        Modifier
                            .offset { androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()) }
                            .size(with(density) { side.toDp() })
                            .zIndex(if (held) 1f else 0f)
                            .graphicsLayer {
                                translationX = if (held) dragX else 0f
                                translationY = if (held) dragY else 0f
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
                                            dragY = 0f
                                            lastTarget = index
                                            var moved = 0f
                                            drag(down.id) { change ->
                                                val d = change.positionChange()
                                                change.consume()
                                                dragX += d.x
                                                dragY += d.y
                                                moved += abs(d.x) + abs(d.y)
                                                if (moved > slop) arrange(true)
                                                val t = targetOf(index)
                                                if (t != lastTarget) {
                                                    lastTarget = t
                                                    haptic(Haptic.TICK)
                                                }
                                            }
                                            val t = targetOf(index)
                                            dragFrom = null
                                            dragX = 0f
                                            dragY = 0f
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
            val ax by animateFloatAsState(addSlot.x, Motion.settle(), label = "x+")
            val ay by animateFloatAsState(addSlot.y, Motion.settle(), label = "y+")
            val aside by animateFloatAsState(addSlot.size, Motion.settle(), label = "lato+")
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(ax.roundToInt(), ay.roundToInt()) }
                    .size(with(density) { aside.toDp() })
                    .bouncyPress(0.95f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.card)
                    .border(cardBorder(), RoundedCornerShape(16.dp))
                    .combinedClickable(onClickLabel = "Aggiungi foto", onLongClick = onAddMenu, onClick = onAdd),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Aggiungi foto", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
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

/**
 * Tiene la riga del cursore ([top]..[bottom], sullo schermo) nella parte visibile [visTop]..[visBottom] (sopra barra e
 * tastiera). Toccata (1.606): la riga va in cima, subito sotto la testata. Scrivendo, quando la riga scende oltre metà,
 * la pagina la riporta in alto (un terzo): non si scrive mai sotto la tastiera. Muovendo solo il cursore basta che si veda.
 */
internal fun keepLineDelta(top: Float, bottom: Float, mode: LineFollow, visTop: Float, visBottom: Float, margin: Float): Float {
    val h = visBottom - visTop
    if (h <= margin * 3) return 0f
    return when {
        mode == LineFollow.TAPPED -> top - (visTop + margin * 0.5f)
        mode == LineFollow.TYPING && bottom > visTop + h * 0.55f -> top - (visTop + h * 0.3f)
        bottom > visBottom - margin -> bottom - (visBottom - margin)
        top < visTop + margin -> top - (visTop + margin)
        else -> 0f
    }
}

private fun androidx.compose.foundation.ScrollState.keepLine(
    scope: kotlinx.coroutines.CoroutineScope, top: Float, bottom: Float, mode: LineFollow, visTop: Float, visBottom: Float, margin: Float,
) {
    val delta = keepLineDelta(top, bottom, mode, visTop, visBottom, margin)
    if (kotlin.math.abs(delta) < 1f) return
    scope.launch { animateScrollBy(delta, Motion.settle()) }
}
