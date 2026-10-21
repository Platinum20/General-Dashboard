package com.platinum20.dashboard.news.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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
    private const val PARALLEL = 4

    private val _state = MutableStateFlow(VerificationState())
    val state: StateFlow<VerificationState> = _state.asStateFlow()

    private enum class Outcome { READABLE, REJECTED, RETRY_LATER }

    /** Articoli in fase di controllo, per non controllarli due volte se si aggiorna la lista nel frattempo. */
    private val inFlight = mutableSetOf<String>()

    suspend fun verify(urls: List<String>, cookies: (String) -> String? = { null }) = coroutineScope {
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
                _state.update {
                    it.copy(
                        readable = if (outcome == Outcome.READABLE) it.readable + url else it.readable,
                        rejected = if (outcome == Outcome.REJECTED) it.rejected + url else it.rejected,
                        pending = (it.pending - 1).coerceAtLeast(0),
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun check(url: String, cookies: (String) -> String?): Outcome = withContext(Dispatchers.IO) {
        if (ArticleCache.get(url) != null) return@withContext Outcome.READABLE
        // Un errore di rete non è un giudizio sull'articolo: riproviamo al prossimo aggiornamento.
        val page = runCatching { ArticleExtractor.download(url, cookies) }.getOrNull()
            ?: return@withContext Outcome.RETRY_LATER
        if (ArticleExtractor.isPaid(page)) return@withContext Outcome.REJECTED
        val article = runCatching { ArticleExtractor.extractBest(page) }.getOrNull()
        if (article == null || article.textLength < MIN_TEXT_LENGTH) return@withContext Outcome.REJECTED
        ArticleCache.put(url, article)
        Outcome.READABLE
    }
}
