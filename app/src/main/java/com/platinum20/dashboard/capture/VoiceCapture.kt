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
        /** Era una domanda (Chiedi al "+", 1.473): niente da salvare, c'è la risposta. */
        data class Answered(val answer: Answer) : Outcome
    }

    /** [chosen] = la chip toccata a mano sopra la barra (1.532); null = decide Canvas, come sempre. */
    suspend fun save(context: Context, text: String, chosen: CaptureDestination? = null): Outcome {
        val guessed = CaptureGuess.guess(text)
        // Scelto a mano non è una domanda: si salva.
        if (chosen == null) CaptureAsk.answer(context, text)?.let { return Outcome.Answered(it) }
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
}
