package com.platinum20.dashboard.media.spotify

/**
 * Cosa fare quando Spotify rifiuta l'accesso (401 o "authentication_error" del riproduttore).
 * Quasi sempre è solo il token di un'ora scaduto, o letto senza rete al risveglio del telefono:
 * si rinnova e si riprova da soli, più volte e senza dire niente. "Ricollega" si chiede solo quando
 * Spotify ha davvero buttato il collegamento ([connected] falso). Dalla 1.561 (Antonio: "mi esce
 * spesso") il secondo intoppo entro 10 minuti non mostra più "riprova tra poco": si riprova ancora
 * con calma, senza rete si aspetta che torni, e il messaggio esce solo se stai ascoltando e Spotify
 * rifiuta anche dopo tutti i tentativi.
 */
object SpotifyRelink {
    /** I tentativi silenziosi contano in questa finestra: se Spotify continua a rifiutare non si gira in tondo. */
    const val WINDOW_MS = 10 * 60_000L
    /** Attesa prima di ogni tentativo: subito, poi 5 secondi, poi 30 (la rete al risveglio ci mette un po'). */
    val BACKOFF_MS = listOf(0L, 5_000L, 30_000L)

    const val RELINK = "Spotify si è scollegato: ricollegalo da Impostazioni › Account"
    const val LATER = "Spotify non ha accettato l'accesso, riprova tra poco"
    const val OFFLINE = "Senza rete: Spotify riparte quando torna"

    enum class Step {
        /** Si rinnova il token e si riaccende la pagina, in silenzio. */
        RETRY,
        /** Collegamento caduto davvero: si chiede di ricollegare. */
        ASK,
        /** Senza rete: si aspetta che torni, poi si riprova. */
        WAIT_NETWORK,
        /** Tentativi finiti ma non stai ascoltando: niente messaggio, il prossimo play riparte da capo. */
        QUIET,
        /** Tentativi finiti mentre ascolti: si dice. */
        LATER,
    }

    /** Solo i tentativi ancora dentro la finestra. */
    fun recent(retries: List<Long>, now: Long): List<Long> = retries.filter { now - it in 0 until WINDOW_MS }

    fun step(connected: Boolean, online: Boolean, recent: List<Long>, playing: Boolean): Step = when {
        !connected -> Step.ASK
        !online -> Step.WAIT_NETWORK
        recent.size < BACKOFF_MS.size -> Step.RETRY
        playing -> Step.LATER
        else -> Step.QUIET
    }

    /** Quanto aspettare prima del tentativo numero [done] (contando da 0). */
    fun delayFor(done: Int): Long = BACKOFF_MS.getOrElse(done) { BACKOFF_MS.last() }

    /** Il testo per un 401 che resta dopo il rinnovo. */
    fun message(connected: Boolean): String = if (connected) LATER else RELINK
}
