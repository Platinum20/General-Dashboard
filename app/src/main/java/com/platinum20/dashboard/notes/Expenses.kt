package com.platinum20.dashboard.notes

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
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
    /** Il movimento Revolut da cui viene (letto dal conto), null per quelle scritte a mano o dalle notifiche. */
    val bankId: String? = null,
    /** L'ora della spesa (a mano, notifica, posta, conto): dentro il giorno vanno in ordine di orario (Antonio). Null = non si sa. */
    val at: LocalTime? = null,
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

/**
 * Le categorie delle spese. Le parole si cercano a inizio parola ("pizza" prende "pizzeria");
 * quelle di 4 lettere o meno solo come parola intera ("bar" non è "barbiere", "gas" non è "gasolio").
 */
enum class ExpenseCategory(val label: String, val words: List<String>) {
    FOOD("Spesa", listOf(
        "spesa", "supermercat", "supermarket", "market", "minimarket", "alimentar", "discount", "ipermercat", "iper", "ipercoop",
        "conad", "coop", "lidl", "esselunga", "eurospin", "carrefour", "despar", "spar", "interspar", "eurospar",
        "penny", "aldi", "famila", "sigma", "crai", "tigre", "todis", "in's", "dok", "md", "pam", "panorama", "bennet",
        "tuodi", "simply", "auchan", "naturasi", "unes", "sisa", "nonna isa", "decò", "pewex",
        "pane", "panett", "panific", "forno", "frutta", "fruttivendol", "macelleri", "pescheri", "salumeri", "mercato",
    )),
    EATING_OUT("Fuori", listOf(
        "pizza", "pizzeri", "ristorant", "trattoria", "osteria", "bar", "caffè", "caffe", "caffetteri", "pasticceri", "bistrot",
        "pub", "cena", "pranzo", "aperitiv", "sushi", "panin", "gelat", "kebab", "birra", "birreria", "colazione",
        "cornetto", "cappuccino", "spritz", "tavola calda", "rosticceri", "piadin", "burger", "hamburger",
        "mcdonald", "burger king", "kfc", "old wild west", "roadhouse", "autogrill", "starbucks",
        "deliveroo", "glovo", "just eat", "justeat", "ticket", "ticket restaurant", "buono pasto", "buoni pasto",
    )),
    TRANSPORT("Trasporti", listOf(
        "onorato", "moby", "tirrenia", "grimaldi", "traghett", "ryanair", "easyjet", "wizz", "ita airways", "volotea",
        "aeroporto", "flixbus", "itabus", "uber", "free now", "freenow", "telepass", "q8", "eni", "enilive", "esso",
        "tamoil", "agip", "atac", "gtt", "trenord", "benzina", "diesel", "gasolio", "carburant",
        "distributor", "autostrad", "pedaggio", "treno", "bus", "autobus", "taxi", "parchegg", "trenitalia", "italo",
        "metro", "autonoleggio", "noleggio", "car sharing", "share now",
        "gomme", "gommista", "tagliando", "bollo auto", "meccanico", "officina", "carrozzeria", "revisione",
    )),
    HOME("Casa", listOf(
        "affitto", "condominio", "ikea", "leroy merlin", "brico", "bricocenter", "casa", "arredament",
        "ferramenta", "detersiv", "pulizie", "lavanderia", "tintoria", "mutuo", "tari", "idraulico", "elettricista",
    )),
    BILLS("Bollette", listOf(
        "windtre", "wind tre", "wind", "tim", "vodafone", "iliad", "fastweb", "kena", "ho mobile", "very mobile",
        "poste mobile", "postemobile", "sky", "dazn", "ricarica", "telefono", "cellulare", "internet", "fibra",
        "enel", "hera", "a2a", "edison", "acea", "iren", "sorgenia", "eon", "e.on", "engie", "plenitude", "servizio elettrico",
        "bolletta", "bollette", "luce", "gas", "acqua", "canone rai", "canone",
    )),
    HEALTH("Salute", listOf(
        "farmac", "parafarmac", "sanitari", "ospedale", "medicin", "medico", "dentista", "odontoiatr", "visita",
        "analisi", "ticket sanitario", "asl", "clinica", "laboratorio", "fisioterap", "palestra", "piscina", "ottic",
        "occhiali", "lenti", "veterinari",
    )),
    FUN("Svago", listOf(
        "cinema", "uci cinema", "the space", "teatro", "museo", "concerto", "ticketone", "vivaticket", "netflix", "spotify",
        "disney", "prime video", "now tv", "apple music", "youtube premium", "twitch", "steam", "playstation", "xbox",
        "nintendo", "gioco", "giochi", "videogioc", "libro", "libri", "libreria", "feltrinelli", "mondadori",
        "discoteca", "serata", "biglietto concerto", "viaggio", "hotel", "albergo", "booking", "airbnb", "b&b",
    )),
    SHOPPING("Acquisti", listOf(
        "amazon", "amzn", "ebay", "zalando", "shein", "temu", "aliexpress", "vinted", "subito", "decathlon",
        "mediaworld", "unieuro", "euronics", "trony", "apple", "zara", "h&m", "primark", "ovs", "bershka", "pull&bear",
        "uniqlo", "nike", "adidas", "foot locker", "sephora", "douglas", "acqua e sapone", "tigota", "kiko", "flying tiger",
        "vestiti", "scarpe", "abbigliament", "regalo", "regali", "profumeri", "elettronica",
    )),
    OTHER("Altro", emptyList());

    companion object {
        /** Chi vince quando un testo tocca più categorie: le più precise prima ("ticket sanitario" è Salute, "spesa farmacia" pure). */
        private val priority = listOf(HEALTH, TRANSPORT, BILLS, EATING_OUT, FOOD, SHOPPING, FUN, HOME)

        private val patterns: Map<ExpenseCategory, Regex> = priority.associateWith { c ->
            // Le più lunghe prima: l'alternativa che vince è la prima che combacia.
            val alts = c.words.sortedByDescending { it.trim().length }.map { w ->
                val q = Regex.escape(w.trim().lowercase())
                if (w.trim().length <= 4) "$q(?![\\p{L}\\d])" else q
            }
            Regex("""(?<![\p{L}\d])(?:${alts.joinToString("|")})""")
        }

        fun guess(text: String): ExpenseCategory {
            val t = text.lowercase()
            if (t.isBlank()) return OTHER
            // A parità, conta la parola più lunga trovata: "spesa farmacia" è più farmacia che spesa.
            var best: ExpenseCategory? = null
            var bestLen = 0
            for (c in priority) {
                val len = patterns.getValue(c).findAll(t).maxOfOrNull { it.value.length } ?: continue
                if (len > bestLen) { best = c; bestLen = len }
            }
            return best ?: OTHER
        }
    }
}

object Expenses {
    /** Dentro il giorno per orario, la più vecchia prima; quelle senza ora (fisse, vecchie) all'inizio del giorno. */
    val byTime: Comparator<Expense> = compareBy<Expense> { it.date }.thenBy { it.at ?: LocalTime.MIN }

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
     * Il nome da mostrare di una spesa: il testo se è un nome vero, altrimenti la categoria ("Pagamento" per Altro,
     * non "Spesa", che sembrerebbe la categoria della spesa al supermercato).
     * Un testo che finisce con una preposizione o un articolo è un pezzo di frase, non un nome.
     */
    fun displayName(e: Expense): String {
        val text = e.text.trim()
        val last = text.substringAfterLast(' ').lowercase()
        if (text.isEmpty() || last in dangling) return if (e.category == ExpenseCategory.OTHER) "Pagamento" else e.category.label
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

    /**
     * Una volta sola, quando nascono Bollette e Acquisti (e "ticket" passa ai buoni pasto): le spese
     * che le vecchie parole avevano messo in Casa, Svago o Salute vanno nella categoria nuova.
     * Solo quei passaggi, così una categoria scelta a mano altrove resta com'è.
     */
    fun upgradeCategory(category: ExpenseCategory, text: String): ExpenseCategory {
        val now = ExpenseCategory.guess(text)
        return if (category to now in categoryMoves) now else category
    }

    private val categoryMoves = setOf(
        ExpenseCategory.HOME to ExpenseCategory.BILLS,
        ExpenseCategory.FUN to ExpenseCategory.SHOPPING,
        ExpenseCategory.HEALTH to ExpenseCategory.EATING_OUT,
    )

    fun total(list: List<Expense>, month: YearMonth): Long =
        list.filter { YearMonth.from(it.date) == month }.sumOf { it.cents }

    /** Per categoria, dalla più pesante. */
    fun byCategory(list: List<Expense>, month: YearMonth): List<Pair<ExpenseCategory, Long>> =
        list.filter { YearMonth.from(it.date) == month }
            .groupBy { it.category }
            .map { (c, l) -> c to l.sumOf { it.cents } }
            .sortedByDescending { it.second }
}
