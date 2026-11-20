package com.platinum20.dashboard.capture

import android.content.Context
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.calendar.QuickEvent
import com.platinum20.dashboard.notes.NotesStore

/**
 * Quello che si detta tenendo premuto il + si salva da solo, nel tipo che indovina Canvas:
 * niente finestra da confermare. Se manca qualcosa (l'importo, il giorno, il permesso
 * del calendario) si apre il dialogo del + con il testo, da completare.
 */
internal object VoiceCapture {
    sealed interface Outcome {
        /** Salvato: [undo] lo toglie, se si può. */
        data class Saved(val mode: CaptureMode, val message: String, val undo: (suspend () -> Unit)?) : Outcome
        data object NeedsDialog : Outcome
        /** "Calendario" scelto a mano senza un giorno nel testo (1.536): si chiede solo quando, nel fumetto. */
        data class NeedsWhen(val text: String) : Outcome
        /** Era una domanda (Chiedi al "+", 1.473): niente da salvare, c'è la risposta. */
        data class Answered(val answer: Answer) : Outcome
    }

    /** [chosen] = la chip toccata a mano sopra la barra (1.532); null = decide Canvas, come sempre. */
    suspend fun save(context: Context, text: String, chosen: CaptureDestination? = null): Outcome {
        val guessed = CaptureGuess.guess(text)
        // Scelto a mano non è una domanda: si salva.
        if (chosen == null) CaptureAsk.answer(context, text)?.let { return Outcome.Answered(it) }
        if (CaptureWhen.needed(chosen, text)) return Outcome.NeedsWhen(text)
        val mode = chosen?.let { CaptureDestination.modeFor(it, guessed, text) } ?: guessed
        if (CaptureDestination.plainTodo(chosen, guessed, text)) {
            NotesStore.init(context)
            val task = NotesStore.addTodo(text) ?: return Outcome.NeedsDialog
            return Outcome.Saved(CaptureMode.TASK, "In Da fare: ${task.text}") { NotesStore.deleteTask(task.id) }
        }
        if (mode == CaptureMode.EVENT) {
            val draft = QuickEvent.parse(text) ?: return Outcome.NeedsDialog
            if (!CalendarRepository.canWrite(context)) return Outcome.NeedsDialog
            val calendar = CalendarRepository.writableCalendars(context).firstOrNull() ?: return Outcome.NeedsDialog
            val id = CalendarRepository.save(context, draft.toDraft(calendar.id)) ?: return Outcome.NeedsDialog
            return Outcome.Saved(mode, "Evento: ${eventLine(draft)} · ${draft.title}") { CalendarRepository.delete(context, id) }
        }
        NotesStore.init(context)
        val result = CaptureSave.save(mode, text)
        if (!result.ok) return Outcome.NeedsDialog
        // "Annulla" toglie quello appena salvato, per quando Canvas ha capito male.
        return Outcome.Saved(mode, result.message, result.undo?.let { undo -> suspend { undo() } })
    }

    /**
     * Salva [text] nel giorno scelto dal fumetto: un evento nel calendario del telefono; senza permesso o calendario
     * un promemoria con la data (sta nel Calendario lo stesso), così non si apre mai il vecchio dialogo.
     */
    suspend fun saveAt(context: Context, text: String, day: java.time.LocalDate, time: java.time.LocalTime?): Outcome {
        val draft = CaptureWhen.draft(text, day, time)
        val calendar = if (CalendarRepository.canWrite(context)) CalendarRepository.writableCalendars(context).firstOrNull() else null
        val id = calendar?.let { CalendarRepository.save(context, draft.toDraft(it.id)) }
        if (id != null) return Outcome.Saved(CaptureMode.EVENT, "Evento: ${eventLine(draft)} · ${draft.title}") { CalendarRepository.delete(context, id) }
        NotesStore.init(context)
        return if (time == null) {
            val taskId = NotesStore.saveDate(null, draft.title, day, yearly = false) ?: return Outcome.NeedsDialog
            Outcome.Saved(CaptureMode.DATE, "Data: ${draft.title} · ${eventLine(draft)}") { NotesStore.deleteTask(taskId) }
        } else {
            val at = draft.start.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val task = NotesStore.addTaskAt(draft.title, at)
            Outcome.Saved(CaptureMode.TASK, "Promemoria: ${eventLine(draft)} · ${draft.title}") { NotesStore.deleteTask(task.id) }
        }
    }
}
