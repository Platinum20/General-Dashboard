package com.platinum20.dashboard.news.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Loaded(val article: ReaderArticle) : ReaderUiState
    /** Pagina scaricata ma senza un testo leggibile (paywall, pagina solo video, ecc.). */
    data object NoText : ReaderUiState
    data class Failed(val message: String) : ReaderUiState
}

class ReaderViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    val url: String = savedStateHandle.get<String>("url").orEmpty()

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = ReaderUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { withContext(Dispatchers.IO) { ArticleExtractor.fetch(url) } }
                .fold(
                    onSuccess = { article ->
                        if (article.textLength < MIN_TEXT_LENGTH) ReaderUiState.NoText else ReaderUiState.Loaded(article)
                    },
                    onFailure = { ReaderUiState.Failed(it.message ?: "Errore di rete.") },
                )
        }
    }

    private companion object {
        const val MIN_TEXT_LENGTH = 200
    }
}
