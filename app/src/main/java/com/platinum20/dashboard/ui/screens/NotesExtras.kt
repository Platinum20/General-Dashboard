package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.Routes
import android.content.ClipboardManager
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.notes.ParcelHint
import com.platinum20.dashboard.notes.ParcelHints
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import androidx.compose.ui.graphics.graphicsLayer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

/** Una fila di chip che va a capo da sola. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            CanvasChip(selected = i == selected, onClick = { onSelect(i) }, label = label)
        }
    }
}

/**
 * Un pacco in "In arrivo", come un conto alla rovescia: cosa arriva, il corriere e a destra quanto manca
 * ("Domani", "3 giorni"). Il camioncino apre il tracciamento; tenendo premuto si segna arrivato (o non ancora).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParcelCard(parcel: Parcel, onClick: () -> Unit) {
    val inApp = LocalInAppNav.current
    val today = LocalDate.now()
    val haptic = rememberHaptic()
    val expected = parcel.expected
    val late = !parcel.delivered && expected != null && expected.isBefore(today)
    val days = expected?.let { ChronoUnit.DAYS.between(today, it) }
    CanvasCard(
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic(if (parcel.delivered) Haptic.TICK else Haptic.CONFIRM)
                    NotesStore.toggleDelivered(parcel.id)
                },
            ),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    parcel.title.trimEnd('"', '“', '”', ' '),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (parcel.delivered) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        "Pacco",
                        parcel.carrier.label.takeIf { parcel.code.isNotEmpty() },
                        when {
                            parcel.delivered -> "arrivato"
                            late -> "atteso ${dueDay(expected!!, today)}, in ritardo"
                            expected != null && days!! > 1 -> longDate(expected).replaceFirstChar { it.lowercase() }
                            expected == null -> "data non nota"
                            else -> null
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            parcel.trackingUrl()?.takeIf { !parcel.delivered }?.let { url ->
                // La pagina del corriere si apre dentro Canvas, non nel browser.
                IconButton(onClick = { inApp?.openWeb(url, parcel.carrier.label) }) {
                    Icon(Icons.Outlined.LocalShipping, contentDescription = "Traccia", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A destra quanto manca, come per le date: il numero grande e "giorni" sotto.
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                val big = when {
                    parcel.delivered -> "✓"
                    days == null -> "?"
                    late -> "In ritardo"
                    days == 0L -> "Oggi"
                    days == 1L -> "Domani"
                    else -> "$days"
                }
                Text(
                    big,
                    style = if (big.length > 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    color = when {
                        late -> MaterialTheme.colorScheme.error
                        parcel.delivered || days == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                if (!parcel.delivered && days != null && days > 1) {
                    Text("giorni", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun dueDay(date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> "oggi"
    1L -> "domani"
    -1L -> "ieri"
    else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN))
}

/** Nuovo pacco (parcel null) o modifica. [initialCode] arriva da un testo condiviso. */
@Composable
fun ParcelDialog(
    parcel: Parcel?,
    onDismiss: () -> Unit,
    initialTitle: String = "",
    initialCode: String = "",
    onDeleted: (Parcel) -> Unit = {},
) {
    var title by remember { mutableStateOf(parcel?.title ?: initialTitle) }
    var code by remember { mutableStateOf(parcel?.code ?: initialCode) }
    var expected by remember { mutableStateOf(parcel?.expected) }
    var picking by remember { mutableStateOf(false) }
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parcel == null) "Nuovo pacco" else "Pacco") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Cosa arriva (es. Cuffie)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().autoFocus(enabled = parcel == null && initialTitle.isBlank()),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("Codice di tracciamento (facoltativo)") },
                    supportingText = { if (code.isNotBlank()) Text(Carrier.detect(code).label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        expected?.let { "Arriva " + longDate(it) } ?: "Quando arriva?",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (expected != null) {
                        IconButton(onClick = { expected = null }) { Icon(Icons.Outlined.Close, contentDescription = "Togli data") }
                    }
                }
            }
        },
        confirmButton = {
            ConfirmButton(onClick = {
                NotesStore.saveParcel(parcel?.id, title, code, expected)
                onDismiss()
            }, enabled = title.isNotBlank() || code.isNotBlank()) { Text("Salva") }
        },
        dismissButton = {
            DismissButton(danger = parcel != null, onClick = {
                onDismiss()
                parcel?.let {
                    NotesStore.deleteParcel(it.id)
                    onDeleted(it)
                }
            }) { Text(if (parcel == null) "Annulla" else "Elimina") }
        },
    )
    if (picking) {
        DatePickDialog(initial = expected ?: LocalDate.now().plusDays(2), onDismiss = { picking = false }, onConfirm = {
            expected = it
            picking = false
        })
    }
}
