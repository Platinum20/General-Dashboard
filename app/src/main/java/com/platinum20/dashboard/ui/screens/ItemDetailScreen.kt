package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.HabitReminder
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Le cose delle Note che si aprono a schermo intero, con la stessa pagina degli eventi del calendario. */
enum class ItemKind { TASK, DATE, PARCEL, HABIT, EXPENSE, FIXED }

/** Che cosa dice una riga della scheda: decide l'icona. */
enum class LineKind { WHEN, REPEAT, STATUS, CODE, AMOUNT, CATEGORY }

data class DetailLine(val kind: LineKind, val text: String)

/** La scheda di una cosa: titolo e righe, come quella di un evento. */
data class ItemDetail(val title: String, val lines: List<DetailLine>)

private val shortDate = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

private fun daysText(days: Long): String = when (days) {
    0L -> "Oggi"
    1L -> "Domani"
    -1L -> "Ieri"
    in 2..Long.MAX_VALUE -> "Tra $days giorni"
    else -> "${-days} giorni fa"
}

/** Le righe della scheda per ogni tipo; null se la cosa non c'è più. Pura, per i test. */
fun itemDetail(data: NotesData, kind: ItemKind, id: Long, now: LocalDateTime = LocalDateTime.now()): ItemDetail? {
    val today = now.toLocalDate()
    return when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
            ItemDetail(
                t.text.ifBlank { "(Senza titolo)" },
                listOfNotNull(
                    DetailLine(LineKind.WHEN, t.dueAt?.let { "Avviso: " + dueLabel(it) } ?: "Senza scadenza"),
                    t.repeat?.let { DetailLine(LineKind.REPEAT, "Si ripete " + it.label) },
                    DetailLine(LineKind.STATUS, if (t.done) "Fatto" else "Da fare"),
                ),
            )
        }
        ItemKind.DATE -> data.countdowns.firstOrNull { it.id == id }?.let { c ->
            val next = c.nextDate(today)
            ItemDetail(
                c.title,
                listOfNotNull(
                    DetailLine(LineKind.WHEN, longDate(next)),
                    DetailLine(LineKind.STATUS, daysText(c.daysLeft(today)) + (c.yearsAtNext(today)?.let { " · compie $it anni" } ?: "")),
                    if (c.yearly) DetailLine(LineKind.REPEAT, "Ogni anno") else null,
                ),
            )
        }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
            ItemDetail(
                p.title.ifBlank { "Pacco" },
                listOfNotNull(
                    DetailLine(
                        LineKind.WHEN,
                        when {
                            p.deliveredOn != null -> "Arrivato il " + p.deliveredOn.format(shortDate)
                            p.expected != null -> "Arriva " + longDate(p.expected).replaceFirstChar { it.lowercase() }
                            else -> "Data di arrivo non nota"
                        },
                    ),
                    p.code.takeIf { it.isNotBlank() }?.let { DetailLine(LineKind.CODE, "$it · ${p.carrier.label}") },
                    DetailLine(LineKind.STATUS, if (p.delivered) "Arrivato" else "In viaggio"),
                ),
            )
        }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
            ItemDetail(
                h.title,
                listOf(
                    DetailLine(LineKind.REPEAT, if (h.weekly) "${h.perWeek} volte a settimana" else "Ogni giorno"),
                    DetailLine(LineKind.STATUS, h.statusLine(now).text + HabitReminder.suffix(h)),
                ),
            )
        }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e ->
            ItemDetail(
                e.text.ifBlank { e.category.label },
                listOfNotNull(
                    DetailLine(LineKind.AMOUNT, Expenses.format(e.cents)),
                    DetailLine(LineKind.WHEN, longDate(e.date)),
                    DetailLine(LineKind.CATEGORY, e.category.label),
                    e.fixedId?.let { DetailLine(LineKind.REPEAT, "Spesa fissa, aggiunta da sola") },
                ),
            )
        }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { f ->
            ItemDetail(
                f.text.ifBlank { f.category.label },
                listOf(
                    DetailLine(LineKind.AMOUNT, Expenses.format(f.cents) + " al mese"),
                    DetailLine(LineKind.REPEAT, "Il ${f.day} di ogni mese"),
                    DetailLine(LineKind.WHEN, "Prossima " + longDate(f.nextDate(today)).replaceFirstChar { it.lowercase() }),
                    DetailLine(LineKind.CATEGORY, f.category.label),
                ),
            )
        }
    }
}

private fun LineKind.icon(): ImageVector = when (this) {
    LineKind.WHEN -> Icons.Outlined.Schedule
    LineKind.REPEAT -> Icons.Outlined.Repeat
    LineKind.STATUS -> Icons.Outlined.Info
    LineKind.CODE -> Icons.Outlined.QrCode2
    LineKind.AMOUNT -> Icons.Outlined.Euro
    LineKind.CATEGORY -> Icons.Outlined.Category
}

/** Il colore del pallino accanto al titolo, come quello del calendario per gli eventi. */
@Composable
private fun ItemKind.dot(): Color = when (this) {
    ItemKind.TASK, ItemKind.HABIT -> MaterialTheme.colorScheme.primary
    ItemKind.DATE -> MaterialTheme.colorScheme.tertiary
    ItemKind.PARCEL -> MaterialTheme.colorScheme.secondary
    ItemKind.EXPENSE, ItemKind.FIXED -> MaterialTheme.colorScheme.outline
}

private fun undo(message: String, restore: () -> Unit) {
    CaptureFeedback.show(CaptureNotice(CaptureMode.NOTE, message, undo = restore, icon = Icons.Outlined.Delete, fly = false))
}

/**
 * La pagina a schermo intero di promemoria, date, pacchi, abitudini e spese: la stessa dell'evento
 * (titolo col pallino, una scheda di righe, Modifica ed Elimina in alto). Modifica apre il solito dialogo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(kind: ItemKind, id: Long, onBack: () -> Unit) {
    val data by NotesStore.data.collectAsState()
    val haptic = rememberHaptic()
    val inApp = LocalInAppNav.current
    var editing by remember { mutableStateOf(false) }
    val detail = itemDetail(data, kind, id)
    // Eliminata dal dialogo o da qui: si torna indietro e l'"Annulla" resta in fondo.
    fun gone(message: String, restore: () -> Unit) {
        haptic(Haptic.CONFIRM)
        undo(message, restore)
        onBack()
    }
    val delete: (() -> Unit)? = if (detail == null) null else when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t -> { NotesStore.deleteTask(id); gone("Promemoria eliminato") { NotesStore.restoreTask(t) } } }
        ItemKind.DATE -> data.countdowns.firstOrNull { it.id == id }?.let { c -> { NotesStore.deleteCountdown(id); gone("Data eliminata") { NotesStore.restoreCountdown(c) } } }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p -> { NotesStore.deleteParcel(id); gone("Pacco eliminato") { NotesStore.restoreParcel(p) } } }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h -> { NotesStore.deleteHabit(id); gone("Abitudine eliminata") { NotesStore.restoreHabit(h) } } }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e -> { NotesStore.deleteExpense(id); gone("Spesa eliminata") { NotesStore.restoreExpense(e) } } }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { f -> { NotesStore.deleteFixedExpense(id); gone("Spesa fissa tolta") { NotesStore.restoreFixedExpense(f) } } }
    }
    // Le spese fisse non hanno un dialogo: si tolgono e si riscrivono.
    val canEdit = detail != null && kind != ItemKind.FIXED

    DetailPage(
        onBack = onBack,
        onEdit = if (canEdit) ({ editing = true }) else null,
        onDelete = delete,
    ) {
        if (detail == null) {
            DetailMissing(Icons.Outlined.EventBusy, "Questa cosa non c'è più.")
            return@DetailPage
        }
        DetailTitle(detail.title, kind.dot())
        CanvasCard(Modifier.fillMaxWidth()) {
            detail.lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                DetailRow(line.kind.icon(), line.text)
            }
        }
        // L'azione di tutti i giorni a portata di pollice, sotto la scheda.
        val action: Triple<ImageVector, String, () -> Unit>? = when (kind) {
            ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
                Triple(if (t.done) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle, if (t.done) "Da rifare" else "Segna come fatto") {
                    haptic(if (t.done) Haptic.TICK else Haptic.CONFIRM)
                    NotesStore.toggleTask(id)
                }
            }
            ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
                val done = h.isDone(LocalDate.now())
                Triple(if (done) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle, if (done) "Non fatta oggi" else "Fatta oggi") {
                    haptic(if (done) Haptic.TICK else Haptic.CONFIRM)
                    NotesStore.toggleHabit(id)
                }
            }
            ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
                Triple(if (p.delivered) Icons.Outlined.RadioButtonUnchecked else Icons.Outlined.CheckCircle, if (p.delivered) "Non ancora arrivato" else "È arrivato") {
                    haptic(if (p.delivered) Haptic.TICK else Haptic.CONFIRM)
                    NotesStore.toggleDelivered(id)
                }
            }
            else -> null
        }
        val tracking = if (kind == ItemKind.PARCEL) data.parcels.firstOrNull { it.id == id }?.takeIf { !it.delivered }?.let { p ->
            p.trackingUrl()?.let { url -> { inApp?.openWeb?.invoke(url, p.carrier.label); Unit } }
        } else null
        if (action != null || tracking != null) {
            CanvasCard(Modifier.fillMaxWidth()) {
                action?.let { (icon, text, onClick) ->
                    Column(Modifier.fillMaxWidth().bouncyPress(0.98f).clickable(onClick = onClick)) { DetailRow(icon, text) }
                }
                tracking?.let { open ->
                    if (action != null) RowDivider()
                    // La pagina del corriere si apre dentro Canvas, non nel browser.
                    Column(Modifier.fillMaxWidth().bouncyPress(0.98f).clickable(onClick = open)) { DetailRow(Icons.Outlined.LocalShipping, "Traccia la spedizione") }
                }
            }
        }
        if (kind == ItemKind.FIXED) {
            DetailNote("Togliendola, le spese già aggiunte nei mesi passati restano.")
        }
    }

    if (editing) {
        val close = { editing = false }
        when (kind) {
            ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
                TaskEditDialog(t, onDismiss = close, onDeleted = { gone("Promemoria eliminato") { NotesStore.restoreTask(it) } })
            }
            ItemKind.DATE -> data.countdowns.firstOrNull { it.id == id }?.let { c ->
                CountdownDialog(c, onDismiss = close, onDeleted = { gone("Data eliminata") { NotesStore.restoreCountdown(it) } })
            }
            ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
                ParcelDialog(p, onDismiss = close, onDeleted = { gone("Pacco eliminato") { NotesStore.restoreParcel(it) } })
            }
            ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
                HabitDialog(h, onDismiss = close, onDeleted = { gone("Abitudine eliminata") { NotesStore.restoreHabit(it) } })
            }
            ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e ->
                ExpenseDialog(e, onDismiss = close, onDeleted = { gone("Spesa eliminata") { NotesStore.restoreExpense(it) } })
            }
            ItemKind.FIXED -> Unit
        }
    }
}

/** Apre da qualunque lista la pagina di una cosa delle Note (nei test e nelle anteprime non fa nulla). */
@Composable
fun rememberOpenItem(): (ItemKind, Long) -> Unit {
    val inApp = LocalInAppNav.current
    return remember(inApp) { { kind, id -> inApp?.openRoute?.invoke(Routes.noteItem(kind, id)) } }
}
