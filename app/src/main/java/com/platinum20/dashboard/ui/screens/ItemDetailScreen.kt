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
                    // Con una scadenza si vede sempre se si ripete: è la riga che si tocca per cambiarlo.
                    t.dueAt?.let { DetailLine(LineKind.REPEAT, t.repeat?.let { "Si ripete " + it.label } ?: "Non si ripete") },
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
                    DetailLine(LineKind.REPEAT, if (c.yearly) "Ogni anno" else "Una volta sola"),
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
                    DetailLine(LineKind.CODE, p.code.takeIf { it.isNotBlank() }?.let { "$it · ${p.carrier.label}" } ?: "Nessun codice"),
                    DetailLine(LineKind.STATUS, if (p.delivered) "Arrivato" else "In viaggio"),
                ),
            )
        }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
            ItemDetail(
                h.title,
                listOf(
                    DetailLine(LineKind.REPEAT, if (h.weekly) "${h.perWeek} volte a settimana" else "Ogni giorno"),
                    DetailLine(LineKind.WHEN, h.remindAt?.let { "Avviso alle " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "Nessun avviso"),
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
 * (titolo col pallino, una scheda di righe, Elimina in alto). Si modifica qui dentro: il titolo si scrive, le righe si toccano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(kind: ItemKind, id: Long, onBack: () -> Unit) {
    val data by NotesStore.data.collectAsState()
    val haptic = rememberHaptic()
    val inApp = LocalInAppNav.current
    var picker by remember { mutableStateOf<Picker?>(null) }
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
    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = delete,
    ) {
        if (detail == null) {
            DetailMissing(Icons.Outlined.EventBusy, "Questa cosa non c'è più.")
            return@DetailPage
        }
        // Si modifica tutto qui, senza dialogo (Antonio): il titolo si scrive, le righe si toccano.
        EditableDetailTitle("$kind-$id", detail.title, kind.dot()) { title -> rename(kind, id, data, title) }
        CanvasCard(Modifier.fillMaxWidth()) {
            detail.lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                when (val edit = lineEdit(kind, id, data, line.kind)) {
                    null -> DetailRow(line.kind.icon(), line.text)
                    is LineEdit.Tap -> DetailTapRow(line.kind.icon(), line.text, onClick = { picker = edit.picker ?: run { edit.onClick(); null } }, onClear = edit.onClear)
                    is LineEdit.Field -> DetailFieldRow("$kind-$id-${line.kind}", line.kind.icon(), edit.value, edit.placeholder, edit.number, edit.onCommit)
                }
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

    when (val p = picker) {
        null -> Unit
        is Picker.DateTime -> DateTimePickDialog(p.initial, onDismiss = { picker = null }) { p.onPick(it); picker = null }
        is Picker.Date -> DatePickDialog(p.initial, onDismiss = { picker = null }) { p.onPick(it); picker = null }
        is Picker.Time -> TimePickDialog(p.initial, onDismiss = { picker = null }) { p.onPick(it); picker = null }
    }
}

/** Le scelte che si aprono toccando una riga: data e ora, solo data, solo ora. */
private sealed interface Picker {
    class DateTime(val initial: Long?, val onPick: (Long) -> Unit) : Picker
    class Date(val initial: LocalDate, val onPick: (LocalDate) -> Unit) : Picker
    class Time(val initial: java.time.LocalTime, val onPick: (java.time.LocalTime) -> Unit) : Picker
}

/** Come si cambia una riga: toccandola (un picker o il fumetto) o scrivendoci dentro. */
private sealed interface LineEdit {
    class Tap(val picker: Picker? = null, val onClick: () -> Unit = {}, val onClear: (() -> Unit)? = null) : LineEdit
    class Field(val value: String, val placeholder: String, val number: Boolean, val onCommit: (String) -> Unit) : LineEdit
}

private fun centsText(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)

/** Il nuovo titolo, salvato nel posto giusto per ogni tipo. */
private fun rename(kind: ItemKind, id: Long, data: NotesData, title: String) {
    when (kind) {
        ItemKind.TASK -> NotesStore.editTask(id, title)
        ItemKind.DATE -> data.countdowns.firstOrNull { it.id == id }?.let { NotesStore.saveCountdown(id, title, it.date, it.yearly) }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { NotesStore.saveParcel(id, title, it.code, it.expected) }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { NotesStore.saveHabit(id, title, it.perWeek, it.remindAt) }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { NotesStore.saveExpense(it.copy(text = title)) }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { NotesStore.saveFixedExpense(it.copy(text = title)) }
    }
}

/** Per ogni riga della scheda, come si modifica; null = si legge e basta (lo stato, i giorni che mancano). */
private fun lineEdit(kind: ItemKind, id: Long, data: NotesData, line: LineKind): LineEdit? {
    fun categories(current: com.platinum20.dashboard.notes.ExpenseCategory, pick: (com.platinum20.dashboard.notes.ExpenseCategory) -> Unit) =
        LineEdit.Tap(onClick = {
            ActionBubble.show(*com.platinum20.dashboard.notes.ExpenseCategory.entries.map { c ->
                BubbleAction(c.icon(), c.label, detail = if (c == current) "adesso" else "") { pick(c) }
            }.toTypedArray())
        })
    return when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
            when (line) {
                LineKind.WHEN -> LineEdit.Tap(
                    Picker.DateTime(t.dueAt) { NotesStore.setTaskDue(id, it) },
                    onClear = t.dueAt?.let { { NotesStore.setTaskRepeat(id, null); NotesStore.setTaskDue(id, null) } },
                )
                LineKind.REPEAT -> LineEdit.Tap(onClick = {
                    ActionBubble.show(
                        BubbleAction(Icons.Outlined.Repeat, "Non si ripete") { NotesStore.setTaskRepeat(id, null) },
                        *com.platinum20.dashboard.notes.Repeat.entries.map { r ->
                            BubbleAction(Icons.Outlined.Repeat, r.label.replaceFirstChar { it.uppercase() }) { NotesStore.setTaskRepeat(id, r) }
                        }.toTypedArray(),
                    )
                })
                else -> null
            }
        }
        ItemKind.DATE -> data.countdowns.firstOrNull { it.id == id }?.let { c ->
            when (line) {
                LineKind.WHEN -> LineEdit.Tap(Picker.Date(c.date) { NotesStore.saveCountdown(id, c.title, it, c.yearly) })
                LineKind.REPEAT -> LineEdit.Tap(onClick = { NotesStore.saveCountdown(id, c.title, c.date, !c.yearly) })
                else -> null
            }
        }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
            when (line) {
                LineKind.WHEN -> if (p.delivered) null else LineEdit.Tap(
                    Picker.Date(p.expected ?: LocalDate.now()) { NotesStore.saveParcel(id, p.title, p.code, it) },
                    onClear = p.expected?.let { { NotesStore.saveParcel(id, p.title, p.code, null) } },
                )
                LineKind.CODE -> LineEdit.Field(p.code, "Codice di tracciamento", number = false) { NotesStore.saveParcel(id, p.title, it, p.expected) }
                else -> null
            }
        }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
            when (line) {
                LineKind.REPEAT -> LineEdit.Tap(onClick = {
                    ActionBubble.show(
                        BubbleAction(Icons.Outlined.Repeat, "Ogni giorno") { NotesStore.saveHabit(id, h.title, 0, h.remindAt) },
                        *(1..6).map { n -> BubbleAction(Icons.Outlined.Repeat, "$n volte a settimana") { NotesStore.saveHabit(id, h.title, n, h.remindAt) } }.toTypedArray(),
                    )
                })
                LineKind.WHEN -> LineEdit.Tap(
                    Picker.Time(h.remindAt ?: java.time.LocalTime.of(21, 0)) { NotesStore.saveHabit(id, h.title, h.perWeek, it) },
                    onClear = h.remindAt?.let { { NotesStore.saveHabit(id, h.title, h.perWeek, null) } },
                )
                else -> null
            }
        }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e ->
            when (line) {
                LineKind.AMOUNT -> LineEdit.Field(centsText(e.cents), "Importo", number = true) { text ->
                    centsOf(text)?.takeIf { it > 0 }?.let { NotesStore.saveExpense(e.copy(cents = it)) }
                }
                LineKind.WHEN -> LineEdit.Tap(Picker.Date(e.date) { NotesStore.saveExpense(e.copy(date = it)) })
                LineKind.CATEGORY -> categories(e.category) { NotesStore.saveExpense(e.copy(category = it)) }
                else -> null
            }
        }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { f ->
            when (line) {
                LineKind.AMOUNT -> LineEdit.Field(centsText(f.cents), "Importo", number = true) { text ->
                    centsOf(text.removeSuffix("al mese").trim())?.takeIf { it > 0 }?.let { NotesStore.saveFixedExpense(f.copy(cents = it)) }
                }
                LineKind.REPEAT -> LineEdit.Tap(Picker.Date(LocalDate.now().withDayOfMonth(f.day.coerceIn(1, 28))) { NotesStore.saveFixedExpense(f.copy(day = it.dayOfMonth)) })
                LineKind.CATEGORY -> categories(f.category) { NotesStore.saveFixedExpense(f.copy(category = it)) }
                else -> null
            }
        }
    }
}

/** Apre da qualunque lista la pagina di una cosa delle Note (nei test e nelle anteprime non fa nulla). */
@Composable
fun rememberOpenItem(): (ItemKind, Long) -> Unit {
    val inApp = LocalInAppNav.current
    return remember(inApp) { { kind, id -> inApp?.openRoute?.invoke(Routes.noteItem(kind, id)) } }
}
