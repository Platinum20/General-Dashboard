package com.platinum20.dashboard.capture

import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDateTime

/**
 * Dove va quello che si scrive col "+" (1.532, Antonio scelta A): sopra la barra una fila di chip, Canvas accende da
 * solo quella giusta e, se sbaglia, se ne tocca un'altra. Il tipo non si chiede mai: si può solo correggere.
 */
internal enum class CaptureDestination(val label: String) {
    TODO("Da fare"),
    JOTTINGS("Appunti"),
    CALENDAR("Agenda"),
    HABITS("Abitudine"),
    ;

    companion object {
        /** La chip accesa da sola: dove Canvas metterebbe il testo. */
        fun of(mode: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): CaptureDestination = when (mode) {
            CaptureMode.NOTE -> JOTTINGS
            CaptureMode.HABIT -> HABITS
            CaptureMode.TASK -> if (QuickParse.parse(text.trim(), now).due == null) TODO else CALENDAR
            CaptureMode.EVENT, CaptureMode.DATE, CaptureMode.PARCEL -> CALENDAR
        }

        /**
         * Il tipo con cui salvare, se la chip scelta a mano è diversa da quella automatica. Nel Calendario resta il tipo
         * indovinato se era già lì (evento, data, pacco, promemoria con l'ora); altrimenti diventa un evento, e se manca
         * il quando si apre la finestra per dirlo.
         */
        fun modeFor(chosen: CaptureDestination, guessed: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): CaptureMode = when (chosen) {
            TODO -> CaptureMode.TASK
            JOTTINGS -> CaptureMode.NOTE
            HABITS -> CaptureMode.HABIT
            CALENDAR -> if (of(guessed, text, now) == CALENDAR) guessed else CaptureMode.EVENT
        }

        /** "Da fare" scelto a mano su un testo con una data: si salva com'è, senza data (se no finirebbe nel Calendario). */
        fun plainTodo(chosen: CaptureDestination?, guessed: CaptureMode, text: String, now: LocalDateTime = LocalDateTime.now()): Boolean =
            chosen == TODO && of(guessed, text, now) != TODO
    }
}
