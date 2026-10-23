package com.platinum20.dashboard.news.reader

import com.platinum20.dashboard.net.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class VerificationState(
    /** Articoli che il lettore riesce a pulire: solo questi compaiono nelle News. */
    val readable: Set<String> = emptySet(),
    /** Articoli a pagamento o che il lettore non riesce a ricavare: nascosti. */
    val rejected: Set<String> = emptySet(),
    /** Quanti articoli sono ancora da controllare. */
    val pending: Int = 0,
)

/**
 * Controlla in anticipo gli articoli della lista: li scarica, verifica che non siano a pagamento
 * e che il lettore ne ricavi un testo pulito. Quelli buoni finiscono in [ArticleCache] e si aprono
 * subito; gli altri non vengono mostrati.
 */
object ArticleVerifier {

    /** Sotto questa lunghezza consideriamo il testo un semplice sommario, non un articolo. */
    private const val MIN_TEXT_LENGTH = 300
    private const val PARALLEL = 6
    private const val CACHE_FILE = "verifiche-articoli.json"
    /** I giudizi salvati valgono qualche giorno: poi gli articoli escono comunque dai feed. */
    private const val KEEP_MS = 3 * 24 * 3_600_000L
    private const val MAX_SAVED = 600

    /** Quando è stato dato ogni giudizio, per salvarli su file e scartare i vecchi. */
    private val judgedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val restoreLock = Mutex()
    private var restored = false

    private val _state = MutableStateFlow(VerificationState())
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    private enum class Outcome { READABLE, REJECTED, RETRY_LATER }

    /** Articoli in fase di controllo, per non controllarli due volte se si aggiorna la lista nel frattempo. */
    private val inFlight = mutableSetOf<String>()

    /**
     * Rimette i giudizi dell'ultima volta: gli articoli già controllati compaiono subito
     * nelle News, senza riscaricarli a ogni avvio dell'app.
     */
    suspend fun restore() = restoreLock.withLock {
        if (restored) return@withLock
        restored = true
        val saved = DiskCache.read(CACHE_FILE)?.let { runCatching { decode(it, System.currentTimeMillis()) }.getOrNull() } ?: return@withLock
        saved.forEach { (url, verdict) -> judgedAt.putIfAbsent(url, verdict.second) }
        _state.update { state ->
            state.copy(
                readable = state.readable + saved.filterValues { it.first }.keys,
                rejected = state.rejected + saved.filterValues { !it.first }.keys,
            )
        }
    }

    suspend fun verify(urls: List<String>, cookies: (String) -> String? = { null }) = coroutineScope {
        restore()
        val known = _state.value.let { it.readable + it.rejected }
        val todo = synchronized(inFlight) {
            urls.distinct().filter { it !in known && it !in inFlight }.also { inFlight += it }
        }
        if (todo.isEmpty()) return@coroutineScope
        _state.update { it.copy(pending = it.pending + todo.size) }
        val permits = Semaphore(PARALLEL)
        todo.map { url ->
            async(Dispatchers.IO) {
                val outcome = permits.withPermit { check(url, cookies) }
                synchronized(inFlight) { inFlight -= url }
                if (outcome != Outcome.RETRY_LATER) judgedAt[url] = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        readable = if (outcome == Outcome.READABLE) it.readable + url else it.readable,
                        rejected = if (outcome == Outcome.REJECTED) it.rejected + url else it.rejected,
                        pending = (it.pending - 1).coerceAtLeast(0),
                    )
                }
            }
        }.awaitAll()
        save()
    }

    private suspend fun save() {
        val state = _state.value
        val verdicts = judgedAt.entries
            .mapNotNull { (url, at) ->
                when (url) {
                    in state.readable -> url to (true to at)
                    in state.rejected -> url to (false to at)
                    else -> null
                }
            }
            .toMap()
        DiskCache.write(CACHE_FILE, encode(verdicts, System.currentTimeMillis()))
    }

    /** url -> (leggibile, quando). Tiene i più recenti, entro [KEEP_MS]. */
    internal fun encode(verdicts: Map<String, Pair<Boolean, Long>>, now: Long): String {
        val array = JSONArray()
        verdicts.entries
            .filter { now - it.value.second < KEEP_MS }
            .sortedByDescending { it.value.second }
            .take(MAX_SAVED)
            .forEach { (url, v) -> array.put(JSONObject().put("u", url).put("ok", v.first).put("t", v.second)) }
        return array.toString()
    }

    internal fun decode(text: String, now: Long): Map<String, Pair<Boolean, Long>> {
        val array = JSONArray(text)
        return (0 until array.length())
            .map { array.getJSONObject(it) }
            .filter { now - it.optLong("t") < KEEP_MS }
            .associate { it.getString("u") to (it.optBoolean("ok") to it.optLong("t")) }
    }

    private suspend fun check(url: String, cookies: (String) -> String?): Outcome = withContext(Dispatchers.IO) {
        if (ArticleCache.get(url) != null) return@withContext Outcome.READABLE
        // Un errore di rete non è un giudizio sull'articolo: riproviamo al prossimo aggiornamento.
        val page = PageLoader.load(url) ?: return@withContext Outcome.RETRY_LATER
        if (ArticleExtractor.isPaid(page)) return@withContext Outcome.REJECTED
        val article = runCatching { ArticleExtractor.extractBest(page) }.getOrNull()
        if (article == null || article.textLength < MIN_TEXT_LENGTH) return@withContext Outcome.REJECTED
        ArticleCache.put(url, article)
        Outcome.READABLE
    }
}
