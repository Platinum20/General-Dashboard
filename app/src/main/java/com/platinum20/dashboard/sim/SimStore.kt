package com.platinum20.dashboard.sim

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Il contatore WindTre: l'ultimo stato letto, se l'account è collegato, e le risposte catturate
 * dall'area clienti. Le risposte restano solo sul telefono (servono a correggere la lettura se
 * WindTre cambia formato) e quelle di accesso non si salvano mai.
 */
object SimStore {
    private const val PREFS = "sim"
    private const val KEY_STATUS = "status"
    private const val KEY_LINKED = "linked"
    private const val CAPTURES = "windtre-captures.json"
    private const val MAX_CAPTURES = 40
    /** Più spesso non serve: i contatori di WindTre stessi si aggiornano con qualche ora di ritardo. */
    const val STALE_MS = 60 * 60 * 1000L

    private var appContext: Context? = null
    private val _status = MutableStateFlow<SimStatus?>(null)
    val status: StateFlow<SimStatus?> = _status.asStateFlow()
    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** Quello letto nel giro in corso (accesso o aggiornamento), non ancora confermato. */
    @Volatile
    private var session: SimStatus? = null
    private val captures = ArrayDeque<JSONObject>()

    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _linked.value = prefs.getBoolean(KEY_LINKED, false)
        _status.value = prefs.getString(KEY_STATUS, null)?.let { runCatching { SimStatus.fromJson(JSONObject(it)) }.getOrNull() }
        runCatching {
            val arr = JSONArray(File(app.filesDir, CAPTURES).readText())
            for (i in 0 until arr.length()) captures.addLast(arr.getJSONObject(i))
        }
    }

    fun isStale(now: Long = System.currentTimeMillis()): Boolean = (status.value?.at ?: 0) < now - STALE_MS

    /** Una risposta JSON dell'area clienti (chiamato dalla pagina, su un thread qualsiasi). */
    fun capture(url: String, body: String) {
        if (WindTreParse.isPrivate(url)) return
        val parsed = WindTreParse.fromJson(body) ?: return
        synchronized(this) {
            session = parsed.orElse(session)
            captures.addLast(JSONObject().put("url", url.substringBefore('?')).put("at", System.currentTimeMillis()).put("body", body.take(200_000)))
            while (captures.size > MAX_CAPTURES) captures.removeFirst()
        }
        commit()
    }

    /** Il testo visibile della pagina: riempie solo quello che il JSON non ha detto. */
    fun pageText(text: String) {
        val parsed = WindTreParse.fromText(text) ?: return
        synchronized(this) { session = (session ?: SimStatus()).orElse(parsed) }
        commit()
    }

    fun beginSession() {
        session = null
    }

    /** Salva subito quello che si è capito: la card si aggiorna mentre la pagina carica ancora. */
    private fun commit() {
        val found = session?.takeIf { it.hasAny } ?: return
        val merged = found.orElse(_status.value).copy(at = System.currentTimeMillis())
        _status.value = merged
        if (!_linked.value) setLinked(true)
        val app = appContext ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STATUS, merged.toJson().toString()).apply()
        val snapshot = synchronized(this) { JSONArray(captures.toList()).toString() }
        runCatching { File(app.filesDir, CAPTURES).writeText(snapshot) }
    }

    fun setLinked(value: Boolean) {
        _linked.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_LINKED, value)?.apply()
    }

    fun setRefreshing(value: Boolean) {
        _refreshing.value = value
    }

    /** Scollega: via stato e risposte salvate (i cookie di WindTre li toglie [WindTreLink.forget]). */
    fun unlink() {
        setLinked(false)
        _status.value = null
        session = null
        synchronized(this) { captures.clear() }
        val app = appContext ?: return
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        File(app.filesDir, CAPTURES).delete()
    }

    /** Le risposte catturate, da mandare a chi sistema la lettura se qualcosa non torna. */
    fun capturesText(): String = synchronized(this) { JSONArray(captures.toList()).toString(1) }
}
