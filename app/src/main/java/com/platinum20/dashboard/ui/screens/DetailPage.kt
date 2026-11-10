package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Il tasto in basso a sinistra di una scheda: l'azione di tutti i giorni, una pillola col verbo (punto 29).
 * Piena quando è da fare ("Segna fatta oggi"), tonale quando è già fatta ([done], "Fatta oggi").
 */
internal data class DetailAction(val icon: ImageVector, val label: String, val done: Boolean = false, val onClick: () -> Unit)

/**
 * La pagina a schermo intero di una cosa sola, uguale per eventi, promemoria, date, pacchi, abitudini e spese.
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
    /** Lo scorrimento della pagina, per chi deve leggerlo (il lettore: avanzamento e punto da riprendere). */
    scroll: androidx.compose.foundation.ScrollState? = null,
    /** Sotto la riga piccola in alto, per esempio la barra di avanzamento del lettore. */
    topExtra: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = rememberHaptic()
    val left = primary ?: onEdit?.let { DetailAction(Icons.Outlined.Edit, "Modifica", onClick = it) }
    val extra = more +
        listOfNotNull(
            onEdit?.takeIf { primary != null }?.let { BubbleAction(Icons.Outlined.Edit, "Modifica", onClick = it) },
            onDelete?.let { BubbleAction(Icons.Outlined.Delete, "Elimina", danger = true, onClick = it) },
        )
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
            if (left != null || extra.isNotEmpty()) {
                // Come nell'editor delle note: i tasti salgono sopra il mini player invece di finirci sotto.
                val playing by com.platinum20.dashboard.media.Playback.now.collectAsStateWithLifecycle()
                val miniRoom = MiniDock.bottomRoom(playing != null, MiniDock.atTop, MiniDock.barHeight, LocalDensity.current.density)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(bottom = miniRoom.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    left?.let { action ->
                        CanvasButton(action.label, onClick = action.onClick, icon = action.icon, primary = !action.done)
                    }
                    Spacer(Modifier.weight(1f))
                    if (extra.isNotEmpty()) {
                        RoundIconButton(Icons.Outlined.MoreHoriz, "Altro") {
                            haptic(Haptic.LONG_PRESS)
                            ActionBubble.show(*extra.toTypedArray())
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
fun EditableDetailTitle(
    key: Any, value: String, dot: Color, sharedKey: String? = null,
    /** A destra del titolo, sulla stessa riga: l'importo di una spesa (punto 27 B). */
    trailing: (@Composable () -> Unit)? = null,
    onCommit: (String) -> Unit,
) {
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
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

/** L'importo accanto al titolo di una spesa: si scrive lì e si salva da solo, come il titolo. */
@Composable
fun DetailAmountField(key: Any, value: String, onCommit: (String) -> Unit) {
    var text by androidx.compose.runtime.saveable.rememberSaveable(key) { androidx.compose.runtime.mutableStateOf(value) }
    val commit by androidx.compose.runtime.rememberUpdatedState(onCommit)
    androidx.compose.runtime.LaunchedEffect(text) {
        kotlinx.coroutines.delay(600)
        if (text.trim() != value.trim()) commit(text.trim())
    }
    val style = MaterialTheme.typography.headlineSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.End,
        fontFeatureSettings = "tnum",
    )
    // Larga quanto il numero scritto: IntrinsicSize.Min su un campo di testo lo schiacciava a zero (1.433, si vedeva solo "€").
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textPx = measurer.measure(text.ifEmpty { "0" }, style, maxLines = 1).size.width
    val fieldWidth = with(density) { textPx.toDp() } .let { amountFieldWidth(it.value).dp }
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = style,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.width(fieldWidth),
        )
        Text(" €", style = style)
    }
}

/** La larghezza del campo importo in dp: il testo più lo spazio del cursore, tra 24 e 180. Pura, per i test. */
internal fun amountFieldWidth(textDp: Float): Float = (textDp + 4f).coerceIn(24f, 180f)

/** Un chip toccabile con la freccia ›: la categoria di una spesa (tocco = il fumetto con le categorie). */
@Composable
fun DetailChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.card)
            .border(androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            .bouncyPress(0.96f)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Cambia categoria", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    /** Appena aggiunta da "+ Luogo": il cursore è già dentro. */
    focus: Boolean = false,
    onCommit: (String) -> Unit,
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
            modifier = Modifier.weight(1f).autoFocus(focus),
            decorationBox = { inner ->
                if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
        )
    }
}

/**
 * Il "quando" sotto il titolo (punto 26): si legge come una frase ("Domani alle 9:00", sotto "mercoledì 11 novembre")
 * ed è scritto una volta sola. Si tocca per cambiarlo (la freccia › come le altre righe toccabili); la prima volta
 * dice anche "tocca per cambiare".
 */
@Composable
fun DetailWhen(main: String, sub: String, onClick: (() -> Unit)?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Il suggerimento una volta sola: si segna come visto appena compare.
    val hint = androidx.compose.runtime.remember { onClick != null && !GestureHints.seen(context, GestureHints.DETAIL_WHEN) }
    androidx.compose.runtime.LaunchedEffect(hint) { if (hint) GestureHints.mark(context, GestureHints.DETAIL_WHEN) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.bouncyPress(0.98f).clickable(onClick = onClick) else Modifier)
            .padding(start = 26.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(main, style = MaterialTheme.typography.titleMedium)
            val line = listOfNotNull(sub.takeIf { it.isNotBlank() }, "tocca per cambiare".takeIf { hint }).joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Cambia",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Le righe vuote non si mostrano (punto 28): in fondo diventano piccoli inviti "+ Luogo", "+ Note". */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DetailAddRow(labels: List<String>, onAdd: (Int) -> Unit) {
    if (labels.isEmpty()) return
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        labels.forEachIndexed { i, label ->
            CanvasButton(label, onClick = { onAdd(i) }, icon = Icons.Outlined.Add)
        }
    }
}
