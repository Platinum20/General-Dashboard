package com.platinum20.dashboard.bank

import android.content.Context
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.work.KeepAliveWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Il conto Revolut collegato: saldo, ultima lettura, sessione scaduta o no. I movimenti finiscono
 * nelle spese del calendario ([NotesStore.syncBank]); le risposte lette restano solo sul telefono
 * (servono a correggere la lettura se Revolut cambia formato), mai quelle di accesso.
 */
object RevolutStore {
    private const val PREFS = "revolut"
    private const val CAPTURES = "revolut-captures.json"
    private const val MAX_CAPTURES = 30
    /**
     * Ogni lettura apre una pagina nascosta; sotto i 15 minuti del lavoro in sottofondo, così
     * ogni giro tocca davvero la sessione e Revolut non la chiude per inattività.
     */
    const val STALE_MS = 14 * 60 * 1000L

    private var appContext: Context? = null
    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()
    private val _balance = MutableStateFlow<Long?>(null)
    val balance: StateFlow<Long?> = _balance.asStateFlow()
    /** Le tasche in euro (conto + salvadanai), coi nomi veri: la card Revolut della Home. */
    private val _pockets = MutableStateFlow<List<Pocket>>(emptyList())
    val pockets: StateFlow<List<Pocket>> = _pockets.asStateFlow()
    private var rawPockets: List<Pocket> = emptyList()
    private var pocketNames: Map<String, String> = emptyMap()
    private val _readAt = MutableStateFlow(0L)
    val readAt: StateFlow<Long> = _readAt.asStateFlow()
    /** La sessione web è finita: serve rifare l'accesso (con la conferma nell'app Revolut). */
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val captures = ArrayDeque<JSONObject>()
    private val requests = LinkedHashSet<String>()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        NotesStore.init(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _linked.value = prefs.getBoolean("linked", false)
        _balance.value = if (prefs.contains("balance")) prefs.getLong("balance", 0) else null
        _readAt.value = prefs.getLong("readAt", 0)
        _expired.value = prefs.getBoolean("expired", false)
        historyDone = prefs.getBoolean("historyDone", false)
        historyFrom = prefs.getLong("historyFrom", 0)
        runCatching {
            val arr = JSONArray(File(app.filesDir, CAPTURES).readText())
            for (i in 0 until arr.length()) captures.addLast(arr.getJSONObject(i))
        }
        rawPockets = RevolutPockets.fromJson(prefs.getString("pockets", null))
        pocketNames = RevolutPockets.namesFromJson(prefs.getString("pocketNames", null))
        _pockets.value = RevolutPockets.named(rawPockets, pocketNames)
    }

    fun isStale(now: Long = System.currentTimeMillis()) = readAt.value < now - STALE_MS

    /** Tutti i movimenti finiscono nel calendario, dal primo (Antonio vuole tutta la storia). */
    val SINCE: LocalDate = LocalDate.of(2000, 1, 1)

    /**
     * La storia completa è già stata letta (pagina per pagina, una volta sola): le riletture
     * successive prendono solo gli ultimi movimenti.
     */
    @Volatile var historyDone = false
        private set
    /** Il movimento più vecchio letto finora (millisecondi): da qui riparte una storia interrotta. */
    @Volatile var historyFrom = 0L
        private set
    /** Quando la pagina ha finito l'ultima rilettura (anche a metà, per errore). */
    @Volatile var pullEndedAt = 0L
        private set

    fun pullEnded(complete: Boolean) {
        pullEndedAt = System.currentTimeMillis()
        if (complete && !historyDone) {
            historyDone = true
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("historyDone", true)?.apply()
        }
    }

    /** Una risposta dell'app web (chiamato dalla pagina, su un thread qualsiasi). */
    fun capture(url: String, status: Int, body: String) {
        val clean = url.substringBefore('?')
        if (RevolutParse.isPrivate(clean)) return
        // 401 su un'API del conto: la sessione è scaduta, i dati vecchi restano ma va rifatto l'accesso.
        if (status == 401 && clean.contains("/api/")) {
            if (_linked.value) setExpired(true)
            return
        }
        if (status !in 200..299) return
        val read = RevolutParse.read(body)
        read?.oldestMs?.let { at ->
            if (historyFrom == 0L || at < historyFrom) {
                historyFrom = at
                appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putLong("historyFrom", at)?.apply()
            }
        }
        synchronized(this) {
            captures.removeAll { it.optString("url") == clean }
            captures.addLast(JSONObject().put("url", clean).put("at", System.currentTimeMillis()).put("body", body.take(100_000)))
            while (captures.size > MAX_CAPTURES) captures.removeFirst()
        }
        // Il calendario si tocca dal thread principale, come fa il resto dell'app.
        if (read != null) main.post { commit(read) }
        RevolutPockets.read(body)?.let { p -> main.post { commitPockets(p) } }
        saveCaptures()
    }

    /** Gli indirizzi chiamati dalla pagina (senza parametri): dicono dove stanno conti e movimenti. */
    fun seen(url: String) {
        val clean = url.substringBefore('?')
        if (RevolutParse.isPrivate(clean)) return
        synchronized(this) {
            if (!requests.add(clean)) return
            if (requests.size > 200) requests.remove(requests.first())
        }
    }

    private fun commit(read: RevolutRead) {
        if (read.txs.isNotEmpty()) NotesStore.syncBank(read.txs, SINCE)
        val prefs = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
        read.balanceCents?.let { _balance.value = it; prefs?.putLong("balance", it) }
        _readAt.value = System.currentTimeMillis()
        prefs?.putLong("readAt", _readAt.value)?.apply()
        if (!_linked.value) setLinked(true)
        if (_expired.value) setExpired(false)
        // Lettura riuscita = sessione buona: se ne tiene la copia per dopo un aggiornamento.
        appContext?.let { RevolutSession.save(it) }
        // Il widget Spese mostra il saldo.
        appContext?.let { runCatching { com.platinum20.dashboard.widget.Widgets.refresh(it, com.platinum20.dashboard.widget.SpendWidget::class.java) } }
    }

    private fun commitPockets(read: PocketRead) {
        // Il wallet le elenca tutte: sostituisce. I nomi dei salvadanai si sommano a quelli già visti.
        read.pockets?.let { rawPockets = it }
        pocketNames = pocketNames + read.names
        _pockets.value = RevolutPockets.named(rawPockets, pocketNames)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("pockets", RevolutPockets.toJson(rawPockets))
            ?.putString("pocketNames", RevolutPockets.namesToJson(pocketNames))
            ?.apply()
    }

    fun setLinked(value: Boolean) {
        _linked.value = value
        appContext?.let { app ->
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("linked", value).apply()
            runCatching { KeepAliveWorker.update(app) }
        }
    }

    fun setExpired(value: Boolean) {
        _expired.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean("expired", value)?.apply()
    }

    fun setRefreshing(value: Boolean) {
        _refreshing.value = value
    }

    private fun saveCaptures() {
        val app = appContext ?: return
        val snapshot = synchronized(this) { JSONArray(captures.toList()).toString() }
        runCatching { File(app.filesDir, CAPTURES).writeText(snapshot) }
    }

    /** Scollega: via saldo e risposte salvate. Le spese già nel calendario restano (sono sue). */
    fun unlink() {
        _linked.value = false
        appContext?.let { runCatching { KeepAliveWorker.update(it) } }
        _balance.value = null
        rawPockets = emptyList(); pocketNames = emptyMap(); _pockets.value = emptyList()
        _readAt.value = 0
        _expired.value = false
        historyDone = false
        historyFrom = 0
        synchronized(this) { captures.clear(); requests.clear() }
        val app = appContext ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        File(app.filesDir, CAPTURES).delete()
    }

    /** Le risposte lette, da mandare a chi sistema la lettura se qualcosa non torna. */
    fun capturesText(): String = synchronized(this) {
        JSONObject().put("richieste", JSONArray(requests.toList())).put("risposte", JSONArray(captures.toList())).toString(1)
    }
}
