package com.platinum20.dashboard.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum20.dashboard.net.userMessage
import com.platinum20.dashboard.news.reader.ArticleVerifier
import com.platinum20.dashboard.news.reader.VerificationState
import com.platinum20.dashboard.news.reader.WebPageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val items: List<NewsItem> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val failures: List<SourceFailure> = emptyList(),
    /** null = tutte le fonti. */
    val selectedSource: String? = null,
    val verification: VerificationState = VerificationState(),
) {
    /** Solo gli articoli che il lettore riesce a pulire, e gratuiti (vedi [ArticleVerifier]). */
    val visibleItems: List<NewsItem>
        get() = items.filter {
            it.link in verification.readable && (selectedSource == null || it.source.id == selectedSource)
        }
}

class NewsViewModel : ViewModel() {

    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    init {
        load(initial = true)
        viewModelScope.launch {
            ArticleVerifier.state.collect { v -> _state.update { it.copy(verification = v) } }
        }
    }

    fun refresh() = load(initial = false)

    fun selectSource(id: String?) = _state.update { it.copy(selectedSource = id) }

    private fun load(initial: Boolean) {
        val s = _state.value
        if (s.loading || s.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching { NewsStore.load(force = !initial) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            items = result.items,
                            failures = result.failures,
                            loading = false,
                            refreshing = false,
                        )
                    }
                    // Controlla in anticipo gli articoli più recenti: in lista finiscono solo quelli leggibili.
                    launch {
                        ArticleVerifier.verify(result.items.take(MAX_VERIFIED).map { it.link }, WebPageRenderer::cookiesFor)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = e.userMessage("Errore di rete."))
                    }
                }
        }
    }

    private companion object {
        /** Quanti articoli recenti controllare: oltre, la lista diventerebbe lenta da preparare. */
        const val MAX_VERIFIED = 60
    }
}
