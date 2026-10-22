package com.platinum20.dashboard.news.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    /** [step] descrive cosa sta facendo il lettore, per non lasciare l'utente davanti a una rotella muta. */
    data class Loading(val step: String) : ReaderUiState
    data class Loaded(val article: ReaderArticle) : ReaderUiState
    /** Pagina raggiunta ma senza un testo leggibile (articolo per abbonati, pagina solo video, ecc.). */
    data object NoText : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

class ReaderViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    val url: String = savedStateHandle.get<String>("url").orEmpty()

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading("Scarico l'articolo…"))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Prova più strade, dalla più veloce alla più lenta, e si ferma appena ha un testo completo:
     * 1. HTML scaricato direttamente (Readability + dati strutturati);
     * 2. versione AMP dell'articolo;
     * 3. pagina caricata in un WebView invisibile, per i siti che compongono il testo in JavaScript.
     * Se nessuna strada dà un testo completo, mostra il risultato migliore trovato.
     */
    private var job: Job? = null

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            // Già controllato e pulito dalla lista notizie: si apre subito.
            ArticleCache.get(url)?.let {
                _state.value = ReaderUiState.Loaded(it)
                return@launch
            }
            _state.value = ReaderUiState.Loading("Scarico l'articolo…")
            val results = mutableListOf<ReaderArticle>()
            var error: Throwable? = null

            val page = runCatching { PageLoader.load(url) ?: throw java.io.IOException("pagina non raggiungibile") }
                .onFailure { error = it }
                .getOrNull()
            page?.let { p -> extract(p)?.let { results += it } }

            if (results.best()?.isComplete() != true && page != null) {
                ArticleExtractor.ampUrl(page)?.let { amp ->
                    _state.value = ReaderUiState.Loading("Provo la versione semplificata…")
                    runCatching { withContext(Dispatchers.IO) { ArticleExtractor.download(amp, WebPageRenderer::cookiesFor) } }
                        .getOrNull()
                        // Il lettore mostra sempre l'indirizzo dell'articolo, non quello AMP.
                        ?.let { extract(it)?.copy(url = page.url) }
                        ?.let { results += it }
                }
            }

            if (results.best()?.isComplete() != true) {
                _state.value = ReaderUiState.Loading("Carico la pagina come un browser…")
                runCatching { WebPageRenderer.render(getApplication<Application>(), url) }
                    .getOrNull()
                    ?.let { extract(it) }
                    ?.let { results += it }
            }

            val best = results.best()
            best?.takeIf { it.textLength >= MIN_TEXT_LENGTH }?.let { ArticleCache.put(url, it) }
            _state.value = when {
                best != null && best.textLength >= MIN_TEXT_LENGTH -> ReaderUiState.Loaded(best)
                page != null || best != null -> ReaderUiState.NoText
                else -> ReaderUiState.Failed(failureMessage(error))
            }
        }
    }

    /** Messaggio per chi legge: niente codici HTTP né testi tecnici in inglese. */
    private fun failureMessage(e: Throwable?): String {
        val fallback = "Impossibile scaricare l'articolo."
        val text = e?.message.orEmpty()
        return when {
            e == null -> fallback
            // 401/403/429…: il sito blocca le app, ma nel browser di solito si apre.
            text.startsWith("HTTP 4") -> "Il sito non lascia leggere l'articolo nell'app: aprilo nell'originale."
            text.startsWith("HTTP ") -> "Il sito non risponde, riprova più tardi."
            // I problemi di rete hanno già un testo chiaro; gli altri messaggi grezzi non aiutano.
            else -> e.userMessage(fallback).takeIf { it != text } ?: fallback
        }
    }

    private suspend fun extract(page: FetchedPage): ReaderArticle? =
        withContext(Dispatchers.Default) { runCatching { ArticleExtractor.extractBest(page) }.getOrNull() }

    private fun List<ReaderArticle>.best() = maxByOrNull { it.textLength }

    private fun ReaderArticle.isComplete() = textLength >= ArticleExtractor.GOOD_TEXT_LENGTH

    private companion object {
        const val MIN_TEXT_LENGTH = 200
    }
}
