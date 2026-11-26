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
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Il tasto in basso a sinistra di una scheda: l'azione di tutti i giorni (Fatto, È arrivato, Modifica). */
internal data class DetailAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * La pagina a schermo intero di una cosa sola, uguale per eventi, promemoria, date, pacchi, abitudini.
 * Dalla 1.315 ha la forma dell'editor delle note (Antonio): in alto solo Indietro e una riga piccola, in fondo
 * a sinistra l'azione principale ([primary], o Modifica) e a destra "Altro", che apre il fumetto di vetro
 * con il resto ([more], Modifica se non sta già a sinistra, Elimina).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailPage(
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** La riga piccola sopra il titolo ("PROMEMORIA", "EVENTO"), come la data di modifica delle note. */
    meta: String? = null,
    primary: DetailAction? = null,
    more: List<BubbleAction> = emptyList(),
    /** La testata del fumetto di "Altro" (lo stesso del tieni premuto, senza Apri); senza, la riga [meta]. */
    head: BubbleHead? = null,
    /** Per ciò che non si può annullare: Elimina si tocca due volte e mostra questa riga. */
    deleteConfirm: String? = null,
    /** Il sottotitolo di Modifica nel fumetto. */
    editDetail: String = "cambia i dettagli",
    /** Lo scorrimento della pagina, per chi deve leggerlo (il lettore: avanzamento e punto da riprendere). */
    scroll: androidx.compose.foundation.ScrollState? = null,
    /** Sotto la riga piccola in alto, per esempio la barra di avanzamento del lettore. */
    topExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = rememberHaptic()
    val left = primary ?: onEdit?.let { DetailAction(Icons.Outlined.Edit, "Modifica", it) }
    val extra = more +
        listOfNotNull(
            onEdit?.takeIf { primary != null }?.let { act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Modifica", editDetail, onClick = it) },
            onDelete?.let { Slot.delete(confirm = deleteConfirm, onClick = it) },
        )
    Scaffold(
        modifier = Modifier.pageFloor(), containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                meta?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp),
                    )
                }
                topExtra?.invoke()
            }
        },
        bottomBar = {
            // Si scrive nel campo Note: la barra delle righe sopra la tastiera al posto dei tasti (1.555), che sfumano uno
            // nell'altro (2026-11-24, proposta 3).
            val writing = richBarShown()
            if (writing || left != null || extra.isNotEmpty()) KeyboardBarSwap(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .miniPlayerFloor()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                writing,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    left?.let { action ->
                        RoundIconButton(action.icon, action.label, onClick = action.onClick)
                    }
                    Text(
                        left?.label.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (extra.isNotEmpty()) {
                        RoundIconButton(Icons.Outlined.MoreHoriz, "Altro") {
                            haptic(Haptic.LONG_PRESS)
                            ActionBubble.show(head ?: meta?.let { BubbleHead(it) }, *extra.toTypedArray())
                        }
                    }
                }
            }
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .verticalScroll(scroll ?: rememberScrollState())
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
fun EditableDetailTitle(key: Any, value: String, dot: Color, sharedKey: String? = null, onCommit: (String) -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(value) }
    val commit by androidx.compose.runtime.rememberUpdatedState(onCommit)
    // La lista da cui si è aperta la scheda, letta una volta: tornando indietro il titolo vola nella stessa riga.
    val origin = androidx.compose.runtime.remember { SharedRows.origin }
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
            // Aprendo dalla riga del calendario, il titolo arriva volando da lì.
            modifier = Modifier.weight(1f).sharedItem(sharedKey?.let { SharedRows.key(origin, it) }),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Titolo", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun DetailFieldRow(
    key: Any, icon: ImageVector, value: String, placeholder: String, number: Boolean = false,
    /** Testo libero (note, luogo): più righe e la maiuscola solo a inizio frase, non tutto maiuscolo come un codice. */
    prose: Boolean = false,
    onCommit: (String) -> Unit,
    /** Un'azione in fondo a destra della riga (es. copia il numero del documento). */
    trailing: (@Composable () -> Unit)? = null,
) {
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
            singleLine = !prose,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if (number) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
                capitalization = when {
                    number -> androidx.compose.ui.text.input.KeyboardCapitalization.None
                    prose -> androidx.compose.ui.text.input.KeyboardCapitalization.Sentences
                    else -> androidx.compose.ui.text.input.KeyboardCapitalization.Characters
                },
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
        trailing?.invoke()
    }
}
