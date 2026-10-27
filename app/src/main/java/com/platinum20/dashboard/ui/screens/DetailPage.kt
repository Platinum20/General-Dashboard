package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * La pagina a schermo intero di una cosa sola, nata con gli eventi del calendario: Indietro a sinistra,
 * Modifica ed Elimina a destra, sotto titolo e schede. Ogni tipo (eventi, promemoria, date, pacchi,
 * abitudini, spese) usa questa, così si aprono tutti allo stesso modo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPage(
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        snackbarHost = { snackbar?.let { SnackbarHost(it) } },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    onEdit?.let { RoundIconButton(Icons.Outlined.Edit, "Modifica", onClick = it) }
                    onDelete?.let { RoundIconButton(Icons.Outlined.Delete, "Elimina", onClick = it) }
                    if (onEdit != null || onDelete != null) Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Il titolo grande col pallino colorato davanti (il colore del calendario, o del tipo). */
@Composable
fun DetailTitle(title: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Text(title.ifBlank { "(Senza titolo)" }, style = MaterialTheme.typography.headlineSmall)
    }
}

/** Una riga della scheda: icona grigia e testo. */
@Composable
fun DetailRow(icon: ImageVector, text: String, maxLines: Int = 3) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Una riga piccola in fondo che spiega qualcosa. */
@Composable
fun DetailNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

/** Quando la cosa è stata eliminata altrove. */
@Composable
fun DetailMissing(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Il titolo che si scrive direttamente nella pagina (Antonio: niente dialogo per modificare). Si salva
 * da solo mezzo secondo dopo l'ultima lettera; vuoto non si salva.
 */
@Composable
fun EditableDetailTitle(key: Any, value: String, dot: Color, onCommit: (String) -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(value) }
    val commit by androidx.compose.runtime.rememberUpdatedState(onCommit)
    androidx.compose.runtime.LaunchedEffect(text) {
        kotlinx.coroutines.delay(500)
        if (text.isNotBlank() && text.trim() != value.trim()) commit(text.trim())
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Titolo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
                inner()
            },
        )
    }
}

/** Una riga che si tocca per cambiarla (data, ripetizione, categoria…), con la X per toglierla se si può. */
@Composable
fun DetailTapRow(icon: ImageVector, text: String, onClick: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().bouncyPress(0.98f).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { DetailRow(icon, text) }
        onClear?.let {
            androidx.compose.material3.IconButton(onClick = it) {
                Icon(Icons.Outlined.Close, contentDescription = "Togli", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Una riga in cui si scrive (importo, codice del pacco): si salva da sola quando smetti di scrivere. */
@Composable
fun DetailFieldRow(key: Any, icon: ImageVector, value: String, placeholder: String, number: Boolean = false, onCommit: (String) -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(value) }
    val commit by androidx.compose.runtime.rememberUpdatedState(onCommit)
    androidx.compose.runtime.LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text.trim() != value.trim()) commit(text.trim())
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
                capitalization = if (number) androidx.compose.ui.text.input.KeyboardCapitalization.None else androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                inner()
            },
        )
    }
}
