package com.platinum20.dashboard.notes

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Una spesa: importo in centesimi, così le somme tornano sempre. */
data class Expense(
    val id: Long,
    val cents: Long,
    val text: String,
    val category: ExpenseCategory,
    val date: LocalDate = LocalDate.now(),
)

enum class ExpenseCategory(val label: String, val words: List<String>) {
    FOOD("Spesa", listOf("spesa", "supermercato", "conad", "coop", "lidl", "esselunga", "eurospin", "carrefour", "pane", "frutta", "mercato", "md ", "despar", "pam")),
    EATING_OUT("Fuori", listOf("pizza", "ristorante", "bar", "caffè", "caffe", "cena", "pranzo", "aperitivo", "sushi", "panino", "gelato", "kebab", "birra", "colazione", "mcdonald", "deliveroo", "glovo", "just eat")),
    TRANSPORT("Trasporti", listOf("benzina", "diesel", "gasolio", "carburante", "autostrada", "pedaggio", "treno", "bus", "autobus", "taxi", "parcheggio", "trenitalia", "italo", "biglietto", "metro", "auto", "gomme", "tagliando", "bollo", "meccanico")),
    HOME("Casa", listOf("bolletta", "luce", "gas", "acqua", "affitto", "condominio", "internet", "telefono", "ricarica", "tari", "ikea", "casa", "fibra")),
    HEALTH("Salute", listOf("farmacia", "medicina", "medico", "dentista", "visita", "analisi", "ticket", "palestra", "ottico")),
    FUN("Svago", listOf("cinema", "netflix", "spotify", "concerto", "libro", "gioco", "steam", "playstation", "disney", "prime", "amazon", "regalo", "vestiti", "scarpe", "abbonamento", "viaggio", "hotel")),
    OTHER("Altro", emptyList());

    companion object {
        fun guess(text: String): ExpenseCategory {
            val t = " ${text.lowercase()} "
            return entries.firstOrNull { c -> c.words.any { t.contains(it) } } ?: OTHER
        }
    }
}

object Expenses {
    private val amount = Regex("""(?<![\p{L}\d])(?:€\s?)?(\d{1,6}(?:[.,]\d{1,2})?)\s?(?:€|euro|eur)?(?![\p{L}\d])""", RegexOption.IGNORE_CASE)
    private val currency = NumberFormat.getCurrencyInstance(Locale.ITALY)

    /** "12,50 pizza", "benzina 40€", "€ 9.99 netflix": importo e descrizione. Null se manca l'importo. */
    fun parse(input: String, today: LocalDate = LocalDate.now()): Expense? {
        // Con più numeri ("pizza 2 persone 30€") vince quello con l'euro accanto.
        val all = amount.findAll(input).toList()
        val m = all.firstOrNull { it.value.contains('€') || it.value.contains("eur", ignoreCase = true) } ?: all.firstOrNull() ?: return null
        val cents = m.groupValues[1].replace(',', '.').toBigDecimal().movePointRight(2).toLong()
        if (cents <= 0) return null
        val text = (input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1))
            .replace(Regex("\\s+"), " ").trim().trim(',', '-', ':').trim()
            .replaceFirstChar { it.uppercase() }
        return Expense(0, cents, text, ExpenseCategory.guess(text), today)
    }

    fun format(cents: Long): String = currency.format(cents / 100.0)

    fun total(list: List<Expense>, month: YearMonth): Long =
        list.filter { YearMonth.from(it.date) == month }.sumOf { it.cents }

    /** Per categoria, dalla più pesante. */
    fun byCategory(list: List<Expense>, month: YearMonth): List<Pair<ExpenseCategory, Long>> =
        list.filter { YearMonth.from(it.date) == month }
            .groupBy { it.category }
            .map { (c, l) -> c to l.sumOf { it.cents } }
            .sortedByDescending { it.second }
}
