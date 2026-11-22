package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.NoteTable
import com.platinum20.dashboard.notes.NoteAudio
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.foundation.layout.Column
import com.platinum20.dashboard.docs.DocumentsStore
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.capture.CaptureFeedback
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.StrikethroughS
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.input.pointer.pointerInput
import com.platinum20.dashboard.notes.NoteHistory
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotesStore

/**
 * Il testo con le righe (1.555, Antonio scelta A di /mnt/project-files/editor-note-2/): punti, voci da spuntare e
 * titoletti mescolati nella stessa nota, la barra Punti · Spunta · Aa sopra la tastiera. Lo usano le note
 * lunghe e il campo "Note" di eventi, promemoria e date.
 */
object RichBar {
    /** Il campo che sta scrivendo adesso: la barra lavora su di lui. */
    class Target(
        val owner: Any,
        val value: () -> TextFieldValue,
        val set: (TextFieldValue) -> Unit,
        /** Annulla e Ripeti (1.579): leggerli in composizione fa ridisegnare la barra quando cambiano. */
        val canUndo: () -> Boolean = { false },
        val canRedo: () -> Boolean = { false },
        val undo: () -> Unit = {},
        val redo: () -> Unit = {},
        /** Le voci in più del menu "/" che sa fare solo chi usa il campo (foto, detta…). */
        val extras: List<SlashItem> = emptyList(),
    )

    var target by mutableStateOf<Target?>(null)
        internal set

    /** La riga di una tabella appena messa dal menu "/": il campo la apre per scriverci (1.588). */
    var openTable by mutableStateOf<Int?>(null)

    /** Il menu "/" aperto (1.589): Invio sceglie la sua prima voce. Null = menu chiuso o niente da scegliere. */
    var slashFirst: (() -> Unit)? = null
}

/** Una voce del menu "/" (1.584): scrivendo "/" a inizio riga. */
class SlashItem(val icon: ImageVector, val label: String, val detail: String, val run: () -> Unit)

/** La barra si vede mentre un campo con le righe scrive con la tastiera aperta, o quando c'è del testo scelto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun richBarShown(): Boolean = RichBar.target.let { t -> t != null && (WindowInsets.isImeVisible || !t.value().selection.collapsed) }

/**
 * Il posto di cerchio e pallino: lo spazio sottile al posto del segno, allargato con la spaziatura delle lettere.
 * Niente ParagraphStyle (1.556): ogni voce diventava un paragrafo a sé e fra le righe compariva una riga vuota.
 */
private val INDENT = 30.sp

/** Il posto del cerchio (19 dp) e del pallino, allineato al bordo del testo. */
private val MARK_SLOT = 20.dp

/**
 * Lo spazio fra lettere sul posto del segno (1.589): il telefono ne usa circa sei decimi (metà va prima della prima
 * lettera della riga e si perde), quindi 46 sp danno circa 28 dp: il cerchio da 20 dp e un po' d'aria prima del testo.
 */
private val MARK_SPACE = 46.sp

/** Quanto è alto il carattere invisibile in cima a ogni riga (1.589): l'aria dopo un Invio. */
private val BREATH_SIZE = 25.sp

/** L'ultima riga della tabella mentre ci si scrive (1.590): la riga più lo spazio per il + sotto. */
private val TALL_SIZE = 42.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RichNoteField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocusChange: (Boolean) -> Unit = {},
    /** Solo da leggere (la pagina dell'evento): si spunta ma non si scrive. */
    readOnly: Boolean = false,
    /** Il fumetto del tieni premuto su una voce offre anche "Manda in Da fare". */
    sendToTodo: Boolean = false,
    /** Spuntata l'ultima voce: chi usa il campo può dire "Tutto fatto". */
    onAllDone: (() -> Unit)? = null,
    /** L'altezza minima del campo: toccando sotto il testo si scrive lo stesso. */
    minHeight: androidx.compose.ui.unit.Dp = 0.dp,
    /** "Cerca nella nota" (1.579): le parole trovate si colorano, [findAt] è quella in vista. */
    find: String? = null,
    findAt: Int = 0,
    /** Le voci in più del menu "/" (foto, scatta, detta…), quando il campo sta in una nota. */
    slashExtras: List<SlashItem> = emptyList(),
    /** L'indice dei titoli (1.586): la riga che comincia a first va in cima alla pagina; second cambia a ogni tocco. */
    revealAt: Pair<Int, Int>? = null,
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val colors = MaterialTheme.colorScheme
    val inApp = LocalInAppNav.current
    val owner = remember { Any() }
    val current by rememberUpdatedState(value)
    val emit by rememberUpdatedState(onValueChange)
    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Le righe che si chiudono (1.588): i titoli chiusi, per testo del titolo; e la tabella che si sta cambiando.
    var folded by remember { mutableStateOf(setOf<String>()) }
    val folds = remember(value.text, folded) { NoteLines.foldRanges(value.text, folded) }
    // La tabella in cui si sta scrivendo (riga di testo della sua prima riga) e la casella da aprire subito (1.590).
    var activeTable by remember { mutableStateOf<Int?>(null) }
    var wantCell by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    val mainFocus = focusRequester ?: remember { FocusRequester() }
    val history = remember { NoteHistory() }
    var historyTick by remember { mutableIntStateOf(0) }
    fun snap(v: TextFieldValue) = NoteHistory.Snap(v.text, v.selection.start, v.selection.end)
    fun restore(s: NoteHistory.Snap?) {
        s ?: return
        historyTick++
        emit(TextFieldValue(s.text, TextRange(s.start.coerceIn(0, s.text.length), s.end.coerceIn(0, s.text.length))))
    }

    /** [typed] = l'ha cambiato la tastiera; falso = un tasto della barra, che sa già cosa fa. */
    fun change(v: TextFieldValue, typed: Boolean = true) {
        val old = current
        // Invio con il menu "/" aperto sceglie la prima voce (1.589), non va a capo.
        val enter = typed && v.text.length == old.text.length + 1 && old.selection.collapsed &&
            v.text == old.text.substring(0, old.selection.end) + "\n" + old.text.substring(old.selection.end)
        if (enter && slashQuery(old.text, old.selection.end) != null) {
            RichBar.slashFirst?.let { it(); return }
        }
        var next = v
        // Un link "@" si cancella tutto insieme (1.589): basta togliere una lettera.
        // Tabella, linea e vocale sono pezzi unici (1.589): la tastiera non li rompe.
        val guard = if (typed && v.text != old.text) NoteLines.guardBlocks(old.text, v.text) else null
        val mention = if (guard == null && typed && v.selection.collapsed) NoteFormat.deleteMention(old.text, v.text, v.selection.end) else null
        if (guard != null) next = TextFieldValue(guard.text, TextRange(guard.start))
        else if (mention != null) next = TextFieldValue(mention.text, TextRange(mention.start))
        else if (typed && v.text != old.text && v.selection.collapsed) {
            NoteLines.afterEdit(old.text, v.text, v.selection.end)?.let { next = TextFieldValue(it.text, TextRange(it.start, it.end)) }
            // I segni dei formati non si vedono (1.577): la tastiera non deve poterli rompere.
            NoteFormat.afterEdit(old.text, old.selection.start, old.selection.end, next.text, next.selection.end)?.let {
                next = TextFieldValue(it.text, TextRange(it.start, it.end))
            }
        }
        // Un formato acceso e rimasto vuoto sparisce quando il cursore va altrove.
        NoteFormat.tidy(next.text, next.selection.start, next.selection.end)?.let { next = TextFieldValue(it.text, TextRange(it.start, it.end)) }
        // Le liste numerate restano 1, 2, 3 (1.579).
        if (next.text != old.text) {
            val r = NoteLines.renumber(NoteLines.Edit(next.text, next.selection.start, next.selection.end))
            if (r.text != next.text) next = TextFieldValue(r.text, TextRange(r.start, r.end))
            history.record(snap(old), snap(next), System.currentTimeMillis())
            historyTick++
        }
        // Il cursore non resta mai dentro "[ ] ": scrivendo lì la voce si romperebbe.
        val safe = if (next.selection.collapsed) {
            val at = NoteLines.outOfPrefix(next.text, next.selection.start)
            if (at != next.selection.start) next.copy(selection = TextRange(at)) else next
        } else next
        // Scrivendo dentro una parte chiusa (Invio in fondo al titolo, Annulla…), quella parte si riapre.
        if (folded.isNotEmpty() && safe.text != old.text) {
            val c = safe.selection.end
            NoteLines.foldRanges(safe.text, folded).filter { c > it.first && c <= it.last + 1 }.forEach { r ->
                val h = NoteLines.lineAt(safe.text, r.first)
                folded = folded - NoteLines.plainLine(safe.text.substring(h.start, h.end))
            }
        }
        emit(safe)
    }

    // Anche con del testo scelto senza tastiera (1.586): la barra Aa deve esserci lo stesso.
    val choosing = !value.selection.collapsed
    DisposableEffect(focused, choosing, readOnly) {
        if ((focused || choosing) && !readOnly) RichBar.target = RichBar.Target(
            owner, { current }, { change(it, typed = false) },
            canUndo = { historyTick; history.canUndo },
            canRedo = { historyTick; history.canRedo },
            undo = { restore(history.undo(snap(current))) },
            redo = { restore(history.redo(snap(current))) },
            extras = slashExtras,
        )
        onDispose { if (RichBar.target?.owner == owner) RichBar.target = null }
    }

    val faded = colors.onSurfaceVariant
    // Una trasformazione nuova a ogni testo nuovo (1.578): con due formati sulla stessa parola il disegno restava
    // quello vecchio finché non si muoveva il cursore.
    val found = remember(value.text, find) { if (find.isNullOrBlank()) emptyList() else NoteLines.matches(value.text, find) }
    val foundStyle = SpanStyle(background = colors.primary.copy(alpha = 0.22f))
    val foundNow = SpanStyle(background = colors.primary.copy(alpha = 0.5f))
    val accent = colors.primary
    val marker = androidx.compose.ui.graphics.Color(0x66FFD54F)
    // Mentre si scrive in una tabella la sua ultima riga è più alta: sotto ci sta il + per una riga nuova (1.590).
    val tallAt = activeTable?.let { NoteTable.blockAt(value.text, it) }?.let { NoteLines.parse(value.text)[it.last].start }
    val transformation = remember(faded, value.text, found, findAt, accent, folds, tallAt) {
        VisualTransformation { input ->
            richText(input.text, faded, found.mapIndexed { i, r -> r to if (i == findAt) foundNow else foundStyle }, accent, marker = marker, folds = folds, tall = tallAt)
        }
    }
    fun hiddenLine(line: NoteLines.Line) = folds.any { line.start in it }

    // Il cursore resta sempre in vista sopra la barra e la tastiera, anche quando Invio fa una riga nuova (1.562):
    // da solo il campo lo porta in vista prima che la riga nuova esista, e finiva sotto la tastiera.
    val follow = remember { BringIntoViewRequester() }
    val room = with(LocalDensity.current) { 32.dp.toPx() }
    LaunchedEffect(value.text, value.selection, focused) {
        if (!focused || readOnly) return@LaunchedEffect
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val at = transformation.filter(AnnotatedString(value.text)).offsetMapping.originalToTransformed(value.selection.end)
        if (at > l.layoutInput.text.length) return@LaunchedEffect
        val cursor = l.getCursorRect(at)
        follow.bringIntoView(Rect(cursor.left, cursor.top - room / 2, cursor.right, cursor.bottom + room))
    }

    // La parola trovata resta in vista, anche senza tastiera.
    LaunchedEffect(found, findAt) {
        val r = found.getOrNull(findAt) ?: return@LaunchedEffect
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val at = transformation.filter(AnnotatedString(value.text)).offsetMapping.originalToTransformed(r.first)
        if (at > l.layoutInput.text.length) return@LaunchedEffect
        val box = l.getCursorRect(at)
        follow.bringIntoView(Rect(box.left, box.top - room * 3, box.right, box.bottom + room * 3))
    }

    // Un titolo toccato nell'indice sale in cima alla pagina.
    LaunchedEffect(revealAt) {
        val at0 = revealAt?.first ?: return@LaunchedEffect
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val at = transformation.filter(AnnotatedString(value.text)).offsetMapping.originalToTransformed(at0.coerceIn(0, value.text.length))
        if (at > l.layoutInput.text.length) return@LaunchedEffect
        val box = l.getCursorRect(at)
        // Un rettangolo più alto dello schermo: la pagina scorre fino al suo inizio.
        follow.bringIntoView(Rect(box.left, box.top - room, box.right, box.top + room * 200))
    }

    // Trascinare una voce (1.579): tieni premuto il cerchio o il pallino e sposta su o giù. Se non si muove, il fumetto.
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dragMoved by remember { mutableStateOf(false) }
    var dragPull by remember { mutableFloatStateOf(0f) }
    var dragEndedAt by remember { mutableLongStateOf(0L) }

    // Una tabella appena messa dal menu "/": si scrive subito nella prima casella.
    LaunchedEffect(RichBar.openTable) {
        val at = RichBar.openTable ?: return@LaunchedEffect
        if (RichBar.target?.owner != owner) return@LaunchedEffect
        RichBar.openTable = null
        wantCell = Triple(at, 0, 0)
    }
    Box(modifier) {
        // Lo sfondo dei riquadri "da non dimenticare", sotto il testo (1.584).
        layout?.let { l ->
            val text = value.text
            val shown = NoteLines.Shown(text, NoteFormat.hidden(text) + folds, breathe = true)
            val drawn = l.layoutInput.text.length
            val density = LocalDensity.current
            shown.lines.filter { it.kind == NoteLines.Kind.CALLOUT && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line ->
                val first = l.getLineForOffset(shown.toShown(line.start))
                val last = l.getLineForOffset(shown.toShown(line.end).coerceAtMost(drawn))
                val top = l.getLineTop(first)
                val h = with(density) { (l.getLineBottom(last) - top).toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(0, top.toInt()) }
                        .fillMaxWidth()
                        .height(h + 4.dp)
                        .offset(y = (-2).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.card),
                )
            }
            // Le bolle dei link "@" (1.589), sotto il testo: una per riga se il link va a capo.
            NoteFormat.mentions(text).filter { r -> folds.none { r.first in it } }.forEach { r ->
                val s0 = shown.toShown(r.first)
                val e0 = shown.toShown(r.last + 1).coerceAtMost(drawn)
                if (s0 >= e0) return@forEach
                val firstRow = l.getLineForOffset(s0)
                val lastRow = l.getLineForOffset((e0 - 1).coerceAtLeast(s0))
                val pad = with(density) { 5.dp.toPx() }
                val fontPx = with(density) { if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else 16.sp.toPx() }
                for (row in firstRow..lastRow) {
                    val x0 = (if (row == firstRow) l.getHorizontalPosition(s0, true) else l.getLineLeft(row)) - pad
                    val x1 = (if (row == lastRow) l.getHorizontalPosition(e0, true) else l.getLineRight(row)) + pad
                    val base = l.getLineBaseline(row)
                    val top = base - fontPx * 1.1f
                    Box(
                        Modifier
                            .offset { IntOffset(x0.coerceAtLeast(0f).toInt(), top.toInt()) }
                            .size(with(density) { (x1 - x0).coerceAtLeast(1f).toDp() }, with(density) { (fontPx * 1.5f).toDp() })
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.14f)),
                    )
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = { change(it) },
            readOnly = readOnly,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            onTextLayout = { layout = it },
            decorationBox = { field ->
                Box {
                    if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    field()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(follow)
                .heightIn(min = minHeight)
                .focusRequester(mainFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                },
        )
        // I cerchi e i pallini, sopra il rientro di ogni voce: si toccano senza aprire la tastiera.
        val l = layout
        if (l != null) {
            // Gli stessi segni nascosti del testo disegnato, se no le posizioni scivolano di riga.
            val text = value.text
            val shown = NoteLines.Shown(text, NoteFormat.hidden(text) + folds, breathe = true)
            val density = LocalDensity.current
            val drawn = l.layoutInput.text.length
            shown.lines.filter { it.isList && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line -> androidx.compose.runtime.key(line.index) {
                val at = shown.toShown(line.start)
                val row = l.getLineForOffset(at)
                // Il segno sta all'altezza delle lettere, non a metà della riga: sopra c'è l'aria dopo l'Invio (1.589).
                val fontPx = with(density) { if (textStyle.fontSize.isSp) textStyle.fontSize.toPx() else 16.sp.toPx() }
                val base = l.getLineBaseline(row)
                val top = (base - fontPx * 1.05f).coerceAtLeast(l.getLineTop(row))
                val height = with(density) { ((base + fontPx * 0.35f).coerceAtMost(l.getLineBottom(row)) - top).toDp() }
                // Il segno si misura da dove comincia il testo della voce (1.583): un rientro prima del testo. Così il cerchio
                // di una voce sta sul bordo del testo normale, non fuori a sinistra, e le sotto-voci scalano di un rientro.
                val textX = l.getHorizontalPosition(shown.toShown(line.contentStart).coerceAtMost(drawn), true)
                val indentPx = with(density) { INDENT.toPx() }
                val left = (textX - indentPx).coerceAtLeast(0f)
                val width = with(density) { (textX - left).coerceAtLeast(1f).toDp() }
                val rowPx = (l.getLineBottom(row) - top).coerceAtLeast(1f)
                val lineNow by rememberUpdatedState(line)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset { IntOffset(left.toInt(), top.toInt()) }
                        .size(width, height)
                        .pointerInput(readOnly) {
                            if (readOnly) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic(Haptic.LONG_PRESS)
                                    dragging = lineNow.index
                                    dragMoved = false
                                    dragPull = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val from = dragging
                                    dragPull += amount.y
                                    if (from != null) {
                                    val count = NoteLines.parse(current.text).size
                                    if (dragPull > rowPx * 0.7f && from < count - 1) {
                                        dragPull -= rowPx
                                        haptic(Haptic.TICK)
                                        dragMoved = true
                                        dragging = from + 1
                                        change(TextFieldValue(NoteLines.move(current.text, from, up = false), current.selection), typed = false)
                                    } else if (dragPull < -rowPx * 0.7f && from > 0) {
                                        dragPull += rowPx
                                        haptic(Haptic.TICK)
                                        dragMoved = true
                                        dragging = from - 1
                                        change(TextFieldValue(NoteLines.move(current.text, from, up = true), current.selection), typed = false)
                                    }
                                    }
                                },
                                onDragEnd = {
                                    dragEndedAt = System.currentTimeMillis()
                                    if (!dragMoved) ActionBubble.show(*lineActions(context, lineNow, current, emit, sendToTodo).toTypedArray())
                                    dragging = null
                                },
                                onDragCancel = {
                                    dragEndedAt = System.currentTimeMillis()
                                    dragging = null
                                },
                            )
                        }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (line.kind == NoteLines.Kind.CHECK) (if (line.done) "Togli la spunta" else "Spunta") else null,
                            onClick = {
                                // Il tocco che chiude un tieni premuto non spunta.
                                if (System.currentTimeMillis() - dragEndedAt < 400) return@combinedClickable
                                if (line.kind == NoteLines.Kind.CHECK) {
                                    haptic(if (line.done) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                                    val now = current
                                    val text = NoteLines.toggle(now.text, line.index)
                                    val delta = text.length - now.text.length
                                    fun move(o: Int) = if (o > line.start) o + delta else o
                                    emit(TextFieldValue(text, TextRange(move(now.selection.start), move(now.selection.end))))
                                    if (!line.done && NoteLines.allDone(text)) onAllDone?.invoke()
                                }
                            },
                        ),
                ) {
                    // Il segno nel suo posto largo quanto il cerchio, sul bordo sinistro del rientro.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(MARK_SLOT).fillMaxHeight()) {
                        LineMark(line, lifted = dragging == line.index)
                    }
                }
            } }
            // Citazione, riquadro e linea (1.584): la barra, l'emoji, la linea. Tieni premuto per le scelte.
            shown.lines.filter { it.hasMark && !it.isList && it.kind != NoteLines.Kind.TABLE && it.kind != NoteLines.Kind.AUDIO && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line -> androidx.compose.runtime.key("m${line.index}") {
                val at = shown.toShown(line.start)
                val first = l.getLineForOffset(at)
                val last = l.getLineForOffset(shown.toShown(line.end).coerceAtMost(drawn))
                val top = l.getLineTop(first)
                val height = with(density) { (l.getLineBottom(if (line.kind == NoteLines.Kind.QUOTE) last else first) - top).toDp() }
                val textX = l.getHorizontalPosition(shown.toShown(line.contentStart).coerceAtMost(drawn), true)
                val left = if (line.kind == NoteLines.Kind.DIVIDER) 0f else (textX - with(density) { INDENT.toPx() }).coerceAtLeast(0f)
                val lineNow by rememberUpdatedState(line)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .offset { IntOffset(left.toInt(), top.toInt()) }
                        .then(if (line.kind == NoteLines.Kind.DIVIDER) Modifier.fillMaxWidth() else Modifier.width(MARK_SLOT))
                        .height(height)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !readOnly,
                            onLongClick = {
                                haptic(Haptic.LONG_PRESS)
                                ActionBubble.show(*markActions(context, lineNow, current, emit).toTypedArray())
                            },
                            onClick = {},
                        ),
                ) {
                    when (line.kind) {
                        NoteLines.Kind.QUOTE -> Box(Modifier.padding(start = 6.dp).width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colors.outline))
                        NoteLines.Kind.CALLOUT -> Text(
                            NoteLines.calloutEmoji(text.substring(line.start, line.end)),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 4.dp).wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                        else -> Box(Modifier.fillMaxWidth().height(1.5.dp).background(colors.outlineVariant))
                    }
                }
            } }
            // I link si toccano (1.586): un tocco apre la nota collegata con "@" o la pagina; tieni premuto = le scelte.
            if (!readOnly) NoteFormat.spans(text).filter { (it.kind == NoteFormat.Kind.LINK || it.kind == NoteFormat.Kind.URL) && folds.none { f -> it.start in f } && NoteLines.lineAt(text, it.start).kind != NoteLines.Kind.AUDIO }.forEach { span ->
                val (url, label) = NoteFormat.linkAt(text, span.start) ?: return@forEach
                val s0 = shown.toShown(span.start)
                val e0 = shown.toShown(span.end).coerceAtMost(drawn)
                if (s0 >= e0) return@forEach
                val firstRow = l.getLineForOffset(s0)
                val lastRow = l.getLineForOffset((e0 - 1).coerceAtLeast(s0))
                for (row in firstRow..lastRow) androidx.compose.runtime.key("l${span.start}-$row") {
                    val x0 = if (row == firstRow) l.getHorizontalPosition(s0, true) else l.getLineLeft(row)
                    val x1 = if (row == lastRow) l.getHorizontalPosition(e0, true) else l.getLineRight(row)
                    val top = l.getLineTop(row)
                    val w = with(density) { (x1 - x0).coerceAtLeast(1f).toDp() }
                    val h = with(density) { (l.getLineBottom(row) - top).toDp() }
                    Box(
                        Modifier
                            .offset { IntOffset(minOf(x0, x1).toInt(), top.toInt()) }
                            .size(w, h)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = {
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleAction(Icons.AutoMirrored.Outlined.OpenInNew, "Apri", if (url.startsWith(NoteFormat.NOTE_LINK)) "la nota collegata" else "la pagina, dentro Canvas") { openNoteLink(inApp, url, label) },
                                        BubbleAction(Icons.Outlined.Edit, "Modifica il testo", "il cursore va sul link") {
                                            emit(TextFieldValue(current.text, TextRange(span.end)))
                                            focusRequester?.requestFocus()
                                        },
                                        BubbleAction(Icons.Outlined.LinkOff, "Togli il link", "resta il testo") {
                                            val e = NoteFormat.link(current.text, span.start, span.start, null)
                                            emit(TextFieldValue(e.text, TextRange(e.end)))
                                        },
                                    )
                                },
                            ) { haptic(Haptic.TICK); openNoteLink(inApp, url, label) },
                    )
                }
            }
            // Le righe che si chiudono (1.588): accanto a ogni titolo con qualcosa sotto, la freccetta; chiuso dice quante righe.
            shown.lines.filter { it.kind == NoteLines.Kind.HEADING && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line ->
                val count = NoteLines.sectionSize(text, line.index)
                if (count == 0) return@forEach
                val name = NoteLines.plainLine(text.substring(line.start, line.end))
                val closed = name in folded
                val row = l.getLineForOffset(shown.toShown(line.end).coerceAtMost(drawn))
                val top = l.getLineTop(row)
                val x = l.getLineRight(row)
                val h = with(density) { (l.getLineBottom(row) - top).toDp() }
                androidx.compose.runtime.key("f${line.index}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .offset { IntOffset(x.toInt(), top.toInt()) }
                            .height(h)
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = name.isNotEmpty()) {
                                haptic(Haptic.TICK)
                                folded = if (closed) folded - name else folded + name
                            }
                            .padding(horizontal = 4.dp),
                    ) {
                        Icon(
                            if (closed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = if (closed) "Apri la parte" else "Chiudi la parte",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        if (closed) Text("$count", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
            // Il menu "/" come su Notion (1.589): sotto la riga del cursore.
            val slashTarget = RichBar.target
            val slashQ = if (focused && !readOnly && value.selection.collapsed) slashQuery(text, value.selection.end) else null
            if (slashQ != null && slashTarget != null && slashTarget.owner == owner) {
                val rect = l.getCursorRect(shown.toShown(value.selection.end).coerceIn(0, drawn))
                Box(
                    Modifier
                        .offset { IntOffset(0, rect.top.toInt()) }
                        .fillMaxWidth()
                        .height(with(density) { (rect.bottom - rect.top).toDp() }),
                ) { SlashMenu(slashTarget, slashQ) }
            }
            // Il vocale (1.589): una barra larga quanto la nota, alta una riga. Play/pausa, avanzamento (si tocca per andare
            // a un punto), durata. Tieni premuto = le scelte.
            shown.lines.filter { it.kind == NoteLines.Kind.AUDIO && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line ->
                val link = NoteFormat.linkAt(text, text.indexOf('[', line.start).coerceAtLeast(line.start)) ?: return@forEach
                val path = link.first.removePrefix(NoteAudio.SCHEME)
                val row = l.getLineForOffset(shown.toShown(line.start))
                val top = l.getLineTop(row)
                val bottom = l.getLineBottom(row)
                androidx.compose.runtime.key("a${line.start}:$path") {
                    AudioPill(
                        path = path,
                        label = link.second,
                        modifier = Modifier
                            .offset { IntOffset(0, top.toInt()) }
                            .fillMaxWidth()
                            .height(with(density) { (bottom - top).toDp() }),
                        onLongPress = if (readOnly) null else ({
                            haptic(Haptic.LONG_PRESS)
                            ActionBubble.show(
                                BubbleAction(Icons.Outlined.Delete, "Togli il vocale", "con Annulla", danger = true) {
                                    val before = current
                                    val l2 = NoteLines.parse(before.text).firstOrNull { it.start == line.start && it.kind == NoteLines.Kind.AUDIO } ?: return@BubbleAction
                                    val t = NoteLines.remove(before.text, l2.index)
                                    NoteAudio.stopPlaying()
                                    emit(TextFieldValue(t, TextRange(l2.start.coerceIn(0, t.length))))
                                    CaptureFeedback.tell(context, "Vocale tolto", Icons.Outlined.Delete, "Annulla") { emit(before) }
                                },
                            )
                        }),
                    )
                }
            }
            // La tabella come su Notion (1.590): le righe "| a | b |" sono nascoste e qui sopra si scrive nelle caselle.
            // Mentre scrivi in una tabella, il + a destra aggiunge una colonna e quello sotto una riga; tieni premuta una
            // casella per le scelte della sua riga e colonna.
            val tableLines = shown.lines.filter { it.kind == NoteLines.Kind.TABLE && !hiddenLine(it) && shown.toShown(it.start) < drawn }
            val blocks = tableLines.fold(mutableListOf<MutableList<NoteLines.Line>>()) { acc, line ->
                if (acc.isNotEmpty() && acc.last().last().index == line.index - 1) acc.last() += line else acc += mutableListOf(line)
                acc
            }
            blocks.forEach { rows ->
                val block = rows.first().index..rows.last().index
                val tops = rows.map { l.getLineTop(l.getLineForOffset(shown.toShown(it.start))) }
                val bottoms = rows.map { l.getLineBottom(l.getLineForOffset(shown.toShown(it.start))) }
                val active = activeTable == block.first
                val heights = rows.indices.map { bottoms[it] - tops[it] }
                // L'ultima riga, se è alta per il +, ha l'altezza delle altre (o la sua parte, se la tabella ha una riga sola).
                val lastH = if (!active) heights.last() else heights.dropLast(1).minOrNull() ?: (heights.last() * BREATH_SIZE.value / TALL_SIZE.value)
                val grid = NoteTable.grid(text, block)
                fun put(g: List<List<String>>, cell: Pair<Int, Int>? = null) {
                    val t = NoteTable.replace(current.text, block, g)
                    val lines = NoteLines.parse(t)
                    val at = if (g.isEmpty()) lines.getOrNull(block.first)?.start ?: t.length else lines[block.first + g.size - 1].end
                    change(TextFieldValue(t, TextRange(at.coerceIn(0, t.length))), typed = false)
                    if (cell != null) wantCell = Triple(block.first, cell.first, cell.second)
                }
                androidx.compose.runtime.key("t${block.first}") {
                    NoteTableBlock(
                        grid = grid,
                        heights = heights.dropLast(1).map { with(density) { it.toDp() } } + with(density) { lastH.toDp() },
                        total = with(density) { (bottoms.last() - tops.first()).toDp() },
                        active = active,
                        readOnly = readOnly,
                        want = wantCell?.takeIf { it.first == block.first }?.let { it.second to it.third },
                        modifier = Modifier.offset { IntOffset(0, tops.first().toInt()) }.fillMaxWidth(),
                        onWanted = { wantCell = null },
                        onFocus = { focusedNow -> if (focusedNow) activeTable = block.first else if (activeTable == block.first) activeTable = null },
                        onCell = { r, c, v -> put(NoteTable.setCell(NoteTable.grid(current.text, block), r, c, v)) },
                        onNext = { r, c ->
                            if (r + 1 < grid.size) wantCell = Triple(block.first, r + 1, c)
                            else {
                                // Invio nell'ultima riga: si torna a scrivere nella nota, nella riga sotto la tabella.
                                val now = current.text
                                val end = NoteLines.parse(now)[block.last].end
                                val t = if (end >= now.length) now + "\n" else now
                                activeTable = null
                                change(TextFieldValue(t, TextRange(end + 1)), typed = false)
                                mainFocus.requestFocus()
                            }
                        },
                        onAddRow = { put(NoteTable.addRow(NoteTable.grid(current.text, block), grid.size), grid.size to 0) },
                        onAddColumn = { put(NoteTable.addColumn(NoteTable.grid(current.text, block), grid.first().size), 0 to grid.first().size) },
                        onLongPress = { r, c ->
                            haptic(Haptic.LONG_PRESS)
                            val g = NoteTable.grid(current.text, block)
                            val rowName = g[r].firstOrNull { it.isNotBlank() }?.let { "\"$it\"" } ?: "la riga ${r + 1}"
                            val colName = g.first().getOrNull(c)?.takeIf { it.isNotBlank() }?.let { "\"$it\"" } ?: "la colonna ${c + 1}"
                            fun undoable(next: List<List<String>>, said: String) {
                                val before = current
                                put(next)
                                CaptureFeedback.tell(context, said, Icons.Outlined.Delete, "Annulla") { emit(before) }
                            }
                            ActionBubble.show(
                                *listOfNotNull(
                                    BubbleAction(
                                        Icons.Outlined.Add, "Aggiungi una riga", "vuota, accanto a $rowName",
                                        chips = listOf(
                                            BubbleChip("Sopra") { put(NoteTable.addRow(g, r), r to c) },
                                            BubbleChip("Sotto") { put(NoteTable.addRow(g, r + 1), r + 1 to c) },
                                        ),
                                    ),
                                    BubbleAction(
                                        Icons.Outlined.Add, "Aggiungi una colonna", "vuota, accanto a $colName",
                                        chips = listOf(
                                            BubbleChip("A sinistra") { put(NoteTable.addColumn(g, c), r to c) },
                                            BubbleChip("A destra") { put(NoteTable.addColumn(g, c + 1), r to c + 1) },
                                        ),
                                    ),
                                    if (g.size > 1) BubbleAction(Icons.Outlined.Delete, "Togli la riga", "$rowName, con Annulla", danger = true) { undoable(NoteTable.dropRow(g, r), "Riga tolta") } else null,
                                    if (g.first().size > 1) BubbleAction(Icons.Outlined.Delete, "Togli la colonna", "$colName, con Annulla", danger = true) { undoable(NoteTable.dropColumn(g, c), "Colonna tolta") } else null,
                                    BubbleAction(Icons.Outlined.Delete, "Togli la tabella", "con Annulla", danger = true) { undoable(emptyList(), "Tabella tolta") },
                                ).toTypedArray(),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Apre un link della nota: "nota:ID" = l'altra nota, "app:" = una pagina di Canvas, "audio:" = lo suona, il resto dentro Canvas. */
internal fun openNoteLink(inApp: InAppNav?, url: String, label: String) {
    val id = url.removePrefix(NoteFormat.NOTE_LINK).toLongOrNull()
    when {
        url.startsWith(NoteFormat.NOTE_LINK) && id != null -> inApp?.openRoute?.invoke(Routes.noteEdit(id))
        url.startsWith(NoteFormat.APP_LINK) ->
            inApp?.openRoute?.invoke(url.removePrefix(NoteFormat.APP_LINK).replace("%20", " ").replace("%28", "(").replace("%29", ")"))
        url.startsWith(NoteAudio.SCHEME) -> NoteAudio.toggle(url.removePrefix(NoteAudio.SCHEME))
        else -> inApp?.openWeb?.invoke(url, label)
    }
}

/** "ven 28 nov, 15:00" per il tasto Calendario della barra; a mezzanotte solo il giorno. */
internal fun dueLabel(due: java.time.LocalDateTime): String {
    val day = due.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ITALIAN))
    return if (due.toLocalTime() == java.time.LocalTime.MIDNIGHT) day else "$day, %d:%02d".format(due.hour, due.minute)
}

/** Una cosa dell'app che si può mettere nella nota con "@". */
internal class MentionTarget(val icon: ImageVector, val title: String, val url: String)

/** Le cose che corrispondono a quello scritto dopo "@": prima le note, poi il resto; al massimo 10. */
internal fun mentionTargets(data: com.platinum20.dashboard.notes.NotesData, docs: List<com.platinum20.dashboard.docs.Document>, query: String): List<MentionTarget> {
    fun hit(t: String) = t.isNotBlank() && t.contains(query, ignoreCase = true)
    val out = mutableListOf<MentionTarget>()
    data.notes.filter { hit(it.plainTitle) }.sortedByDescending { it.updatedAt }.take(5)
        .forEach { out += MentionTarget(Icons.AutoMirrored.Outlined.Notes, it.title, NoteFormat.NOTE_LINK + it.id) }
    data.tasks.filter { !it.done && hit(it.text) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(4)
        .forEach { out += MentionTarget(if (it.dueAt != null) Icons.Outlined.Event else Icons.Outlined.TaskAlt, it.text, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.TASK, it.id)) }
    data.habits.filter { hit(it.title) }.take(3)
        .forEach { out += MentionTarget(Icons.Outlined.LocalFireDepartment, it.title, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.HABIT, it.id)) }
    data.parcels.filter { hit(it.title) }.take(2)
        .forEach { out += MentionTarget(Icons.Outlined.LocalShipping, it.title, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.PARCEL, it.id)) }
    docs.filter { hit(it.title) }.take(2)
        .forEach { out += MentionTarget(Icons.Outlined.Badge, it.title, NoteFormat.APP_LINK + Routes.document(it.id)) }
    MENTION_PAGES.filter { (name, _) -> query.isNotEmpty() && hit(name) }
        .forEach { (name, route) -> out += MentionTarget(Icons.Outlined.Apps, name, NoteFormat.APP_LINK + route) }
    return out.take(10)
}

/** Le sezioni di Canvas che si possono collegare con "@". */
private val MENTION_PAGES = listOf(
    "Meteo" to Routes.WEATHER, "SIM" to Routes.SIM, "Calendario" to Routes.NOTES, "News" to Routes.NEWS,
    "Media" to Routes.MEDIA, "Musica" to Routes.MUSIC_ALL, "Radio" to Routes.RADIO, "Podcast" to Routes.PODCAST,
    "Da fare" to Routes.LATER_ARCHIVE, "Appunti" to Routes.JOTTINGS_ARCHIVE, "Cerca" to Routes.SEARCH,
)

/** Il fumetto del tieni premuto su citazione, riquadro o linea. */
private fun markActions(
    context: android.content.Context,
    line: NoteLines.Line,
    value: TextFieldValue,
    emit: (TextFieldValue) -> Unit,
): List<BubbleAction> {
    val text = value.text
    fun set(next: String, at: Int = line.start) = emit(TextFieldValue(next, TextRange(at.coerceIn(0, next.length))))
    return when (line.kind) {
        NoteLines.Kind.DIVIDER -> listOf(
            BubbleAction(Icons.Outlined.Delete, "Togli la linea", "con Annulla", danger = true) {
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "Linea tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
            },
        )
        else -> listOfNotNull(
            if (line.kind == NoteLines.Kind.CALLOUT) BubbleAction(
                Icons.Outlined.EmojiEmotions, "Emoji del riquadro", "tocca quella che vuoi",
                chips = CALLOUT_EMOJIS.map { e ->
                    BubbleChip(e, selected = NoteLines.calloutEmoji(text.substring(line.start, line.end)) == e) { set(NoteLines.setCalloutEmoji(text, line.index, e), line.end) }
                },
            ) else null,
            BubbleAction(Icons.AutoMirrored.Outlined.Notes, "Diventa testo", if (line.kind == NoteLines.Kind.QUOTE) "senza la barra" else "senza il riquadro") {
                set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.TEXT))
            },
            BubbleAction(Icons.Outlined.Delete, "Togli la riga", "con Annulla", danger = true) {
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "Riga tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
            },
        )
    }
}

/** Le emoji da scegliere per il riquadro. */
private val CALLOUT_EMOJIS = listOf("💡", "⚠️", "📌", "❗", "✅", "🔑", "📞", "❤️")

/** Il cerchio vuoto, il cerchio pieno con la spunta, o il pallino del punto. */
@Composable
private fun LineMark(line: NoteLines.Line, lifted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // Mentre la si trascina, la voce ha il segno blu: si vede quale si sta spostando.
    val ink = if (lifted) colors.primary else colors.onSurface
    when {
        line.kind == NoteLines.Kind.NUMBER -> Text(
            "${line.number}.",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (lifted) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
        )
        // requiredSize: tondi sempre, anche se il posto è più stretto.
        line.kind == NoteLines.Kind.BULLET -> Box(Modifier.requiredSize(if (lifted) 9.dp else 6.dp).clip(CircleShape).background(ink))
        line.done -> Box(contentAlignment = Alignment.Center, modifier = Modifier.requiredSize(19.dp).clip(CircleShape).background(colors.primary)) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(13.dp))
        }
        else -> Box(Modifier.requiredSize(19.dp).border(1.6.dp, if (lifted) colors.primary else colors.onSurfaceVariant, CircleShape))
    }
}

/**
 * Il testo disegnato: formati, titoletti, voci fatte barrate e sbiadite, il rientro di punti e voci. I segni dei formati
 * ([NoteFormat.hidden]) e delle voci ([NoteLines.Shown]) non si vedono mai (1.577).
 */
private fun richText(
    text: String,
    faded: androidx.compose.ui.graphics.Color,
    marks: List<Pair<IntRange, SpanStyle>> = emptyList(),
    accent: androidx.compose.ui.graphics.Color = faded,
    /** Le righe sotto i titoli chiusi (1.588): non si disegnano. */
    folds: List<IntRange> = emptyList(),
    marker: androidx.compose.ui.graphics.Color = faded,
    /** La riga (dove comincia) da fare più alta: l'ultima della tabella in cui si scrive. */
    tall: Int? = null,
): TransformedText {
    val styled = buildAnnotatedString {
        append(text)
        NoteFormat.spans(text).forEach { s ->
            val style = when (s.kind) {
                NoteFormat.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                NoteFormat.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteFormat.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                NoteFormat.Kind.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                NoteFormat.Kind.MARK -> null
                // Evidenziatore (1.584): uno sfondo giallo chiaro, l'unico colore che non vuol dire niente altro.
                NoteFormat.Kind.HIGHLIGHT -> SpanStyle(background = marker)
                // I link "@" stanno in una bolla (disegnata sotto il testo), senza sottolineatura (1.589).
                NoteFormat.Kind.LINK, NoteFormat.Kind.URL ->
                    if (s.kind == NoteFormat.Kind.LINK && NoteFormat.linkAt(text, s.start)?.first?.let(NoteFormat::isMention) == true) SpanStyle(color = accent)
                    else SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            }
            if (style != null) addStyle(style, s.start, s.end)
        }
        NoteLines.parse(text).filter { it.kind == NoteLines.Kind.CHECK && it.done && it.end > it.contentStart }.forEach {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = faded), it.contentStart, it.end)
        }
        // La citazione: testo in corsivo e un po' più chiaro, accanto alla barra.
        NoteLines.parse(text).filter { it.kind == NoteLines.Kind.QUOTE && it.end > it.contentStart }.forEach {
            addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = faded), it.contentStart, it.end)
        }
        marks.forEach { (r, style) -> if (r.last < text.length) addStyle(style, r.first, r.last + 1) }
    }
    val view = NoteLines.Shown(text, NoteFormat.hidden(text) + folds, breathe = true)
    if (view.cuts.isEmpty()) return TransformedText(styled, OffsetMapping.Identity)
    val shown = buildAnnotatedString {
        var at = 0
        view.cuts.forEach { c ->
            append(styled.subSequence(at, c.start))
            append(c.with)
            at = c.end
        }
        append(styled.subSequence(at, text.length))
        view.lines.filter { it.hasMark && it.prefix > 0 && !it.isBlock && folds.none { f -> it.start in f } }.forEach { line ->
            val s = view.toShown(line.start)
            // Ogni livello di sotto-voce sposta il testo di un altro rientro (1.579); citazione e riquadro uno solo.
            // Spazio fra lettere sul posto del segno, mai ParagraphStyle: ogni voce diventava un paragrafo con una riga
            // vuota sotto (1.556, di nuovo nella 1.587).
            if (s < length) addStyle(SpanStyle(letterSpacing = (MARK_SPACE.value * (1 + line.level)).sp), s, s + 1)
        }
        // L'aria fra le righe (1.589): il carattere invisibile in cima a ogni riga è più grande del testo, così la riga
        // dopo un Invio ha più spazio sopra; le righe che vanno a capo da sole restano strette.
        view.lines.filter { folds.none { f -> it.start in f } }.forEach { line ->
            val at = view.toShown(line.start) + if (line.hasMark && line.prefix > 0) 1 else 0
            if (at < length) addStyle(SpanStyle(fontSize = if (line.start == tall) TALL_SIZE else BREATH_SIZE), at, at + 1)
        }
    }
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = view.toShown(offset).coerceIn(0, shown.length)
        override fun transformedToOriginal(offset: Int) = view.toText(offset).coerceIn(0, text.length)
    }
    return TransformedText(shown, mapping)
}

/** Il fumetto del tieni premuto su una voce o un punto. */
private fun lineActions(
    context: android.content.Context,
    line: NoteLines.Line,
    value: TextFieldValue,
    emit: (TextFieldValue) -> Unit,
    sendToTodo: Boolean,
): List<BubbleAction> {
    val text = value.text
    val count = NoteLines.parse(text).size
    fun set(next: String) = emit(TextFieldValue(next, TextRange(NoteLines.parse(next).getOrNull(line.index)?.end ?: next.length)))
    val words = NoteLines.plainLine(text.substring(line.start, line.end))
    return listOfNotNull(
        if (line.kind == NoteLines.Kind.CHECK) BubbleAction(Icons.AutoMirrored.Outlined.FormatListBulleted, "Diventa un punto", "il cerchio diventa un pallino") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.BULLET)) }
        else BubbleAction(Icons.Outlined.RadioButtonUnchecked, "Diventa da spuntare", "il pallino diventa un cerchio") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.CHECK)) },
        BubbleAction(Icons.AutoMirrored.Outlined.Notes, "Diventa testo", "senza pallino né cerchio") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.TEXT)) },
        if (line.index > 0) BubbleAction(Icons.Outlined.ArrowUpward, "Sposta su", "scambia con la riga sopra") { set(NoteLines.move(text, line.index, up = true)) } else null,
        if (line.index < count - 1) BubbleAction(Icons.Outlined.ArrowDownward, "Sposta giù", "scambia con la riga sotto") { set(NoteLines.move(text, line.index, up = false)) } else null,
        if (sendToTodo && words.isNotEmpty()) BubbleAction(Icons.Outlined.TaskAlt, "Manda in Da fare", "la trovi nella card della Home") {
            NotesStore.addTodo(words)?.let { task ->
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "In Da fare", Icons.Outlined.TaskAlt, "Annulla") {
                    NotesStore.deleteTask(task.id)
                    emit(TextFieldValue(text, value.selection))
                }
            }
        } else null,
        BubbleAction(Icons.Outlined.Delete, "Togli la riga", "con Annulla", danger = true) {
            set(NoteLines.remove(text, line.index))
            CaptureFeedback.tell(context, "Riga tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
        },
    )
}

/**
 * La barra sopra la tastiera (1.579, /mnt/project-files/editor-note-3/barra.png): solo icone tonde in gruppi.
 * Annulla · Ripeti | Punti · Numeri · Spunta · rientro → ← | Aa. Con del testo scelto, o dopo Aa: grassetto, corsivo,
 * barrato, titolo | X per tornare. Accesa = blu; spenta (grigia) = adesso non si può.
 */
@Composable
internal fun RichToolbar(modifier: Modifier = Modifier) {
    val target = RichBar.target ?: return
    val haptic = rememberHaptic()
    val v = target.value()
    var formats by remember(target.owner) { mutableStateOf(false) }
    val selecting = !v.selection.collapsed
    val line = NoteLines.lineAt(v.text, v.selection.min.coerceIn(0, v.text.length))
    // Ogni tocco legge il valore di adesso, non quello di quando la barra si è disegnata. Dal menu "/" il valore di
    // adesso è quello senza la "/" scritta, che il campo non ha ancora ricevuto ([slashBase]).
    var slashBase: TextFieldValue? = null
    fun kind(k: NoteLines.Kind) {
        haptic(Haptic.TICK)
        val now = slashBase ?: target.value()
        val e = NoteLines.renumber(NoteLines.setKind(now.text, now.selection.min, now.selection.max, k))
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    fun indent(by: Int) {
        haptic(Haptic.TICK)
        val now = target.value()
        val e = NoteLines.indent(now.text, now.selection.min, now.selection.max, by)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    fun mark(m: String) {
        haptic(Haptic.TICK)
        val now = target.value()
        val e = NoteFormat.toggle(now.text, now.selection.min, now.selection.max, m)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val clipboard = LocalClipboardManager.current
    val cursor = v.selection.end.coerceIn(0, v.text.length)
    val slash = if (selecting) null else slashQuery(v.text, cursor)
    val mention = if (selecting || slash != null) null else mentionQuery(v.text, cursor)
    val linkHere = if (selecting) null else NoteFormat.linkAt(v.text, cursor)
    fun openLink(url: String, label: String) {
        haptic(Haptic.TICK)
        openNoteLink(inApp, url, label)
    }
    // Una data scritta nella riga del cursore (1.588): "venerdì alle 15 dentista" → il tasto per metterla nel Calendario.
    val lineText = v.text.substring(line.start, line.end)
    val dated = remember(lineText) {
        if (line.isBlock || NoteFormat.linkAt(lineText, 0) != null || lineText.contains("](")) null
        else NoteLines.plainLine(lineText).takeIf { it.length >= 4 }?.let { p -> QuickParse.parse(p).due?.let { p to it } }
    }
    fun toCalendar() {
        val (plain, due) = dated ?: return
        haptic(Haptic.CONFIRM)
        val task = NotesStore.addTask(plain) ?: return
        val now = target.value()
        val l = NoteLines.lineAt(now.text, now.selection.end)
        val e = NoteFormat.mentionLink(now.text, l.contentStart, l.end, plain, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.TASK, task.id))
        val t = e.text.let { if (it.getOrNull(e.start - 1) == ' ') it.removeRange(e.start - 1, e.start) else it }
        target.set(TextFieldValue(t, TextRange((e.start - 1).coerceIn(0, t.length))))
        CaptureFeedback.tell(context, "Nel Calendario: " + dueLabel(due), Icons.Outlined.Event)
    }
    fun link() {
        haptic(Haptic.TICK)
        val now = target.value()
        val there = NoteFormat.linkAt(now.text, now.selection.min)
        val copied = clipboard.getText()?.text?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (there == null && copied == null) {
            CaptureFeedback.tell(context, "Copia prima il link, poi tocca di nuovo")
            return
        }
        val e = NoteFormat.link(now.text, now.selection.min, now.selection.max, copied)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    fun divider() {
        haptic(Haptic.TICK)
        val now = slashBase ?: target.value()
        val e = NoteLines.insertDivider(now.text, now.selection.end)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        when {
            mention != null -> {
                // "@" (1.584, tutta l'app dalla 1.588): note, promemoria e cose da fare, abitudini, pacchi, documenti,
                // sezioni. Diventa un link che si apre con un tocco.
                val data by NotesStore.data.collectAsStateWithLifecycle()
                val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
                val hits = remember(data, docs, mention.second) { mentionTargets(data, docs, mention.second) }
                if (hits.isEmpty()) BarLabel(Icons.Outlined.Close, if (mention.second.isEmpty()) "Scrivi un nome" else "Niente con \"${mention.second}\"") {}
                hits.forEach { h ->
                    BarLabel(h.icon, h.title.take(28)) {
                        haptic(Haptic.TICK)
                        val now = target.value()
                        val m = mentionQuery(now.text, now.selection.end) ?: return@BarLabel
                        val e = NoteFormat.mentionLink(now.text, m.first, now.selection.end, h.title, h.url)
                        target.set(TextFieldValue(e.text, TextRange(e.start)))
                    }
                }
            }
            selecting || formats -> {
                val active = NoteFormat.active(v.text, v.selection.min, v.selection.max)
                BarIcon(Icons.Outlined.FormatBold, "Grassetto", on = NoteFormat.Kind.BOLD in active) { mark(NoteFormat.BOLD) }
                BarIcon(Icons.Outlined.FormatItalic, "Corsivo", on = NoteFormat.Kind.ITALIC in active) { mark(NoteFormat.ITALIC) }
                BarIcon(Icons.Outlined.StrikethroughS, "Barrato", on = NoteFormat.Kind.STRIKE in active) { mark(NoteFormat.STRIKE) }
                BarIcon(Icons.Outlined.BorderColor, "Evidenzia", on = NoteFormat.Kind.HIGHLIGHT in active) { mark(NoteFormat.HIGHLIGHT) }
                BarIcon(Icons.Outlined.Link, "Link (copia prima l'indirizzo)", on = NoteFormat.Kind.LINK in active) { link() }
                BarSeparator()
                BarIcon(Icons.Outlined.Title, "Titolo", on = line.kind == NoteLines.Kind.HEADING) { kind(NoteLines.Kind.HEADING) }
                BarIcon(Icons.Outlined.FormatQuote, "Citazione", on = line.kind == NoteLines.Kind.QUOTE) { kind(NoteLines.Kind.QUOTE) }
                BarIcon(Icons.Outlined.Lightbulb, "Riquadro con emoji", on = line.kind == NoteLines.Kind.CALLOUT) { kind(NoteLines.Kind.CALLOUT) }
                BarIcon(Icons.Outlined.HorizontalRule, "Linea") { divider() }
                BarSeparator()
                BarIcon(Icons.Outlined.Close, "Torna a punti e spunte") {
                    haptic(Haptic.TICK)
                    formats = false
                    if (selecting) target.set(v.copy(selection = TextRange(v.selection.max)))
                }
            }
            else -> {
                // Una data nella riga: per primo il tasto che la mette nel Calendario (1.588).
                dated?.let { (_, due) ->
                    BarLabel(Icons.Outlined.Event, dueLabel(due)) { toCalendar() }
                    BarSeparator()
                }
                // Il cursore su un link: per primo il tasto che lo apre.
                linkHere?.let { (url, label) ->
                    BarIcon(Icons.AutoMirrored.Outlined.OpenInNew, "Apri il link", on = true) { openLink(url, label) }
                    BarSeparator()
                }
                BarIcon(Icons.AutoMirrored.Outlined.Undo, "Annulla", enabled = target.canUndo()) { haptic(Haptic.TICK); target.undo() }
                BarIcon(Icons.AutoMirrored.Outlined.Redo, "Ripeti", enabled = target.canRedo()) { haptic(Haptic.TICK); target.redo() }
                BarSeparator()
                BarIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Elenco puntato", on = line.kind == NoteLines.Kind.BULLET) { kind(NoteLines.Kind.BULLET) }
                BarIcon(Icons.Outlined.FormatListNumbered, "Elenco numerato", on = line.kind == NoteLines.Kind.NUMBER) { kind(NoteLines.Kind.NUMBER) }
                BarIcon(Icons.Outlined.RadioButtonUnchecked, "Voce da spuntare", on = line.kind == NoteLines.Kind.CHECK) { kind(NoteLines.Kind.CHECK) }
                BarIcon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Sotto-voce", enabled = NoteLines.canIndent(v.text, v.selection.min, 1)) { indent(1) }
                BarIcon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Su di un livello", enabled = NoteLines.canIndent(v.text, v.selection.min, -1)) { indent(-1) }
                BarSeparator()
                BarIcon(Icons.Outlined.TextFormat, "Formati, citazione, riquadro, linea, link") {
                    haptic(Haptic.TICK)
                    formats = true
                }
            }
        }
    }
}

/** "/" scritto a inizio riga e quello che segue (minuscolo), se il cursore sta lì; null = niente menu. */
internal fun slashQuery(text: String, cursor: Int): String? {
    val l = NoteLines.lineAt(text, cursor)
    if (l.kind != NoteLines.Kind.TEXT || cursor < l.contentStart + 1) return null
    val typed = text.substring(l.contentStart, cursor)
    if (!typed.startsWith("/") || typed.length > 14 || typed.any { it.isWhitespace() }) return null
    return typed.drop(1).lowercase()
}

/** "@" a inizio parola e quello che segue fino al cursore: (dove sta la @, cosa si cerca); null = niente. */
internal fun mentionQuery(text: String, cursor: Int): Pair<Int, String>? {
    if (cursor <= 0) return null
    val at = text.lastIndexOf('@', cursor - 1)
    if (at < 0 || (at > 0 && !text[at - 1].isWhitespace())) return null
    val typed = text.substring(at + 1, cursor)
    if (typed.length > 24 || typed.any { it == '\n' } || typed.count { it == ' ' } > 2) return null
    return at to typed
}

/** Un tasto con l'icona e il nome: per le scelte del menu "/" e delle note con "@". */
@Composable
private fun BarLabel(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 42.dp)
            .bouncyPress(0.92f)
            .clip(RoundedCornerShape(21.dp))
            .background(colors.card)
            .border(cardBorder(), RoundedCornerShape(21.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1)
    }
}

/** Un tasto della barra: tondo, solo l'icona. Acceso = blu come tutte le azioni, spento = sbiadito e fermo. */
@Composable
private fun BarIcon(icon: ImageVector, description: String, on: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        on -> colors.onPrimary
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .then(if (enabled) Modifier.bouncyPress(0.9f) else Modifier)
            .clip(CircleShape)
            .background(if (on) colors.primary else colors.card)
            .border(cardBorder(), CircleShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

/** La linea sottile fra un gruppo di tasti e l'altro. */
@Composable
private fun BarSeparator() {
    Box(Modifier.padding(horizontal = 2.dp).width(1.dp).heightIn(min = 26.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/**
 * Il campo "Note" di promemoria e date (ItemDetail): come [DetailFieldRow] ma con le righe e la barra. Salva da solo
 * poco dopo l'ultima modifica.
 */
@Composable
internal fun DetailRichRow(key: Any, icon: ImageVector, value: String, placeholder: String, onCommit: (String) -> Unit) {
    var field by androidx.compose.runtime.saveable.rememberSaveable(key, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    val commit by rememberUpdatedState(onCommit)
    androidx.compose.runtime.LaunchedEffect(field.text) {
        kotlinx.coroutines.delay(600)
        if (field.text.trim() != value.trim()) commit(field.text.trim())
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp).size(20.dp))
        Spacer(Modifier.width(14.dp))
        RichNoteField(
            value = field,
            onValueChange = { field = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            placeholder = placeholder,
            sendToTodo = true,
            modifier = Modifier.weight(1f),
        )
    }
}

/** La barra di un vocale nella nota (1.589): play/pausa all'inizio, l'avanzamento che si tocca, la durata in fondo. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AudioPill(path: String, label: String, modifier: Modifier, onLongPress: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val playing by NoteAudio.playing.collectAsStateWithLifecycle()
    val paused by NoteAudio.paused.collectAsStateWithLifecycle()
    val mine = playing == path
    val running = mine && !paused
    var progress by remember { mutableFloatStateOf(0f) }
    val total = remember(path) { NoteAudio.length(path) }
    LaunchedEffect(mine, paused) {
        if (!mine) { progress = 0f; return@LaunchedEffect }
        progress = NoteAudio.progress(path)
        while (running) { progress = NoteAudio.progress(path); kotlinx.coroutines.delay(100) }
    }
    // Senza il file (nota da un altro telefono, file tolto) resta la durata scritta nella riga.
    val written = Regex("""\d+:\d{2}""").find(label)?.value
    val shownTime = if (mine && total > 0) NoteAudio.duration((total * progress).toLong().coerceAtLeast(1000)) else if (total > 0) NoteAudio.duration(total) else written ?: ""
    Row(
        modifier = modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.card)
            .border(cardBorder(), RoundedCornerShape(50))
            .combinedClickable(onClick = { NoteAudio.toggle(path) }, onLongClick = onLongPress)
            .padding(start = 4.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .padding(3.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (running) "Pausa" else "Ascolta",
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(path) {
                    detectTapGestures { o -> NoteAudio.seek(path, o.x / size.width) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(colors.onSurface.copy(alpha = 0.12f)))
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).clip(RoundedCornerShape(50)).background(colors.primary))
        }
        Text(shownTime, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
    }
}
