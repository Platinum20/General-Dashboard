package com.platinum20.dashboard.media

/** Una voce della coda: [id] resta lo stesso anche se la voce si sposta, così la lista anima bene. */
data class QueueEntry(val id: Long, val item: ResumeItem)

/** La coda in un momento: le voci e quella che suona (-1 se nessuna). */
data class QueueState(val entries: List<QueueEntry> = emptyList(), val current: Int = -1) {
    val now: QueueEntry? get() = entries.getOrNull(current)
    val upcoming: List<QueueEntry> get() = if (current < 0) entries else entries.drop(current + 1)
    val hasNext: Boolean get() = current + 1 < entries.size
    val hasPrevious: Boolean get() = current > 0
}

/**
 * La coda unica dell'app: YouTube, musica, Spotify, radio e podcast uno dopo l'altro, in qualunque
 * ordine. Le operazioni sulla lista sono funzioni pure (provate nei test); [CanvasPlayer] le applica,
 * le salva e suona la voce corrente.
 */
object QueueOps {
    /** Stessa cosa? Per YouTube conta il video, che sia guardato o ascoltato. */
    fun same(a: ResumeItem, b: ResumeItem): Boolean {
        fun family(k: ResumeKind) = if (k == ResumeKind.MUSIC) ResumeKind.YOUTUBE else k
        return family(a.kind) == family(b.kind) && a.key == b.key
    }

    /**
     * Suona [item] adesso: se è già la corrente resta lì (aggiornata), se è la prossima si va avanti,
     * se no entra subito dopo la corrente e lo diventa. Quello che veniva dopo resta in coda.
     */
    fun playNow(state: QueueState, item: ResumeItem, id: Long): QueueState {
        val now = state.now
        if (now != null && same(now.item, item)) {
            return state.copy(entries = state.entries.toMutableList().also { it[state.current] = now.copy(item = item) })
        }
        val next = state.entries.getOrNull(state.current + 1)
        if (next != null && same(next.item, item)) {
            return QueueState(state.entries.toMutableList().also { it[state.current + 1] = next.copy(item = item) }, state.current + 1)
        }
        val at = state.current + 1
        val list = state.entries.toMutableList().apply { add(at, QueueEntry(id, item)) }
        return QueueState(list, at)
    }

    fun add(state: QueueState, item: ResumeItem, id: Long): QueueState =
        state.copy(entries = state.entries + QueueEntry(id, item))

    /** Subito dopo quella che suona. */
    fun addNext(state: QueueState, item: ResumeItem, id: Long): QueueState {
        val at = (state.current + 1).coerceAtMost(state.entries.size)
        return state.copy(entries = state.entries.toMutableList().apply { add(at, QueueEntry(id, item)) })
    }

    /** Una lista (un album, i brani di una ricerca) che parte da [start]: sostituisce quello che veniva dopo. */
    fun playList(state: QueueState, items: List<ResumeItem>, start: Int, firstId: Long): QueueState {
        val kept = if (state.current < 0) emptyList() else state.entries.take(state.current + 1)
        val added = items.mapIndexed { i, it -> QueueEntry(firstId + i, it) }
        return QueueState(kept + added, kept.size + start.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    }

    fun remove(state: QueueState, id: Long): QueueState {
        val index = state.entries.indexOfFirst { it.id == id }
        if (index < 0) return state
        val list = state.entries.filterNot { it.id == id }
        val current = when {
            index < state.current -> state.current - 1
            // Tolta quella che suona: "avanti" porta a quella che la seguiva.
            index == state.current -> state.current - 1
            else -> state.current
        }
        return QueueState(list, current.coerceAtMost(list.size - 1))
    }

    /** Sposta di [by] posti (-1 su, +1 giù), seguendo la voce corrente se si scavalca. */
    fun move(state: QueueState, id: Long, by: Int): QueueState {
        val from = state.entries.indexOfFirst { it.id == id }
        if (from < 0) return state
        val to = (from + by).coerceIn(0, state.entries.size - 1)
        if (to == from) return state
        val list = state.entries.toMutableList()
        val entry = list.removeAt(from)
        list.add(to, entry)
        val current = when (state.current) {
            from -> to
            in minOf(from, to)..maxOf(from, to) -> if (to < from) state.current + 1 else state.current - 1
            else -> state.current
        }
        return QueueState(list, current)
    }

    fun jump(state: QueueState, id: Long): QueueState {
        val index = state.entries.indexOfFirst { it.id == id }
        return if (index < 0) state else state.copy(current = index)
    }

    /** Toglie quello che deve ancora venire (la corrente e le passate restano). */
    fun clearUpcoming(state: QueueState): QueueState =
        if (state.current < 0) QueueState() else QueueState(state.entries.take(state.current + 1), state.current)

    /** Tiene al massimo [keep] voci già ascoltate prima della corrente. */
    fun trim(state: QueueState, keep: Int = 30): QueueState {
        val drop = (state.current - keep).coerceAtLeast(0)
        return if (drop == 0) state else QueueState(state.entries.drop(drop), state.current - drop)
    }
}
