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
    /** La spesa fissa che l'ha aggiunta da sola, null per quelle scritte a mano. */
    val fixedId: Long? = null,
)

/**
 * Una spesa fissa: affitto, bollette, abbonamenti. Ogni mese, il giorno [day]
 * (o l'ultimo del mese se è più corto), si aggiunge da sola alle spese.
 */
data class FixedExpense(
    val id: Long,
    val cents: Long,
    val text: String,
    val category: ExpenseCategory,
    val day: Int,
    /** Il primo mese in cui va aggiunta. */
    val from: YearMonth,
    /** L'ultimo mese già aggiunto, null se mai. */
    val lastMonth: YearMonth? = null,
) {
    fun dateIn(month: YearMonth): LocalDate = month.atDay(day.coerceAtMost(month.lengthOfMonth()))

    /** I mesi da aggiungere fino a oggi compreso (più d'uno se l'app non si apriva da tempo). */
    fun dueMonths(today: LocalDate): List<YearMonth> {
        var month = lastMonth?.plusMonths(1) ?: from
        val out = mutableListOf<YearMonth>()
        while (!dateIn(month).isAfter(today)) {
            out += month
            month = month.plusMonths(1)
        }
        return out
    }

    /** La prossima volta che si aggiunge. */
    fun nextDate(today: LocalDate): LocalDate {
        var month = lastMonth?.plusMonths(1) ?: from
        while (dateIn(month).isBefore(today)) month = month.plusMonths(1)
        return dateIn(month)
    }
}

enum class ExpenseCategory(val label: String, val words: List<String>) {
    FOOD("Spesa", listOf("penny", " aldi", "famila", "sigma", "crai", "tigre", "todis", "in's", "dok ", "iper", "spesa", "supermercato", "conad", "coop", "lidl", "esselunga", "eurospin", "carrefour", "pane", "frutta", "mercato", "md ", "despar", "pam")),
    EATING_OUT("Fuori", listOf("pizza", "ristorante", "bar", "caffè", "caffe", "cena", "pranzo", "aperitivo", "sushi", "panino", "gelato", "kebab", "birra", "colazione", "mcdonald", "deliveroo", "glovo", "just eat")),
    TRANSPORT("Trasporti", listOf("onorato", "moby", "tirrenia", "grimaldi", "traghett", "ryanair", "easyjet", "wizz", "ita airways", "flixbus", "uber", "free now", "freenow", "telepass", " q8", " eni ", "enilive", " esso", "tamoil", "atac", "gtt", "trenord", "benzina", "diesel", "gasolio", "carburante", "autostrada", "pedaggio", "treno", "bus", "autobus", "taxi", "parcheggio", "trenitalia", "italo", "biglietto", "metro", "auto", "gomme", "tagliando", "bollo", "meccanico")),
    HOME("Casa", listOf("windtre", "wind tre", "wind", " tim ", "vodafone", "iliad", "fastweb", "kena", "enel", "hera", "a2a", "edison", "acea", "bolletta", "luce", "gas", "acqua", "affitto", "condominio", "internet", "telefono", "ricarica", "tari", "ikea", "casa", "fibra")),
    HEALTH("Salute", listOf("farmac", "parafarmac", "sanitari", "ospedale", "farmacia", "medicina", "medico", "dentista", "visita", "analisi", "ticket", "palestra", "ottico")),
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

    private val everyMonth = Regex("""(?<![\p{L}\d])ogni mese(?: il| al)? (\d{1,2})(?![\p{L}\d])|(?<![\p{L}\d])il (\d{1,2}) (?:di )?ogni mese(?![\p{L}\d])""", RegexOption.IGNORE_CASE)

    /**
     * "ogni mese il 5 affitto 650" o "netflix 12,99 il 15 di ogni mese": una spesa fissa.
     * Se il giorno di questo mese è già passato, si parte dal mese dopo. Null se non è fissa.
     */
    fun parseFixed(input: String, today: LocalDate = LocalDate.now()): FixedExpense? {
        val m = everyMonth.find(input) ?: return null
        val day = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
        if (day !in 1..31) return null
        val rest = input.substring(0, m.range.first) + " " + input.substring(m.range.last + 1)
        val e = parse(rest, today) ?: return null
        val month = YearMonth.from(today)
        val first = FixedExpense(0, e.cents, e.text, e.category, day, month)
        return if (first.dateIn(month).isBefore(today)) first.copy(from = month.plusMonths(1)) else first
    }

    /** Del mese: quanto è fisso e quanto variabile. */
    fun split(list: List<Expense>, month: YearMonth): Pair<Long, Long> {
        val inMonth = list.filter { YearMonth.from(it.date) == month }
        val fixed = inMonth.filter { it.fixedId != null }.sumOf { it.cents }
        return fixed to inMonth.sumOf { it.cents } - fixed
    }

    /** Le fisse che devono ancora arrivare entro la fine del mese di [today]. */
    fun fixedStillDue(fixed: List<FixedExpense>, today: LocalDate): Long {
        val month = YearMonth.from(today)
        return fixed.filter { f ->
            val next = f.nextDate(today)
            YearMonth.from(next) == month && next.isAfter(today)
        }.sumOf { it.cents }
    }

    fun format(cents: Long): String = currency.format(cents / 100.0)

    /** Parole che non chiudono mai un nome: "fronte di" è un pezzo di notifica preso male. */
    private val dangling = setOf("di", "a", "da", "in", "con", "su", "per", "tra", "fra", "il", "lo", "la", "i", "gli", "le", "del", "della", "al", "alla", "e", "un", "una")

    /**
     * Il nome da mostrare di una spesa: il testo se è un nome vero, altrimenti la categoria ("Spesa" per Altro).
     * Un testo che finisce con una preposizione o un articolo è un pezzo di frase, non un nome.
     */
    fun displayName(e: Expense): String {
        val text = e.text.trim()
        val last = text.substringAfterLast(' ').lowercase()
        if (text.isEmpty() || last in dangling) return if (e.category == ExpenseCategory.OTHER) "Spesa" else e.category.label
        // I nomi dei negozi sulle carte arrivano tagliati a 25 lettere ("Farmacenter Dottor Moresc"): si dice che continua.
        return if (text.length >= CARD_NAME && text.last().isLetter()) "$text…" else text
    }

    /** Quante lettere tiene il nome del negozio nelle notifiche della carta. */
    private const val CARD_NAME = 25

    /** Le spese finite in "Altro" che ora si riconoscono (nuove parole delle categorie): la categoria giusta. */
    fun recategorized(list: List<Expense>): List<Expense> = list.map { e ->
        if (e.category != ExpenseCategory.OTHER) e
        else ExpenseCategory.guess(e.text).let { if (it == ExpenseCategory.OTHER) e else e.copy(category = it) }
    }

    fun total(list: List<Expense>, month: YearMonth): Long =
        list.filter { YearMonth.from(it.date) == month }.sumOf { it.cents }

    /** Per categoria, dalla più pesante. */
    fun byCategory(list: List<Expense>, month: YearMonth): List<Pair<ExpenseCategory, Long>> =
        list.filter { YearMonth.from(it.date) == month }
            .groupBy { it.category }
            .map { (c, l) -> c to l.sumOf { it.cents } }
            .sortedByDescending { it.second }
}
