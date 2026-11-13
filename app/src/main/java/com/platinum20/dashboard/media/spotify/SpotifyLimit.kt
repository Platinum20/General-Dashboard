package com.platinum20.dashboard.media.spotify

/**
 * Il "troppe richieste" di Spotify (429). Prima l'app continuava a chiedere (Release Radar, Top 50,
 * discografie, lo stato del player ogni pochi secondi) e Spotify allungava l'attesa: tutto sembrava
 * fermo anche dopo un nuovo accesso. Ora si rispetta l'attesa che chiede (Retry-After) e intanto non
 * si va in rete: si dice subito quanto manca.
 */
class SpotifyLimit(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile
    private var until = 0L

    /** Spotify ha risposto 429 con questa intestazione Retry-After (secondi). */
    fun hit(retryAfter: String?) {
        val wait = retryAfterMs(retryAfter)
        until = maxOf(until, clock() + wait)
    }

    /** Millisecondi ancora da aspettare prima di poter chiedere di nuovo (0 = via libera). */
    fun left(): Long = (until - clock()).coerceAtLeast(0)

    companion object {
        /** Senza intestazione (o illeggibile) mezzo minuto; mai più di un giorno. */
        fun retryAfterMs(header: String?): Long {
            val seconds = header?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: return 30_000
            return seconds.coerceAtMost(24 * 3600) * 1000
        }

        /** Un'attesa breve si fa e basta; oltre questa si risponde subito con quanto manca. */
        const val SHORT_WAIT_MS = 4_000L

        /** "riprova tra 20 secondi" / "tra 3 minuti" / "tra 2 ore", come lo direbbe una persona. */
        fun message(leftMs: Long): String {
            val s = (leftMs + 999) / 1000
            val wait = when {
                s < 60 -> "$s secondi"
                s < 3600 -> ((s + 59) / 60).let { if (it == 1L) "un minuto" else "$it minuti" }
                else -> ((s + 3599) / 3600).let { if (it == 1L) "un'ora" else "$it ore" }
            }
            return "Spotify chiede di aspettare: troppe richieste, riprova tra $wait"
        }
    }
}
