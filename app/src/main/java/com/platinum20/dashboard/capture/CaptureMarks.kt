package com.platinum20.dashboard.capture

/**
 * Le piccole letture del riquadro "+" (1.641, Antonio): le parole di data/ora da colorare mentre scrivi, così vedi che
 * le ho capite, e le righe "- " che diventano tanti Da fare.
 */
object CaptureMarks {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"
    private const val DAYS = "lunedì|lunedi|martedì|martedi|mercoledì|mercoledi|giovedì|giovedi|venerdì|venerdi|sabato|domenica"
    private const val MONTHS = "gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre"

    private val whenRx = Regex(
        B0 + "(?:" + listOf(
            "dopodomani", "domani(?: mattina| sera| pomeriggio)?", "oggi", "stasera", "stamattina", "stanotte", "stamani",
            "(?:prossim[oa] |questo |questa )?(?:$DAYS)(?: prossim[oa])?",
            "(?:la )?settimana prossima", "(?:il )?mese prossimo", "fra \\d+ (?:giorni|settimane|mesi|ore|minuti)",
            "tra \\d+ (?:giorni|settimane|mesi|ore|minuti)",
            "(?:il )?\\d{1,2}(?: (?:$MONTHS))", "\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?",
            "(?:alle|all'|verso le|ore|dalle|entro le) ?\\d{1,2}(?:[:.]\\d{2})?",
            "a mezzogiorno", "a mezzanotte",
        ).joinToString("|") + ")" + B1,
        RegexOption.IGNORE_CASE,
    )

    /** Dove sono nel testo le parole che dicono quando ("domani", "alle 9", "il 3 dicembre"). */
    fun whenRanges(text: String): List<IntRange> = whenRx.findAll(text).map { it.range }.toList()

    private val dash = Regex("""^\s*[-•*]\s+""")

    /**
     * Più righe che cominciano tutte con "- " (o "• "): ognuna è un Da fare a sé. Null se non è una lista così
     * (una riga sola, o righe senza trattino), e il testo si salva come sempre.
     */
    fun dashItems(text: String): List<String>? {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2 || !lines.all { dash.containsMatchIn(it) }) return null
        return lines.map { it.replace(dash, "").trim() }.filter { it.isNotEmpty() }.takeIf { it.size >= 2 }
    }

    /** Quanto silenzio (nessuna parola nuova) prima che il vocale bloccato si fermi da solo. */
    const val SILENCE_MS = 4_000L

    /** Il vocale bloccato si chiude da solo: c'è già del testo e da [SILENCE_MS] non arriva niente di nuovo. */
    fun autoStop(locked: Boolean, partial: String, quietForMs: Long): Boolean =
        locked && partial.isNotBlank() && quietForMs >= SILENCE_MS
}
