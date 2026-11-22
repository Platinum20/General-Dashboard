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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import com.platinum20.dashboard.notes.NotesStore

/**
 * Il testo con le righe (1.555, Antonio scelta A di /mnt/project-files/editor-note-2/): punti, voci da spuntare e
 * titoletti mescolati nella stessa nota, la barra Punti · Spunta · Titolo · Aa sopra la tastiera. Lo usano le note
 * lunghe e il campo "Note" di eventi, promemoria e date.
 */
object RichBar {
    /** Il campo che sta scrivendo adesso: la barra lavora su di lui. */
    class Target(val owner: Any, val value: () -> TextFieldValue, val set: (TextFieldValue) -> Unit)

    var target by mutableStateOf<Target?>(null)
        internal set
}

/** La barra si vede solo mentre un campo con le righe scrive e la tastiera è aperta. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun richBarShown(): Boolean = RichBar.target != null && WindowInsets.isImeVisible

/**
 * Il posto di cerchio e pallino: lo spazio sottile al posto del segno, allargato con la spaziatura delle lettere.
 * Niente ParagraphStyle (1.556): ogni voce diventava un paragrafo a sé e fra le righe compariva una riga vuota.
 */
private val INDENT = 32.sp

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
) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    val colors = MaterialTheme.colorScheme
    val owner = remember { Any() }
    val current by rememberUpdatedState(value)
    val emit by rememberUpdatedState(onValueChange)
    var focused by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    /** [typed] = l'ha cambiato la tastiera; falso = un tasto della barra, che sa già cosa fa. */
    fun change(v: TextFieldValue, typed: Boolean = true) {
        val old = current
        var next = v
        if (typed && v.text != old.text && v.selection.collapsed) {
            NoteLines.afterEdit(old.text, v.text, v.selection.end)?.let { next = TextFieldValue(it.text, TextRange(it.start, it.end)) }
            // I segni dei formati non si vedono (1.577): la tastiera non deve poterli rompere.
            NoteFormat.afterEdit(old.text, old.selection.start, old.selection.end, next.text, next.selection.end)?.let {
                next = TextFieldValue(it.text, TextRange(it.start, it.end))
            }
        }
        // Un formato acceso e rimasto vuoto sparisce quando il cursore va altrove.
        NoteFormat.tidy(next.text, next.selection.start, next.selection.end)?.let { next = TextFieldValue(it.text, TextRange(it.start, it.end)) }
        // Il cursore non resta mai dentro "[ ] ": scrivendo lì la voce si romperebbe.
        val safe = if (next.selection.collapsed) {
            val at = NoteLines.outOfPrefix(next.text, next.selection.start)
            if (at != next.selection.start) next.copy(selection = TextRange(at)) else next
        } else next
        emit(safe)
    }

    DisposableEffect(focused, readOnly) {
        if (focused && !readOnly) RichBar.target = RichBar.Target(owner, { current }, { change(it, typed = false) })
        onDispose { if (RichBar.target?.owner == owner) RichBar.target = null }
    }

    val faded = colors.onSurfaceVariant
    val transformation = remember(faded) {
        VisualTransformation { input -> richText(input.text, faded) }
    }

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

    Box(modifier) {
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
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
            val shown = NoteLines.Shown(text, NoteFormat.hidden(text))
            val density = LocalDensity.current
            val drawn = l.layoutInput.text.length
            shown.lines.filter { it.isList && shown.toShown(it.start) < drawn }.forEach { line -> androidx.compose.runtime.key(line.index) {
                val at = shown.toShown(line.start)
                val place = l.getBoundingBox(at)
                val row = l.getLineForOffset(at)
                val top = l.getLineTop(row)
                val height = with(density) { (l.getLineBottom(row) - top).toDp() }
                // Tutto il rientro fino al testo: il riquadro del solo spazio sottile è largo un filo e schiacciava i cerchi (1.559).
                val width = with(density) { (l.getHorizontalPosition(at + 1, true) - place.left).coerceAtLeast(place.width).toDp() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(place.left.toInt(), top.toInt()) }
                        .size(width, height)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (line.kind == NoteLines.Kind.CHECK) (if (line.done) "Togli la spunta" else "Spunta") else null,
                            onLongClick = {
                                haptic(Haptic.LONG_PRESS)
                                ActionBubble.show(*lineActions(context, line, current, emit, sendToTodo).toTypedArray())
                            },
                            onClick = {
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
                    LineMark(line)
                }
            } }
        }
    }
}

/** Il cerchio vuoto, il cerchio pieno con la spunta, o il pallino del punto. */
@Composable
private fun LineMark(line: NoteLines.Line) {
    val colors = MaterialTheme.colorScheme
    when {
        // requiredSize: tondi sempre, anche se il posto è più stretto.
        line.kind == NoteLines.Kind.BULLET -> Box(Modifier.requiredSize(6.dp).clip(CircleShape).background(colors.onSurface))
        line.done -> Box(contentAlignment = Alignment.Center, modifier = Modifier.requiredSize(19.dp).clip(CircleShape).background(colors.primary)) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(13.dp))
        }
        else -> Box(Modifier.requiredSize(19.dp).border(1.6.dp, colors.onSurfaceVariant, CircleShape))
    }
}

/**
 * Il testo disegnato: formati, titoletti, voci fatte barrate e sbiadite, il rientro di punti e voci. I segni dei formati
 * ([NoteFormat.hidden]) e delle voci ([NoteLines.Shown]) non si vedono mai (1.577).
 */
private fun richText(text: String, faded: androidx.compose.ui.graphics.Color): TransformedText {
    val styled = buildAnnotatedString {
        append(text)
        NoteFormat.spans(text).forEach { s ->
            val style = when (s.kind) {
                NoteFormat.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                NoteFormat.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteFormat.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                NoteFormat.Kind.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                NoteFormat.Kind.MARK -> null
            }
            if (style != null) addStyle(style, s.start, s.end)
        }
        NoteLines.parse(text).filter { it.kind == NoteLines.Kind.CHECK && it.done && it.end > it.contentStart }.forEach {
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = faded), it.contentStart, it.end)
        }
    }
    val view = NoteLines.Shown(text, NoteFormat.hidden(text))
    if (view.cuts.isEmpty()) return TransformedText(styled, OffsetMapping.Identity)
    val shown = buildAnnotatedString {
        var at = 0
        view.cuts.forEach { c ->
            append(styled.subSequence(at, c.start))
            append(c.with)
            at = c.end
        }
        append(styled.subSequence(at, text.length))
        view.lines.filter { it.isList && it.prefix > 0 }.forEach { line ->
            val s = view.toShown(line.start)
            if (s < length) addStyle(SpanStyle(letterSpacing = INDENT), s, s + 1)
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
        if (line.kind == NoteLines.Kind.CHECK) BubbleAction(Icons.AutoMirrored.Outlined.FormatListBulleted, "Diventa un punto") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.BULLET)) }
        else BubbleAction(Icons.Outlined.RadioButtonUnchecked, "Diventa da spuntare") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.CHECK)) },
        BubbleAction(Icons.AutoMirrored.Outlined.Notes, "Diventa testo") { set(NoteLines.setLineKind(text, line.index, NoteLines.Kind.TEXT)) },
        if (line.index > 0) BubbleAction(Icons.Outlined.ArrowUpward, "Sposta su") { set(NoteLines.move(text, line.index, up = true)) } else null,
        if (line.index < count - 1) BubbleAction(Icons.Outlined.ArrowDownward, "Sposta giù") { set(NoteLines.move(text, line.index, up = false)) } else null,
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
 * La barra sopra la tastiera: Punti, Spunta, Titolo (acceso = com'è la riga del cursore) e Aa. Con del testo scelto, o
 * dopo Aa, diventa grassetto, corsivo, barrato; la X torna alla barra delle righe.
 */
@Composable
internal fun RichToolbar(modifier: Modifier = Modifier) {
    val target = RichBar.target ?: return
    val haptic = rememberHaptic()
    val v = target.value()
    var formats by remember(target.owner) { mutableStateOf(false) }
    val selecting = !v.selection.collapsed
    val line = NoteLines.lineAt(v.text, v.selection.min.coerceIn(0, v.text.length))
    fun kind(k: NoteLines.Kind) {
        haptic(Haptic.TICK)
        val e = NoteLines.setKind(v.text, v.selection.min, v.selection.max, k)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    fun mark(m: String) {
        haptic(Haptic.TICK)
        val e = NoteFormat.toggle(v.text, v.selection.min, v.selection.max, m)
        target.set(TextFieldValue(e.text, TextRange(e.start, e.end)))
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.horizontalScroll(rememberScrollState()),
    ) {
        if (selecting || formats) {
            val active = NoteFormat.active(v.text, v.selection.min, v.selection.max)
            BarChip(null, "G", "Grassetto", NoteFormat.Kind.BOLD in active, SpanStyle(fontWeight = FontWeight.Bold)) { mark(NoteFormat.BOLD) }
            BarChip(null, "C", "Corsivo", NoteFormat.Kind.ITALIC in active, SpanStyle(fontStyle = FontStyle.Italic)) { mark(NoteFormat.ITALIC) }
            BarChip(null, "B", "Barrato", NoteFormat.Kind.STRIKE in active, SpanStyle(textDecoration = TextDecoration.LineThrough)) { mark(NoteFormat.STRIKE) }
            BarChip(Icons.Outlined.Title, "Titolo", "Titoletto", line.kind == NoteLines.Kind.HEADING) { kind(NoteLines.Kind.HEADING) }
            BarChip(Icons.Outlined.Close, null, "Torna a punti e spunte", false) {
                haptic(Haptic.TICK)
                formats = false
                if (selecting) target.set(v.copy(selection = TextRange(v.selection.max)))
            }
        } else {
            BarChip(Icons.AutoMirrored.Outlined.FormatListBulleted, "Punti", "Elenco puntato", line.kind == NoteLines.Kind.BULLET) { kind(NoteLines.Kind.BULLET) }
            BarChip(Icons.Outlined.RadioButtonUnchecked, "Spunta", "Voce da spuntare", line.kind == NoteLines.Kind.CHECK) { kind(NoteLines.Kind.CHECK) }
            BarChip(Icons.Outlined.Title, "Titolo", "Titoletto", line.kind == NoteLines.Kind.HEADING) { kind(NoteLines.Kind.HEADING) }
            BarChip(null, "Aa", "Grassetto, corsivo, barrato", false) {
                haptic(Haptic.TICK)
                formats = true
            }
        }
    }
}

/** Un tasto della barra: acceso = blu, come tutte le azioni. */
@Composable
private fun BarChip(icon: ImageVector?, label: String?, description: String, on: Boolean, style: SpanStyle? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint = if (on) colors.onPrimary else colors.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .bouncyPress(0.92f)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) colors.primary else colors.card)
            .border(cardBorder(), RoundedCornerShape(20.dp))
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = if (label == null) 11.dp else 14.dp, vertical = 9.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = if (label == null) description else null, tint = tint, modifier = Modifier.size(18.dp))
        if (icon != null && label != null) Spacer(Modifier.width(6.dp))
        if (label != null) {
            Text(
                if (style != null) AnnotatedString(label, spanStyles = listOf(AnnotatedString.Range(style, 0, label.length))) else AnnotatedString(label),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
    }
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
