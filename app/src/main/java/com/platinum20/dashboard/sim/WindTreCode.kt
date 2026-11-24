package com.platinum20.dashboard.sim

import android.content.Context
import com.platinum20.dashboard.mail.GmailImap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Il codice di conferma che WindTre manda per mail a ogni accesso nuovo ("981707 è il tuo codice di
 * verifica.", da accesso-clienti@windtre.it, vale 5 minuti). Con Gmail collegato Canvas lo legge da sé
 * e la rilettura nascosta rientra senza di te. Solo lettura: la mail non si segna come letta.
 */
object WindTreCode {
    private const val QUERY = "from:accesso-clienti@windtre.it newer_than:1d"
    /** Di solito arriva in pochi secondi: oltre un minuto non si aspetta (batteria). */
    private const val WAIT_MS = 60_000L

    /** Il codice dall'oggetto (o dal testo) della mail; null se non c'è. */
    fun fromText(text: String): String? =
        Regex("""(\d{4,8})\s+è il tuo codice""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
            ?: Regex("""codice di verifica è\s*(\d{4,8})""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)

    /** Aspetta la mail arrivata dopo [since] e ne prende il codice; null senza Gmail o se non arriva. */
    suspend fun await(context: Context, since: Long): String? = withContext(Dispatchers.IO) {
        val end = System.currentTimeMillis() + WAIT_MS
        // Una sola connessione per tutta l'attesa, non una nuova a ogni controllo.
        val session = runCatching { GmailImap.session(context) }.getOrNull() ?: return@withContext null
        session.use { s ->
            delay(5_000)
            while (System.currentTimeMillis() < end) {
                val found = runCatching {
                    s.search(QUERY).sortedDescending().take(2).firstNotNullOfOrNull { uid ->
                        // Un minuto di margine: l'orologio della mail e quello del telefono non coincidono al secondo.
                        s.message(uid)?.takeIf { it.at >= since - 60_000 }?.let { fromText(it.subject) ?: fromText(it.body) }
                    }
                }.getOrElse { return@withContext null }
                if (found != null) return@withContext found
                delay(10_000)
            }
        }
        null
    }
}
