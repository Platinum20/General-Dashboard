package com.platinum20.dashboard.docs

import java.text.Normalizer
import java.time.LocalDate

/** I documenti che Canvas riconosce da solo da una foto. */
enum class DocKind(val label: String) {
    ID_CARD("Carta d'identità"),
    LICENSE("Patente"),
    HEALTH_CARD("Tessera sanitaria"),
    PASSPORT("Passaporto"),
    VEHICLE("Libretto"),
    INSURANCE("Assicurazione"),
    RESIDENCE("Permesso di soggiorno"),
    OTHER("Documento"),
}

enum class DocSide { FRONT, BACK }

/** Quello che si legge da una foto di documento: tipo, lato e, se ci sono, numero e scadenza. */
data class DocScan(
    val kind: DocKind,
    val side: DocSide,
    val number: String? = null,
    val expiry: LocalDate? = null,
)

/**
 * Pura: dalle righe lette sul telefono (OCR) a "è un documento?". Si guarda solo alle parole stampate
 * su ogni documento italiano (CARTA DI IDENTITÀ, PATENTE DI GUIDA…), così uno scontrino o un biglietto
 * non diventano mai un documento. Il retro si riconosce dalla banda MRZ (<<<), dalla TEAM o dalla
 * tabella delle categorie della patente.
 */
object DocReader {
    private val FRONT_WORDS = listOf(
        DocKind.ID_CARD to listOf("CARTA DI IDENTITA", "CARTA D'IDENTITA", "IDENTITY CARD"),
        DocKind.LICENSE to listOf("PATENTE DI GUIDA", "DRIVING LICENCE", "DRIVING LICENSE"),
        DocKind.PASSPORT to listOf("PASSAPORTO", "PASSPORT"),
        DocKind.RESIDENCE to listOf("PERMESSO DI SOGGIORNO", "RESIDENCE PERMIT"),
        DocKind.VEHICLE to listOf("CARTA DI CIRCOLAZIONE", "CERTIFICATO DI CIRCOLAZIONE"),
        DocKind.INSURANCE to listOf("CERTIFICATO DI ASSICURAZIONE", "CONTRASSEGNO DI ASSICURAZIONE", "POLIZZA"),
        DocKind.HEALTH_CARD to listOf("TESSERA SANITARIA", "CARTA NAZIONALE DEI SERVIZI"),
    )

    /** Le categorie della patente: sul retro sono una colonna, 4 o più insieme vogliono dire retro. */
    private val LICENSE_CATEGORIES = setOf("AM", "A1", "A2", "B1", "BE", "C1", "C1E", "CE", "D1", "D1E", "DE")

    private val DATE = Regex("""(?<!\d)(\d{1,2})\s?[./\- ]\s?(\d{1,2})\s?[./\- ]\s?(\d{4}|\d{2})(?!\d)""")
    private val CODICE_FISCALE = Regex("""\b[A-Z]{6}\d{2}[A-Z]\d{2}[A-Z]\d{3}[A-Z]\b""")
    private val CIE_NUMBER = Regex("""\b[A-Z]{2}\d{5}[A-Z]{2}\b""")
    private val PASSPORT_NUMBER = Regex("""\b[A-Z]{2}\d{7}\b""")
    private val LICENSE_NUMBER = Regex("""\b(?=[A-Z0-9]*\d)(?=[A-Z0-9]*[A-Z])[A-Z0-9]{10}\b""")
    private val PLATE = Regex("""\b[A-Z]{2}\s?\d{3}\s?[A-Z]{2}\b""")

    /** Null se la foto non è un documento. */
    fun read(rows: List<String>, today: LocalDate = LocalDate.now()): DocScan? {
        val lines = rows.map(::plain).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val all = lines.joinToString(" ")
        val back = backKind(lines, all)
        val front = FRONT_WORDS.firstOrNull { (_, words) -> words.any { it in all } }?.first
        val (kind, side) = when {
            // Il passaporto ha la banda MRZ sulla pagina della foto: è il suo fronte.
            back == DocKind.PASSPORT -> DocKind.PASSPORT to DocSide.FRONT
            back != null -> back to DocSide.BACK
            front != null -> front to DocSide.FRONT
            else -> return null
        }
        return DocScan(kind, side, number(kind, all), expiry(all, today))
    }

    private fun backKind(lines: List<String>, all: String): DocKind? {
        val mrz = lines.map { it.replace(" ", "") }.firstOrNull { it.count { c -> c == '<' } >= 5 }
        if (mrz != null) return when {
            mrz.startsWith("P<") -> DocKind.PASSPORT
            mrz.startsWith("C") || mrz.startsWith("I") -> DocKind.ID_CARD
            else -> DocKind.OTHER
        }
        if ("TESSERA EUROPEA" in all || "EUROPEAN HEALTH" in all || "T.E.A.M" in all) return DocKind.HEALTH_CARD
        val words = all.split(Regex("""[^A-Z0-9]+""")).toSet()
        if (words.count { it in LICENSE_CATEGORIES } >= 4) return DocKind.LICENSE
        return null
    }

    private fun number(kind: DocKind, all: String): String? = when (kind) {
        DocKind.ID_CARD -> CIE_NUMBER.find(all)?.value
        DocKind.PASSPORT -> PASSPORT_NUMBER.find(all)?.value
        DocKind.HEALTH_CARD -> CODICE_FISCALE.find(all)?.value
        DocKind.LICENSE -> LICENSE_NUMBER.findAll(all).map { it.value }.firstOrNull { !CODICE_FISCALE.matches(it) }
        DocKind.VEHICLE, DocKind.INSURANCE -> PLATE.find(all)?.value?.replace(" ", "")
        else -> null
    }?.takeIf { it.isNotBlank() }

    /**
     * La scadenza è la data più lontana stampata sul documento: nascita e rilascio vengono sempre prima.
     * Solo se è da poco passata o nel futuro (fino a 20 anni): un documento scaduto da anni non va in calendario.
     */
    fun expiry(text: String, today: LocalDate): LocalDate? =
        DATE.findAll(text).mapNotNull { m ->
            val (d, mo, y) = m.destructured
            // Anno a due cifre: "25" è il 2025, "90" il 1990.
            val year = y.toInt().let { if (y.length == 2) (if (it <= today.year % 100 + 30) 2000 else 1900) + it else it }
            runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
        }.filter { it.isAfter(today.minusYears(1)) && it.isBefore(today.plusYears(20)) }.maxOrNull()

    /** Maiuscole senza accenti né doppi spazi: "Identità" e "IDENTITA" sono la stessa parola. */
    private fun plain(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{M}+"""), "")
            .uppercase().replace('’', '\'').replace(Regex("""\s+"""), " ").trim()
}
