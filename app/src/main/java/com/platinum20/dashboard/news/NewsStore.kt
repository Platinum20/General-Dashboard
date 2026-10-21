package com.platinum20.dashboard.news

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Notizie condivise tra la sezione News e il recap della Dashboard, per non scaricarle due volte. */
object NewsStore {
    private const val MAX_AGE_MS = 10 * 60_000L

    private val repository = NewsRepository()
    private val mutex = Mutex()
    private val _result = MutableStateFlow<NewsResult?>(null)
    val result: StateFlow<NewsResult?> = _result.asStateFlow()
    private var fetchedAt = 0L

    suspend fun load(force: Boolean = false): NewsResult {
        mutex.withLock {
            val cached = _result.value
            if (!force && cached != null && System.currentTimeMillis() - fetchedAt < MAX_AGE_MS) return cached
            val fresh = repository.fetchAll()
            _result.value = fresh
            fetchedAt = System.currentTimeMillis()
            return fresh
        }
    }
}
