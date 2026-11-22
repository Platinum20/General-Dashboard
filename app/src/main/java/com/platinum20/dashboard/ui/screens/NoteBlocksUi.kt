package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.platinum20.dashboard.notes.LinkPreview

/**
 * I pezzi nuovi della nota (1.590, idee prese da Notion): due colonne, sotto-nota, anteprima di un link, bacheca.
 * Ognuno sta sopra la sua riga nascosta; l'altezza la misura chi li usa e la riga sotto si allarga quanto serve.
 */

/** Due colonne di testo, una accanto all'altra; si scrive direttamente dentro. Tieni premuto = le scelte. */
@Composable
internal fun NoteColumnsBlock(
    left: String,
    right: String,
    readOnly: Boolean,
    want: Int?,
    modifier: Modifier,
    onWanted: () -> Unit,
    onChange: (String, String) -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
        ColumnText(left, readOnly, want == 0, Modifier.weight(1f), onWanted, { onChange(it, right) }, onLongPress)
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).fillMaxHeight().background(colors.outlineVariant.copy(alpha = 0.7f)))
        ColumnText(right, readOnly, want == 1, Modifier.weight(1f), onWanted, { onChange(left, it) }, onLongPress)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnText(
    text: String,
    readOnly: Boolean,
    wanted: Boolean,
    modifier: Modifier,
    onWanted: () -> Unit,
    onChange: (String) -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (local.text.trim() != text.trim()) local = TextFieldValue(text, TextRange(text.length))
    LaunchedEffect(wanted) {
        if (wanted) {
            runCatching { focus.requestFocus() }
            onWanted()
        }
    }
    val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    Box(modifier.heightIn(min = 28.dp)) {
        BasicTextField(
            value = local,
            onValueChange = { v ->
                local = v
                if (v.text != text) onChange(v.text)
            },
            readOnly = readOnly,
            textStyle = style,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field ->
                Box {
                    if (local.text.isEmpty()) Text("Scrivi…", style = style, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                    field()
                }
            },
        )
        if (!focused) {
            Box(
                Modifier
                    .matchParentSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (!readOnly) runCatching { focus.requestFocus() } },
                        onLongClick = if (readOnly) null else onLongPress,
                    ),
            )
        }
    }
}

/** Una sotto-nota: la sua emoji, il titolo di adesso e la freccia; un tocco la apre. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotePageRow(emoji: String, title: String, modifier: Modifier, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(vertical = 3.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(cardBorder(), RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** L'anteprima di un link: immagine a sinistra, titolo e sito. Finché non è arrivata, il sito e l'indirizzo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteBookmarkCard(url: String, card: LinkPreview.Card?, fallback: String, modifier: Modifier, onOpen: () -> Unit, onLongPress: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(vertical = 4.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(cardBorder(), RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(76.dp).fillMaxHeight().background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            card?.image?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().fillMaxHeight()) }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card?.title ?: fallback,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                card?.site ?: url.substringAfter("://"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Una scheda della bacheca: il testo e in che colonna sta. */
internal class BoardCard(val line: Int, val title: String, val state: Int)

private val BOARD_COLUMNS = listOf("Da fare", "In corso", "Fatto")

/**
 * La bacheca (1.590): le voci da spuntare sotto "[bacheca]" in tre colonne, Da fare, In corso, Fatto.
 * Tocco o tieni premuto su una scheda = spostala, cambiala, toglila; + in fondo a Da fare = una nuova.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteBoard(
    cards: List<BoardCard>,
    readOnly: Boolean,
    modifier: Modifier,
    onCard: (BoardCard) -> Unit,
    onAdd: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(vertical = 4.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BOARD_COLUMNS.forEachIndexed { state, name ->
            val mine = cards.filter { it.state == state }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.onSurface.copy(alpha = 0.05f))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state == 1) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    if (mine.isNotEmpty()) Text("${mine.size}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                mine.forEach { c ->
                    Text(
                        c.title.ifBlank { "Senza nome" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state == 2) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.card)
                            .border(cardBorder(), RoundedCornerShape(8.dp))
                            .combinedClickable(enabled = !readOnly, onClick = { onCard(c) }, onLongClick = { onCard(c) })
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                    )
                }
                if (state == 0 && !readOnly) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = onAdd)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text("Aggiungi", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Il nome di una scheda, nuova o da cambiare: una casella sola, Fatto la salva. */
@Composable
internal fun BoardTextDialog(title: String, start: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var value by remember { mutableStateOf(TextFieldValue(start, TextRange(start.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val style = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
            BasicTextField(
                value = value,
                onValueChange = { value = it.copy(text = it.text.replace('\n', ' ')) },
                textStyle = style,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { field ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .border(cardBorder(), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (value.text.isEmpty()) Text("Cosa c'è da fare", style = style, color = colors.onSurfaceVariant.copy(alpha = 0.6f))
                        field()
                    }
                },
            )
        },
        confirmButton = { ConfirmButton(onClick = { if (value.text.isNotBlank()) onDone(value.text.trim()) else onDismiss() }) { Text("Fatto") } },
        dismissButton = { DismissButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
