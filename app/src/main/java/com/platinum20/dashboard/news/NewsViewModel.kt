package com.platinum20.dashboard.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val failedSources: List<NewsSource> = emptyList(),
    /** null = tutte le fonti. */
    val selectedSource: String? = null,
) {
    val visibleItems: List<NewsItem>
        get() = if (selectedSource == null) items else items.filter { it.source.id == selectedSource }
}

class NewsViewModel : ViewModel() {

    private val repository = NewsRepository()
    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun selectSource(id: String?) = _state.update { it.copy(selectedSource = id) }

    private fun load(initial: Boolean) {
        val s = _state.value
        if (s.loading || s.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
            runCatching { repository.fetchAll() }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            items = result.items,
                            failedSources = result.failedSources,
                            loading = false,
                            refreshing = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = e.message ?: "Errore di rete.")
                    }
                }
        }
    }
}
