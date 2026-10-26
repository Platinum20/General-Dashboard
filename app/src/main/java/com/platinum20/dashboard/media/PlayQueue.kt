package com.platinum20.dashboard.media

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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
 * ordine. Quello che parte (da qualunque schermata) diventa la voce corrente; "Aggiungi alla coda"
 * mette in fondo; finito un brano parte il successivo, di qualunque fonte sia.
 * Le operazioni sulla lista sono funzioni pure (provate nei test); [PlayQueue] le applica e le salva.
 */
object QueueOps {
    /** Stessa cosa? Per YouTube conta il video, che sia guardato o ascoltato. */
    fun same(a: ResumeItem, b: ResumeItem): Boolean {
        fun family(k: ResumeKind) = if (k == ResumeKind.MUSIC) ResumeKind.YOUTUBE else k
        return family(a.kind) == family(b.kind) && a.key == b.key
    }

    /** È partito [item]: se è già la corrente resta tutto com'è, se no entra subito dopo la corrente e lo diventa. */
    fun started(state: QueueState, item: ResumeItem, id: Long): QueueState {
        val now = state.now
        if (now != null && same(now.item, item)) {
            return state.copy(entries = state.entries.toMutableList().also { it[state.current] = now.copy(item = item) })
        }
        // Era la prossima in coda (es. partita da sola): si va avanti senza doppioni.
        val next = state.entries.getOrNull(state.current + 1)
        if (next != null && same(next.item, item)) return state.copy(current = state.current + 1)
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

object PlayQueue {
    private const val KEY = "queue"
    private const val KEY_CURRENT = "queue_current"
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context
    private var nextId = 1L

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("play_queue", Context.MODE_PRIVATE)
        val entries = runCatching {
            val array = JSONArray(prefs.getString(KEY, "[]"))
            (0 until array.length()).mapNotNull { i -> runCatching { ResumeItem.fromJson(array.getJSONObject(i)) }.getOrNull() }
        }.getOrDefault(emptyList()).mapIndexed { i, it -> QueueEntry(i + 1L, it) }
        nextId = entries.size + 1L
        _state.value = QueueState(entries, prefs.getInt(KEY_CURRENT, -1).coerceIn(-1, entries.size - 1))
    }

    private fun set(state: QueueState) {
        val trimmed = QueueOps.trim(state)
        _state.value = trimmed
        if (!::prefs.isInitialized) return
        val array = JSONArray()
        trimmed.entries.forEach { array.put(it.item.toJson()) }
        prefs.edit().putString(KEY, array.toString()).putInt(KEY_CURRENT, trimmed.current).apply()
    }

    private fun id(): Long = nextId++

    /** Lo chiamano i player quando parte qualcosa, da qualunque schermata. */
    fun started(item: ResumeItem) {
        if (item.kind == ResumeKind.VIDEO) return
        set(QueueOps.started(_state.value, item, id()))
    }

    fun add(item: ResumeItem) = set(QueueOps.add(_state.value, item, id()))

    fun addNext(item: ResumeItem) = set(QueueOps.addNext(_state.value, item, id()))

    fun remove(id: Long) = set(QueueOps.remove(_state.value, id))

    fun move(id: Long, by: Int) = set(QueueOps.move(_state.value, id, by))

    fun clearUpcoming() = set(QueueOps.clearUpcoming(_state.value))

    /** Fa partire una lista (album, brani di una ricerca) da [start]; quello che c'era dopo si sostituisce. */
    fun playList(context: Context, items: List<ResumeItem>, start: Int) {
        if (items.isEmpty()) return
        init(context)
        val first = nextId
        nextId += items.size
        set(QueueOps.playList(_state.value, items, start, first))
        _state.value.now?.let { launch(context, it.item) }
    }

    /** Salta a una voce della coda e la fa partire. */
    fun jump(context: Context, id: Long) {
        set(QueueOps.jump(_state.value, id))
        _state.value.now?.let { launch(context, it.item) }
    }

    /** Avanti nella coda, se c'è qualcosa dopo. */
    fun next(context: Context): Boolean {
        val s = _state.value
        if (!s.hasNext) return false
        set(s.copy(current = s.current + 1))
        launch(context, _state.value.now!!.item)
        return true
    }

    fun previous(context: Context): Boolean {
        val s = _state.value
        if (!s.hasPrevious) return false
        set(s.copy(current = s.current - 1))
        launch(context, _state.value.now!!.item)
        return true
    }

    /** Finito quello che suonava: parte il successivo, se c'è. */
    fun ended(context: Context) {
        next(context)
    }

    private fun launch(context: Context, item: ResumeItem) {
        // Dalla coda si parte dall'inizio, tranne i podcast che riprendono dal punto lasciato.
        // Un brano di Spotify suona da solo (senza il suo album): dopo decide la coda.
        MediaLauncher.start(context, if (item.kind == ResumeKind.PODCAST) item else item.copy(positionMs = 0), autoplay = true, single = true)
    }
}
