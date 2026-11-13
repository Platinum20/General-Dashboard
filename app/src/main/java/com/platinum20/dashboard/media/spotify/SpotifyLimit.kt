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

    /** Dove ricordare l'attesa: riaprendo l'app non si torna a bussare (ogni 429 in più la allunga). */
    var onChange: ((Long) -> Unit)? = null

    /** L'attesa salvata l'ultima volta (già passata = niente). */
    fun restore(savedUntil: Long) {
        until = maxOf(until, savedUntil)
    }

    /** Spotify ha risposto 429 con questa intestazione Retry-After (secondi). */
    fun hit(retryAfter: String?) {
        val wait = retryAfterMs(retryAfter)
        until = maxOf(until, clock() + wait)
        onChange?.invoke(until)
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

/**
 * Quante chiamate si fanno in [windowMs]: Spotify conta le richieste dell'app in una finestra di 30
 * secondi e, se si esagera, blocca tutto per ore (anche il play). Le chiamate di contorno (liste,
 * Release Radar, foto degli artisti) hanno un tetto più basso, così il play trova sempre posto.
 */
class CallBudget(
    private val windowMs: Long = 30_000,
    private val max: Int = 40,
    private val backgroundMax: Int = 20,
) {
    private val all = ArrayDeque<Long>()
    private val background = ArrayDeque<Long>()

    /** 0 = via libera (la chiamata è contata); altrimenti quanti millisecondi aspettare prima di riprovare. */
    @Synchronized
    fun reserve(now: Long, isBackground: Boolean): Long {
        while (all.isNotEmpty() && now - all.first() >= windowMs) all.removeFirst()
        while (background.isNotEmpty() && now - background.first() >= windowMs) background.removeFirst()
        if (all.size >= max) return windowMs - (now - all.first())
        if (isBackground && background.size >= backgroundMax) return windowMs - (now - background.first())
        all.addLast(now)
        if (isBackground) background.addLast(now)
        return 0
    }

    companion object {
        /** I comandi del riproduttore (play, pausa, stato) passano davanti a tutto il resto. */
        fun isBackground(path: String): Boolean = !path.startsWith("me/player")
    }
}
