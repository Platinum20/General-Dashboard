package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.notes.WhenText
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Le cose delle Note che si aprono a schermo intero, con la stessa pagina degli eventi del calendario.
 * Promemoria e date da ricordare sono la stessa cosa dalla 1.361 ([TASK], anche tutto il giorno).
 */
enum class ItemKind { TASK, PARCEL, HABIT, EXPENSE, FIXED }

/** Che cosa dice una riga della scheda: decide l'icona. */
enum class LineKind { WHEN, ALL_DAY, REPEAT, ALERT, PLACE, NOTES, STATUS, CODE, AMOUNT, CATEGORY }

data class DetailLine(val kind: LineKind, val text: String)

/** Il "quando" sotto il titolo (punto 26): la frase ("Domani alle 9:00") e sotto la data per intero. */
data class WhenLine(val main: String, val sub: String)

/**
 * La scheda di una cosa: titolo, il quando sotto il titolo, e solo le righe piene (punto 28); quelle vuote
 * che si possono aggiungere stanno in [addable] e diventano "+ Luogo", "+ Note" in fondo.
 */
data class ItemDetail(
    val title: String,
    val lines: List<DetailLine>,
    val whenLine: WhenLine? = null,
    val addable: List<LineKind> = emptyList(),
    /** Spese: l'importo accanto al nome (punto 27 B), "12,50 €". */
    val amount: String? = null,
    /** Spese: la categoria, un chip con › sotto il titolo. */
    val category: com.platinum20.dashboard.notes.ExpenseCategory? = null,
    /** Abitudini: la serie grande in alto e la settimana a pallini (punto 30 B). */
    val habit: HabitView? = null,
)

/** Un giorno della settimana dell'abitudine: la lettera, se fatta, se è oggi, se deve ancora venire. */
data class HabitDay(val date: LocalDate, val letter: String, val done: Boolean, val today: Boolean, val future: Boolean)

/** La testa della scheda abitudine: "5 giorni di fila", sotto come va, e la settimana da lunedì. */
data class HabitView(val streak: String, val sub: String, val week: List<HabitDay>)

/** La serie e la settimana di un'abitudine (punto 30 B): solo questa settimana, niente mese. Pura, per i test. */
internal fun habitView(h: com.platinum20.dashboard.notes.Habit, now: LocalDateTime): HabitView {
    val today = now.toLocalDate()
    val monday = today.with(java.time.DayOfWeek.MONDAY)
    val letters = listOf("L", "M", "M", "G", "V", "S", "D")
    val week = (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        HabitDay(d, letters[i], h.isDone(d), d == today, d.isAfter(today))
    }
    val streak = if (h.weekly) h.weekStreak(today).let { n ->
        when (n) { 0 -> "${h.weekCount(today)} su ${h.perWeek} questa settimana"; 1 -> "1 settimana di fila"; else -> "$n settimane di fila" }
    } else h.streak(today).let { n ->
        when (n) { 0 -> "Nessun giorno di fila"; 1 -> "1 giorno di fila"; else -> "$n giorni di fila" }
    }
    val sub = when {
        h.weekly && h.weekStreak(today) > 0 -> "${h.weekCount(today)} su ${h.perWeek} questa settimana"
        h.isDone(today) -> "Fatta oggi"
        else -> "Da fare oggi"
    }
    return HabitView(streak, sub + HabitReminder.suffix(h), week)
}

private val shortDate = DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN)

private fun daysText(days: Long): String = when (days) {
    0L -> "Oggi"
    1L -> "Domani"
    -1L -> "Ieri"
    in 2..Long.MAX_VALUE -> "Tra $days giorni"
    else -> "${-days} giorni fa"
}

/** "mercoledì 11 novembre", con l'anno solo se non è quello di [today]. */
internal fun whenDate(day: LocalDate, today: LocalDate): String =
    day.format(if (day.year == today.year) fullDay else fullDayYear)

private val fullDay = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val fullDayYear = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)

/**
 * Il quando di un promemoria o di una data, sotto il titolo (punto 26): "Domani alle 9:00", sotto "mercoledì 11
 * novembre · compie 30 anni". Senza data: "Senza data". Pura, per i test.
 */
internal fun taskWhen(t: TaskItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): WhenLine {
    val today = now.toLocalDate()
    val day = t.nextDay(today, zone) ?: return WhenLine("Senza data", if (t.done) "Fatto" else "")
    val time = t.dueAt?.takeIf { !t.allDay }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(hourFormat) }
    val main = daysText(ChronoUnit.DAYS.between(today, day)) + (time?.let { " alle $it" } ?: "")
    val sub = whenDate(day, today) + (t.yearsAtNext(today, zone)?.let { " · compie $it anni" } ?: "")
    return WhenLine(main, sub)
}

/**
 * Le righe di un promemoria o di una data, nello stesso ordine della scheda di un evento (Antonio: "che abbiano
 * le stesse cose"): tutto il giorno, si ripete, avviso, luogo, note. Il quando sta sotto il titolo ([taskWhen]);
 * luogo e note solo se scritti (punto 28). Pura, per i test.
 */
internal fun taskLines(t: TaskItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<DetailLine> {
    val day = t.nextDay(now.toLocalDate(), zone)
    return listOfNotNull(
        // Con una data si vede sempre tutto: sono le righe che si toccano per cambiarlo.
        day?.let { DetailLine(LineKind.ALL_DAY, if (t.allDay) "Tutto il giorno" else "A un'ora precisa") },
        day?.let { DetailLine(LineKind.REPEAT, WhenText.repeat(t.repeat)) },
        day?.let { DetailLine(LineKind.ALERT, WhenText.alert(t)) },
        t.place.takeIf { it.isNotBlank() }?.let { DetailLine(LineKind.PLACE, it) },
        t.notes.takeIf { it.isNotBlank() }?.let { DetailLine(LineKind.NOTES, it) },
    )
}

/** Le righe vuote di un promemoria che si possono aggiungere con "+". */
private fun taskAddable(t: TaskItem): List<LineKind> = listOfNotNull(
    LineKind.PLACE.takeIf { t.place.isBlank() },
    LineKind.NOTES.takeIf { t.notes.isBlank() },
)

private val hourFormat = DateTimeFormatter.ofPattern("H:mm")

/** Le righe della scheda per ogni tipo; null se la cosa non c'è più. Pura, per i test. */
fun itemDetail(data: NotesData, kind: ItemKind, id: Long, now: LocalDateTime = LocalDateTime.now()): ItemDetail? {
    val today = now.toLocalDate()
    return when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
            ItemDetail(t.text.ifBlank { "(Senza titolo)" }, taskLines(t, now), taskWhen(t, now), taskAddable(t))
        }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
            val whenLine = when {
                p.deliveredOn != null -> WhenLine("Arrivato", "il " + p.deliveredOn.format(shortDate))
                p.expected != null && p.expected.isBefore(today) -> WhenLine("In ritardo", "atteso " + whenDate(p.expected, today))
                p.expected != null -> WhenLine(
                    "Arriva " + daysText(ChronoUnit.DAYS.between(today, p.expected)).replaceFirstChar { it.lowercase() },
                    whenDate(p.expected, today),
                )
                else -> WhenLine("In viaggio", "data di arrivo non nota")
            }
            ItemDetail(
                p.title.ifBlank { "Pacco" },
                listOfNotNull(p.code.takeIf { it.isNotBlank() }?.let { DetailLine(LineKind.CODE, "$it · ${p.carrier.label}") }),
                whenLine,
                listOfNotNull(LineKind.CODE.takeIf { p.code.isBlank() }),
            )
        }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
            ItemDetail(
                h.title,
                listOf(
                    DetailLine(LineKind.REPEAT, if (h.weekly) "${h.perWeek} volte a settimana" else "Ogni giorno"),
                    DetailLine(LineKind.WHEN, h.remindAt?.let { "Avviso alle " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: "Nessun avviso"),
                ),
                habit = habitView(h, now),
            )
        }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e ->
            ItemDetail(
                e.text.ifBlank { e.category.label },
                listOfNotNull(
                    // L'ora della spesa (Revolut, notifica, a mano) accanto al giorno, come nelle cose a un'ora (Antonio).
                    DetailLine(LineKind.WHEN, longDate(e.date) + (e.at?.let { ", " + it.format(hourFormat) } ?: "")),
                    e.fixedId?.let { DetailLine(LineKind.REPEAT, "Spesa fissa, aggiunta da sola") },
                ),
                amount = Expenses.format(e.cents),
                category = e.category,
            )
        }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { f ->
            ItemDetail(
                f.text.ifBlank { f.category.label },
                listOf(
                    DetailLine(LineKind.REPEAT, "Il ${f.day} di ogni mese"),
                    DetailLine(LineKind.WHEN, "Prossima " + longDate(f.nextDate(today)).replaceFirstChar { it.lowercase() }),
                ),
                amount = Expenses.format(f.cents),
                category = f.category,
            )
        }
    }
}

private fun LineKind.icon(): ImageVector = when (this) {
    LineKind.WHEN -> Icons.Outlined.Schedule
    LineKind.ALL_DAY -> Icons.Outlined.WbSunny
    LineKind.REPEAT -> Icons.Outlined.Repeat
    LineKind.ALERT -> Icons.Outlined.Notifications
    LineKind.PLACE -> Icons.Outlined.Place
    LineKind.NOTES -> Icons.AutoMirrored.Outlined.Notes
    LineKind.STATUS -> Icons.Outlined.Info
    LineKind.CODE -> Icons.Outlined.QrCode2
    LineKind.AMOUNT -> Icons.Outlined.Euro
    LineKind.CATEGORY -> Icons.Outlined.Category
}

/** L'invito per una riga vuota: "Luogo", "Note", "Codice". */
private fun LineKind.addLabel(): String = when (this) {
    LineKind.PLACE -> "Luogo"
    LineKind.NOTES -> "Note"
    LineKind.CODE -> "Codice"
    else -> name
}

/** Il colore del pallino accanto al titolo, come quello del calendario per gli eventi. */
@Composable
private fun ItemKind.dot(): Color = when (this) {
    ItemKind.TASK, ItemKind.HABIT -> MaterialTheme.colorScheme.primary
    ItemKind.PARCEL -> MaterialTheme.colorScheme.secondary
    ItemKind.EXPENSE, ItemKind.FIXED -> MaterialTheme.colorScheme.outline
}

/** La riga piccola in alto nella scheda: che cosa è. */
private fun ItemKind.metaLabel(): String = when (this) {
    ItemKind.TASK -> "Promemoria"
    ItemKind.PARCEL -> "Pacco"
    ItemKind.HABIT -> "Abitudine"
    ItemKind.EXPENSE -> "Spesa"
    ItemKind.FIXED -> "Spesa fissa"
}

private fun undo(message: String, restore: () -> Unit) {
    CaptureFeedback.show(CaptureNotice(CaptureMode.NOTE, message, undo = restore, icon = Icons.Outlined.Delete, fly = false))
}

/**
 * La pagina a schermo intero di promemoria, date, pacchi, abitudini e spese: la stessa dell'evento
 * (titolo col pallino, una scheda di righe; in basso l'azione principale e "Altro" con Elimina). Si modifica qui dentro: il titolo si scrive, le righe si toccano.
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
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p -> { NotesStore.deleteParcel(id); gone("Pacco eliminato") { NotesStore.restoreParcel(p) } } }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h -> { NotesStore.deleteHabit(id); gone("Abitudine eliminata") { NotesStore.restoreHabit(h) } } }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { e -> { NotesStore.deleteExpense(id); gone("Spesa eliminata") { NotesStore.restoreExpense(e) } } }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { f -> { NotesStore.deleteFixedExpense(id); gone("Spesa fissa tolta") { NotesStore.restoreFixedExpense(f) } } }
    }
    // L'azione di tutti i giorni in basso a sinistra, come la lista nell'editor delle note (Antonio).
    // Una pillola col verbo (punto 29): piena quando è da fare, tonale quando è fatto.
    val action: DetailAction? = when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.takeIf { it.tickable }?.let { t ->
            DetailAction(Icons.Outlined.Check, if (t.done) "Fatto" else "Segna fatto", done = t.done) {
                haptic(if (t.done) Haptic.TICK else Haptic.CONFIRM)
                NotesStore.toggleTask(id)
            }
        }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { h ->
            val done = h.isDone(LocalDate.now())
            DetailAction(Icons.Outlined.Check, if (done) "Fatta oggi" else "Segna fatta oggi", done = done) {
                haptic(if (done) Haptic.TICK else Haptic.CONFIRM)
                NotesStore.toggleHabit(id)
            }
        }
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { p ->
            DetailAction(Icons.Outlined.Check, if (p.delivered) "Arrivato" else "Segna arrivato", done = p.delivered) {
                haptic(if (p.delivered) Haptic.TICK else Haptic.CONFIRM)
                NotesStore.toggleDelivered(id)
            }
        }
        else -> null
    }
    val tracking = if (kind == ItemKind.PARCEL) data.parcels.firstOrNull { it.id == id }?.takeIf { !it.delivered }?.let { p ->
        p.trackingUrl()?.let { url -> { inApp?.openWeb?.invoke(url, p.carrier.label); Unit } }
    } else null
    // Il quando sotto il titolo si tocca per cambiarlo (punto 26).
    val whenEdit = if (detail?.whenLine != null) lineEdit(kind, id, data, LineKind.WHEN) as? LineEdit.Tap else null
    // Le righe vuote aggiunte con "+": restano finché si è nella scheda, poi se scritte sono righe piene.
    var adding by androidx.compose.runtime.saveable.rememberSaveable("$kind-$id") { mutableStateOf(listOf<String>()) }
    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = delete,
        meta = if (detail == null) null else kind.metaLabel(),
        primary = action,
        // La pagina del corriere si apre dentro Canvas, non nel browser. "Togli la data" sta qui (punto 26), non accanto al dito.
        more = listOfNotNull(
            tracking?.let { open -> BubbleAction(Icons.Outlined.LocalShipping, "Traccia la spedizione", onClick = open) },
            whenEdit?.onClear?.let { clear -> BubbleAction(Icons.Outlined.EventBusy, "Togli la data", onClick = clear) },
        ),
    ) {
        if (detail == null) {
            DetailMissing(Icons.Outlined.EventBusy, "Questa cosa non c'è più.")
            return@DetailPage
        }
        // Si modifica tutto qui, senza dialogo (Antonio): il titolo si scrive, le righe si toccano.
        // Spese: nome come titolo, l'importo accanto (punto 27 B), si scrive lì; sotto la categoria come chip.
        val amountEdit = if (detail.amount != null) lineEdit(kind, id, data, LineKind.AMOUNT) as? LineEdit.Field else null
        EditableDetailTitle(
            "$kind-$id", detail.title, kind.dot(), sharedKey = SharedKeys.item(kind, id),
            trailing = amountEdit?.let { edit -> { DetailAmountField("$kind-$id-amount", edit.value, edit.onCommit) } },
        ) { title -> rename(kind, id, data, title) }
        detail.category?.let { c ->
            val edit = lineEdit(kind, id, data, LineKind.CATEGORY) as? LineEdit.Tap
            DetailChip(c.icon(), c.label, onClick = { edit?.onClick?.invoke() })
        }
        detail.habit?.let { v -> HabitHead(v) { day -> haptic(Haptic.TICK); NotesStore.toggleHabit(id, day) } }
        detail.whenLine?.let { w ->
            DetailWhen(w.main, w.sub, onClick = whenEdit?.let { edit -> { picker = edit.picker ?: run { edit.onClick(); null } } })
        }
        val added = detail.addable.filter { it.name in adding }
        val lines = detail.lines + added.map { DetailLine(it, "") }
        if (lines.isNotEmpty()) CanvasCard(Modifier.fillMaxWidth()) {
            lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                when (val edit = lineEdit(kind, id, data, line.kind)) {
                    null -> DetailRow(line.kind.icon(), line.text)
                    is LineEdit.Tap -> DetailTapRow(line.kind.icon(), line.text, onClick = { picker = edit.picker ?: run { edit.onClick(); null } }, onClear = edit.onClear)
                    is LineEdit.Field -> DetailFieldRow(
                        "$kind-$id-${line.kind}", line.kind.icon(), edit.value, edit.placeholder, edit.number, edit.prose,
                        focus = line.kind in added, onCommit = edit.onCommit,
                    )
                }
            }
        }
        val more = detail.addable.filter { it.name !in adding }
        DetailAddRow(more.map { it.addLabel() }) { i -> adding = adding + more[i].name }
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
    class Field(val value: String, val placeholder: String, val number: Boolean, val prose: Boolean = false, val onCommit: (String) -> Unit) : LineEdit
}

private fun centsText(cents: Long): String = "%d,%02d".format(cents / 100, cents % 100)

/** Il nuovo titolo, salvato nel posto giusto per ogni tipo. */
private fun rename(kind: ItemKind, id: Long, data: NotesData, title: String) {
    when (kind) {
        ItemKind.TASK -> NotesStore.editTask(id, title)
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { NotesStore.saveParcel(id, title, it.code, it.expected) }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { NotesStore.saveHabit(id, title, it.perWeek, it.remindAt) }
        ItemKind.EXPENSE -> data.expenses.firstOrNull { it.id == id }?.let { NotesStore.saveExpense(it.copy(text = title)) }
        ItemKind.FIXED -> data.fixedExpenses.firstOrNull { it.id == id }?.let { NotesStore.saveFixedExpense(it.copy(text = title)) }
    }
}

/** Per ogni riga della scheda, come si modifica; null = si legge e basta (lo stato, i giorni che mancano). */
private fun lineEdit(kind: ItemKind, id: Long, data: NotesData, line: LineKind): LineEdit? {
    fun categories(current: com.platinum20.dashboard.notes.ExpenseCategory, pick: (com.platinum20.dashboard.notes.ExpenseCategory) -> Unit) =
        LineEdit.Tap(onClick = { ActionBubble.show(*categoryChoices(current, pick).toTypedArray()) })
    return when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t ->
            when (line) {
                // Tutto il giorno si sceglie solo il giorno; con l'ora, giorno e ora.
                LineKind.WHEN -> LineEdit.Tap(
                    if (t.allDay) Picker.Date(t.nextDay() ?: LocalDate.now()) { NotesStore.setTaskDay(id, it) }
                    else Picker.DateTime(t.dueAt) { NotesStore.setTaskDue(id, it) },
                    onClear = t.dueAt?.let { { NotesStore.setTaskRepeat(id, null); NotesStore.setTaskDue(id, null) } },
                )
                LineKind.ALL_DAY -> LineEdit.Tap(onClick = { NotesStore.setTaskAllDay(id, !t.allDay) })
                LineKind.REPEAT -> LineEdit.Tap(onClick = {
                    ActionBubble.show(
                        BubbleAction(Icons.Outlined.Repeat, "Non si ripete", detail = if (t.repeat == null) "adesso" else "") { NotesStore.setTaskRepeat(id, null) },
                        *com.platinum20.dashboard.notes.Repeat.entries.map { r ->
                            BubbleAction(Icons.Outlined.Repeat, r.label.replaceFirstChar { it.uppercase() }, detail = if (t.repeat == r) "adesso" else "") { NotesStore.setTaskRepeat(id, r) }
                        }.toTypedArray(),
                    )
                })
                LineKind.ALERT -> LineEdit.Tap(onClick = {
                    ActionBubble.show(*com.platinum20.dashboard.notes.Alert.entries.map { a ->
                        BubbleAction(Icons.Outlined.Notifications, WhenText.alert(t.copy(alert = a)), detail = if (t.alert == a) "adesso" else "") { NotesStore.setTaskAlert(id, a) }
                    }.toTypedArray())
                })
                LineKind.PLACE -> LineEdit.Field(t.place, "Luogo", number = false, prose = true) { NotesStore.setTaskPlace(id, it) }
                LineKind.NOTES -> LineEdit.Field(t.notes, "Note", number = false, prose = true) { NotesStore.setTaskNotes(id, it) }
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

/** La serie grande e la settimana a pallini: un pallino passato si tocca per segnare un giorno dimenticato. */
@Composable
private fun HabitHead(v: HabitView, onToggle: (LocalDate) -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.padding(start = 26.dp, end = 4.dp)) {
        androidx.compose.material3.Text(v.streak, style = MaterialTheme.typography.headlineMedium)
        androidx.compose.material3.Text(v.sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        v.week.forEach { d ->
            val blue = MaterialTheme.colorScheme.primary
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .then(if (d.future) Modifier else Modifier.clickable { onToggle(d.date) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    d.letter,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (d.today) blue else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(22.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .then(
                            when {
                                d.done -> Modifier.background(blue)
                                d.future -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                else -> Modifier.border(1.5.dp, if (d.today) blue else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                            },
                        ),
                )
            }
        }
    }
}
