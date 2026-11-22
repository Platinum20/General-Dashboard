package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * La tabella dentro la nota, come su Notion (1.590): niente finestra a parte, si scrive nelle caselle. La prima riga è in
 * grassetto. Mentre si scrive nella tabella ([active]) compaiono il + a destra (colonna nuova) e quello sotto (riga
 * nuova); tieni premuta una casella = le scelte della sua riga e colonna. [heights] = l'altezza di ogni riga, presa
 * dalle righe di testo nascoste sotto; [total] = tutto lo spazio, compreso quello del + sotto.
 */
@Composable
internal fun NoteTableBlock(
    grid: List<List<String>>,
    heights: List<Dp>,
    total: Dp,
    active: Boolean,
    readOnly: Boolean,
    want: Pair<Int, Int>?,
    modifier: Modifier,
    onWanted: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onCell: (Int, Int, String) -> Unit,
    onNext: (Int, Int) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onLongPress: (Int, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tableH = heights.fold(0.dp) { a, b -> a + b }
    val line = colors.outlineVariant.copy(alpha = 0.6f)
    Box(modifier.height(total)) {
        Row(Modifier.fillMaxWidth().height(tableH), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.card)
                    .border(cardBorder(), RoundedCornerShape(10.dp)),
            ) {
                grid.forEachIndexed { r, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(heights.getOrElse(r) { heights.last() })
                            .drawBehind { if (r > 0) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) },
                    ) {
                        row.forEachIndexed { c, cell ->
                            TableCell(
                                text = cell, r = r, c = c, readOnly = readOnly, want = want, line = line,
                                onWanted = onWanted, onFocus = onFocus, onCell = onCell, onNext = onNext, onLongPress = onLongPress,
                            )
                        }
                    }
                }
            }
            if (active && !readOnly) PlusStrip(Modifier.width(22.dp).fillMaxHeight(), "Aggiungi una colonna", onAddColumn)
        }
        if (active && !readOnly) {
            PlusStrip(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(end = 28.dp, bottom = 3.dp)
                    .fillMaxWidth()
                    .height((total - tableH - 8.dp).coerceIn(14.dp, 24.dp)),
                "Aggiungi una riga",
                onAddRow,
            )
        }
    }
}

/** Una casella: si scrive dentro; da chiusa un tocco la apre, tieni premuto = le scelte. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.TableCell(
    text: String,
    r: Int,
    c: Int,
    readOnly: Boolean,
    want: Pair<Int, Int>?,
    line: androidx.compose.ui.graphics.Color,
    onWanted: () -> Unit,
    onFocus: (Boolean) -> Unit,
    onCell: (Int, Int, String) -> Unit,
    onNext: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // Il testo della casella si tiene qui mentre si scrive: nella riga "| a | b |" gli spazi in fondo spariscono, e
    // senza questo non si potrebbe scrivere lo spazio fra due parole.
    var local by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    if (local.text.trim() != text) local = TextFieldValue(text, TextRange(text.length))
    LaunchedEffect(want) {
        if (want == (r to c)) {
            runCatching { focus.requestFocus() }
            onWanted()
        }
    }
    val style = MaterialTheme.typography.bodyMedium.copy(
        color = colors.onSurface,
        fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .drawBehind { if (c > 0) drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
            .then(if (focused) Modifier.border(2.dp, colors.primary, RoundedCornerShape(6.dp)) else Modifier),
    ) {
        BasicTextField(
            value = local,
            onValueChange = { v ->
                local = v
                if (v.text != text) onCell(r, c, v.text)
            },
            readOnly = readOnly,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext(r, c) }),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused != focused) {
                        focused = it.isFocused
                        onFocus(it.isFocused)
                    }
                },
            decorationBox = { field ->
                Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) { field() }
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
                        onLongClick = if (readOnly) null else ({ onLongPress(r, c) }),
                    ),
            )
        }
    }
}

/** La striscia sottile con il + accanto alla tabella. */
@Composable
private fun PlusStrip(modifier: Modifier, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.card)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = label, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}
