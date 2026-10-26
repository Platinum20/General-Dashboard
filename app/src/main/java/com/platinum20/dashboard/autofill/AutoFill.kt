package com.platinum20.dashboard.autofill

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationManagerCompat
import com.platinum20.dashboard.notes.Expense
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

enum class SuggestionState { PENDING, ADDED, DISMISSED }

/** Una cosa trovata da sola, con da dove arriva e cosa ne è stato. */
data class Suggestion(
    val id: Long,
    val found: Found,
    val app: String,
    val seenAt: Long,
    val state: SuggestionState = SuggestionState.PENDING,
    /** L'id del pacco, della spesa o del promemoria creato: serve ad "Annulla". */
    val ref: Long? = null,
    /** Salvata senza chiedere (con "Salva senza chiedere" acceso). */
    val auto: Boolean = false,
)

/** Regole pure: cosa è davvero nuovo e cosa tenere. Separate per poterle provare nei test. */
object AutoFillRules {
    /** Dopo tanto tempo una chiave si può dimenticare: le notifiche vecchie non tornano. */
    const val KEEP_DAYS = 60L

    fun isNew(found: Found, seen: List<Suggestion>, notes: NotesData): Boolean {
        if (seen.any { it.found.key == found.key }) return false
        return when (found) {
            is Found.NewParcel -> notes.parcels.none { p ->
                (found.code.isNotEmpty() && p.code.equals(found.code, true)) ||
                    (found.code.isEmpty() && p.title.equals(found.title, true))
            }
            // Una spesa uguale scritta a mano lo stesso giorno: è la stessa.
            is Found.Spent -> notes.expenses.none { it.cents == found.cents && it.date == found.date }
            is Found.Deadline -> notes.tasks.none { it.text.equals(found.title, true) } &&
                notes.countdowns.none { it.title.equals(found.title, true) && it.date == found.due.toLocalDate() }
            is Found.Delivered -> true
        }
    }

    /** Le cose lette in ritardo (SMS vecchi, notifiche rimaste in tendina) devono essere ancora attuali. */
    fun isFresh(found: Found, msgAt: LocalDateTime, now: LocalDateTime): Boolean = when (found) {
        is Found.Deadline -> found.due.isAfter(now)
        is Found.NewParcel -> msgAt.isAfter(now.minusDays(10)) && found.expected?.isBefore(now.toLocalDate()) != true
        is Found.Spent -> msgAt.isAfter(now.minusDays(45))
        is Found.Delivered -> true
    }

    fun prune(list: List<Suggestion>, nowMs: Long): List<Suggestion> {
        val limit = nowMs - KEEP_DAYS * 24 * 3600_000L
        return list.filter { it.seenAt >= limit || it.state == SuggestionState.PENDING }
    }

    /** In Oggi: quelle da decidere e quelle salvate da sole nelle ultime 24 ore. */
    fun visible(list: List<Suggestion>, nowMs: Long): List<Suggestion> = list.filter {
        it.state == SuggestionState.PENDING ||
            (it.state == SuggestionState.ADDED && it.auto && nowMs - it.seenAt < 24 * 3600_000L)
    }.sortedByDescending { it.seenAt }
}

/**
 * Compila da solo: tiene le cose trovate in notifiche e SMS, le propone in Oggi
 * e, se l'utente lo vuole, le salva senza chiedere. Tutto resta sul telefono.
 */
object AutoFill {
    private const val FILE = "autofill.json"
    private const val PREFS = "autofill"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_SAVE = "auto_save"
    private const val KEY_INTRO_DISMISSED = "intro_dismissed"
    private const val KEY_SMS_SCANNED = "sms_scanned"
    private const val KEY_TIDIED = "tidied_1181"

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences
    private lateinit var appContext: Context

    private val _items = MutableStateFlow<List<Suggestion>>(emptyList())
    val items: StateFlow<List<Suggestion>> = _items.asStateFlow()
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _autoSave = MutableStateFlow(false)
    val autoSave: StateFlow<Boolean> = _autoSave.asStateFlow()
    private val _introDismissed = MutableStateFlow(false)
    val introDismissed: StateFlow<Boolean> = _introDismissed.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        appContext = context.applicationContext
        NotesStore.init(appContext)
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _enabled.value = prefs.getBoolean(KEY_ENABLED, false)
        _autoSave.value = prefs.getBoolean(KEY_AUTO_SAVE, false)
        _introDismissed.value = prefs.getBoolean(KEY_INTRO_DISMISSED, false)
        file = File(appContext.filesDir, FILE)
        _items.value = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
        if (!prefs.getBoolean(KEY_TIDIED, false)) {
            runCatching { tidySaved() }
            prefs.edit().putBoolean(KEY_TIDIED, true).apply()
        }
    }

    /**
     * Le cose salvate prima della 1.181: i promemoria con la spunta diventano date da aspettare
     * e i titoli lunghi presi dalle mail ("Scadenza: La tua prova gratuita di Metal") diventano corti.
     */
    private fun tidySaved() {
        val zone = ZoneId.systemDefault()
        val next = _items.value.map { s ->
            when (val f = s.found) {
                is Found.Deadline -> {
                    val title = Extractor.shortTitle(f.title).ifEmpty { f.title }
                    val task = s.ref?.let { NotesStore.task(it) }?.takeIf { s.state == SuggestionState.ADDED && !it.done && it.dueAt != null }
                    val ref = if (task == null) s.ref else {
                        NotesStore.deleteTask(task.id)
                        NotesStore.saveCountdown(null, title, Instant.ofEpochMilli(task.dueAt!!).atZone(zone).toLocalDate(), false)
                    }
                    s.copy(found = f.copy(title = title), ref = ref)
                }
                is Found.NewParcel -> if (f.code.isNotEmpty()) s else {
                    val title = Extractor.itemName(f.title).ifEmpty { f.title }
                    if (s.state == SuggestionState.ADDED) s.ref?.let { NotesStore.renameParcel(it, title) }
                    s.copy(found = f.copy(title = title))
                }
                else -> s
            }
        }
        update { next }
    }

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun setAutoSave(on: Boolean) {
        _autoSave.value = on
        prefs.edit().putBoolean(KEY_AUTO_SAVE, on).apply()
    }

    fun dismissIntro() {
        _introDismissed.value = true
        prefs.edit().putBoolean(KEY_INTRO_DISMISSED, true).apply()
    }

    var smsScannedAt: Long
        get() = prefs.getLong(KEY_SMS_SCANNED, 0L)
        set(value) = prefs.edit().putLong(KEY_SMS_SCANNED, value).apply()

    /** Canvas ha il permesso di leggere le notifiche (si dà dalle impostazioni di Android). */
    fun listenerAllowed(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun listenerComponent(context: Context) = ComponentName(context, AutoFillListener::class.java)

    /**
     * Passa un messaggio al setaccio e tiene quello che è nuovo.
     * Restituisce le proposte aggiunte (per la notifica).
     */
    @Synchronized
    fun offer(msg: Incoming, notify: Boolean = true): List<Suggestion> {
        if (!::file.isInitialized) return emptyList()
        val now = System.currentTimeMillis()
        val added = mutableListOf<Suggestion>()
        for (found in Extractor.extract(msg)) {
            if (found is Found.Delivered) {
                // Un pacco consegnato non va proposto: si segna e basta.
                NotesStore.markDelivered(found.code)
                update { list ->
                    list.map {
                        val f = it.found
                        if (it.state == SuggestionState.PENDING && f is Found.NewParcel && f.code.equals(found.code, true)) {
                            it.copy(state = SuggestionState.DISMISSED)
                        } else it
                    }
                }
                continue
            }
            if (!AutoFillRules.isFresh(found, msg.at, LocalDateTime.now())) continue
            if (!AutoFillRules.isNew(found, _items.value, NotesStore.data.value)) continue
            var item = Suggestion(newId(), found, msg.app, now)
            if (_autoSave.value) item = item.copy(state = SuggestionState.ADDED, ref = apply(found), auto = true)
            update { listOf(item) + it }
            added += item
        }
        if (notify && added.isNotEmpty()) AutoFillNotifier.show(appContext, added)
        return added
    }

    @Synchronized
    fun accept(id: Long) {
        val item = _items.value.firstOrNull { it.id == id && it.state == SuggestionState.PENDING } ?: return
        val ref = apply(item.found)
        update { list -> list.map { if (it.id == id) it.copy(state = SuggestionState.ADDED, ref = ref) else it } }
        AutoFillNotifier.cancel(appContext, id)
    }

    fun acceptAll() = _items.value.filter { it.state == SuggestionState.PENDING }.forEach { accept(it.id) }

    @Synchronized
    fun dismiss(id: Long) {
        update { list -> list.map { if (it.id == id && it.state == SuggestionState.PENDING) it.copy(state = SuggestionState.DISMISSED) else it } }
        AutoFillNotifier.cancel(appContext, id)
    }

    /** Toglie quello che era stato salvato da solo e lo scarta. */
    @Synchronized
    fun undo(id: Long) {
        val item = _items.value.firstOrNull { it.id == id && it.state == SuggestionState.ADDED } ?: return
        item.ref?.let { ref ->
            when (item.found) {
                is Found.NewParcel -> NotesStore.deleteParcel(ref)
                is Found.Spent -> NotesStore.deleteExpense(ref)
                // Prima della 1.181 era un promemoria, ora è una data: l'id è di uno dei due.
                is Found.Deadline -> {
                    NotesStore.deleteCountdown(ref)
                    NotesStore.deleteTask(ref)
                }
                is Found.Delivered -> Unit
            }
        }
        update { list -> list.map { if (it.id == id) it.copy(state = SuggestionState.DISMISSED, ref = null) else it } }
        AutoFillNotifier.cancel(appContext, id)
    }

    /** Lascia stare quelli salvati da soli: escono dalla card di Oggi. */
    fun clearRecent() = update { list -> list.map { if (it.auto && it.state == SuggestionState.ADDED) it.copy(auto = false) else it } }

    fun forgetAll() = update { emptyList() }

    private fun apply(found: Found): Long? = when (found) {
        is Found.NewParcel -> NotesStore.addParcel(found.title, found.code, found.expected)
        is Found.Spent -> NotesStore.addExpense(Expense(0, found.cents, found.merchant, found.category, found.date)).id
        // Una data da aspettare, non un promemoria da spuntare: la scadenza di un abbonamento non si "fa".
        is Found.Deadline -> NotesStore.saveCountdown(null, found.title, found.due.toLocalDate(), false)
        is Found.Delivered -> null
    }

    private var lastId = 0L
    private fun newId(): Long = maxOf(System.currentTimeMillis(), lastId + 1).also { lastId = it }

    private fun update(change: (List<Suggestion>) -> List<Suggestion>) {
        val next = AutoFillRules.prune(change(_items.value), System.currentTimeMillis())
        if (next == _items.value) return
        _items.value = next
        if (::file.isInitialized) runCatching {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(serialize(next))
            tmp.renameTo(file)
        }
    }

    // --- File ---

    fun serialize(list: List<Suggestion>): String = JSONArray().apply {
        list.forEach { s ->
            val o = JSONObject().put("id", s.id).put("app", s.app).put("seenAt", s.seenAt)
                .put("state", s.state.name).put("auto", s.auto)
            s.ref?.let { o.put("ref", it) }
            when (val f = s.found) {
                is Found.NewParcel -> o.put("type", "parcel").put("title", f.title).put("code", f.code)
                    .apply { f.expected?.let { put("expected", it.toString()) } }
                is Found.Spent -> o.put("type", "expense").put("cents", f.cents).put("merchant", f.merchant)
                    .put("category", f.category.name).put("date", f.date.toString()).put("at", f.at.toString())
                is Found.Deadline -> o.put("type", "deadline").put("title", f.title).put("due", f.due.toString())
                is Found.Delivered -> o.put("type", "delivered").put("code", f.code)
            }
            put(o)
        }
    }.toString()

    fun parse(raw: String): List<Suggestion> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val found: Found = when (o.getString("type")) {
                    "parcel" -> Found.NewParcel(
                        o.optString("title"), o.optString("code"),
                        if (o.has("expected")) LocalDate.parse(o.getString("expected")) else null,
                    )
                    "expense" -> Found.Spent(
                        o.getLong("cents"), o.optString("merchant"),
                        runCatching { ExpenseCategory.valueOf(o.optString("category")) }.getOrDefault(ExpenseCategory.OTHER),
                        LocalDate.parse(o.getString("date")), LocalDateTime.parse(o.getString("at")),
                    )
                    "deadline" -> Found.Deadline(o.optString("title"), LocalDateTime.parse(o.getString("due")))
                    else -> Found.Delivered(o.optString("code"))
                }
                Suggestion(
                    o.getLong("id"), found, o.optString("app"), o.optLong("seenAt"),
                    runCatching { SuggestionState.valueOf(o.optString("state")) }.getOrDefault(SuggestionState.DISMISSED),
                    if (o.has("ref")) o.getLong("ref") else null,
                    o.optBoolean("auto"),
                )
            }.getOrNull()
        }
    }
}
