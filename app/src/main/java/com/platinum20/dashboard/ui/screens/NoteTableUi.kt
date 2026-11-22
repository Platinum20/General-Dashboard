package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/**
 * La tabella si cambia qui (1.588): una casella per cella, la prima riga in grassetto. Più riga / più colonna /
 * meno colonna; "Fatto" la rimette nella nota.
 */
@Composable
internal fun TableEditorDialog(start: List<List<String>>, onDone: (List<List<String>>) -> Unit, onDismiss: () -> Unit) {
    var grid by remember { mutableStateOf(start.map { it.toList() }) }
    fun set(r: Int, c: Int, v: String) {
        grid = grid.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, x -> if (j == c) v else x } else row }
    }
    val cols = grid.maxOfOrNull { it.size } ?: 0
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tabella") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    grid.forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEachIndexed { c, cell ->
                                val style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (r == 0) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                BasicTextField(
                                    value = cell,
                                    onValueChange = { set(r, c, it.replace('\n', ' ').replace('|', '/')) },
                                    singleLine = true,
                                    textStyle = style,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    decorationBox = { field ->
                                        Box(
                                            Modifier
                                                .width(110.dp)
                                                .border(cardBorder(), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            if (cell.isEmpty()) Text(if (r == 0) "Titolo" else "", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            field()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CanvasChip(selected = false, onClick = { grid = grid + listOf(List(cols) { "" }) }, label = "+ Riga")
                    if (grid.size > 2) CanvasChip(selected = false, onClick = { grid = grid.dropLast(1) }, label = "− Riga")
                    if (cols < 6) CanvasChip(selected = false, onClick = { grid = grid.map { it + "" } }, label = "+ Colonna")
                    if (cols > 1) CanvasChip(selected = false, onClick = { grid = grid.map { it.dropLast(1) } }, label = "− Colonna")
                }
            }
        },
        confirmButton = { ConfirmButton(onClick = { onDone(grid) }) { Text("Fatto") } },
        dismissButton = { DismissButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
