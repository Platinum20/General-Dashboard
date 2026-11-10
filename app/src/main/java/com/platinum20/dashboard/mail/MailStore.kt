package com.platinum20.dashboard.mail

import android.content.Context
import android.content.SharedPreferences
import com.platinum20.dashboard.autofill.AutoFill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GmailAuthException : IOException("Gmail va ricollegato")

/**
 * Le mail di Gmail lette sul telefono: quelle nuove passano dall'estrattore di "Compila da solo"
 * (pacchi, scadenze, prenotazioni, rinnovi) e le importanti non lette finiscono nella card "Dalla posta".
 * Nessun server in mezzo: si parla direttamente con Gmail.
 */
object MailStore {
    private const val BASE = "https://gmail.googleapis.com/gmail/v1/users/me/"
    private const val FILE = "mail.json"
    private const val PREFS = "gmail_sync"
    private const val KEY_LAST = "last_sync"
    private const val KEY_SEEN = "seen"
    private const val KEY_OPENED = "opened"
    /** Al primo collegamento si guardano le ultime due settimane. */
    private const val FIRST_DAYS = 14L
    private const val MAX_SCAN = 60
    private const val MAX_SEEN = 400

    private lateinit var file: File
    private lateinit var prefs: SharedPreferences
    private val lock = Mutex()

    private val _important = MutableStateFlow<List<MailMessage>>(emptyList())
    /** Le mail importanti non lette degli ultimi giorni, le più recenti prima. */
    val important: StateFlow<List<MailMessage>> = _important.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    fun init(context: Context) {
        if (::file.isInitialized) return
        val app = context.applicationContext
        GmailAuth.init(app)
        GmailImap.init(app)
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _lastSync.value = prefs.getLong(KEY_LAST, 0L)
        file = File(app.filesDir, FILE)
        _important.value = runCatching { parse(file.readText()) }.getOrDefault(emptyList())
    }

    fun find(id: String): MailMessage? = _important.value.firstOrNull { it.id == id }

    /** Gmail collegato in uno dei due modi: password per app (IMAP) o accesso Google. */
    fun connected(): Boolean = GmailImap.linked.value || GmailAuth.connected.value

    /**
     * Legge le mail nuove. Restituisce quante cose ha trovato per Note, o lancia l'errore da mostrare.
     * Con la password per app si passa da IMAP; altrimenti dall'API con l'accesso Google, e se Google
     * vuole di nuovo il consenso non lancia: [GmailAuth.needsConsent] lo dice alla UI.
     */
    suspend fun sync(context: Context): Int {
        init(context)
        // Password rifiutata: niente nuovi tentativi finché non la si inserisce di nuovo (Google bloccherebbe l'accesso).
        if (GmailImap.linked.value && GmailImap.rejected.value) return 0
        if (GmailImap.linked.value) return lock.withLock {
            val session = GmailImap.session(context) ?: return@withLock 0
            try {
                sync(context, ImapSource(session))
            } finally {
                withContext(Dispatchers.IO) { session.close() }
            }
        }
        if (!GmailAuth.connected.value) return 0
        return lock.withLock {
            val token = GmailAuth.token(context) ?: return@withLock 0
            try {
                sync(context, ApiSource(token))
            } catch (_: GmailAuthException) {
                GmailAuth.token(context, forceRefresh = true)?.let { sync(context, ApiSource(it)) } ?: 0
            }
        }
    }

    /** Da dove arrivano le mail: stessa ricerca di Gmail, stessi id, due strade. */
    private interface Source {
        /** Id delle mail trovate, le più recenti prima. */
        suspend fun search(query: String, max: Int): List<String>
        suspend fun message(id: String): MailMessage?
    }

    private class ApiSource(private val token: String) : Source {
        override suspend fun search(query: String, max: Int) =
            MailParse.ids(MailStore.get(token, "messages", mapOf("q" to query, "maxResults" to "$max")))

        override suspend fun message(id: String) = MailParse.message(MailStore.get(token, "messages/$id", mapOf("format" to "full")))
    }

    private class ImapSource(private val session: GmailImap.Session) : Source {
        private val uids = mutableMapOf<String, Long>()

        override suspend fun search(query: String, max: Int): List<String> = withContext(Dispatchers.IO) {
            val found = session.search(query).sortedDescending().take(max)
            session.ids(found).entries.sortedByDescending { it.key }.map { (uid, id) ->
                uids[id] = uid
                id
            }
        }

        override suspend fun message(id: String): MailMessage? {
            val uid = uids[id] ?: return null
            return withContext(Dispatchers.IO) { session.message(uid) }
        }
    }

    private suspend fun sync(context: Context, source: Source): Int {
        AutoFill.init(context)
        val now = System.currentTimeMillis()
        val first = _lastSync.value == 0L
        // Un'ora di margine: una mail arrivata mentre leggevamo non si perde.
        val since = if (first) now - FIRST_DAYS * 24 * 3600_000L else _lastSync.value - 3600_000L
        val seen = prefs.getString(KEY_SEEN, "").orEmpty().split(',').filter { it.isNotEmpty() }.toMutableList()
        var found = 0
        val ids = source.search(MailParse.scanQuery(since), MAX_SCAN)
        // Dalla più vecchia alla più nuova: "consegnato" arriva dopo "spedito".
        for (id in ids.reversed()) {
            if (id in seen) continue
            val m = source.message(id) ?: continue
            seen += id
            if (!MailParse.worthScanning(m)) continue
            // La prima volta niente notifiche a raffica: si salva tutto in silenzio.
            found += AutoFill.offer(MailParse.incoming(m), notify = !first).size
        }
        prefs.edit().putString(KEY_SEEN, seen.takeLast(MAX_SEEN).joinToString(",")).putLong(KEY_LAST, now).apply()
        _lastSync.value = now

        val known = _important.value.associateBy { it.id }
        val opened = prefs.getStringSet(KEY_OPENED, emptySet()).orEmpty()
        val importantIds = source.search(MailParse.IMPORTANT_QUERY, 8).filter { it !in opened }.take(5)
        val important = importantIds.mapNotNull { id -> known[id] ?: source.message(id) }.sortedByDescending { it.at }
        setImportant(important)
        return found
    }

    /** Aperta in Canvas: esce dalla card, anche prima che Gmail la segni come letta. */
    fun markSeen(id: String) {
        if (::prefs.isInitialized) {
            // Gli id di Gmail crescono nel tempo: bastano gli ultimi.
            val opened = (prefs.getStringSet(KEY_OPENED, emptySet()).orEmpty() + id).sortedDescending().take(50).toSet()
            prefs.edit().putStringSet(KEY_OPENED, opened).apply()
        }
        setImportant(_important.value.filterNot { it.id == id })
    }

    fun forget() {
        if (!::file.isInitialized) return
        prefs.edit().clear().apply()
        _lastSync.value = 0L
        setImportant(emptyList())
    }

    private fun setImportant(list: List<MailMessage>) {
        if (list == _important.value) return
        _important.value = list
        if (::file.isInitialized) runCatching {
            val tmp = File(file.parentFile, "$FILE.tmp")
            tmp.writeText(serialize(list))
            tmp.renameTo(file)
        }
    }

    private suspend fun get(token: String, path: String, params: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val query = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            val conn = (URL("$BASE$path?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    401 -> throw GmailAuthException()
                    else -> {
                        val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        throw IOException(
                            when {
                                "accessNotConfigured" in body || "SERVICE_DISABLED" in body -> "Gmail API non abilitata nel progetto Google Cloud"
                                code == 403 -> "Google non concede ancora la lettura delle mail: controlla l'ambito nella schermata di consenso"
                                else -> "Gmail non risponde ($code), riprova più tardi"
                            }
                        )
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

    // --- File ---

    fun serialize(list: List<MailMessage>): String = JSONArray().apply {
        list.forEach { m ->
            put(
                JSONObject().put("id", m.id).put("from", m.from).put("fromAddress", m.fromAddress).put("subject", m.subject)
                    .put("snippet", m.snippet).put("at", m.at).put("labels", JSONArray(m.labels.toList())).put("body", m.body)
            )
        }
    }.toString()

    fun parse(raw: String): List<MailMessage> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val labels = o.optJSONArray("labels")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
                MailMessage(
                    o.getString("id"), o.optString("from"), o.optString("fromAddress"), o.optString("subject"),
                    o.optString("snippet"), o.optLong("at"), labels, o.optString("body"),
                )
            }.getOrNull()
        }
    }
}
