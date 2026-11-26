package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.platinum20.dashboard.notes.NoteFormat

/**
 * I formati dentro le caselle piccole della nota, colonne e tabella (2026-11-26, la guida mostrava "**Rotella**"):
 * grassetto, corsivo, barrato, evidenziato e colori come nel testo, e i segni non si vedono mai.
 */
internal class InlineMarks(private val accent: Color, private val marker: Color = Color(0x66FFD54F)) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText = inlineMarks(text.text, accent, marker)

    override fun equals(other: Any?) = other is InlineMarks && other.accent == accent && other.marker == marker
    override fun hashCode() = accent.hashCode() * 31 + marker.hashCode()
}

/** Il testo con i formati e senza segni, e come si passa da un punto del testo vero a quello disegnato. */
internal fun inlineMarks(raw: String, accent: Color, marker: Color): TransformedText {
    val hidden = NoteFormat.hidden(raw)
    val spans = NoteFormat.spans(raw)
    if (hidden.isEmpty() && spans.isEmpty()) return TransformedText(AnnotatedString(raw), OffsetMapping.Identity)
    val styled = buildAnnotatedString {
        append(raw)
        spans.forEach { s ->
            val style = when (s.kind) {
                NoteFormat.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                NoteFormat.Kind.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                NoteFormat.Kind.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                NoteFormat.Kind.HEADING -> SpanStyle(fontWeight = FontWeight.SemiBold)
                NoteFormat.Kind.HIGHLIGHT -> SpanStyle(background = marker)
                NoteFormat.Kind.COLOR -> NoteFormat.colorAt(raw, s.start)?.let { c ->
                    if (c.isUpperCase()) SpanStyle(background = noteColor(c)) else SpanStyle(color = noteColor(c))
                }
                NoteFormat.Kind.LINK, NoteFormat.Kind.URL -> SpanStyle(color = accent)
                NoteFormat.Kind.MARK -> null
            }
            if (style != null) addStyle(style, s.start, s.end)
        }
    }
    val shown = buildAnnotatedString {
        var at = 0
        hidden.forEach { r ->
            if (r.first > at) append(styled.subSequence(at, r.first))
            at = maxOf(at, r.last + 1)
        }
        if (at < raw.length) append(styled.subSequence(at, raw.length))
    }
    val collapse = NoteFormat.Collapse(hidden)
    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = collapse.toShown(offset).coerceIn(0, shown.length)
        override fun transformedToOriginal(offset: Int) = collapse.toText(offset).coerceIn(0, raw.length)
    }
    return TransformedText(shown, mapping)
}
