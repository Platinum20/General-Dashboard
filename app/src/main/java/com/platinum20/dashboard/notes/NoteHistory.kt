package com.platinum20.dashboard.notes

/**
 * Annulla e Ripeti dell'editor (1.579): una parola scritta di fila è un passo solo, il resto (Invio, cancellare un
 * pezzo, un tasto della barra) è un passo a sé. Funzione pura: si prova senza telefono.
 */
class NoteHistory(private val max: Int = 200) {
    data class Snap(val text: String, val start: Int, val end: Int = start)

    private val back = ArrayDeque<Snap>()
    private val ahead = ArrayDeque<Snap>()
    private var lastAt = 0L
    private var typing = false
    /** Dove stava il cursore dopo l'ultima lettera: una lettera scritta altrove è un passo nuovo (2026-11-26). */
    private var lastEnd = -1

    val canUndo: Boolean get() = back.isNotEmpty()
    val canRedo: Boolean get() = ahead.isNotEmpty()

    /** Il testo è cambiato da [before] ad [after] all'istante [now] (millisecondi). */
    fun record(before: Snap, after: Snap, now: Long) {
        if (before.text == after.text) return
        val c = after.start - 1
        val oneLetter = after.text.length == before.text.length + 1 && c in after.text.indices && !after.text[c].isWhitespace()
        if (!(oneLetter && typing && now - lastAt < PAUSE && after.start - 1 == lastEnd)) {
            back.addLast(before)
            if (back.size > max) back.removeFirst()
        }
        ahead.clear()
        lastAt = now
        typing = oneLetter
        lastEnd = after.start
    }

    fun undo(current: Snap): Snap? {
        val s = back.removeLastOrNull() ?: return null
        ahead.addLast(current)
        typing = false
        return s
    }

    fun redo(current: Snap): Snap? {
        val s = ahead.removeLastOrNull() ?: return null
        back.addLast(current)
        typing = false
        return s
    }

    private companion object {
        /** Oltre questa pausa una nuova lettera comincia un passo nuovo. */
        const val PAUSE = 1500L
    }
}
