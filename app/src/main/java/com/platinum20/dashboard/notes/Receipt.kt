package com.platinum20.dashboard.notes

import java.time.LocalDate

/** Una riga letta dalla foto, con la sua posizione verticale (pixel della foto). */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int)

/** Quello che si capisce da uno scontrino: totale, negozio e data (se ci sono). */
data class ReceiptGuess(val cents: Long, val store: String?, val date: LocalDate?) {
    /** Il testo per il campo delle spese: "63,40 Conad", che diventa una spesa con la sua categoria. */
    val captureText: String
        get() = "${cents / 100},${(cents % 100).toString().padStart(2, '0')} ${store ?: "scontrino"}"
}

/**
 * Lettura degli scontrini italiani dal testo riconosciuto sul telefono. Tutto puro: la foto e il
 * riconoscimento stanno fuori, qui arrivano solo le righe.
 */
object Receipt {
    private val amount = Regex("""(?<![\d,.])-?(\d{1,3}(?:\.\d{3})+|\d{1,5})[,.](\d{2})(?![\d])(?![.,]\d)""")
    private val date = Regex("""(?<!\d)(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4}|\d{2})(?!\d)""")

    /** Le parole del totale, dalla più sicura. */
    private val totalKeys = listOf(
        Regex("""TOTALE\s+COMPLESSIVO"""),
        Regex("""IMPORTO\s+PAGATO"""),
        Regex("""(?<!SUB)(?<!SUB )TOTALE(?!\s*(IVA|SCONT|PEZZI|ARTICOLI|RISPARMI))|\bTOT\.?\s*EURO\b|\bTOTAL\b"""),
        Regex("""PAGAMENTO\s+(ELETTRONICO|CARTA|BANCOMAT)|\bDA\s+PAGARE\b"""),
    )

    /** Righe i cui importi non sono mai il totale: contanti dati, resto, iva, sconti, subtotali. */
    private val notTotal = Regex("""CONTANT|RESTO|\bIVA\b|SCONT|SUBTOT|RISPARMI|NON RISCOSSO|BUONO|PUNTI""")

    /** Righe che non sono il nome del negozio. */
    private val notStore = Regex(
        """DOCUMENTO|COMMERCIALE|SCONTRINO|FISCALE|P\.?\s?IVA|PARTITA|C\.?F\.?\b|\bVIA\b|VIALE|PIAZZA|CORSO|\bTEL|WWW|HTTP|@|BENVENUT|GRAZIE|CASSA|OPERATORE|\bEURO\b|DESCRIZIONE""",
    )
    private val legalSuffix = Regex("""[\s,]+(S\.?\s?P\.?\s?A\.?|S\.?\s?R\.?\s?L\.?\s?S?\.?|S\.?\s?N\.?\s?C\.?|S\.?\s?A\.?\s?S\.?|SOC\.?\s?COOP\.?.*)$""")

    /**
     * Mette insieme le righe che stanno alla stessa altezza (il riconoscimento separa spesso la
     * colonna delle descrizioni da quella dei prezzi), da sinistra a destra, dall'alto in basso.
     */
    fun rows(lines: List<OcrLine>): List<String> {
        val out = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.top }.forEach { line ->
            val center = (line.top + line.bottom) / 2
            val row = out.lastOrNull()?.takeIf { r -> r.any { center in it.top..it.bottom } }
            if (row != null) row += line else out += mutableListOf(line)
        }
        return out.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    fun parse(rows: List<String>): ReceiptGuess? {
        val upper = rows.map { it.uppercase() }
        val cents = total(upper) ?: return null
        return ReceiptGuess(cents, store(rows), date(upper))
    }

    private fun amounts(line: String): List<Long> =
        amount.findAll(line).mapNotNull { m ->
            if (m.value.startsWith("-")) return@mapNotNull null
            "${m.groupValues[1].replace(".", "")}${m.groupValues[2]}".toLongOrNull()
        }.filter { it > 0 }.toList()

    private fun total(rows: List<String>): Long? {
        for (key in totalKeys) {
            rows.forEachIndexed { i, row ->
                if (key.containsMatchIn(row) && !notTotal.containsMatchIn(row.replace(key, ""))) {
                    // L'importo sta sulla stessa riga o, se le colonne non si sono unite, su quella sotto.
                    amounts(row).lastOrNull()?.let { return it }
                    rows.getOrNull(i + 1)?.takeIf { !notTotal.containsMatchIn(it) }?.let { next -> amounts(next).lastOrNull()?.let { return it } }
                }
            }
        }
        // Nessuna parola del totale: il numero più grande, tolti contanti e resto.
        return rows.filterNot { notTotal.containsMatchIn(it) }.flatMap { amounts(it) }.maxOrNull()
    }

    /** Il negozio: la prima riga in alto fatta soprattutto di lettere, senza indirizzi o diciture fiscali. */
    private fun store(rows: List<String>): String? =
        rows.take(6).map { it.trim() }.firstOrNull { row ->
            val letters = row.count { it.isLetter() }
            letters >= 2 && letters >= row.count { it.isDigit() } * 2 && !notStore.containsMatchIn(row.uppercase())
        }?.let { raw ->
            raw.uppercase().replace(legalSuffix, "").trim().trim('.', ',', '-', '*').trim()
                .lowercase().split(Regex("\\s+")).joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
                .ifEmpty { null }
        }

    private fun date(rows: List<String>): LocalDate? =
        rows.firstNotNullOfOrNull { row ->
            date.find(row)?.let { m ->
                val (d, mo, y) = m.destructured
                val year = if (y.length == 2) 2000 + y.toInt() else y.toInt()
                runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
            }
        }
}
