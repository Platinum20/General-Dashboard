package com.platinum20.dashboard.news

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * Cosa hai già visto nelle News: l'ora dell'ultima visita (per separare le notizie nuove)
 * e gli articoli aperti (per attenuarli). La sezione serve a rispondere a "cosa è successo
 * da quando ho guardato l'ultima volta?".
 */
object NewsHistory {
    private const val PREFS = "news_history"
    private const val KEY_LAST_VISIT = "last_visit"
    private const val KEY_READ = "read"
    private const val MAX_READ = 300

    private lateinit var prefs: SharedPreferences
    private val _read = MutableStateFlow<List<String>>(emptyList())
    val read: StateFlow<List<String>> = _read.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _read.value = prefs.getString(KEY_READ, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    /** L'ultima volta che hai lasciato le News; null alla prima apertura. */
    fun lastVisit(): Instant? = prefs.getLong(KEY_LAST_VISIT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun markVisit(at: Instant = Instant.now()) {
        prefs.edit().putLong(KEY_LAST_VISIT, at.toEpochMilli()).apply()
    }

    fun markRead(link: String) {
        if (link in _read.value) return
        // I più recenti in coda; i più vecchi escono quando la lista è piena.
        val list = (_read.value + link).takeLast(MAX_READ)
        _read.value = list
        prefs.edit().putString(KEY_READ, list.joinToString("\n")).apply()
    }
}
