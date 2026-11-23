package com.platinum20.dashboard.mail

import com.platinum20.dashboard.notes.Carrier

/**
 * "Salva in nota" da una mail (1.606, idea 12): della mail restano solo le cose utili (codici, orari e date,
 * indirizzi, numero di spedizione), una per riga. Pure, provate nei test.
 */
object MailFacts {
    /** Al massimo tante righe: è un promemoria della mail, non la sua copia. */
    const val MAX_LINES = 6

    private val codeRx = Regex(
        """(?i)\b(codice(?: di)?(?: prenotazione| conferma| ordine| biglietto| pnr)?|prenotazione(?: n\.?| numero)?|conferma(?: n\.?)?|numero d['i] ?ordine|ordine(?: n\.?| numero)?|pnr|booking(?: code| ref(?:erence)?)?|pin|otp)\s*[:#nN°.\-]*\s*([A-Z0-9][A-Z0-9\-]{3,15})\b""",
    )
    private val timeRx = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
    private val dateRx = Regex(
        """(?i)\b(\d{1,2}[/.]\d{1,2}(?:[/.]\d{2,4})?|\d{1,2}\s+(?:gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)[a-z]*(?:\s+\d{4})?)\b""",
    )
    private val whenWordRx = Regex("""(?i)\b(check[- ]?in|check[- ]?out|partenza|arrivo|ritiro|consegna|appuntamento|orario|inizio|data|il giorno|alle ore|apertura)\b""")
    private val addressRx = Regex(
        """(?i)\b((?:via|viale|piazza|piazzale|corso|largo|vicolo|strada|lungomare|lungotevere|contrada)\s+[A-ZÀ-Ýa-zà-ý'.\s]{2,40}?,?\s*\d{1,4}[a-zA-Z]?(?:\s*,\s*\d{5})?(?:\s*,?\s*[A-ZÀ-Ý][a-zà-ý]+(?:\s[A-ZÀ-Ý][a-zà-ý]+)?)?)""",
    )

    /** Le righe utili della mail, senza doppioni e in ordine: codici, quando, dove, spedizione. */
    fun facts(subject: String, body: String): List<String> {
        val text = "$subject\n$body"
        val out = linkedSetOf<String>()
        codeRx.findAll(text).forEach { m ->
            val code = m.groupValues[2]
            // Un codice vero ha almeno una cifra (non "CONFERMATA" o "GRAZIE").
            if (code.any { it.isDigit() } && code.length >= 4) out += "Codice: $code"
        }
        Carrier.findCode(text)?.let { out += "Spedizione: $it" }
        text.lineSequence().map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 6..120 && (timeRx.containsMatchIn(it) || dateRx.containsMatchIn(it)) && whenWordRx.containsMatchIn(it) }
            .take(2)
            .forEach { out += it.trimEnd('.', ';') }
        addressRx.find(body)?.value?.replace(Regex("\\s+"), " ")?.trim()?.trimEnd(',', '.')?.let { out += "Indirizzo: $it" }
        return out.take(MAX_LINES)
    }

    /** Le righe da mettere nella nota: quelle utili, o l'inizio del testo se non ce ne sono, con il mittente. */
    fun noteLines(m: MailMessage): List<String> {
        val found = facts(m.subject, m.body.ifEmpty { m.snippet })
        val lines = found.ifEmpty {
            listOfNotNull(m.snippet.replace(Regex("\\s+"), " ").trim().take(160).ifBlank { null })
        }
        return lines + "✉ Da ${m.from.ifBlank { m.fromAddress }}"
    }

    /** Il titolo della nota nuova: l'oggetto della mail. */
    fun noteTitle(m: MailMessage): String = m.subject.replace('\n', ' ').trim().ifBlank { "Dalla posta" }
}
