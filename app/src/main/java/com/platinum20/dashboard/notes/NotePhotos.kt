package com.platinum20.dashboard.notes

/**
 * Le foto di una nota (1.529: più d'una). Solo conti sulla lista di percorsi, per poterli provare; i file li tiene
 * [NotesStore] e la fila dell'editor li disegna. La prima è la copertina sulla linea del tempo.
 */
object NotePhotos {
    /** Aggiunge in fondo, senza doppioni. */
    fun add(list: List<String>, new: List<String>): List<String> = list + new.filter { it.isNotEmpty() && it !in list }.distinct()

    fun remove(list: List<String>, path: String): List<String> = list - path

    /** "Annulla": la foto torna al suo posto. */
    fun restore(list: List<String>, path: String, index: Int): List<String> =
        if (path in list) list else list.toMutableList().apply { add(index.coerceIn(0, size), path) }

    fun move(list: List<String>, from: Int, to: Int): List<String> {
        if (from !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to.coerceIn(0, size - 1), removeAt(from)) }
    }

    fun toFront(list: List<String>, path: String): List<String> = move(list, list.indexOf(path), 0)

    fun replace(list: List<String>, old: String, new: String): List<String> = list.map { if (it == old) new else it }

    /** Dove cade la foto trascinata: un posto ogni [step] pixel, dentro la fila. */
    fun target(from: Int, offset: Float, step: Float, size: Int): Int {
        if (size <= 0 || step <= 0f) return from
        return (from + Math.round(offset / step)).coerceIn(0, size - 1)
    }

    /** Di quanti posti si sposta la foto [i] mentre [from] viene portata su [to]: le altre si fanno da parte. */
    fun shift(i: Int, from: Int, to: Int): Int = when {
        i == from -> 0
        from < to && i in (from + 1)..to -> -1
        from > to && i in to until from -> 1
        else -> 0
    }

    /** "1 foto", "3 foto": nella riga in alto dell'editor. */
    fun label(n: Int): String? = if (n <= 0) null else "$n foto"
}
