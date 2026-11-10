package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
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
enum class ItemKind { TASK, PARCEL, HABIT }

/** Che cosa dice una riga della scheda: decide l'icona. */
enum class LineKind { WHEN, ALL_DAY, REPEAT, ALERT, PLACE, NOTES, STATUS, CODE }

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

/**
 * Le righe di un promemoria o di una data, nello stesso ordine della scheda di un evento (Antonio: "che abbiano
 * le stesse cose"): quando, tutto il giorno, si ripete, avviso, luogo, note, e quanto manca. Pura, per i test.
 */
internal fun taskLines(t: TaskItem, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): List<DetailLine> {
    val today = now.toLocalDate()
    val day = t.nextDay(today, zone)
    val time = t.dueAt?.takeIf { !t.allDay }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime().format(hourFormat) }
    val status = when {
        t.done -> "Fatto"
        day == null -> "Da fare"
        else -> daysText(ChronoUnit.DAYS.between(today, day)) + (t.yearsAtNext(today, zone)?.let { " · compie $it anni" } ?: "")
    }
    return listOfNotNull(
        DetailLine(LineKind.WHEN, day?.let { longDate(it) + (time?.let { h -> ", $h" } ?: "") } ?: "Senza data"),
        // Con una data si vede sempre tutto: sono le righe che si toccano per cambiarlo.
        day?.let { DetailLine(LineKind.ALL_DAY, if (t.allDay) "Tutto il giorno" else "A un'ora precisa") },
        day?.let { DetailLine(LineKind.REPEAT, WhenText.repeat(t.repeat)) },
        day?.let { DetailLine(LineKind.ALERT, WhenText.alert(t)) },
        DetailLine(LineKind.PLACE, t.place),
        DetailLine(LineKind.NOTES, t.notes),
        DetailLine(LineKind.STATUS, status),
    )
}

private val hourFormat = DateTimeFormatter.ofPattern("H:mm")

/** Le righe della scheda per ogni tipo; null se la cosa non c'è più. Pura, per i test. */
fun itemDetail(data: NotesData, kind: ItemKind, id: Long, now: LocalDateTime = LocalDateTime.now()): ItemDetail? {
    val today = now.toLocalDate()
    return when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.let { t -> ItemDetail(t.text.ifBlank { "(Senza titolo)" }, taskLines(t, now)) }
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
}

/** Il colore del pallino accanto al titolo, come quello del calendario per gli eventi. */
@Composable
private fun ItemKind.dot(): Color = when (this) {
    ItemKind.TASK, ItemKind.HABIT -> MaterialTheme.colorScheme.primary
    ItemKind.PARCEL -> MaterialTheme.colorScheme.secondary
}

/** La riga piccola in alto nella scheda: che cosa è. */
private fun ItemKind.metaLabel(): String = when (this) {
    ItemKind.TASK -> "Promemoria"
    ItemKind.PARCEL -> "Pacco"
    ItemKind.HABIT -> "Abitudine"
}

private fun undo(message: String, restore: () -> Unit) {
    CaptureFeedback.show(CaptureNotice(CaptureMode.NOTE, message, undo = restore, icon = Icons.Outlined.Delete, fly = false))
}

/**
 * La pagina a schermo intero di promemoria, date, pacchi e abitudini: la stessa dell'evento
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
    }
    // L'azione di tutti i giorni in basso a sinistra, come la lista nell'editor delle note (Antonio).
    val action: Triple<ImageVector, String, () -> Unit>? = when (kind) {
        ItemKind.TASK -> data.tasks.firstOrNull { it.id == id }?.takeIf { it.tickable }?.let { t ->
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
    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = delete,
        meta = if (detail == null) null else kind.metaLabel(),
        primary = action?.let { (icon, text, onClick) -> DetailAction(icon, text, onClick) },
        // La pagina del corriere si apre dentro Canvas, non nel browser.
        more = listOfNotNull(tracking?.let { open -> BubbleAction(Icons.Outlined.LocalShipping, "Traccia la spedizione", onClick = open) }),
    ) {
        if (detail == null) {
            DetailMissing(Icons.Outlined.EventBusy, "Questa cosa non c'è più.")
            return@DetailPage
        }
        // Si modifica tutto qui, senza dialogo (Antonio): il titolo si scrive, le righe si toccano.
        EditableDetailTitle("$kind-$id", detail.title, kind.dot(), sharedKey = SharedKeys.item(kind, id)) { title -> rename(kind, id, data, title) }
        CanvasCard(Modifier.fillMaxWidth()) {
            detail.lines.forEachIndexed { i, line ->
                if (i > 0) RowDivider()
                when (val edit = lineEdit(kind, id, data, line.kind)) {
                    null -> DetailRow(line.kind.icon(), line.text)
                    is LineEdit.Tap -> DetailTapRow(line.kind.icon(), line.text, onClick = { picker = edit.picker ?: run { edit.onClick(); null } }, onClear = edit.onClear)
                    is LineEdit.Field -> DetailFieldRow("$kind-$id-${line.kind}", line.kind.icon(), edit.value, edit.placeholder, edit.number, edit.prose, edit.onCommit)
                }
            }
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

/** Il nuovo titolo, salvato nel posto giusto per ogni tipo. */
private fun rename(kind: ItemKind, id: Long, data: NotesData, title: String) {
    when (kind) {
        ItemKind.TASK -> NotesStore.editTask(id, title)
        ItemKind.PARCEL -> data.parcels.firstOrNull { it.id == id }?.let { NotesStore.saveParcel(id, title, it.code, it.expected) }
        ItemKind.HABIT -> data.habits.firstOrNull { it.id == id }?.let { NotesStore.saveHabit(id, title, it.perWeek, it.remindAt) }
    }
}

/** Per ogni riga della scheda, come si modifica; null = si legge e basta (lo stato, i giorni che mancano). */
private fun lineEdit(kind: ItemKind, id: Long, data: NotesData, line: LineKind): LineEdit? {
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
    }
}

/** Apre da qualunque lista la pagina di una cosa delle Note (nei test e nelle anteprime non fa nulla). */
@Composable
fun rememberOpenItem(): (ItemKind, Long) -> Unit {
    val inApp = LocalInAppNav.current
    return remember(inApp) { { kind, id -> inApp?.openRoute?.invoke(Routes.noteItem(kind, id)) } }
}
