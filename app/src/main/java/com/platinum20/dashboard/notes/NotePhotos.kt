package com.platinum20.dashboard.notes

/**
 * Le foto di una nota (1.529: più d'una). Solo conti sulla lista di percorsi, per poterli provare; i file li tiene
 * [NotesStore] e la fila dell'editor li disegna. La prima è la copertina sulla linea del tempo.
 */
object NotePhotos {
    /** Aggiunge in fondo, senza doppioni. */
    fun add(list: List<String>, new: List<String>): List<String> = list + new.filter { it.isNotEmpty() && it !in list }.distinct()

    /**
     * Senza doppioni di contenuto (1.552): la stessa foto aggiunta due volte resta una, la prima. [prints] = impronta
     * per percorso ([NotePhotoFiles.fingerprint]); una foto senza impronta (file illeggibile) resta com'è.
     */
    fun unique(list: List<String>, prints: Map<String, String>): List<String> {
        val seen = mutableSetOf<String>()
        return list.filter { p -> prints[p]?.let { seen.add(it) } ?: true }
    }

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

    /** Un posto nel mosaico dell'editor: angolo in alto a sinistra e lato, in pixel. */
    data class Slot(val x: Float, val y: Float, val size: Float) {
        val cx get() = x + size / 2
        val cy get() = y + size / 2
    }

    /**
     * Il mosaico (1.575): la prima grande, a destra due più piccole una sopra l'altra (alte insieme quanto la grande);
     * scorrendo a destra tutte le altre a pagine 2x2, larghe quanto la grande. [width] = larghezza visibile: le prime
     * tre la riempiono giusta.
     */
    fun slots(count: Int, width: Float, gap: Float): List<Slot> {
        val big = (width - gap / 2) / 1.5f
        val small = (big - gap) / 2
        val gridStart = big + gap + small + gap
        return List(count) { i ->
            when (i) {
                0 -> Slot(0f, 0f, big)
                1 -> Slot(big + gap, 0f, small)
                2 -> Slot(big + gap, small + gap, small)
                else -> {
                    val k = i - 3
                    val x = gridStart + (k / 4) * (big + gap) + (k % 2) * (small + gap)
                    Slot(x, ((k % 4) / 2) * (small + gap), small)
                }
            }
        }
    }

    /** Larghezza di tutto il mosaico, per lo scorrimento. */
    fun mosaicWidth(slots: List<Slot>): Float = slots.maxOfOrNull { it.x + it.size } ?: 0f

    /** Il posto più vicino al dito (il centro della foto trascinata). */
    fun nearest(slots: List<Slot>, x: Float, y: Float): Int =
        slots.indices.minByOrNull { val s = slots[it]; (s.cx - x) * (s.cx - x) + (s.cy - y) * (s.cy - y) } ?: 0

    /** "1 foto", "3 foto": nella riga in alto dell'editor. */
    fun label(n: Int): String? = if (n <= 0) null else "$n foto"
}
