package com.platinum20.dashboard.media

/** Una lista caricata dalle API, con stato di caricamento ed errore. */
data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val loadedAt: Long = 0,
)
