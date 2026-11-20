package com.platinum20.dashboard.media.spotify

/**
 * Cosa fare quando Spotify rifiuta l'accesso (401 o "authentication_error" del riproduttore).
 * Quasi sempre è solo il token di un'ora scaduto o letto senza rete: si rinnova e si riprova da soli.
 * "Ricollega" si chiede solo quando Spotify ha davvero buttato il collegamento (refresh token rifiutato
 * = [connected] falso): prima lo si chiedeva a ogni intoppo, con la chiave inserita e valida.
 */
object SpotifyRelink {
    /** Un tentativo silenzioso ogni tanto: se Spotify continua a rifiutare non si gira in tondo. */
    const val QUIET_MS = 10 * 60_000L

    const val RELINK = "Spotify si è scollegato: ricollegalo da Impostazioni › Account"
    const val LATER = "Spotify non ha accettato l'accesso, riprova tra poco"

    enum class Step { RETRY, ASK, LATER }

    fun step(connected: Boolean, lastRetryAt: Long, now: Long): Step = when {
        !connected -> Step.ASK
        now - lastRetryAt >= QUIET_MS -> Step.RETRY
        else -> Step.LATER
    }

    /** Il testo per un 401 che resta dopo il rinnovo. */
    fun message(connected: Boolean): String = if (connected) LATER else RELINK
}
