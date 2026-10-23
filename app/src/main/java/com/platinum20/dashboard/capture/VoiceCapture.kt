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
    }

    suspend fun save(context: Context, text: String): Outcome {
        val mode = CaptureGuess.guess(text)
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
        return Outcome.Saved(mode, result.message, result.undo?.let { undo -> { undo() } })
    }
}
