package com.platinum20.dashboard.capture

import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.Expenses
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.QuickParse
import com.platinum20.dashboard.notes.Repeat
import com.platinum20.dashboard.ui.screens.dueLabel
import com.platinum20.dashboard.ui.screens.longDate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Com'è andato un salvataggio: [ok] falso se manca qualcosa, e [message] dice cosa.
 * [undo] toglie quello che è appena stato creato: serve all'"Annulla" quando Canvas ha capito male il tipo.
 */
internal data class CaptureResult(
    val ok: Boolean,
    val message: String,
    val dueAt: Long? = null,
    val undo: (() -> Unit)? = null,
)

/** Un'abitudine scritta a parole: "ogni giorno leggere", "palestra 3 volte a settimana alle 19". */
internal data class HabitDraft(val title: String, val perWeek: Int, val remindAt: LocalTime?)

/** Una data da ricordare scritta a parole: "compleanno di Marta 12 marzo". */
internal data class DateDraft(val title: String, val date: LocalDate, val yearly: Boolean)

/**
 * Salva quello che si scrive nel + o in cima alle Note, nel tipo indovinato da [CaptureGuess]:
 * lo stesso codice per la finestrella, per il campo delle Note e per la dettatura.
 * Gli eventi no: vanno nel calendario e chiedono il permesso, li salva [CaptureDialog].
 */
internal object CaptureSave {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"

    internal val perWeek = Regex("$B0(\\d) volte (?:a|alla|per|la) settimana$B1", RegexOption.IGNORE_CASE)
    internal val everyDay = Regex("$B0(?:ogni giorno|tutti i giorni|ogni mattina|ogni sera)$B1", RegexOption.IGNORE_CASE)
    internal val yearlyWords = Regex("$B0(?:compleanno|anniversario|onomastico)$B1", RegexOption.IGNORE_CASE)
    internal val clock = Regex("$B0(?:alle|ore|all'|verso le)\\s*\\d", RegexOption.IGNORE_CASE)

    fun habitOf(text: String, now: LocalDateTime = LocalDateTime.now()): HabitDraft? {
        val times = perWeek.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..6 } ?: 0
        val rest = perWeek.replace(text, " ").replace(everyDay, " ")
        val entry = QuickParse.parse(rest, now)
        val title = entry.text.trim().replaceFirstChar { it.uppercase() }
        if (title.isEmpty()) return null
        // L'ora diventa il "Ricordamelo alle…" dell'abitudine, solo se è scritta davvero.
        val remind = if (clock.containsMatchIn(rest)) entry.due?.toLocalTime() else null
        return HabitDraft(title, times, remind)
    }

    fun dateOf(text: String, now: LocalDateTime = LocalDateTime.now()): DateDraft? {
        val entry = QuickParse.parse(text, now)
        val day = entry.due?.toLocalDate() ?: return null
        val title = entry.text.trim().replaceFirstChar { it.uppercase() }.ifEmpty { return null }
        return DateDraft(title, day, entry.repeat == Repeat.YEARLY || yearlyWords.containsMatchIn(text))
    }

    /** La riga sotto il campo: cosa verrà salvato. Null se non c'è niente da dire. */
    fun preview(mode: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): String? {
        if (text.isBlank()) return null
        return when (mode) {
            CaptureMode.EXPENSE -> Expenses.parseFixed(text)?.let { "Ogni mese il ${it.day} · ${Expenses.format(it.cents)} · ${it.category.label}" }
                ?: Expenses.parse(text)?.let { "${Expenses.format(it.cents)} · ${it.category.label}" }
                ?: "Scrivi anche l'importo"
            CaptureMode.HABIT -> habitOf(text, now)?.let { h ->
                (if (h.perWeek > 0) "${h.perWeek} volte a settimana" else "Ogni giorno") +
                    (h.remindAt?.let { " · avviso alle ${it.hour}:${"%02d".format(it.minute)}" } ?: "") + " · ${h.title}"
            }
            CaptureMode.DATE -> dateOf(text, now)?.let { d ->
                longDate(d.date) + (if (d.yearly) " · ogni anno" else "") + " · ${d.title}"
            } ?: "Scrivi anche il giorno: es. 12 marzo"
            CaptureMode.PARCEL -> Carrier.findCode(text)?.let { "$it · ${Carrier.detect(it).label}" }
            CaptureMode.TASK, CaptureMode.NOTE, CaptureMode.EVENT -> null
        }
    }

    fun save(mode: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): CaptureResult {
        if (text.isBlank()) return CaptureResult(false, "Scrivi qualcosa")
        return when (mode) {
            CaptureMode.NOTE -> {
                val id = NotesStore.saveNote(null, text.trim())
                CaptureResult(true, "Nota salvata", undo = id?.let { { NotesStore.deleteNote(it) } })
            }
            CaptureMode.EXPENSE -> {
                // "ogni mese il 5 affitto 650" è una spesa fissa; il resto una spesa di oggi.
                if (Expenses.parseFixed(text) != null) {
                    val fixed = NotesStore.addFixedExpense(text) ?: return CaptureResult(false, "Manca l'importo")
                    CaptureResult(true, "Spesa fissa: ${Expenses.format(fixed.cents)} il ${fixed.day} di ogni mese", undo = { NotesStore.undoFixedExpense(fixed.id) })
                } else {
                    val saved = NotesStore.addExpense(text) ?: return CaptureResult(false, "Manca l'importo")
                    CaptureResult(true, "Spesa: ${Expenses.format(saved.cents)} · ${saved.category.label}", undo = { NotesStore.deleteExpense(saved.id) })
                }
            }
            CaptureMode.PARCEL -> {
                val code = Carrier.findCode(text).orEmpty()
                // Il titolo è la prima riga senza il codice, accorciata; "arriva venerdì" fa da data attesa.
                val firstLine = text.lineSequence().map { it.replace(code, "", ignoreCase = true).trim() }
                    .firstOrNull { it.isNotEmpty() }.orEmpty()
                val entry = QuickParse.parse(firstLine, now)
                val title = (if (entry.due != null) entry.text else firstLine).take(60)
                val id = NotesStore.addParcel(title.ifEmpty { "Pacco" }, code, entry.due?.toLocalDate())
                CaptureResult(true, "Pacco aggiunto" + if (code.isNotEmpty()) " · ${Carrier.detect(code).label}" else "", undo = { NotesStore.deleteParcel(id) })
            }
            CaptureMode.HABIT -> {
                val h = habitOf(text, now) ?: return CaptureResult(false, "Scrivi cosa vuoi fare")
                val id = NotesStore.saveHabit(null, h.title, h.perWeek, h.remindAt)
                CaptureResult(true, "Abitudine: ${h.title}", undo = id?.let { { NotesStore.deleteHabit(it) } })
            }
            CaptureMode.DATE -> {
                val d = dateOf(text, now) ?: return CaptureResult(false, "Scrivi anche il giorno: es. 12 marzo")
                val id = NotesStore.saveCountdown(null, d.title, d.date, d.yearly)
                CaptureResult(true, "Data: ${d.title} · ${longDate(d.date)}", undo = id?.let { { NotesStore.deleteCountdown(it) } })
            }
            CaptureMode.TASK -> {
                val task = NotesStore.addTask(text) ?: return CaptureResult(false, "Scrivi qualcosa")
                val due = task.dueAt
                CaptureResult(true, if (due != null) "Promemoria: ${dueLabel(due)}" else "Promemoria salvato", due, undo = { NotesStore.deleteTask(task.id) })
            }
            CaptureMode.EVENT -> CaptureResult(false, "Gli eventi si salvano nel calendario")
        }
    }
}
