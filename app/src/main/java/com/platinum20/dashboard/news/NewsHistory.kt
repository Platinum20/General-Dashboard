package com.platinum20.dashboard.news

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
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
    private val _lastVisit = MutableStateFlow<Instant?>(null)
    /** L'ultima volta che hai lasciato le News, osservabile: la card in Home si aggiorna appena esci. */
    val lastVisitFlow: StateFlow<Instant?> = _lastVisit.asStateFlow()

    /** La visita in corso: l'ora da cui le storie contano come nuove, e se tornando dal lettore la si riprende. */
    private var session: Instant? = null
    private var hasSession = false
    @Volatile
    private var held = false
    private val _settled = MutableStateFlow<Set<String>>(emptySet())
    /**
     * Gli articoli che in questa visita contano come visti: quelli aperti prima di entrare e quelli segnati
     * come letti qui. Gli articoli aperti durante la visita no: la loro riga resta al suo posto, attenuata.
     */
    val settled: StateFlow<Set<String>> = _settled.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _read.value = prefs.getString(KEY_READ, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
        _lastVisit.value = lastVisit()
    }

    /**
     * Si entra nelle News: da quando contano le storie nuove. Aprire un articolo lascia la pagina (e segna
     * la visita), ma tornando dal lettore la visita è la stessa: le nuove restano nuove ([holdSession]).
     */
    fun enter(): Instant? {
        val since = sessionStart(held, hasSession, session, lastVisit())
        if (!(held && hasSession)) _settled.value = _read.value.toSet()
        held = false
        session = since
        hasSession = true
        return since
    }

    /** Segnati come letti qui: la loro storia scende tra le già viste. */
    fun settle(links: Collection<String>) {
        _settled.value = _settled.value + links
    }

    /** Si apre un articolo dalla lista: al ritorno la visita continua. */
    fun holdSession() {
        held = true
    }

    /** Da quando contano le nuove adesso: la visita in corso se c'è, altrimenti l'ultima. */
    fun since(): Instant? = if (hasSession) session else lastVisit()

    internal fun sessionStart(held: Boolean, hasSession: Boolean, current: Instant?, last: Instant?): Instant? =
        if (held && hasSession) current else last

    /** L'ultima volta che hai lasciato le News; null alla prima apertura. */
    fun lastVisit(): Instant? = prefs.getLong(KEY_LAST_VISIT, 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun markVisit(at: Instant = Instant.now()) {
        prefs.edit().putLong(KEY_LAST_VISIT, at.toEpochMilli()).apply()
        _lastVisit.value = at
    }

    fun markRead(link: String) {
        if (link in _read.value) return
        // I più recenti in coda; i più vecchi escono quando la lista è piena.
        val list = (_read.value + link).takeLast(MAX_READ)
        _read.value = list
        prefs.edit().putString(KEY_READ, list.joinToString("\n")).apply()
    }

    /**
     * Riga sopra il titolo delle News. Con 0 nuove dall'ultima visita non si dice "niente di nuovo"
     * sopra notizie fresche: si dice quanto è recente l'ultima ("Aggiornate 2 min fa").
     */
    fun headline(total: Int, newCount: Int?, newest: Instant?, now: Instant = Instant.now()): String = when {
        total == 0 -> "Notizie libere"
        newCount == null || newCount >= total -> "$total notizie"
        newCount == 1 -> "1 nuova dall'ultima visita"
        newCount > 1 -> "$newCount nuove dall'ultima visita"
        newest == null -> "$total notizie"
        else -> {
            val minutes = Duration.between(newest, now).toMinutes().coerceAtLeast(0)
            when {
                minutes < 1 -> "Aggiornate adesso"
                minutes < 60 -> "Aggiornate $minutes min fa"
                minutes < 24 * 60 -> "Aggiornate ${minutes / 60} h fa"
                else -> "Niente di nuovo dall'ultima visita"
            }
        }
    }
}
