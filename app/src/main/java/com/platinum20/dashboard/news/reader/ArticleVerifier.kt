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

    suspend fun verify(urls: List<String>, cookies: (String) -> String? = { null }) = coroutineScope {
        val known = _state.value.let { it.readable + it.rejected }
        val todo = urls.distinct().filter { it !in known }
        if (todo.isEmpty()) return@coroutineScope
        _state.update { it.copy(pending = it.pending + todo.size) }
        val permits = Semaphore(PARALLEL)
        todo.map { url ->
            async(Dispatchers.IO) {
                val ok = permits.withPermit { check(url, cookies) }
                _state.update {
                    it.copy(
                        readable = if (ok) it.readable + url else it.readable,
                        rejected = if (ok) it.rejected else it.rejected + url,
                        pending = (it.pending - 1).coerceAtLeast(0),
                    )
                }
            }
        }.awaitAll()
    }

    private suspend fun check(url: String, cookies: (String) -> String?): Boolean = withContext(Dispatchers.IO) {
        ArticleCache.get(url)?.let { return@withContext true }
        val page = runCatching { ArticleExtractor.download(url, cookies) }.getOrNull() ?: return@withContext false
        if (ArticleExtractor.isPaid(page)) return@withContext false
        val article = runCatching { ArticleExtractor.extractBest(page) }.getOrNull() ?: return@withContext false
        if (article.textLength < MIN_TEXT_LENGTH) return@withContext false
        ArticleCache.put(url, article)
        true
    }
}
