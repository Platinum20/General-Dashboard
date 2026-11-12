package com.platinum20.dashboard.shifts

import android.content.Context
import com.platinum20.dashboard.work.ShiftWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * I turni di Antonio letti da Creators Pro: stanno solo qui (non nel calendario del telefono) e il
 * calendario di Canvas li mostra come eventi. Ogni lettura sostituisce i turni del periodo letto:
 * un turno aggiunto compare, uno tolto sparisce.
 */
object ShiftStore {
    private const val PREFS = "shifts"
    private const val CAPTURES = "shift-captures.json"
    private const val MAX_CAPTURES = 60
    /** Rilettura con l'app aperta: spesso, perché un turno nuovo deve comparire subito. */
    const val STALE_MS = 5 * 60 * 1000L

    private var appContext: Context? = null
    private val _shifts = MutableStateFlow<List<Shift>>(emptyList())
    val shifts: StateFlow<List<Shift>> = _shifts.asStateFlow()
    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()
    private val _readAt = MutableStateFlow(0L)
    val readAt: StateFlow<Long> = _readAt.asStateFlow()
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** La busta paga: com'è andato ogni turno, e i mesi. */
    private val _pay = MutableStateFlow<List<ShiftEarning>>(emptyList())
    val pay: StateFlow<List<ShiftEarning>> = _pay.asStateFlow()
    private val _payMonths = MutableStateFlow<List<PayMonth>>(emptyList())
    val payMonths: StateFlow<List<PayMonth>> = _payMonths.asStateFlow()

    /** Gli id con cui Creators Pro indica Antonio (righe della timeline, utente): servono ai turni senza nome. */
    @Volatile private var myIds: Set<String> = emptySet()
    /** L'ultima pagina timeline aperta: la rilettura nascosta riapre quella. */
    @Volatile var timelineUrl: String? = null
        private set

    /** Statistiche, chat di assistenza, errori: riempirebbero il file dei dati letti senza dire niente dei turni. */
    private val NOISE = Regex("(?i)google-analytics|googletagmanager|sentry|intercom|hotjar|segment\\.io|mixpanel|amplitude|clarity\\.ms|doubleclick|facebook")

    /** C'è qualcosa da mandare per sistemare la lettura (anche se nessun turno è stato riconosciuto). */
    private val _hasData = MutableStateFlow(false)
    val hasData: StateFlow<Boolean> = _hasData.asStateFlow()
    /** Il testo visibile della pagina (la timeline come la vede Antonio), solo per il file dei dati letti. */
    @Volatile private var pageText: String = ""

    fun pageText(text: String) {
        pageText = text.take(200_000)
        _hasData.value = true
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val captures = ArrayDeque<JSONObject>()
    private val requests = LinkedHashSet<String>()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _linked.value = prefs.getBoolean("linked", false)
        _readAt.value = prefs.getLong("readAt", 0)
        _expired.value = prefs.getBoolean("expired", false)
        _shifts.value = ShiftParse.fromJson(prefs.getString("shifts", null))
        _pay.value = ShiftPay.fromJson(prefs.getString("pay", null))
        _payMonths.value = ShiftPay.monthsFromJson(prefs.getString("payMonths", null))
        myIds = prefs.getStringSet("myIds", emptySet()).orEmpty()
        timelineUrl = prefs.getString("timelineUrl", null)
        runCatching {
            val arr = JSONArray(File(app.filesDir, CAPTURES).readText())
            for (i in 0 until arr.length()) captures.addLast(arr.getJSONObject(i))
        }
        _hasData.value = captures.isNotEmpty()
    }

    /** L'ultima volta che l'API ha risposto 401 (non autorizzato). */
    @Volatile var refusedAt = 0L
        private set

    fun isStale(now: Long = System.currentTimeMillis()) = readAt.value < now - STALE_MS

    /** Le risposte di accesso non si tengono mai (password, token), né i dati del bonifico (IBAN, indirizzo). */
    fun isPrivate(url: String) = Regex("(?i)login|signin|sign-in|auth|token|password|session|otp|2fa|payout").containsMatchIn(url)

    /** Una risposta della pagina (chiamato dalla pagina, su un thread qualsiasi). */
    fun capture(url: String, status: Int, body: String) {
        if (isPrivate(url) || NOISE.containsMatchIn(url)) return
        // Un 401 si ricorda e basta: decide [CreatorsLink.refresh] se l'accesso è davvero finito.
        if (status == 401) { refusedAt = System.currentTimeMillis(); return }
        if (status !in 200..299) return
        if (ShiftPay.isPay(url)) return payCapture(url, body)
        val read = ShiftParse.read(body, myIds, url = url)
        synchronized(this) {
            val clean = url.substringBefore('#')
            captures.removeAll { it.optString("url") == clean }
            captures.addLast(JSONObject().put("url", clean).put("at", System.currentTimeMillis()).put("turni", read?.mine?.size ?: -1).put("body", body.take(100_000)))
            while (captures.size > MAX_CAPTURES) captures.removeFirst()
        }
        _hasData.value = true
        saveCaptures()
        // Tempo reale (WebSocket) o un turno solo che non è suo: si aggiunge e basta, non si toglie niente.
        val partial = read?.let { if (url.startsWith("ws") || (it.seen == 1 && it.mine.isEmpty())) it.additive() else it }
        if (partial != null) main.post { commit(partial) }
    }

    /**
     * La busta paga: si tengono solo i numeri dei turni e del mese. Il dettaglio porta ogni vendita coi nomi
     * dei fan: nel file dei dati letti va solo quanti turni sono stati letti, non la risposta.
     */
    private fun payCapture(url: String, body: String) {
        val months = ShiftPay.months(body, url)
        val shifts = ShiftPay.shifts(body, url)
        synchronized(this) {
            val clean = url.substringBefore('#')
            captures.removeAll { it.optString("url") == clean }
            val kept = if (months != null) body.take(20_000) else "turni letti: ${shifts?.size ?: -1}"
            captures.addLast(JSONObject().put("url", clean).put("at", System.currentTimeMillis()).put("turni", shifts?.size ?: months?.size ?: -1).put("body", kept))
            while (captures.size > MAX_CAPTURES) captures.removeFirst()
        }
        _hasData.value = true
        saveCaptures()
        main.post {
            if (months != null) _payMonths.value = ShiftPay.mergeMonths(_payMonths.value, months)
            if (shifts != null) _pay.value = ShiftPay.mergeShifts(_pay.value, shifts)
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
                ?.putString("pay", ShiftPay.toJson(_pay.value))
                ?.putString("payMonths", ShiftPay.monthsToJson(_payMonths.value))
                ?.apply()
        }
    }

    /** Com'è andato quel turno, se la busta paga lo dice. */
    fun payFor(shift: Shift): ShiftEarning? = ShiftPay.of(shift, _pay.value)

    fun seen(url: String) {
        if (isPrivate(url)) return
        synchronized(this) {
            if (!requests.add(url.substringBefore('?'))) return
            if (requests.size > 200) requests.remove(requests.first())
        }
    }

    /** La pagina aperta: se è la timeline, è quella da riaprire. */
    fun visited(url: String) {
        if (!url.contains("timeline", ignoreCase = true) || url == timelineUrl) return
        timelineUrl = url
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("timelineUrl", url)?.apply()
    }

    private fun commit(read: ShiftRead) {
        if (read.myIds.isNotEmpty()) myIds = myIds + read.myIds
        _shifts.value = ShiftParse.merge(_shifts.value, read)
        _readAt.value = System.currentTimeMillis()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("shifts", ShiftParse.toJson(_shifts.value))
            ?.putStringSet("myIds", myIds)
            ?.putLong("readAt", _readAt.value)
            ?.apply()
        if (!_linked.value) setLinked(true)
        if (_expired.value) setExpired(false)
        // Lettura buona: si tiene la sessione così com'è adesso, per le WebView che verranno.
        appContext?.let { runCatching { CreatorsSession.save(it) } }
        // Il widget Calendario mostra anche i turni.
        appContext?.let { runCatching { com.platinum20.dashboard.widget.Widgets.refresh(it, com.platinum20.dashboard.widget.TasksWidget::class.java) } }
    }

    fun setLinked(value: Boolean) {
        _linked.value = value
        appContext?.let { app ->
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("linked", value).apply()
            if (value) ShiftWorker.schedule(app) else ShiftWorker.cancel(app)
            runCatching { com.platinum20.dashboard.work.KeepAliveWorker.update(app) }
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

    /** Scollega: via i turni (sono di Creators Pro, senza collegamento non si tengono aggiornati). */
    fun unlink() {
        _linked.value = false
        appContext?.let { ShiftWorker.cancel(it); runCatching { com.platinum20.dashboard.work.KeepAliveWorker.update(it) } }
        _shifts.value = emptyList()
        _pay.value = emptyList()
        _payMonths.value = emptyList()
        _readAt.value = 0
        _expired.value = false
        myIds = emptySet()
        timelineUrl = null
        synchronized(this) { captures.clear(); requests.clear() }
        pageText = ""
        _hasData.value = false
        val app = appContext ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        File(app.filesDir, CAPTURES).delete()
    }

    fun capturesText(): String = synchronized(this) {
        JSONObject()
            .put("turni_letti", JSONArray(ShiftParse.toJson(_shifts.value)))
            .put("pagina", timelineUrl ?: "")
            .put("testo_pagina", pageText)
            .put("richieste", JSONArray(requests.toList()))
            .put("risposte", JSONArray(captures.toList()))
            .toString(1)
    }
}
