package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.West
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.RemoveDone
import com.platinum20.dashboard.notes.NoteLists
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.composed
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.launch
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
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.SideEffect
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
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.capture.CaptureFeedback
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.platinum20.dashboard.notes.NoteHistory
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.FormatQuote
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
        /** Il + della barra (2026-11-24, proposta 1): il fumetto Aggiungi di chi usa il campo. Null = i blocchi del menu "/". */
        val add: (() -> Unit)? = null,
        /** Una foto nel testo che va in cima alla nota (proposta 6): solo l'editor della nota lo sa fare. */
        val photoToTop: ((String) -> Unit)? = null,
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

/** Il posto di cerchio, pallino e barra della citazione: il rientro di ogni livello prima del testo. */
private val INDENT = 30.sp

/** Quanto è largo lo spazio sottile al posto del segno ([NoteLines.PLACE]) nel testo delle note. */
private val THIN_SPACE = 3.sp

/** La maniglia ⋮⋮ nel margine a sinistra del testo (2026-11-24). */
private val HANDLE_WIDTH = 22.dp

/** Dove va una riga trascinata: su, giù, dentro (sotto-voce), fuori (su di un livello). */
internal enum class LineDrag { NONE, UP, DOWN, IN, OUT }

/**
 * Pura (2026-11-24, proposta 4): il dito spostato di [x] e [y] da quando la riga si è mossa l'ultima volta. Di lato conta
 * solo se il gesto è chiaramente di lato (più di metà rientro e una volta e mezza più che in verticale).
 */
internal fun lineDragStep(x: Float, y: Float, rowPx: Float, indentPx: Float): LineDrag = when {
    kotlin.math.abs(x) > indentPx * 0.8f && kotlin.math.abs(x) > kotlin.math.abs(y) * 1.5f -> if (x > 0) LineDrag.IN else LineDrag.OUT
    y > rowPx * 0.7f -> LineDrag.DOWN
    y < -rowPx * 0.7f -> LineDrag.UP
    else -> LineDrag.NONE
}

/** Il posto del cerchio (19 dp) e del pallino, allineato al bordo del testo. */
private val MARK_SLOT = 20.dp

/** Quanto è alto il carattere invisibile in cima a ogni riga (1.589): l'aria dopo un Invio. */
private val BREATH_SIZE = 25.sp

/** L'ultima riga della tabella mentre ci si scrive (1.590): la riga più lo spazio per il + sotto. */
private val TALL_SIZE = 42.sp

/** L'aria sopra un titoletto e sotto un riquadro o una tabella (2026-11-26): il carattere invisibile è più grande. */
private val HEADING_AIR = 34.sp
private val TABLE_AIR = 8.dp
private val BLOCK_AIR = 8.dp
private val AFTER_BOX_AIR = 34.sp

/**
 * Dove comincia il testo vero di una riga del campo, sotto l'aria (il carattere invisibile più grande): la linea di
 * base meno l'altezza di una riga di testo normale (16 sp circa, come nel resto dell'editor).
 */
internal fun textTopPx(l: androidx.compose.ui.text.TextLayoutResult, row: Int, density: androidx.compose.ui.unit.Density): Float {
    val font = l.layoutInput.style.fontSize.let { if (it.isSp) it else 16.sp }
    val ascent = with(density) { font.toPx() } * 1.0f
    return (l.getLineBaseline(row) - ascent).coerceIn(l.getLineTop(row), l.getLineBottom(row))
}

/** Quanto è grande il carattere dell'aria in cima a una riga di testo: dipende da cosa è e da cosa c'è sopra. */
internal fun lineAir(line: NoteLines.Line, above: NoteLines.Line?): androidx.compose.ui.unit.TextUnit {
    // Sotto ogni pezzo inserito (riquadro, tabella, colonne, bacheca, vocale, link…) e sopra un riquadro: la stessa aria.
    val afterBox = above != null && (above.kind == NoteLines.Kind.CALLOUT || above.isBlock)
    val boxHere = above != null && line.kind == NoteLines.Kind.CALLOUT
    val heading = line.kind == NoteLines.Kind.HEADING && line.index > 0
    return when {
        heading -> HEADING_AIR
        afterBox || boxHere -> AFTER_BOX_AIR
        else -> BREATH_SIZE
    }
}

/** Lo spazio dell'icona davanti a un link "@" (1.596) e l'aria in fondo alla bolla. */
private val MENTION_ICON_SPACE = 17.sp
private val MENTION_END_SPACE = 5.sp

/** Una riga che scivola (proposta 2): la riga dove sta ora, l'alto e l'altezza di dove stava, in px del campo. */
private class Glide(val to: Int, val fromTop: Float, val height: Float)

/** Gli ultimi due disegni del testo, ognuno col suo testo: lo spostamento di una riga parte da quello di prima. */
private class LaidOut {
    var curText: String? = null
    var curLayout: TextLayoutResult? = null
    var prevText: String? = null
    var prevLayout: TextLayoutResult? = null
    fun push(text: String, l: TextLayoutResult) {
        if (text != curText) { prevText = curText; prevLayout = curLayout }
        curText = text
        curLayout = l
    }
}

/**
 * Fin dove è barrata una voce da spuntare (2026-11-24, proposta 1): tutta se fatta, niente se no. La voce appena toccata
 * si barra man mano (da sinistra) quando diventa fatta, e si sbarra da destra quando la spunta si toglie. Null = niente.
 */
internal fun sweepEnd(line: NoteLines.Line, sweep: Triple<Int, Boolean, Float>?): Int? {
    val len = line.end - line.contentStart
    val s = sweep?.takeIf { it.first == line.start && it.second == line.done }
    return when {
        s == null -> if (line.done) line.end else null
        s.second -> line.contentStart + (len * s.third.coerceIn(0f, 1f)).toInt()
        else -> line.contentStart + (len * (1f - s.third.coerceIn(0f, 1f))).toInt()
    }
}

/** Il testo come lo disegna l'editor: segni nascosti, righe chiuse, aria fra le righe, posti dei link "@". */
internal fun editorShown(text: String, folds: List<IntRange>) =
    NoteLines.Shown(
        text,
        NoteFormat.hidden(text) + NoteFormat.mentionEmojis(text).map { it.first } + folds,
        breathe = true,
        slots = NoteFormat.mentionSlots(text),
    )

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
    /**
     * Chi scorre la pagina (1.594): riceve la riga del cursore (alto e basso, sullo schermo) a ogni cambio e quando la
     * tastiera si apre, e la tiene sopra la tastiera. Senza, il campo chiede da solo di restare in vista.
     */
    keepInView: ((top: Float, bottom: Float, mode: LineFollow) -> Unit)? = null,
    /** Cresce a ogni tocco sotto l'ultima riga (fuori dal campo): conta come un tocco sul testo, la riga va in cima. */
    tappedBelow: Int = 0,
    /** La nota di questo campo (2026-11-23): l'evento messo in calendario da una data la richiama con "@". */
    noteId: Long? = null,
    /**
     * L'aria sopra la prima riga (2026-11-23). Nelle righe dentro una card (Appunti, note di un evento) va tolta: se no
     * il testo sta più in basso dell'icona e la card sembra più alta delle altre righe.
     */
    firstAir: Boolean = true,
    /** Le scelte in più del fumetto di una riga che sa fare solo l'editor della nota (Ricordamelo, 2026-11-24). */
    lineMore: ((text: String, cursor: Int) -> List<BubbleAction>)? = null,
    /** Il + della barra sopra la tastiera (2026-11-24): lo stesso fumetto del + in basso. */
    onAdd: (() -> Unit)? = null,
    /** "Metti in cima" sul fumetto di una foto nel testo (2026-11-24). */
    onPhotoToTop: ((String) -> Unit)? = null,
    /** La maniglia ⋮⋮ a sinistra della riga del cursore (2026-11-24, proposta 5): serve il margine a sinistra del campo. */
    lineHandle: Boolean = false,
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
    // Il tocco dopo la fine di una riga (2026-11-26): dove il campo metterebbe il cursore e dove va davvero.
    var tapEnd by remember { mutableStateOf<TapEnd?>(null) }
    // Le righe che si chiudono (1.588): i titoli chiusi, per testo del titolo; e la tabella che si sta cambiando.
    var folded by remember { mutableStateOf(setOf<String>()) }
    // Le righe nascoste: sotto i titoli chiusi e, dalla 1.590, le spunte di una bacheca (si vedono come schede).
    val folds = remember(value.text, folded) { NoteLines.foldRanges(value.text, folded) + NoteLines.boardRanges(value.text) }
    // Quanto deve essere alta la riga nascosta sotto due colonne, sotto-nota, anteprima, bacheca (1.590): la misura il
    // pezzo disegnato sopra, la chiave è il testo della riga.
    var blockSp by remember { mutableStateOf(mapOf<String, Float>()) }
    // [extra] = gli sp in più che la riga ha comunque (l'ultima riga della tabella col + sotto): non si contano.
    fun fit(key: String, linePx: Float, needPx: Int, extra: Float = 0f) {
        if (needPx <= 0 || linePx <= 0f) return
        val cur = blockSp[key] ?: BREATH_SIZE.value
        val own = linePx * cur / (cur + extra)
        if (kotlin.math.abs(needPx - own) < 3f) return
        val next = (needPx * (cur + extra) / linePx).coerceIn(8f, 4000f)
        if (kotlin.math.abs(next - cur) > 0.3f) blockSp = blockSp.filterKeys { it in value.text } + (key to next)
    }
    var boardDialog by remember { mutableStateOf<Triple<Int, Int, String>?>(null) }
    val notesData by NotesStore.data.collectAsStateWithLifecycle()
    val previews by com.platinum20.dashboard.notes.LinkPreview.cards.collectAsStateWithLifecycle()
    val fieldClipboard = LocalClipboardManager.current
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

    // La spunta che si disegna (2026-11-24, proposta 1): la voce toccata (dove comincia), se diventa fatta, e il barrato
    // che scorre da sinistra a destra (al contrario quando si toglie).
    var tickAt by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val tickRun = remember { androidx.compose.animation.core.Animatable(1f) }
    // La voce nuova (Invio su una voce, o una riga che diventa voce): il suo segno cresce invece di comparire.
    var grownAt by remember { mutableStateOf<Int?>(null) }
    // Mentre scrivi la maniglia ⋮⋮ sta ferma, nascosta: torna quando ti fermi mezzo secondo (proposta 4).
    var still by remember { mutableStateOf(true) }
    LaunchedEffect(value.text) {
        still = false
        kotlinx.coroutines.delay(CALM_MS)
        still = true
    }
    /** [typed] = l'ha cambiato la tastiera; falso = un tasto della barra, che sa già cosa fa. */
    fun change(v: TextFieldValue, typed: Boolean = true) {
        val old = current
        var next = v
        // Toccando dopo il testo di una riga il campo metteva il cursore all'inizio della riga dopo: va in fondo a quella toccata.
        val end = tapEnd
        if (end != null && typed && v.text == old.text && v.selection.collapsed && v.selection.start == end.raw &&
            android.os.SystemClock.uptimeMillis() - end.at <= TAP_WINDOW_MS) {
            next = v.copy(selection = TextRange(end.goal.coerceIn(0, v.text.length)))
        }
        // Un link "@" si cancella tutto insieme (1.589): basta togliere una lettera.
        // Tabella, linea e vocale sono pezzi unici (1.589): la tastiera non li rompe.
        val guard = if (typed && v.text != old.text) NoteLines.guardBlocks(old.text, v.text) else null
        val mention = if (guard == null && typed && v.selection.collapsed) NoteFormat.deleteMention(old.text, v.text, v.selection.end) else null
        if (guard != null) next = TextFieldValue(guard.text, TextRange(guard.start))
        else if (mention != null) next = TextFieldValue(mention.text, TextRange(mention.start))
        else if (typed && v.text != old.text && v.selection.collapsed) {
            // Un indirizzo da solo sulla riga e Invio: diventa la sua anteprima (1.590).
            val bookmark = NoteLines.autoBookmark(old.text, v.text, v.selection.end)
            if (bookmark != null) next = TextFieldValue(bookmark.text, TextRange(bookmark.start))
            else NoteLines.afterEdit(old.text, v.text, v.selection.end)?.let { next = TextFieldValue(it.text, TextRange(it.start, it.end)) }
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
        if (safe.text != old.text && safe.selection.collapsed) {
            val was = NoteLines.lineAt(old.text, old.selection.end.coerceIn(0, old.text.length))
            val now = NoteLines.lineAt(safe.text, safe.selection.end.coerceIn(0, safe.text.length))
            if (now.isList && (!was.isList || safe.text.count { it == '\n' } > old.text.count { it == '\n' })) grownAt = now.start
        }
        emit(safe)
    }

    /**
     * Il fumetto di una riga (tieni premuto sul segno, o tocco sulla maniglia). Dopo Su, Giù, rientro o tipo si rifà sulla
     * stessa riga (2026-11-26): prima restava quello di prima e il secondo tocco ripartiva dal testo vecchio (Su due
     * volte = su di una sola). Le modifiche passano da [change]: Annulla le conosce e i numeri si rifanno.
     */
    fun lineBubble(v: TextFieldValue, l: NoteLines.Line) {
        val apply: (TextFieldValue) -> Unit = { change(it, typed = false) }
        val again: (TextFieldValue, Int) -> Unit = { nv, i -> NoteLines.parse(nv.text).getOrNull(i)?.let { lineBubble(nv, it) } }
        ActionBubble.show(lineHead(l, v.text), *(lineActions(context, l, v, apply, sendToTodo, again) + lineMore?.invoke(v.text, l.end).orEmpty()).toTypedArray())
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
            add = onAdd,
            photoToTop = onPhotoToTop,
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
    // Le caselle della tabella vanno a capo (2026-11-26): si misura il testo per fare la riga alta quanto serve.
    val cellMeasurer = rememberTextMeasurer()
    val cellStyle = MaterialTheme.typography.bodyMedium
    // Mentre si scrive in una tabella la sua ultima riga è più alta: sotto ci sta il + per una riga nuova (1.590).
    val tallAt = activeTable?.let { NoteTable.blockAt(value.text, it) }?.let { NoteLines.parse(value.text)[it.last].start }
    // Le parti intelligenti (2026-11-23): #etichette in blu, date e codici di spedizione sottolineati, codici in una bolla.
    val smart = remember(value.text) { com.platinum20.dashboard.notes.NoteSmart.find(value.text, com.platinum20.dashboard.notes.NoteFormat.linkRanges(value.text)) }
    val smartStyles = remember(smart, accent) {
        smart.map { sm ->
            sm.range to when (sm.kind) {
                com.platinum20.dashboard.notes.NoteSmart.Kind.TAG -> SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)
                com.platinum20.dashboard.notes.NoteSmart.Kind.CODE -> SpanStyle(color = accent, background = accent.copy(alpha = 0.12f), fontWeight = FontWeight.SemiBold)
                else -> SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            }
        }
    }
    val sweep = tickAt?.let { Triple(it.first, it.second, tickRun.value) }
    val transformation = remember(faded, value.text, found, findAt, accent, folds, tallAt, blockSp, smartStyles, firstAir, sweep) {
        VisualTransformation { input ->
            richText(input.text, faded, smartStyles + found.mapIndexed { i, r -> r to if (i == findAt) foundNow else foundStyle }, accent, marker = marker, folds = folds, tall = tallAt, sizes = blockSp, firstAir = firstAir, sweep = sweep)
        }
    }
    fun hiddenLine(line: NoteLines.Line) = folds.any { line.start in it }

    // Il cursore resta sempre in vista sopra la barra e la tastiera, anche quando Invio fa una riga nuova (1.562):
    // da solo il campo lo porta in vista prima che la riga nuova esista, e finiva sotto la tastiera.
    val follow = remember { BringIntoViewRequester() }
    val room = with(LocalDensity.current) { 32.dp.toPx() }
    // Scrivendo (1.591): la riga sale in alto, sotto c'è lo spazio vuoto della pagina, mai sotto la tastiera.
    var followedText by remember { mutableStateOf(value.text) }
    var fieldAt by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val keep by rememberUpdatedState(keepInView)
    // La tastiera che si apre (o cresce) rifà il conto: la riga del cursore non resta sotto.
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    // Il tocco sul testo (1.606): la riga toccata va in cima. Resta così anche mentre la tastiera si apre, finché non si scrive.
    var tapAt by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }
    var followedSelection by remember { mutableStateOf(value.selection) }
    var atTop by remember { mutableStateOf(false) }
    // Il tocco lo dice anche il campo stesso (premuto e lasciato): con la tastiera già aperta il dito può non arrivare qui.
    val fieldTouch = remember { MutableInteractionSource() }
    LaunchedEffect(fieldTouch) {
        fieldTouch.interactions.collect {
            if (it is androidx.compose.foundation.interaction.PressInteraction.Release) { tapAt = android.os.SystemClock.uptimeMillis(); tapCount++ }
        }
    }
    var seenBelow by remember { mutableStateOf(tappedBelow) }
    LaunchedEffect(value.text, value.selection, focused, imeBottom, tapCount, tappedBelow) {
        val below = tappedBelow != seenBelow
        if (below) { seenBelow = tappedBelow; tapAt = android.os.SystemClock.uptimeMillis() }
        if (!focused || readOnly) return@LaunchedEffect
        val typing = value.text != followedText && !below
        val moved = value.selection != followedSelection || below
        followedText = value.text
        followedSelection = value.selection
        val sinceTap = android.os.SystemClock.uptimeMillis() - tapAt
        atTop = followMode(typing, moved, value.selection.collapsed, sinceTap, atTop)
        // Il cursore spostato dal tocco usa il tocco: un altro spostamento dopo (maniglia, frecce) non porta più in cima.
        if (typing || (moved && sinceTap <= TAP_WINDOW_MS)) tapAt = 0L
        withFrameNanos { }
        val l = layout ?: return@LaunchedEffect
        val at = transformation.filter(AnnotatedString(value.text)).offsetMapping.originalToTransformed(value.selection.end)
        if (at > l.layoutInput.text.length) return@LaunchedEffect
        val cursor = l.getCursorRect(at)
        val pager = keep
        val where = fieldAt
        if (pager != null && where != null && where.isAttached) {
            val top = where.localToRoot(androidx.compose.ui.geometry.Offset(0f, cursor.top)).y
            pager(top, top + cursor.height, if (atTop) LineFollow.TAPPED else if (typing) LineFollow.TYPING else LineFollow.MOVE)
            // Il campo, dopo il tocco, chiede da solo di mostrare il cursore e quello ferma lo scorrimento appena partito:
            // poco dopo si riguarda dov'è la riga e, se non è ancora in cima, si finisce il lavoro.
            if (atTop) repeat(2) {
                kotlinx.coroutines.delay(220)
                if (!where.isAttached) return@LaunchedEffect
                val again = where.localToRoot(androidx.compose.ui.geometry.Offset(0f, cursor.top)).y
                keep?.invoke(again, again + cursor.height, LineFollow.TAPPED)
            }
            return@LaunchedEffect
        }
        // Un rettangolo più alto dello schermo: la pagina scorre finché la riga sta in alto, con un po' di righe sopra.
        if (typing) follow.bringIntoView(Rect(cursor.left, cursor.top - room * 3, cursor.right, cursor.top + room * 200))
        else follow.bringIntoView(Rect(cursor.left, cursor.top - room / 2, cursor.right, cursor.bottom + room))
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
    var dragPullX by remember { mutableFloatStateOf(0f) }
    var dragEndedAt by remember { mutableLongStateOf(0L) }
    val motionScope = androidx.compose.runtime.rememberCoroutineScope()
    // Le righe che scivolano (proposta 2): dove stavano (in px nel campo) e dove stanno ora, con una banda che le segue.
    val glideRun = remember { androidx.compose.animation.core.Animatable(1f) }
    var glides by remember { mutableStateOf<List<Glide>>(emptyList()) }
    val laidOut = remember { LaidOut() }
    LaunchedEffect(value.text) {
        // Il disegno di prima: se quello nuovo è già arrivato è il penultimo, se no l'ultimo.
        val fresh = laidOut.curText == value.text
        val before = if (fresh) laidOut.prevText else laidOut.curText
        val oldLayout = if (fresh) laidOut.prevLayout else laidOut.curLayout
        if (before == null || oldLayout == null || dragging != null) return@LaunchedEffect
        val moves = NoteLines.moved(before, value.text)
        if (moves.isEmpty()) return@LaunchedEffect
        val oldShown = editorShown(before, NoteLines.foldRanges(before, folded) + NoteLines.boardRanges(before))
        val oldLines = NoteLines.parse(before)
        val drawnOld = oldLayout.layoutInput.text.length
        glides = moves.mapNotNull { (from, to) ->
            val line = oldLines.getOrNull(from) ?: return@mapNotNull null
            val at = oldShown.toShown(line.contentStart).coerceAtMost(drawnOld)
            val row = oldLayout.getLineForOffset(at)
            Glide(to, oldLayout.getLineTop(row), oldLayout.getLineBottom(row) - oldLayout.getLineTop(row))
        }
        glideRun.snapTo(0f)
        glideRun.animateTo(1f, Motion.settle())
        glides = emptyList()
    }

    boardDialog?.let { (board, item, start) ->
        BoardTextDialog(
            title = if (item < 0) "Nuova scheda" else "Cambia la scheda",
            start = start,
            onDone = { t ->
                boardDialog = null
                val now = current.text
                val next = if (item < 0) NoteLines.boardAdd(now, board, t) else NoteLines.setContent(now, item, t)
                change(TextFieldValue(next, current.selection), typed = false)
            },
            onDismiss = { boardDialog = null },
        )
    }
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
            val shown = editorShown(text, folds)
            val drawn = l.layoutInput.text.length
            val density = LocalDensity.current
            shown.lines.filter { it.kind == NoteLines.Kind.CALLOUT && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line ->
                val first = l.getLineForOffset(shown.toShown(line.start))
                val last = l.getLineForOffset(shown.toShown(line.end).coerceAtMost(drawn))
                // Dal testo, non dall'aria sopra la riga (2026-11-26): il riquadro ha lo stesso margine sopra e sotto.
                val pad = with(density) { 7.dp.toPx() }
                val top = (textTopPx(l, first, density) - pad).coerceAtLeast(l.getLineTop(first))
                val h = with(density) { (l.getLineBottom(last) + pad - top).toDp() }
                Box(
                    Modifier
                        .offset { IntOffset(0, top.toInt()) }
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.glassCard),
                )
            }
            // Le righe spostate scivolano (proposta 2): una banda leggera parte da dove stava la riga e si posa dove sta ora.
            if (glides.isNotEmpty()) {
                val p = glideRun.value
                glides.forEach { g ->
                    val line = shown.lines.getOrNull(g.to) ?: return@forEach
                    val row = l.getLineForOffset(shown.toShown(line.contentStart).coerceAtMost(drawn))
                    val y = g.fromTop + (l.getLineTop(row) - g.fromTop) * p
                    Box(
                        Modifier
                            .offset { IntOffset(0, y.toInt()) }
                            .fillMaxWidth()
                            .height(with(density) { g.height.toDp() })
                            .graphicsLayer { alpha = (1f - p).coerceIn(0f, 1f) }
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary.copy(alpha = 0.16f)),
                    )
                }
            }
            // Le bolle dei link "@" (1.589), sotto il testo: una per riga se il link va a capo.
            val labelEmojis = NoteFormat.mentionEmojis(text).associate { it.first.first - 1 to it.second }
            NoteFormat.mentions(text).filter { r -> folds.none { r.first in it } && !NoteLines.lineAt(text, r.first).isBlock }.forEach { r ->
                // Il posto dell'icona (1.597): anche a inizio riga, dove prima c'è il carattere dell'aria.
                val slot = shown.slotAt(r.first)
                val s0 = slot ?: shown.toShown(r.first)
                val e0 = shown.toShown(r.last + 1).coerceAtMost(drawn)
                if (s0 >= e0) return@forEach
                val firstRow = l.getLineForOffset(s0)
                val lastRow = l.getLineForOffset((e0 - 1).coerceAtLeast(s0))
                // Le bolle hanno già l'aria dentro (i posti dell'icona e della fine): fra due bolle vicine resta uno spiraglio.
                val pad = with(density) { (-1).dp.toPx() }
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
                    // L'icona di cosa è (1.596): nota (o la sua emoji), promemoria, abitudine, pacco, documento, sezione.
                    if (row == firstRow && slot != null && shown.shown.getOrNull(s0)?.toString() == NoteFormat.MENTION_OPEN && s0 + 1 <= drawn &&
                        l.getLineForOffset(s0 + 1) == row) {
                        val url = NoteFormat.linkAt(text, r.first)?.first
                        // Una sola icona: l'emoji della nota, se no quella scritta nel nome, se no l'icona del tipo.
                        val mark = url?.let { mentionMark(it, notesData) }?.let { m -> if (m.emoji == null && labelEmojis[r.first] != null) MentionMark(m.icon, labelEmojis[r.first]) else m }
                        val a = l.getHorizontalPosition(s0, true)
                        val size = with(density) { fontPx.toDp() }
                        // Sempre allo stesso posto: dal bordo della bolla, un filo d'aria, poi l'icona.
                        val cx = a + with(density) { 3.dp.toPx() } + fontPx / 2f
                        val cy = base - fontPx * 0.36f
                        val half = with(density) { size.toPx() / 2f }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .offset { IntOffset((cx - half).toInt(), (cy - half).toInt()) }
                                .size(size),
                        ) {
                            when {
                                mark?.emoji != null -> Text(mark.emoji, fontSize = with(density) { (fontPx * 0.82f).toSp() }, maxLines = 1)
                                else -> Icon(mark?.icon ?: Icons.AutoMirrored.Outlined.Notes, contentDescription = null, tint = colors.primary, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        /** Dove deve andare il cursore per un tocco in [p] dopo il testo di una riga o sotto l'ultima; null = dove dice il campo. */
        fun tapTarget(p: androidx.compose.ui.geometry.Offset): TapEnd? {
            val l = layout ?: return null
            val text = current.text
            if (text.isEmpty() || l.lineCount == 0) return null
            val map = transformation.filter(AnnotatedString(text)).offsetMapping
            val raw = map.transformedToOriginal(l.getOffsetForPosition(p))
            val last = l.lineCount - 1
            val goal = when {
                p.y > l.getLineBottom(last) -> text.length
                else -> {
                    val row = l.getLineForVerticalPosition(p.y)
                    if (p.x <= l.getLineRight(row)) return null
                    map.transformedToOriginal(rowEnd(l.layoutInput.text, l.getLineEnd(row)))
                }
            }
            return if (goal == raw) null else TapEnd(raw, goal, android.os.SystemClock.uptimeMillis())
        }
        // Il gesto resta lo stesso fra una composizione e l'altra: legge sempre il testo e il disegno di adesso.
        val tapDown by rememberUpdatedState<(androidx.compose.ui.geometry.Offset) -> Unit> { p -> tapEnd = if (readOnly) null else tapTarget(p) }
        BasicTextField(
            value = value,
            onValueChange = { change(it) },
            readOnly = readOnly,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = transformation,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            onTextLayout = { layout = it; laidOut.push(current.text, it) },
            decorationBox = { field ->
                Box {
                    if (value.text.isEmpty()) Text(placeholder, style = textStyle, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    field()
                }
            },
            interactionSource = fieldTouch,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { fieldAt = it }
                .tapWatch(
                    onDown = { p -> tapDown(p) },
                    onTap = { tapAt = android.os.SystemClock.uptimeMillis(); tapCount++ },
                )
                .bringIntoViewRequester(follow)
                .heightIn(min = minHeight)
                .focusRequester(mainFocus)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                },
        )
        /**
         * Trascinare una riga (dal segno o dalla maniglia): su e giù la sposta, di lato la rientra o la riporta su di un
         * livello (2026-11-24). Il fumetto aperto dal tieni premuto si chiude appena il dito si muove.
         */
        fun Modifier.lineDrag(lineOf: () -> NoteLines.Line, rowPx: Float, afterLongPress: Boolean): Modifier = pointerInput(readOnly, afterLongPress, rowPx) {
            if (readOnly) return@pointerInput
            val indentPx = INDENT.toPx()
            val start: (androidx.compose.ui.geometry.Offset) -> Unit = {
                dragging = lineOf().index
                dragMoved = false
                dragPull = 0f
                dragPullX = 0f
            }
            val end: () -> Unit = {
                dragEndedAt = System.currentTimeMillis()
                dragging = null
            }
            val drag: (androidx.compose.ui.input.pointer.PointerInputChange, androidx.compose.ui.geometry.Offset) -> Unit = { pc, amount ->
                pc.consume()
                dragPull += amount.y
                dragPullX += amount.x
                val from = dragging
                if (from != null) {
                    if (!dragMoved && (kotlin.math.abs(dragPull) > rowPx * 0.3f || kotlin.math.abs(dragPullX) > indentPx * 0.4f)) ActionBubble.dismiss()
                    val now = current
                    val lines = NoteLines.parse(now.text)
                    when (lineDragStep(dragPullX, dragPull, rowPx, indentPx)) {
                        LineDrag.DOWN -> if (from < lines.size - 1) {
                            dragPull -= rowPx
                            haptic(Haptic.TICK)
                            dragMoved = true
                            dragging = from + 1
                            change(TextFieldValue(NoteLines.move(now.text, from, up = false), TextRange(NoteLines.moveCursor(now.text, from, false, now.selection.start), NoteLines.moveCursor(now.text, from, false, now.selection.end))), typed = false)
                        }
                        LineDrag.UP -> if (from > 0) {
                            dragPull += rowPx
                            haptic(Haptic.TICK)
                            dragMoved = true
                            dragging = from - 1
                            change(TextFieldValue(NoteLines.move(now.text, from, up = true), TextRange(NoteLines.moveCursor(now.text, from, true, now.selection.start), NoteLines.moveCursor(now.text, from, true, now.selection.end))), typed = false)
                        }
                        LineDrag.IN, LineDrag.OUT -> {
                            val by = if (dragPullX > 0) 1 else -1
                            dragPullX = 0f
                            val at = lines.getOrNull(from)
                            if (at != null && NoteLines.canIndent(now.text, at.start, by)) {
                                haptic(Haptic.TICK)
                                dragMoved = true
                                val e = NoteLines.renumber(NoteLines.indent(now.text, at.start, at.start, by))
                                val delta = e.text.length - now.text.length
                                fun keep(o: Int) = if (o > at.start) (o + delta).coerceIn(at.start, e.text.length) else o
                                change(TextFieldValue(e.text, TextRange(keep(now.selection.start), keep(now.selection.end))), typed = false)
                            }
                        }
                        LineDrag.NONE -> {}
                    }
                }
            }
            if (afterLongPress) detectDragGesturesAfterLongPress(onDragStart = start, onDragEnd = end, onDragCancel = end, onDrag = drag)
            else detectDragGestures(onDragStart = start, onDragEnd = end, onDragCancel = end, onDrag = drag)
        }
        // I cerchi e i pallini, sopra il rientro di ogni voce: si toccano senza aprire la tastiera.
        val l = layout
        if (l != null) {
            // Gli stessi segni nascosti del testo disegnato, se no le posizioni scivolano di riga.
            val text = value.text
            val shown = editorShown(text, folds)
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
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (line.kind == NoteLines.Kind.CHECK) (if (line.done) "Togli la spunta" else "Spunta") else null,
                            // Tieni premuto = il fumetto della voce (1.604). Senza questo, sul telefono il tieni premuto
                            // valeva come tocco: spuntava la voce e il fumetto non si apriva.
                            onLongClickLabel = if (readOnly) null else "Scelte della riga",
                            onLongClick = if (readOnly) null else ({
                                haptic(Haptic.LONG_PRESS)
                                lineBubble(current, lineNow)
                            }),
                            onClick = {
                                // Il tocco che chiude un tieni premuto non spunta.
                                if (System.currentTimeMillis() - dragEndedAt < 400) return@combinedClickable
                                if (line.kind == NoteLines.Kind.CHECK) {
                                    haptic(if (line.done) Haptic.TOGGLE_OFF else Haptic.TOGGLE_ON)
                                    tickAt = line.start to !line.done
                                    motionScope.launch {
                                        tickRun.snapTo(0f)
                                        tickRun.animateTo(1f, Motion.settle())
                                        tickAt = null
                                    }
                                    val now = current
                                    val text = NoteLines.toggle(now.text, line.index)
                                    val delta = text.length - now.text.length
                                    fun move(o: Int) = if (o > line.start) o + delta else o
                                    emit(TextFieldValue(text, TextRange(move(now.selection.start), move(now.selection.end))))
                                    if (!line.done && NoteLines.allDone(text)) onAllDone?.invoke()
                                }
                            },
                        )
                        // Tieni premuto e trascina: su e giù sposta la voce, di lato la rientra (2026-11-24, proposta 4).
                        // Dopo il clic, così il trascinamento riceve il dito per primo: prima il tieni premuto del clic lo
                        // prendeva e la voce non si muoveva.
                        .lineDrag({ lineNow }, rowPx, afterLongPress = true),
                ) {
                    // Il segno nel suo posto largo quanto il cerchio, sul bordo sinistro del rientro.
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.width(MARK_SLOT).fillMaxHeight()) {
                        LineMark(line, lifted = dragging == line.index, ticking = tickAt?.first == line.start, grow = grownAt == line.start)
                    }
                }
            } }
            // La maniglia ⋮⋮ della riga dove sta il cursore (2026-11-24, proposta 5): le righe di testo e i titoli non hanno
            // un segno da tenere premuto. Tocco = il fumetto della riga, trascina = la sposti. Solo su quella riga.
            if (lineHandle && focused && !readOnly && value.selection.collapsed) {
                val handleAlpha by androidx.compose.animation.core.animateFloatAsState(if (still || dragging != null) 1f else 0f, Motion.settle(), label = "maniglia")
                val cur = shown.lines.firstOrNull { value.selection.end in it.start..it.end }
                if (cur != null && (cur.kind == NoteLines.Kind.TEXT || cur.kind == NoteLines.Kind.HEADING) && !hiddenLine(cur) &&
                    cur.end > cur.contentStart && shown.toShown(cur.start) < drawn
                ) androidx.compose.runtime.key("handle") {
                    val row = l.getLineForOffset(shown.toShown(cur.contentStart).coerceAtMost(drawn))
                    val top = l.getLineTop(row)
                    val rowPx = (l.getLineBottom(row) - top).coerceAtLeast(1f)
                    val curNow by rememberUpdatedState(cur)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { IntOffset(-HANDLE_WIDTH.roundToPx(), top.toInt()) }
                            .size(HANDLE_WIDTH, with(density) { rowPx.toDp() })
                            .lineDrag({ curNow }, rowPx, afterLongPress = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = "Scelte della riga",
                            ) {
                                if (System.currentTimeMillis() - dragEndedAt < 400) return@clickable
                                haptic(Haptic.LONG_PRESS)
                                lineBubble(current, curNow)
                            },
                    ) {
                        Icon(
                            Icons.Outlined.DragIndicator, contentDescription = null,
                            tint = if (dragging == cur.index) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(18.dp).graphicsLayer { alpha = handleAlpha },
                        )
                    }
                }
            }
            // Citazione, riquadro e linea (1.584): la barra, l'emoji, la linea. Tieni premuto per le scelte.
            shown.lines.filter { (it.kind == NoteLines.Kind.QUOTE || it.kind == NoteLines.Kind.CALLOUT || it.kind == NoteLines.Kind.DIVIDER) && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line -> androidx.compose.runtime.key("m${line.index}") {
                val at = shown.toShown(line.start)
                val first = l.getLineForOffset(at)
                val last = l.getLineForOffset(shown.toShown(line.end).coerceAtMost(drawn))
                // Barra ed emoji partono dal testo, non dall'aria sopra la riga (2026-11-26).
                val top = if (line.kind == NoteLines.Kind.DIVIDER) l.getLineTop(first) else textTopPx(l, first, density)
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
                                ActionBubble.show(lineHead(lineNow, current.text), *markActions(context, lineNow, current, emit).toTypedArray())
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
            if (!readOnly) NoteFormat.spans(text).filter { (it.kind == NoteFormat.Kind.LINK || it.kind == NoteFormat.Kind.URL) && folds.none { f -> it.start in f } && !NoteLines.lineAt(text, it.start).isBlock }.forEach { span ->
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
                                        BubbleHead("Link", label),
                                        Slot.open { openNoteLink(inApp, url, label) },
                                        act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Modifica il testo", "il cursore va sul link") {
                                            emit(TextFieldValue(current.text, TextRange(span.end)))
                                            focusRequester?.requestFocus()
                                        },
                                        BubbleAction(Icons.Outlined.LinkOff, "Togli", danger = true, slot = BubbleSlot.AWAY) {
                                            val e = NoteFormat.link(current.text, span.start, span.start, null)
                                            emit(TextFieldValue(e.text, TextRange(e.end)))
                                        },
                                    )
                                },
                            ) { haptic(Haptic.TICK); openNoteLink(inApp, url, label) },
                    )
                }
            }
            // Le parti intelligenti si toccano (2026-11-23): etichetta = la sua pagina, data = in calendario, codice = copiato,
            // codice di spedizione = segui il pacco. Tieni premuto = le stesse scelte nel fumetto.
            if (!readOnly) smart.filter { sm -> folds.none { f -> sm.range.first in f } && !NoteLines.lineAt(text, sm.range.first).isBlock }.forEach { sm ->
                val s0 = shown.toShown(sm.range.first)
                val e0 = shown.toShown(sm.range.last + 1).coerceAtMost(drawn)
                if (s0 >= e0) return@forEach
                val firstRow = l.getLineForOffset(s0)
                val lastRow = l.getLineForOffset((e0 - 1).coerceAtLeast(s0))
                for (row in firstRow..lastRow) androidx.compose.runtime.key("s${sm.range.first}-$row") {
                    val x0 = if (row == firstRow) l.getHorizontalPosition(s0, true) else l.getLineLeft(row)
                    val x1 = if (row == lastRow) l.getHorizontalPosition(e0, true) else l.getLineRight(row)
                    val top = l.getLineTop(row)
                    val w = with(density) { (x1 - x0).coerceAtLeast(1f).toDp() }
                    val h = with(density) { (l.getLineBottom(row) - top).toDp() }
                    val acts = { smartActions(context, inApp, sm, current.text, noteId, fieldClipboard) }
                    Box(
                        Modifier
                            .offset { IntOffset(minOf(x0, x1).toInt(), top.toInt()) }
                            .size(w, h)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onLongClick = { acts().takeIf { it.isNotEmpty() }?.let { haptic(Haptic.LONG_PRESS); ActionBubble.show(smartHead(sm), *it.toTypedArray()) } },
                            ) {
                                haptic(Haptic.TICK)
                                when (sm.kind) {
                                    com.platinum20.dashboard.notes.NoteSmart.Kind.TAG -> inApp?.openRoute?.invoke(Routes.tag(sm.value))
                                    com.platinum20.dashboard.notes.NoteSmart.Kind.CODE -> copySmart(context, fieldClipboard, sm.value)
                                    else -> acts().takeIf { it.isNotEmpty() }?.let { ActionBubble.show(smartHead(sm), *it.toTypedArray()) }
                                }
                            },
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
            // ---- 1.590: due colonne, sotto-note, anteprime dei link, bacheca ----
            fun replaceLine(index: Int, with: String?) {
                val now = current.text
                val ln = NoteLines.parse(now).getOrNull(index) ?: return
                val t = if (with == null) NoteLines.remove(now, index) else now.substring(0, ln.start) + with + now.substring(ln.end)
                val at = if (with == null) ln.start else ln.start + with.length
                change(TextFieldValue(t, TextRange(at.coerceIn(0, t.length))), typed = false)
            }
            fun undoable(said: String, run: () -> Unit) {
                val before = current
                run()
                CaptureFeedback.tell(context, said, Icons.Outlined.Delete, "Annulla") { emit(before) }
            }
            val newBlocks = setOf(NoteLines.Kind.COLUMNS, NoteLines.Kind.PAGE, NoteLines.Kind.BOOKMARK, NoteLines.Kind.BOARD, NoteLines.Kind.IMAGE)
            shown.lines.filter { it.kind in newBlocks && !hiddenLine(it) && shown.toShown(it.start) < drawn }.forEach { line ->
                val raw = text.substring(line.start, line.end)
                val row = l.getLineForOffset(shown.toShown(line.start))
                val top = l.getLineTop(row)
                val bottom = l.getLineBottom(row)
                // Sopra un pezzo inserito dopo il testo un po' d'aria (2026-11-26), come sotto; fra due uguali no.
                val above = shown.lines.subList(0, line.index.coerceAtMost(shown.lines.size)).lastOrNull { !hiddenLine(it) }
                val gap = if (above != null && above.kind != line.kind && !above.isBlock) BLOCK_AIR else 0.dp
                val place = Modifier
                    .offset { IntOffset(0, top.toInt()) }
                    .fillMaxWidth()
                    .onSizeChanged { fit(raw, bottom - top, it.height) }
                    .padding(top = gap)
                androidx.compose.runtime.key("b${line.index}:${line.kind}") {
                    when (line.kind) {
                        NoteLines.Kind.COLUMNS -> {
                            val (a, b) = NoteLines.columns(raw)
                            NoteColumnsBlock(
                                left = a, right = b, readOnly = readOnly,
                                want = wantCell?.takeIf { it.first == line.index }?.third,
                                modifier = place,
                                onWanted = { wantCell = null },
                                onChange = { x, y -> replaceLine(line.index, NoteLines.columnsLine(x, y)) },
                                onLongPress = {
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleHead("Due colonne"),
                                        act(BubbleGroup.CAMBIA, Icons.AutoMirrored.Outlined.Notes, "Diventa testo normale", "prima la sinistra, poi la destra") {
                                            change(TextFieldValue(NoteLines.uncolumn(current.text, line.index), TextRange(line.start)), typed = false)
                                        },
                                        act(BubbleGroup.CAMBIA, Icons.Outlined.SwapHoriz, "Scambia le colonne", "la destra va a sinistra") { replaceLine(line.index, NoteLines.columnsLine(b, a)) },
                                        Slot.remove { undoable("Colonne tolte") { replaceLine(line.index, null) } },
                                    )
                                },
                            )
                        }
                        NoteLines.Kind.PAGE -> {
                            val id = NoteLines.pageId(raw)
                            val note = notesData.notes.firstOrNull { it.id == id }
                            val label = NoteFormat.linkAt(raw, 1)?.second?.removePrefix("📄 ") ?: "Pagina"
                            val name = note?.plainTitle?.ifEmpty { null } ?: label
                            NotePageRow(
                                emoji = note?.emoji ?: "📄",
                                title = name,
                                modifier = place,
                                onOpen = { haptic(Haptic.TICK); openNoteLink(inApp, "${NoteFormat.NOTE_LINK}$id", name) },
                                onLongPress = if (readOnly) null else ({
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleHead("Sotto-nota", name),
                                        Slot.open { openNoteLink(inApp, "${NoteFormat.NOTE_LINK}$id", name) },
                                        Slot.remove { undoable("Tolta dalla nota, resta fra le note") { replaceLine(line.index, null) } },
                                    )
                                }),
                            )
                        }
                        NoteLines.Kind.BOOKMARK -> {
                            val url = NoteLines.bookmarkUrl(raw).orEmpty()
                            LaunchedEffect(url) { if (url.isNotEmpty()) com.platinum20.dashboard.notes.LinkPreview.fetch(context, url) }
                            val card = previews[url]
                            NoteBookmarkCard(
                                url = url, card = card, fallback = NoteLines.host(url), modifier = place,
                                onOpen = { haptic(Haptic.TICK); openBookmark(inApp, url, card) },
                                onLongPress = if (readOnly) null else ({
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleHead("Anteprima", card?.title ?: NoteLines.host(url)),
                                        Slot.open { openBookmark(inApp, url, card) },
                                        act(BubbleGroup.CAMBIA, Icons.Outlined.Link, "Diventa un link nel testo", "solo il titolo, sottolineato") {
                                            val title = (card?.title ?: NoteLines.host(url)).replace('[', '(').replace(']', ')')
                                            replaceLine(line.index, "[$title]($url)")
                                        },
                                        Slot.shareText(context, com.platinum20.dashboard.share.ShareTexts.lines(card?.title, url)),
                                        Slot.remove { undoable("Anteprima tolta") { replaceLine(line.index, null) } },
                                    )
                                }),
                            )
                        }
                        NoteLines.Kind.BOARD -> {
                            val cards = NoteLines.boardItems(text, line.index).map {
                                BoardCard(it.index, NoteLines.plainLine(text.substring(it.start, it.end)), NoteLines.stateOf(it))
                            }
                            NoteBoard(
                                cards = cards, readOnly = readOnly, modifier = place,
                                onCard = { c ->
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleHead("Scheda", c.title),
                                        choice(
                                            "Sposta in",
                                            *listOf("Da fare", "In corso", "Fatto").mapIndexed { i, n ->
                                                BubbleChip(n, selected = c.state == i) { change(TextFieldValue(NoteLines.setState(current.text, c.line, i), current.selection), typed = false) }
                                            }.toTypedArray(),
                                        ),
                                        act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Cambia il testo", "della scheda") { boardDialog = Triple(line.index, c.line, c.title) },
                                        Slot.remove {
                                            undoable("Scheda tolta") { change(TextFieldValue(NoteLines.remove(current.text, c.line), current.selection), typed = false) }
                                        },
                                    )
                                },
                                onAdd = { haptic(Haptic.TICK); boardDialog = Triple(line.index, -1, "") },
                                onLongPress = if (readOnly) null else ({
                                    haptic(Haptic.LONG_PRESS)
                                    ActionBubble.show(
                                        BubbleHead("Bacheca"),
                                        act(BubbleGroup.CAMBIA, Icons.AutoMirrored.Outlined.FormatListBulleted, "Diventa una lista", "le schede tornano voci da spuntare") { replaceLine(line.index, null) },
                                        act(BubbleGroup.CREA, Icons.Outlined.Add, "Aggiungi una scheda", "in Da fare") { boardDialog = Triple(line.index, -1, "") },
                                        Slot.remove {
                                            undoable("Bacheca tolta") {
                                                val now = current.text
                                                val bl = NoteLines.parse(now).getOrNull(line.index) ?: return@undoable
                                                val r = NoteLines.blockOf(now, bl)
                                                val to = if (r.last < now.length) r.last + 1 else r.last
                                                val from = if (to == r.last && r.first > 0) r.first - 1 else r.first
                                                change(TextFieldValue(now.removeRange(from, to), TextRange(from)), typed = false)
                                            }
                                        },
                                    )
                                }),
                            )
                        }
                        NoteLines.Kind.IMAGE -> {
                            val path = NoteLines.imagePath(raw).orEmpty()
                            fun photoActions() {
                                haptic(Haptic.LONG_PRESS)
                                ActionBubble.show(
                                    BubbleHead("Foto"),
                                    *listOfNotNull(
                                        onPhotoToTop?.let { top ->
                                            act(BubbleGroup.CAMBIA, Icons.Outlined.VerticalAlignTop, "Metti in cima", "va fra le foto sopra il testo") {
                                                replaceLine(line.index, null)
                                                top(path)
                                            }
                                        },
                                    ).toTypedArray(),
                                    Slot.share { com.platinum20.dashboard.notes.NotePhotoFiles.share(context, "", listOf(path)) },
                                    Slot.remove { undoable("Foto tolta") { replaceLine(line.index, null) } },
                                )
                            }
                            NoteImageBlock(path, place, onTap = { if (!readOnly) photoActions() }, onLongPress = if (readOnly) null else ({ photoActions() }))
                        }
                        else -> {}
                    }
                }
            }
            // Il menu "/" non si apre più (Antonio 2026-11-24): era un doppione del + della barra, che aggiunge le stesse cose.
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
                                BubbleHead("Vocale", link.second),
                                Slot.remove {
                                    val before = current
                                    val l2 = NoteLines.parse(before.text).firstOrNull { it.start == line.start && it.kind == NoteLines.Kind.AUDIO } ?: return@remove
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
                val grid = NoteTable.grid(text, block)
                // Ogni riga è alta quanto la casella più lunga, che va a capo (2026-11-26: prima il testo usciva dai lati).
                // Sopra la tabella un po' d'aria, dentro la prima riga.
                val air = with(density) { TABLE_AIR.toPx() }
                val plusW = if (active && !readOnly) with(density) { 28.dp.toPx() } else 0f
                val cellPad = with(density) { 20.dp.toPx() }
                val needs = rows.indices.map { r ->
                    val cells = grid.getOrNull(r).orEmpty()
                    val cellW = ((l.size.width - plusW) / cells.size.coerceAtLeast(1) - cellPad).toInt().coerceAtLeast(1)
                    val style = cellStyle.copy(fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal)
                    val tallest = cells.maxOfOrNull { cell ->
                        cellMeasurer.measure(NoteFormat.plain(cell).ifEmpty { " " }, style, constraints = Constraints(maxWidth = cellW)).size.height
                    } ?: 0
                    tallest + with(density) { 16.dp.toPx() } + if (r == 0) air else 0f
                }
                val heightsNow = rows.indices.map { bottoms[it] - tops[it] }
                SideEffect {
                    rows.forEachIndexed { i, row ->
                        val tallRow = active && i == rows.lastIndex
                        fit(text.substring(row.start, row.end), heightsNow[i], needs[i].toInt(), if (tallRow) TALL_SIZE.value - BREATH_SIZE.value else 0f)
                    }
                }
                val heights = heightsNow.mapIndexed { i, h -> if (i == 0) (h - air).coerceAtLeast(1f) else h }
                // L'ultima riga, se è alta per il +, è alta quanto il suo testo; sotto resta il posto del +.
                val lastH = if (!active) heights.last() else (needs.last() - if (rows.size == 1) air else 0f).coerceAtMost(heights.last())
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
                        total = with(density) { (bottoms.last() - tops.first() - air).coerceAtLeast(1f).toDp() },
                        active = active,
                        readOnly = readOnly,
                        want = wantCell?.takeIf { it.first == block.first }?.let { it.second to it.third },
                        modifier = Modifier.offset { IntOffset(0, (tops.first() + air).toInt()) }.fillMaxWidth(),
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
                            fun undoable(next: List<List<String>>, said: String) {
                                val before = current
                                put(next)
                                CaptureFeedback.tell(context, said, Icons.Outlined.Delete, "Annulla") { emit(before) }
                            }
                            ActionBubble.show(
                                BubbleHead("Tabella", "riga ${r + 1} · colonna ${('A' + c)}"),
                                *listOfNotNull(
                                    choice(
                                        "Aggiungi una riga",
                                        BubbleChip("Sopra", icon = Icons.Outlined.VerticalAlignTop) { put(NoteTable.addRow(g, r), r to c) },
                                        BubbleChip("Sotto", icon = Icons.Outlined.VerticalAlignBottom) { put(NoteTable.addRow(g, r + 1), r + 1 to c) },
                                    ),
                                    choice(
                                        "Aggiungi una colonna",
                                        BubbleChip("A sinistra", icon = Icons.Outlined.West) { put(NoteTable.addColumn(g, c), r to c) },
                                        BubbleChip("A destra", icon = Icons.Outlined.East) { put(NoteTable.addColumn(g, c + 1), r to c + 1) },
                                    ),
                                    listOfNotNull(
                                        if (g.size > 1) BubbleChip("la riga ${r + 1}") { undoable(NoteTable.dropRow(g, r), "Riga tolta") } else null,
                                        if (g.first().size > 1) BubbleChip("la colonna ${('A' + c)}") { undoable(NoteTable.dropColumn(g, c), "Colonna tolta") } else null,
                                    ).takeIf { it.isNotEmpty() }?.let { choice("Togli", *it.toTypedArray()) },
                                    Slot.delete { undoable(emptyList(), "Tabella tolta") },
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
        url.startsWith(com.platinum20.dashboard.notes.NoteSeek.SCHEME) -> com.platinum20.dashboard.notes.NoteSeek.open(url)
        else -> inApp?.openWeb?.invoke(url, label)
    }
}

/**
 * Apre l'anteprima di un link (2026-11-23): un video YouTube o un brano Spotify parte nel player di Canvas,
 * una pagina si apre nel lettore come un articolo delle News.
 */
internal fun openBookmark(inApp: InAppNav?, url: String, card: com.platinum20.dashboard.notes.LinkPreview.Card?) {
    if (InAppLinks.target(url) is LinkTarget.Web) inApp?.openRoute?.invoke(Routes.reader(url, card?.site ?: NoteLines.host(url), card?.title.orEmpty()))
    else openNoteLink(inApp, url, card?.title ?: url)
}

/** Copia un codice o una password della nota e lo dice nel fumetto in basso (2026-11-23). */
internal fun copySmart(context: android.content.Context, clip: androidx.compose.ui.platform.ClipboardManager, value: String) {
    clip.setText(androidx.compose.ui.text.AnnotatedString(value))
    com.platinum20.dashboard.capture.CaptureFeedback.tell(context, "Copiato negli appunti del telefono", Icons.Outlined.ContentCopy)
}

/** La testata del fumetto di una parte intelligente: "DATA · venerdì 28". */
internal fun smartHead(sm: com.platinum20.dashboard.notes.NoteSmart.Smart) = BubbleHead(
    when (sm.kind) {
        com.platinum20.dashboard.notes.NoteSmart.Kind.DATE -> "Data"
        com.platinum20.dashboard.notes.NoteSmart.Kind.TAG -> "Etichetta"
        com.platinum20.dashboard.notes.NoteSmart.Kind.CODE -> "Codice"
        com.platinum20.dashboard.notes.NoteSmart.Kind.PARCEL -> "Pacco"
    },
    if (sm.kind == com.platinum20.dashboard.notes.NoteSmart.Kind.TAG) "#${sm.value}" else sm.value,
)

/** Le scelte di una parte intelligente della nota (2026-11-23): data, etichetta, codice, codice di spedizione. */
internal fun smartActions(
    context: android.content.Context,
    inApp: InAppNav?,
    sm: com.platinum20.dashboard.notes.NoteSmart.Smart,
    text: String,
    noteId: Long?,
    clip: androidx.compose.ui.platform.ClipboardManager,
): List<BubbleAction> = when (sm.kind) {
    com.platinum20.dashboard.notes.NoteSmart.Kind.DATE -> {
        val due = com.platinum20.dashboard.notes.NoteSmart.dateOf(sm.value)
        val title = com.platinum20.dashboard.notes.NoteSmart.titleAround(text, sm)
        if (due == null) emptyList() else listOf(
            act(BubbleGroup.METTI, Icons.Outlined.Event, "Metti in agenda", "\"$title\", ${dueLabel(due)}") {
                val at = due.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val task = NotesStore.addTaskAt(title, at)
                val nid = noteId
                if (nid != null) NotesStore.note(nid)?.let { n ->
                    NotesStore.setTaskNotes(task.id, "[${n.plainTitle.ifEmpty { "Nota" }.replace('[', '(').replace(']', ')')}](${NoteFormat.NOTE_LINK}$nid)")
                }
                com.platinum20.dashboard.capture.CaptureFeedback.tell(context, "In agenda: ${dueLabel(due)}", Icons.Outlined.Event, "Annulla") { NotesStore.deleteTask(task.id) }
            },
            act(BubbleGroup.VAI, Icons.Outlined.CalendarMonth, "Vedi quel giorno", "l'agenda su ${dueLabel(due).substringBefore(',')}") {
                inApp?.openRoute?.invoke(Routes.NOTES)
            },
        )
    }
    com.platinum20.dashboard.notes.NoteSmart.Kind.TAG -> listOf(
        act(BubbleGroup.VAI, Icons.Outlined.Tag, "Tutto con #${sm.value}", "note, cose da fare e date con questa etichetta") { inApp?.openRoute?.invoke(Routes.tag(sm.value)) },
    )
    com.platinum20.dashboard.notes.NoteSmart.Kind.CODE -> listOf(
        Slot.share(copy = { copySmart(context, clip, sm.value) }, copyTitle = "Copia il codice", copyDetail = "da incollare altrove") { shareText(context, sm.value) },
    )
    com.platinum20.dashboard.notes.NoteSmart.Kind.PARCEL -> {
        val known = NotesStore.data.value.parcels.firstOrNull { it.code.equals(sm.value, ignoreCase = true) }
        listOf(
            if (known != null) Slot.open {
                inApp?.openRoute?.invoke(Routes.noteItem(ItemKind.PARCEL, known.id))
            } else Slot.follow(false) {
                val title = com.platinum20.dashboard.notes.NoteSmart.titleAround(text, sm).ifEmpty { "Pacco" }
                val id = NotesStore.addParcel(title, sm.value, null)
                inApp?.openRoute?.invoke(Routes.noteItem(ItemKind.PARCEL, id))
            },
            Slot.share(copy = { copySmart(context, clip, sm.value) }, copyTitle = "Copia il codice", copyDetail = "da incollare altrove") { shareText(context, sm.value) },
        )
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
internal fun mentionTargets(
    data: com.platinum20.dashboard.notes.NotesData,
    docs: List<com.platinum20.dashboard.docs.Document>,
    query: String,
    /** Gli eventi del calendario del telefono (1.598), dal mese scorso ai prossimi tre. */
    events: List<com.platinum20.dashboard.calendar.CalendarEvent> = emptyList(),
    now: Long = System.currentTimeMillis(),
    /** I turni (2026-11-23): i prossimi si collegano, e le persone in doppio hanno la loro pagina. */
    shifts: List<com.platinum20.dashboard.shifts.Shift> = emptyList(),
    /** Le playlist di Spotify (2026-11-23): toccando il link si apre la pagina della playlist. */
    playlists: List<com.platinum20.dashboard.media.spotify.SpItem> = emptyList(),
): List<MentionTarget> {
    fun hit(t: String) = t.isNotBlank() && t.contains(query, ignoreCase = true)
    val out = mutableListOf<MentionTarget>()
    data.notes.filter { hit(it.plainTitle) }.sortedByDescending { it.updatedAt }.take(5)
        .forEach { out += MentionTarget(Icons.AutoMirrored.Outlined.Notes, it.title, NoteFormat.NOTE_LINK + it.id) }
    // Eventi: uno per evento (le ripetizioni no), prima i più vicini a oggi.
    events.filter { hit(it.title) }.sortedBy { kotlin.math.abs(it.begin - now) }.distinctBy { it.eventId }.take(4)
        .forEach { out += MentionTarget(Icons.Outlined.Event, it.title, NoteFormat.APP_LINK + Routes.calendarEvent(it.eventId, it.begin, it.end)) }
    com.platinum20.dashboard.notes.NoteSmart.people(shifts.map { it.mates }).map(com.platinum20.dashboard.notes.NoteSmart::firstName).distinct()
        .filter { hit(it) }.take(2)
        .forEach { out += MentionTarget(Icons.Outlined.Person, it, NoteFormat.APP_LINK + Routes.person(it)) }
    shifts.filter { it.end >= now && (hit(it.title) || it.mates.any(::hit) || hit("turno")) }.sortedBy { it.start }.take(3)
        .forEach { sh ->
            val day = java.time.Instant.ofEpochMilli(sh.start).atZone(java.time.ZoneId.systemDefault())
            val label = "${sh.title} · " + day.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.ITALIAN))
            out += MentionTarget(Icons.Outlined.Work, label, NoteFormat.APP_LINK + Routes.calendarEvent(sh.eventId, sh.start, sh.end))
        }
    playlists.filter { hit(it.title) }.take(3)
        .forEach { out += MentionTarget(Icons.Outlined.MusicNote, it.title, NoteFormat.APP_LINK + Routes.spotifyItem(it)) }
    data.tasks.filter { !it.done && hit(it.text) }.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(4)
        .forEach { out += MentionTarget(if (it.dueAt != null) Icons.Outlined.Event else Icons.Outlined.TaskAlt, it.text, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.TASK, it.id)) }
    data.habits.filter { hit(it.title) }.take(3)
        .forEach { out += MentionTarget(Icons.Outlined.LocalFireDepartment, it.title, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.HABIT, it.id)) }
    data.parcels.filter { hit(it.title) }.take(2)
        .forEach { out += MentionTarget(Icons.Outlined.LocalShipping, it.title, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.PARCEL, it.id)) }
    docs.filter { hit(it.title) }.take(2)
        .forEach { out += MentionTarget(Icons.Outlined.Badge, it.title, NoteFormat.APP_LINK + Routes.document(it.id)) }
    MENTION_PAGES.filter { (name, _) -> query.isNotEmpty() && hit(name) }
        .forEach { (name, route) -> out += MentionTarget(pageIcon(route), name, NoteFormat.APP_LINK + route) }
    return out.take(10)
}

/** Le sezioni di Canvas che si possono collegare con "@". */
private val MENTION_PAGES = listOf(
    "Meteo" to Routes.WEATHER, "SIM" to Routes.SIM, "Agenda" to Routes.NOTES, "News" to Routes.NEWS,
    "Musica" to Routes.MUSIC_DISCOVER, "Video" to Routes.MEDIA_NEWS, "Radio" to Routes.RADIO, "Podcast" to Routes.PODCAST,
    "Da fare" to Routes.LATER_ARCHIVE, "Cerca" to Routes.SEARCH,
)

/** L'icona di ogni sezione collegabile con "@" (1.596). */
private fun pageIcon(route: String): ImageVector = when (route) {
    Routes.WEATHER -> Icons.Outlined.WbSunny
    Routes.SIM -> Icons.Outlined.SimCard
    Routes.NOTES -> Icons.Outlined.CalendarMonth
    Routes.NEWS -> Icons.Outlined.Newspaper
    Routes.MEDIA, Routes.MUSIC_ALL, Routes.MUSIC_DISCOVER -> Icons.Outlined.MusicNote
    Routes.MEDIA_NEWS -> Icons.Outlined.SmartDisplay
    Routes.RADIO -> Icons.Outlined.Radio
    Routes.PODCAST -> Icons.Outlined.Podcasts
    Routes.LATER_ARCHIVE -> Icons.Outlined.TaskAlt
    Routes.JOTTINGS_ARCHIVE -> Icons.AutoMirrored.Outlined.Notes
    Routes.SEARCH -> Icons.Outlined.Search
    else -> Icons.Outlined.Apps
}

/** Cosa si disegna davanti a un link "@": l'emoji della nota se ce l'ha, se no l'icona del tipo. */
internal class MentionMark(val icon: ImageVector, val emoji: String? = null)

/** L'icona di un link "@" dal suo indirizzo (1.596): nota, promemoria o data, cosa da fare, abitudine, pacco, documento, sezione. */
internal fun mentionMark(url: String, data: com.platinum20.dashboard.notes.NotesData): MentionMark {
    if (url.startsWith(NoteFormat.NOTE_LINK)) {
        val id = url.removePrefix(NoteFormat.NOTE_LINK).toLongOrNull()
        return MentionMark(Icons.AutoMirrored.Outlined.Notes, data.notes.firstOrNull { it.id == id }?.emoji)
    }
    val route = url.removePrefix(NoteFormat.APP_LINK)
    val item = Regex("""notes/item/(\w+)/(-?\d+)""").find(route)
    if (item != null) {
        val id = item.groupValues[2].toLongOrNull()
        return MentionMark(
            when (item.groupValues[1]) {
                ItemKind.HABIT.name -> Icons.Outlined.LocalFireDepartment
                ItemKind.PARCEL.name -> Icons.Outlined.LocalShipping
                else -> if (data.tasks.firstOrNull { it.id == id }?.dueAt != null) Icons.Outlined.Event else Icons.Outlined.TaskAlt
            },
        )
    }
    if (route.startsWith("home/document/")) return MentionMark(Icons.Outlined.Badge)
    if (route.startsWith("home/calendar/event?id=-")) return MentionMark(Icons.Outlined.Work)
    if (route.startsWith("home/calendar/event")) return MentionMark(Icons.Outlined.Event)
    if (route.startsWith("notes/person/")) return MentionMark(Icons.Outlined.Person)
    if (route.startsWith("media/spotify/item/")) return MentionMark(Icons.Outlined.MusicNote)
    return MentionMark(pageIcon(route))
}

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
            Slot.remove {
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "Linea tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
            },
        )
        else -> listOfNotNull(
            if (line.kind == NoteLines.Kind.CALLOUT) dots(
                "Emoji",
                *CALLOUT_EMOJIS.map { e ->
                    BubbleChip(e, selected = NoteLines.calloutEmoji(text.substring(line.start, line.end)) == e, emoji = e) { set(NoteLines.setCalloutEmoji(text, line.index, e), line.end) }
                }.toTypedArray(),
            ) else null,
            transformAction(line.kind) { k -> set(NoteLines.renumber(NoteLines.transform(text, line.start, line.start, k)).text) },
            Slot.remove {
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "Riga tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
            },
        )
    }
}

/** Le emoji da scegliere per il riquadro. */
private val CALLOUT_EMOJIS = listOf("💡", "⚠️", "📌", "❗", "✅", "🔑", "📞", "❤️")

/** Il quadrato vuoto, il quadrato pieno con la spunta (come su Notion, 1.590), o il pallino del punto. */
@Composable
private fun LineMark(line: NoteLines.Line, lifted: Boolean = false, ticking: Boolean = false, grow: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    // La spunta si disegna (proposta 1): il quadrato si riempie con una molla e il segno si scopre da sinistra. Solo per
    // la voce toccata: le altre (aprendo la nota, righe che cambiano posto) sono subito come sono.
    val fill = remember { androidx.compose.animation.core.Animatable(if (line.done) 1f else 0f) }
    LaunchedEffect(line.done, ticking) {
        val goal = if (line.done) 1f else 0f
        if (ticking) fill.animateTo(goal, Motion.lively()) else fill.snapTo(goal)
    }
    // La voce nuova (proposta 2): il segno cresce da piccolo.
    val size = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(grow, line.start) {
        if (!grow) return@LaunchedEffect
        size.snapTo(0.2f)
        size.animateTo(1f, Motion.lively())
    }
    Box(Modifier.graphicsLayer { scaleX = size.value; scaleY = size.value }, contentAlignment = Alignment.Center) {
        LineMarkShape(line, lifted, fill.value)
    }
}

@Composable
private fun LineMarkShape(line: NoteLines.Line, lifted: Boolean, fill: Float) {
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
        // Quadrati come su Notion (1.590), non più cerchi.
        line.kind == NoteLines.Kind.CHECK && (line.done || fill > 0.01f) -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.requiredSize(18.dp)
                .border(1.6.dp, if (lifted) colors.primary else colors.onSurfaceVariant.copy(alpha = 1f - fill.coerceIn(0f, 1f)), RoundedCornerShape(4.dp)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.requiredSize(18.dp)
                    .graphicsLayer { val k = 0.55f + 0.45f * fill; scaleX = k; scaleY = k; alpha = fill.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(4.dp)).background(colors.primary),
            ) {
                // Il segno si scopre da sinistra a destra, come se si disegnasse.
                Icon(
                    Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary,
                    modifier = Modifier.size(13.dp).drawWithContent {
                        clipRect(right = size.width * fill.coerceIn(0f, 1f)) { this@drawWithContent.drawContent() }
                    },
                )
            }
        }
        // In corso (1.590, dalla bacheca): il quadrato blu con un quadratino dentro.
        line.doing -> Box(contentAlignment = Alignment.Center, modifier = Modifier.requiredSize(18.dp).border(1.6.dp, colors.primary, RoundedCornerShape(4.dp))) {
            Box(Modifier.requiredSize(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
        }
        else -> Box(Modifier.requiredSize(18.dp).border(1.6.dp, if (lifted) colors.primary else colors.onSurfaceVariant, RoundedCornerShape(4.dp)))
    }
}

/**
 * Il testo disegnato: formati, titoletti, voci fatte barrate e sbiadite, il rientro di punti e voci. I segni dei formati
 * ([NoteFormat.hidden]) e delle voci ([NoteLines.Shown]) non si vedono mai (1.577).
 */
internal fun richText(
    text: String,
    faded: androidx.compose.ui.graphics.Color,
    marks: List<Pair<IntRange, SpanStyle>> = emptyList(),
    accent: androidx.compose.ui.graphics.Color = faded,
    /** Le righe sotto i titoli chiusi (1.588): non si disegnano. */
    folds: List<IntRange> = emptyList(),
    marker: androidx.compose.ui.graphics.Color = faded,
    /** La riga (dove comincia) da fare più alta: l'ultima della tabella in cui si scrive. */
    tall: Int? = null,
    /** L'altezza (in sp del carattere invisibile) delle righe-pezzo, per il loro testo (1.590). */
    sizes: Map<String, Float> = emptyMap(),
    /** Senza aria sopra la prima riga: la prima riga semplice resta alta come il testo. */
    firstAir: Boolean = true,
    /** La spunta appena toccata (2026-11-24): dove comincia la voce, se diventa fatta, quanto è avanti il barrato (0…1). */
    sweep: Triple<Int, Boolean, Float>? = null,
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
                // I colori (1.590): minuscola = testo, maiuscola = sfondo.
                NoteFormat.Kind.COLOR -> NoteFormat.colorAt(text, s.start)?.let { c ->
                    if (c.isUpperCase()) SpanStyle(background = noteColor(c)) else SpanStyle(color = noteColor(c))
                }
                // Evidenziatore (1.584): uno sfondo giallo chiaro, l'unico colore che non vuol dire niente altro.
                NoteFormat.Kind.HIGHLIGHT -> SpanStyle(background = marker)
                // I link "@" stanno in una bolla (disegnata sotto il testo), senza sottolineatura (1.589).
                NoteFormat.Kind.LINK, NoteFormat.Kind.URL ->
                    if (s.kind == NoteFormat.Kind.LINK && NoteFormat.linkAt(text, s.start)?.first?.let(NoteFormat::isMention) == true) SpanStyle(color = accent)
                    else SpanStyle(color = accent, textDecoration = TextDecoration.Underline)
            }
            if (style != null) addStyle(style, s.start, s.end)
        }
        NoteLines.parse(text).filter { it.kind == NoteLines.Kind.CHECK && it.end > it.contentStart }.forEach {
            val upTo = sweepEnd(it, sweep) ?: return@forEach
            if (upTo > it.contentStart) addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = faded), it.contentStart, upTo)
        }
        // La citazione: testo in corsivo e un po' più chiaro, accanto alla barra.
        NoteLines.parse(text).filter { it.kind == NoteLines.Kind.QUOTE && it.end > it.contentStart }.forEach {
            addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = faded), it.contentStart, it.end)
        }
        marks.forEach { (r, style) -> if (r.last < text.length) addStyle(style, r.first, r.last + 1) }
    }
    val view = editorShown(text, folds)
    val drawn = buildAnnotatedString {
        var at = 0
        view.cuts.forEach { c ->
            append(styled.subSequence(at, c.start))
            append(c.with)
            at = c.end
        }
        append(styled.subSequence(at, text.length))
        // I link "@" (1.596): il posto dell'icona davanti e un po' d'aria in fondo, dentro la bolla.
        NoteFormat.mentionSlots(text).forEach { (at0, slot) ->
            val at = view.slotAt(at0) ?: return@forEach
            if (at < length && view.shown.getOrNull(at)?.toString() == slot) {
                val open = text.getOrNull(at0) == '['
                addStyle(SpanStyle(letterSpacing = if (open) MENTION_ICON_SPACE else MENTION_END_SPACE), at, at + 1)
            }
        }
        // L'aria fra le righe (1.589): il carattere invisibile in cima a ogni riga è più grande del testo, così la riga
        // dopo un Invio ha più spazio sopra; le righe che vanno a capo da sole restano strette.
        // Più aria sopra i titoletti e sotto riquadri e tabelle (2026-11-26, la guida): prima il titolo toccava il blocco sopra.
        view.lines.filter { folds.none { f -> it.start in f } }.forEach { line ->
            val at = view.toShown(line.start) + if (line.hasMark && line.prefix > 0) 1 else 0
            if (!firstAir && line.index == 0 && !line.isBlock && line.start != tall) return@forEach
            val stored = sizes[text.substring(line.start, line.end)]
            val size = when {
                line.start == tall -> (maxOf(TALL_SIZE.value, (stored ?: BREATH_SIZE.value) + TALL_SIZE.value - BREATH_SIZE.value)).sp
                line.isBlock -> stored?.sp ?: BREATH_SIZE
                // La riga visibile sopra: sotto una bacheca le sue spunte sono nascoste.
                else -> lineAir(line, view.lines.subList(0, line.index.coerceAtMost(view.lines.size)).lastOrNull { p -> folds.none { p.start in it } })
            }
            if (at < length) addStyle(SpanStyle(fontSize = size), at, at + 1)
        }
    }
    // Il rientro sospeso: ogni riga scritta è un paragrafo con il suo rientro, così il testo di voci, sotto-voci e
    // citazioni che va a capo resta allineato al testo, non torna al bordo né finisce sulla barra. L'"a capo" fra un
    // paragrafo e l'altro diventa uno spazio di larghezza zero (stessa lunghezza, posizioni uguali): con l'"a capo"
    // dentro, ogni voce aveva una riga vuota sotto (1.556, 1.587).
    val paras = view.paragraphs { line -> folds.any { line.start in it } }
    val chars = drawn.text.toCharArray()
    paras.dropLast(1).forEach { p -> if (p.end > 0 && chars[p.end - 1] == '\n') chars[p.end - 1] = '\u200B' }
    val shown = AnnotatedString(
        String(chars),
        spanStyles = drawn.spanStyles,
        paragraphStyles = paras.filter { it.end > it.start }.map { p ->
            val first = (INDENT.value * p.indents).sp
            // Le righe dopo la prima partono dove comincia il testo: dopo il rientro e lo spazio sottile del segno.
            val rest = if (p.indents > 0) (INDENT.value * p.indents + THIN_SPACE.value).sp else 0.sp
            AnnotatedString.Range(ParagraphStyle(textIndent = TextIndent(firstLine = first, restLine = rest)), p.start, p.end)
        },
    )
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
    /** Il fumetto si rifà sulla riga [Int] del testo nuovo, dopo un tocco che lo lascia aperto. */
    again: (TextFieldValue, Int) -> Unit = { _, _ -> },
): List<BubbleAction> {
    val text = value.text
    val count = NoteLines.parse(text).size
    fun set(next: String) = emit(TextFieldValue(next, TextRange(NoteLines.parse(next).getOrNull(line.index)?.end ?: next.length)))
    // La riga resta la stessa (spostata in [at]): il cursore la segue e il fumetto riparte da lì.
    fun keep(next: String, at: Int = line.index) {
        val nv = TextFieldValue(next, TextRange(NoteLines.parse(next).getOrNull(at)?.end ?: next.length))
        emit(nv)
        again(nv, at)
    }
    val words = NoteLines.plainLine(text.substring(line.start, line.end))
    return listOfNotNull(
        transformAction(line.kind) { k -> keep(NoteLines.renumber(NoteLines.transform(text, line.start, line.start, k)).text) },
        listOfNotNull(
            if (line.index > 0) BubbleChip("Su", icon = Icons.Outlined.ArrowUpward) { keep(NoteLines.move(text, line.index, up = true), line.index - 1) } else null,
            if (line.index < count - 1) BubbleChip("Giù", icon = Icons.Outlined.ArrowDownward) { keep(NoteLines.move(text, line.index, up = false), line.index + 1) } else null,
        ).takeIf { it.isNotEmpty() }?.let { choice("Sposta", *it.toTypedArray()) },
        // Il rientro (2026-11-24): uscito dalla barra, sta qui e sul segno trascinato di lato.
        listOfNotNull(
            if (NoteLines.canIndent(text, line.start, -1)) BubbleChip("In fuori", icon = Icons.AutoMirrored.Outlined.FormatIndentDecrease) {
                keep(NoteLines.indent(text, line.start, line.start, -1).text)
            } else null,
            if (NoteLines.canIndent(text, line.start, 1)) BubbleChip("Sotto-voce", icon = Icons.AutoMirrored.Outlined.FormatIndentIncrease) {
                keep(NoteLines.indent(text, line.start, line.start, 1).text)
            } else null,
        ).takeIf { it.isNotEmpty() }?.let { choice("Rientro", *it.toTypedArray()) },
        if (sendToTodo && words.isNotEmpty()) act(BubbleGroup.METTI, Icons.Outlined.TaskAlt, "Manda in Da fare", "la trovi nella card della Home") {
            NotesStore.addTodo(words)?.let { task ->
                set(NoteLines.remove(text, line.index))
                CaptureFeedback.tell(context, "In Da fare", Icons.Outlined.TaskAlt, "Annulla") {
                    NotesStore.deleteTask(task.id)
                    emit(TextFieldValue(text, value.selection))
                }
            }
        } else null,
        // Lista e Ricomincia (2026-11-24, da Altro della nota): lavorano sulle voci, quindi stanno qui.
        listActions(context, line, value, emit),
        if (line.isList && NoteLines.hasDone(text)) act(
            BubbleGroup.SEGNA, Icons.Outlined.Replay, "Ricomincia la lista",
            NoteLists.doneCount(text).let { if (it == 1) "toglie la spunta, il testo resta" else "toglie le $it spunte, il testo resta" },
        ) { undoableAll(context, value, emit, NoteLines.uncheckAll(text), "Lista da rifare", Icons.Outlined.Replay) } else null,
        Slot.remove {
            set(NoteLines.remove(text, line.index))
            CaptureFeedback.tell(context, "Riga tolta", Icons.Outlined.Delete, "Annulla") { emit(TextFieldValue(text, value.selection)) }
        },
    )
}

/** Tutta la nota cambia, con Annulla. */
private fun undoableAll(context: android.content.Context, value: TextFieldValue, emit: (TextFieldValue) -> Unit, next: String, said: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    emit(TextFieldValue(next, TextRange(next.length.coerceAtMost(value.selection.end))))
    CaptureFeedback.tell(context, said, icon, "Annulla") { emit(value) }
}

/** La fila "Lista" (Fatte giù · A-Z · Togli fatte): solo su una voce di una lista con più di una voce. */
private fun listActions(context: android.content.Context, line: NoteLines.Line, value: TextFieldValue, emit: (TextFieldValue) -> Unit): BubbleAction? {
    val text = value.text
    if (!line.isList || NoteLines.parse(text).count { it.isList } < 2) return null
    val done = NoteLines.hasDone(text)
    return choice(
        "Lista",
        *listOfNotNull(
            if (!done) null else BubbleChip("Fatte giù", icon = Icons.Outlined.VerticalAlignBottom) { undoableAll(context, value, emit, NoteLines.doneToBottom(text), "Fatte in fondo", Icons.Outlined.VerticalAlignBottom) },
            BubbleChip("A-Z", icon = Icons.Outlined.SortByAlpha) { undoableAll(context, value, emit, NoteLines.sortAlpha(text), "Lista in ordine", Icons.Outlined.SortByAlpha) },
            if (!done) null else BubbleChip("Togli fatte", icon = Icons.Outlined.RemoveDone) { undoableAll(context, value, emit, NoteLines.removeDone(text), "Fatte tolte", Icons.Outlined.Delete) },
        ).toTypedArray(),
    )
}

/**
 * La barra sopra la tastiera (1.579, /mnt/project-files/editor-note-3/barra.png): solo icone tonde in gruppi.
 * Annulla · Ripeti | Punti · Numeri · Spunta · rientro → ← | Aa. Con del testo scelto, o dopo Aa: grassetto, corsivo,
 * barrato, titolo | X per tornare. Accesa = blu; spenta (grigia) = adesso non si può.
 */
/**
 * I tasti in basso e la barra delle righe si danno il cambio (2026-11-24, proposta 3): sfumano uno nell'altro e la barra
 * sale di un filo, mentre il bordo (con imePadding fuori) segue la tastiera. Il + sta quasi nello stesso punto nei due.
 */
@Composable
internal fun KeyboardBarSwap(modifier: Modifier, writing: Boolean, buttons: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedContent(
        targetState = writing,
        modifier = modifier,
        contentAlignment = Alignment.BottomStart,
        label = "barra",
        transitionSpec = {
            (androidx.compose.animation.fadeIn(Motion.settle()) +
                androidx.compose.animation.slideInVertically(Motion.settle()) { it / 3 }) togetherWith
                androidx.compose.animation.fadeOut(Motion.settle()) using
                androidx.compose.animation.SizeTransform(clip = false)
        },
    ) { bar -> if (bar) RichToolbar(Modifier.fillMaxWidth()) else buttons() }
}

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
    val cursor = v.selection.end.coerceIn(0, v.text.length)
    val mention = if (selecting) null else mentionQuery(v.text, cursor)
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
    // La chip della data aspetta che ti fermi mezzo secondo (2026-11-24, proposta 4): scrivendo "venerdì" non lampeggia
    // a ogni lettera (ven… vene…).
    var calmLine by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lineText) {
        kotlinx.coroutines.delay(CALM_MS)
        calmLine = lineText
    }
    val datedShown = dated.takeIf { calmLine == lineText }
    fun toCalendar() {
        val (plain, due) = dated ?: return
        haptic(Haptic.CONFIRM)
        val task = NotesStore.addTask(plain) ?: return
        val now = target.value()
        val l = NoteLines.lineAt(now.text, now.selection.end)
        val e = NoteFormat.mentionLink(now.text, l.contentStart, l.end, plain, NoteFormat.APP_LINK + Routes.noteItem(ItemKind.TASK, task.id))
        val t = e.text.let { if (it.getOrNull(e.start - 1) == ' ') it.removeRange(e.start - 1, e.start) else it }
        target.set(TextFieldValue(t, TextRange((e.start - 1).coerceIn(0, t.length))))
        CaptureFeedback.tell(context, "In agenda: " + dueLabel(due), Icons.Outlined.Event)
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Il + della barra: il fumetto Aggiungi di chi usa il campo; senza, i blocchi del menu "/" (note di eventi e date).
    fun add() {
        haptic(Haptic.LONG_PRESS)
        // Il fumetto sta in basso: la tastiera si chiude per lasciarlo vedere, il cursore resta dov'è.
        keyboard?.hide()
        target.add?.let { it(); return }
        val entries = slashEntries(target) { pid, name -> openNoteLink(inApp, "${NoteFormat.NOTE_LINK}$pid", name) }
        fun chip(id: String, label: String) = entries.firstOrNull { it.id == id }?.let { e -> BubbleChip(label, icon = e.icon) { e.run(target.value()) } }
        ActionBubble.show(
            BubbleHead("Aggiungi", "nel testo"),
            choice("Blocchi", *listOfNotNull(chip("tabella", "Tabella"), chip("colonne", "Colonne"), chip("bacheca", "Bacheca")).toTypedArray()),
            choice("Collega", *listOfNotNull(chip("sottonota", "Sotto-nota"), chip("collega", "Un'altra nota")).toTypedArray()),
        )
    }
    val showFormats = selecting || formats
    // Quello che galleggia sopra la barra (2026-11-24, proposte 2 e 3): la barra resta sempre uguale, sopra compaiono
    // i formati (Aa o testo scelto), i risultati di "@" e il tasto della data o del link della riga.
    // Con un fumetto aperto (Colore, Trasforma in…) la fila sparisce: il Popup sta sopra a tutto e lo copriva.
    val floating = ActionBubble.actions == null && (mention != null || showFormats || datedShown != null || linkHere != null)
    var barWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(modifier.onGloballyPositioned { barWidth = it.size.width }) {
        if (floating && barWidth > 0) {
            val gap = with(density) { 8.dp.roundToPx() }
            val above = remember(gap) {
                object : androidx.compose.ui.window.PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: androidx.compose.ui.unit.IntRect, windowSize: androidx.compose.ui.unit.IntSize,
                        layoutDirection: androidx.compose.ui.unit.LayoutDirection, popupContentSize: androidx.compose.ui.unit.IntSize,
                    ) = IntOffset(anchorBounds.left, (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0))
                }
            }
            androidx.compose.ui.window.Popup(popupPositionProvider = above, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                Column(
                    Modifier.width(with(density) { barWidth.toDp() }),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (mention != null) {
                        // "@" (1.584, tutta l'app dalla 1.588): note, promemoria e cose da fare, abitudini, pacchi, documenti,
                        // sezioni. Diventa un link che si apre con un tocco.
                        val data by NotesStore.data.collectAsStateWithLifecycle()
                        val docs by DocumentsStore.docs.collectAsStateWithLifecycle()
                        // Gli eventi del telefono (1.598): letti una volta quando si apre "@", se c'è il permesso del calendario.
                        var events by remember { mutableStateOf<List<com.platinum20.dashboard.calendar.CalendarEvent>>(emptyList()) }
                        LaunchedEffect(Unit) {
                            if (com.platinum20.dashboard.calendar.CalendarRepository.hasPermission(context)) events = runCatching {
                                com.platinum20.dashboard.calendar.CalendarRepository.eventsFrom(context, java.time.LocalDate.now().minusDays(30), 120).values.flatten()
                            }.getOrDefault(emptyList())
                        }
                        val shifts by com.platinum20.dashboard.shifts.ShiftStore.shifts.collectAsStateWithLifecycle()
                        val playlists by com.platinum20.dashboard.media.spotify.SpotifyStore.playlists.collectAsStateWithLifecycle()
                        val lists = playlists.data.orEmpty()
                        val hits = remember(data, docs, events, mention.second, shifts, lists) {
                            mentionTargets(data, docs, mention.second, events, shifts = shifts, playlists = lists)
                        }
                        FloatPanel {
                            @OptIn(ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (hits.isEmpty()) BarLabel(Icons.Outlined.Close, if (mention.second.isEmpty()) "Scrivi un nome" else "Niente con \"${mention.second}\"") {}
                                hits.take(8).forEach { h ->
                                    BarLabel(h.icon, h.title.take(28)) {
                                        haptic(Haptic.TICK)
                                        val now = target.value()
                                        val m = mentionQuery(now.text, now.selection.end) ?: return@BarLabel
                                        val e = NoteFormat.mentionLink(now.text, m.first, now.selection.end, h.title, h.url)
                                        target.set(TextFieldValue(e.text, TextRange(e.start)))
                                    }
                                }
                            }
                        }
                    }
                    if (showFormats && mention == null) {
                        val active = NoteFormat.active(v.text, v.selection.min, v.selection.max)
                        // Una fila sola (Antonio 2026-11-24): titolo, grassetto, corsivo, barrato, evidenzia, colore, citazione,
                        // riquadro. "Trasforma in" non c'è più: il tipo di riga si cambia già da qui, dalla barra e dal fumetto della riga.
                        // Una bolla sola (Antonio 2026-11-24): a sinistra grassetto, corsivo, barrato, evidenzia, colore e titolo; dopo il
                        // divisore, a destra, citazione e riquadro.
                        val fs = (with(density) { barWidth.toDp() } - 10.dp - 48.dp - 11.dp) / 8
                        val fside = fs.coerceIn(32.dp, 42.dp)
                        FloatPanel {
                            BarIcon(Icons.Outlined.FormatBold, "Grassetto", on = NoteFormat.Kind.BOLD in active, side = fside) { mark(NoteFormat.BOLD) }
                            BarIcon(Icons.Outlined.FormatItalic, "Corsivo", on = NoteFormat.Kind.ITALIC in active, side = fside) { mark(NoteFormat.ITALIC) }
                            BarIcon(Icons.Outlined.StrikethroughS, "Barrato", on = NoteFormat.Kind.STRIKE in active, side = fside) { mark(NoteFormat.STRIKE) }
                            BarIcon(Icons.Outlined.BorderColor, "Evidenzia", on = NoteFormat.Kind.HIGHLIGHT in active, side = fside) { mark(NoteFormat.HIGHLIGHT) }
                            BarIcon(Icons.Outlined.Palette, "Colore del testo o dello sfondo", on = NoteFormat.Kind.COLOR in active, side = fside) {
                                haptic(Haptic.TICK)
                                val was = target.value().let { NoteFormat.colorHere(it.text, it.selection.min, it.selection.max) }
                                ActionBubble.show(BubbleHead("Colore"), *colorActions(was) { code ->
                                    val now = target.value()
                                    val e = NoteFormat.color(now.text, now.selection.min, now.selection.max, code)
                                    target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
                                }.toTypedArray())
                            }
                            BarIcon(Icons.Outlined.Title, "Titolo", on = line.kind == NoteLines.Kind.HEADING, side = fside) { kind(NoteLines.Kind.HEADING) }
                            BarSeparator()
                            BarIcon(Icons.Outlined.FormatQuote, "Citazione", on = line.kind == NoteLines.Kind.QUOTE, side = fside) { kind(NoteLines.Kind.QUOTE) }
                            BarIcon(Icons.Outlined.Lightbulb, "Riquadro con emoji", on = line.kind == NoteLines.Kind.CALLOUT, side = fside) { kind(NoteLines.Kind.CALLOUT) }
                        }
                    }
                    // Una data scritta nella riga (1.588) o il cursore su un link: una chip a destra, sopra la barra.
                    if (mention == null && (dated != null || linkHere != null)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                            linkHere?.let { (url, label) -> FloatChip(Icons.AutoMirrored.Outlined.OpenInNew, "Apri il link") { openLink(url, label) } }
                            datedShown?.let { (_, due) -> FloatChip(Icons.Outlined.Event, dueLabel(due) + " → Agenda") { toCalendar() } }
                        }
                    }
                }
            }
        }
        // La barra (2026-11-24, proposte 1 e 2): otto tasti fissi, sempre negli stessi punti, niente da scorrere.
        // + · Annulla · Ripeti | Punti · Numeri · Spunta | Aa · chiudi la tastiera.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val side = ((maxWidth - 38.dp) / 8).coerceIn(34.dp, 42.dp)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.Outlined.Add, "Aggiungi", plus = true, side = side) { add() }
                BarIcon(Icons.AutoMirrored.Outlined.Undo, "Annulla", enabled = target.canUndo(), side = side) { haptic(Haptic.TICK); target.undo() }
                BarIcon(Icons.AutoMirrored.Outlined.Redo, "Ripeti", enabled = target.canRedo(), side = side) { haptic(Haptic.TICK); target.redo() }
                BarSeparator()
                BarIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Elenco puntato", on = line.kind == NoteLines.Kind.BULLET, side = side) { kind(NoteLines.Kind.BULLET) }
                BarIcon(Icons.Outlined.FormatListNumbered, "Elenco numerato", on = line.kind == NoteLines.Kind.NUMBER, side = side) { kind(NoteLines.Kind.NUMBER) }
                BarIcon(Icons.Outlined.CheckBoxOutlineBlank, "Voce da spuntare", on = line.kind == NoteLines.Kind.CHECK, side = side) { kind(NoteLines.Kind.CHECK) }
                BarSeparator()
                BarIcon(Icons.Outlined.TextFormat, "Formati, colori, link, titolo, citazione, riquadro", on = showFormats, side = side) {
                    haptic(Haptic.TICK)
                    if (showFormats) {
                        formats = false
                        if (selecting) target.set(v.copy(selection = TextRange(v.selection.max)))
                    } else formats = true
                }
                BarIcon(Icons.Outlined.KeyboardHide, "Chiudi la tastiera", side = side) {
                    haptic(Haptic.TICK)
                    formats = false
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
            }
        }
    }
    // Aa acceso resta finché lo spegni; togliere una selezione chiude la fila se Aa era spento.
}

/** Quanto aspettano la chip della data e la maniglia ⋮⋮ dopo l'ultima lettera (2026-11-24, proposta 4). */
private const val CALM_MS = 500L

/**
 * Quello che compare sopra la barra sale dalla barra (2026-11-24, proposta 4): parte un filo più in basso e trasparente
 * e si posa con la molla viva, invece di comparire in un fotogramma.
 */
private fun Modifier.popIn(): Modifier = composed {
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, Motion.lively()) }
    val rise = with(LocalDensity.current) { 10.dp.toPx() }
    graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = rise * (1f - p.value)
        val k = 0.96f + 0.04f * p.value
        scaleX = k
        scaleY = k
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}

/** Una fila di tasti in vetro sopra la barra: come il menu "/". */
@Composable
private fun FloatPanel(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .popIn()
            .shadow(10.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f))
            .border(glassRim(), shape)
            .padding(5.dp),
        content = content,
    )
}

/** La chip blu che galleggia sopra la barra: la data della riga nel Calendario, il link da aprire. */
@Composable
private fun FloatChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(21.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .popIn()
            .heightIn(min = 40.dp)
            .bouncyPress(0.92f)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(colors.primary)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary, maxLines = 1)
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
            .background(colors.glassTile)
            .border(glassRim(), RoundedCornerShape(21.dp))
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
private fun BarIcon(
    icon: ImageVector, description: String, on: Boolean = false, enabled: Boolean = true,
    /** Il + (2026-11-24): pieno come il tasto tondo "Aggiungi" delle pagine. */
    plus: Boolean = false,
    side: androidx.compose.ui.unit.Dp = 42.dp,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        plus -> colors.surface
        on -> colors.onPrimary
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(side)
            .then(if (enabled) Modifier.bouncyPress(0.9f) else Modifier)
            .clip(CircleShape)
            .background(if (plus) colors.onSurface else if (on) colors.primary else colors.glassTile)
            .border(glassRim(), CircleShape)
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
            firstAir = false,
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
            .background(colors.glassCard)
            .border(glassRim(), RoundedCornerShape(50))
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

/** "Trasforma in" (1.590): la riga diventa un'altra cosa; il chip acceso è quello che è adesso. */
internal fun transformAction(now: NoteLines.Kind, to: (NoteLines.Kind) -> Unit): BubbleAction = selector(
    "È una riga di",
    *LINE_KINDS.map { (icon, label, k) -> BubbleChip(label, selected = now == k, icon = icon) { to(k) } }.toTypedArray(),
)

/** I tipi di riga con le icone della barra sopra la tastiera: un selettore, quello di adesso si allarga col nome. */
internal val LINE_KINDS = listOf(
    Triple(Icons.AutoMirrored.Outlined.Notes, "Testo", NoteLines.Kind.TEXT),
    Triple(Icons.Outlined.Title, "Titolo", NoteLines.Kind.HEADING),
    Triple(Icons.AutoMirrored.Outlined.FormatListBulleted, "Punti", NoteLines.Kind.BULLET),
    Triple(Icons.Outlined.FormatListNumbered, "Numeri", NoteLines.Kind.NUMBER),
    Triple(Icons.Outlined.CheckBox, "Spunta", NoteLines.Kind.CHECK),
    Triple(Icons.Outlined.FormatQuote, "Citazione", NoteLines.Kind.QUOTE),
    Triple(Icons.Outlined.Lightbulb, "Riquadro", NoteLines.Kind.CALLOUT),
)

/** La testata del fumetto di una riga: che riga è e le sue parole. */
internal fun lineHead(line: NoteLines.Line, text: String): BubbleHead = BubbleHead(
    when (line.kind) {
        NoteLines.Kind.DIVIDER -> "Linea"
        NoteLines.Kind.CALLOUT -> "Riquadro"
        NoteLines.Kind.QUOTE -> "Citazione"
        NoteLines.Kind.CHECK -> "Voce"
        else -> "Riga"
    },
    NoteLines.plainLine(text.substring(line.start, line.end)).take(40),
)

/** I colori del testo: lettera del segno, nome, in ordine cromatico (rosso → rosa, sette per riga). */
internal val NOTE_COLORS = listOf(
    'r' to "Rosso", 'a' to "Arancio", 'g' to "Giallo", 'v' to "Verde", 'b' to "Blu", 'p' to "Viola", 'f' to "Rosa",
)

/** Il colore vero di una lettera: minuscola = testo, maiuscola = sfondo (più chiaro). */
internal fun noteColor(code: Char): androidx.compose.ui.graphics.Color {
    val c = when (code.lowercaseChar()) {
        'r' -> androidx.compose.ui.graphics.Color(0xFFE5534B)
        'a' -> androidx.compose.ui.graphics.Color(0xFFF0883E)
        'v' -> androidx.compose.ui.graphics.Color(0xFF3FB950)
        'g' -> androidx.compose.ui.graphics.Color(0xFFD9A520)
        'b' -> androidx.compose.ui.graphics.Color(0xFF4C8DF6)
        'f' -> androidx.compose.ui.graphics.Color(0xFFE45A9B)
        else -> androidx.compose.ui.graphics.Color(0xFFA371F7)
    }
    return if (code.isUpperCase()) c.copy(alpha = 0.28f) else c
}

/**
 * Il fumetto dei colori: testo o sfondo. Niente tasto per togliere (Antonio 2026-11-24): il colore acceso si
 * ritocca e si spegne, nessun colore scelto = nessun colore.
 */
internal fun colorActions(now: Char? = null, apply: (Char?) -> Unit): List<BubbleAction> = listOf(
    // Pallini piccoli su una riga, come le emoji (Antonio 2026-11-24).
    dots("Testo", *NOTE_COLORS.map { (c, n) -> colorDot(c, n, now, apply) }.toTypedArray()),
    dots("Sfondo", *NOTE_COLORS.map { (c, n) -> colorDot(c.uppercaseChar(), n, now, apply) }.toTypedArray()),
)

private fun colorDot(code: Char, name: String, now: Char?, apply: (Char?) -> Unit) =
    BubbleChip(name, selected = now == code, color = noteColor(code)) { apply(colorPick(now, code)) }

/** Pura: toccare il colore che c'è già lo toglie, un altro lo mette. */
internal fun colorPick(now: Char?, tapped: Char): Char? = if (now == tapped) null else tapped

/** Con la tastiera già aperta il campo mette il cursore anche più di un secondo dopo il tocco (aspetta il doppio tocco). */
internal const val TAP_WINDOW_MS = 2_500L

/** Come la pagina tiene la riga del cursore: solo in vista, scrivendo (sale a un terzo), toccata (in cima). */
enum class LineFollow { MOVE, TYPING, TAPPED }

/**
 * La riga sta in cima? Sì quando un tocco ha appena messo il cursore (non una selezione), e resta sì mentre la tastiera
 * si apre; scrivere o spostare il cursore in altro modo la rimette normale.
 */
internal fun followMode(typing: Boolean, moved: Boolean, collapsed: Boolean, sinceTapMs: Long, wasAtTop: Boolean): Boolean = when {
    typing -> false
    moved -> sinceTapMs in 0..TAP_WINDOW_MS && collapsed
    else -> wasAtTop
}

/** Un tocco dopo il testo: [raw] = dove il campo mette il cursore, [goal] = dove deve andare. */
internal data class TapEnd(val raw: Int, val goal: Int, val at: Long)

/**
 * La fine di una riga disegnata: l'"a capo" fra due paragrafi è disegnato come spazio di larghezza zero e sta in fondo
 * alla riga, così il cursore va prima di lui (dopo, sarebbe già la riga sotto).
 */
internal fun rowEnd(shown: CharSequence, end: Int): Int {
    val e = end.coerceIn(0, shown.length)
    return if (e > 0 && (shown[e - 1] == '\u200B' || shown[e - 1] == '\n')) e - 1 else e
}

/** Vede i tocchi brevi sul campo senza consumarli (il campo mette lo stesso il cursore); un trascinamento non conta. */
private fun Modifier.tapWatch(onDown: (androidx.compose.ui.geometry.Offset) -> Unit = {}, onTap: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onDown(down.position)
        var far = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) far = true
            if (!change.pressed) {
                if (!far && change.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) onTap()
                return@awaitEachGesture
            }
        }
    }
}
