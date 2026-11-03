package com.platinum20.dashboard.bank

import com.platinum20.dashboard.notes.ExpenseCategory
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Un movimento del conto, già ridotto a quello che serve al calendario: [cents] è l'uscita
 * (positiva), [counts] dice se è una spesa vera e ancora valida (non rifiutata, non annullata).
 */
data class BankTx(
    val id: String,
    val cents: Long,
    val text: String,
    val category: ExpenseCategory,
    val date: LocalDate,
    val counts: Boolean = true,
)

/** Quello che la lettura di una risposta ha trovato: movimenti e, se c'era, il saldo in euro. */
data class RevolutRead(val txs: List<BankTx> = emptyList(), val balanceCents: Long? = null) {
    val hasAny get() = txs.isNotEmpty() || balanceCents != null
}

/**
 * Le risposte che l'app web di Revolut riceve dal suo server (le stesse che usa per disegnare la
 * home). Il formato non è pubblico: si riconoscono i movimenti dalla forma (id, importo in
 * centesimi, data d'inizio) e i conti dal saldo con la valuta, ovunque stiano nel JSON.
 */
object RevolutParse {
    /** Solo euro: le tasche in altre valute avrebbero importi non sommabili alle spese. */
    private const val CURRENCY = "EUR"

    /** Uscite che non sono spese: soldi spostati fra tasche proprie, cambi, investimenti. */
    private val notSpending = Regex("EXCHANGE|TOPUP|TOP_UP|CASHBACK|REFUND|REWARD|TRADE|INVEST|CRYPTO|STOCK|SAVINGS|VAULT|INTEREST", RegexOption.IGNORE_CASE)
    private val internalText = Regex("""(?i)\b(pocket|vault|salvadanai|savings|risparmi|to eur|exchanged|cambiat|da tasca|alla tasca|round.?up|arrotonda)""")
    private val cancelled = setOf("DECLINED", "REVERTED", "FAILED", "CANCELLED", "CANCELED", "DELETED")
    private val valid = setOf("COMPLETED", "PENDING", "CREATED", "")

    /**
     * Le categorie di Revolut (quelle che si vedono nell'app, anche se cambiate a mano lì) sulle
     * nostre. "services", "general", "transfers", "cash" non dicono niente: decidono MCC e nome.
     */
    private val revolutCategories = mapOf(
        "groceries" to ExpenseCategory.FOOD,
        "restaurants" to ExpenseCategory.EATING_OUT,
        "transport" to ExpenseCategory.TRANSPORT,
        "travel" to ExpenseCategory.FUN,
        "entertainment" to ExpenseCategory.FUN,
        "health" to ExpenseCategory.HEALTH,
        "utilities" to ExpenseCategory.BILLS,
        "bills" to ExpenseCategory.BILLS,
        "shopping" to ExpenseCategory.SHOPPING,
        "furniture" to ExpenseCategory.HOME,
        "housing" to ExpenseCategory.HOME,
        "home" to ExpenseCategory.HOME,
    )

    /** Per i nomi di categoria che non conosciamo ancora (anche quelle degli esercenti). */
    private val categories = listOf(
        Regex("grocer|supermarket|food_and_grocer") to ExpenseCategory.FOOD,
        Regex("""restaurant|cafe|coffee|fast.?food|\bbars?\b|eating|takeaway|delivery""") to ExpenseCategory.EATING_OUT,
        Regex("""transport|fuel|gas_station|taxi|parking|\bcar\b|car_rental|train|airline|flight""") to ExpenseCategory.TRANSPORT,
        Regex("utilit|bill|telecom|phone|mobile") to ExpenseCategory.BILLS,
        Regex("health|pharma|medical|doctor|fitness|gym") to ExpenseCategory.HEALTH,
        Regex("entertain|leisure|travel|hotel|accommodation|holiday|cinema|game|subscription|media|music") to ExpenseCategory.FUN,
        Regex("shopping|retail|clothing|electronic|online") to ExpenseCategory.SHOPPING,
        Regex("housing|home|rent|furnit") to ExpenseCategory.HOME,
    )

    /** La categoria di Revolut per un nome: prima quelle note, poi per somiglianza. */
    fun revolutCategory(name: String): ExpenseCategory? {
        val n = name.trim().lowercase().ifEmpty { return null }
        return revolutCategories[n] ?: categories.firstOrNull { it.first.containsMatchIn(n) }?.second
    }

    /**
     * Il codice MCC dell'esercente, quando il tag di Revolut è generico ("services", "general"):
     * è lo standard dei circuiti, più preciso del nome.
     */
    fun mccCategory(mcc: Int): ExpenseCategory? = when (mcc) {
        5411, 5412, 5422, 5441, 5451, 5462, 5499 -> ExpenseCategory.FOOD
        in 5811..5814 -> ExpenseCategory.EATING_OUT
        in 3000..3299, in 3351..3441, 4011, 4111, 4112, 4121, 4131, 4411, 4511, 4582, 4784, 4789,
        5541, 5542, 5983, 7511, 7512, 7513, 7523, 7531, 7534, 7538, 7542, 7549 -> ExpenseCategory.TRANSPORT
        4812, 4814, 4815, 4816, 4899, 4900 -> ExpenseCategory.BILLS
        5122, 5912, 5975, 5976, 7997, 8011, 8021, 8031, 8041, 8042, 8043, 8049, 8050, 8062, 8071, 8099 -> ExpenseCategory.HEALTH
        in 3501..3999, 4722, 7011, 7832, 7841, 7911, 7922, 7929, 7932, 7933, 7941, in 7991..7999, in 5815..5818 -> ExpenseCategory.FUN
        1520, 1711, 1731, 5200, 5211, 5231, 5251, 5261, 5712, 5713, 5714, 5718, 5719, 5722, 7210, 7211, 7216, 7217 -> ExpenseCategory.HOME
        in 5300..5399, in 5600..5699, 5732, 5734, 5735, 5945, 5947, 5970, 5992, 5993, 5994, 5999 -> ExpenseCategory.SHOPPING
        else -> null
    }

    fun read(body: String, zone: ZoneId = ZoneId.systemDefault()): RevolutRead? {
        val root = runCatching { if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body) }.getOrNull() ?: return null
        val txs = LinkedHashMap<String, BankTx>()
        val balances = mutableListOf<Long>()
        walk(root, 0) { o ->
            if (looksLikeTx(o)) tx(o, zone)?.let { txs[it.id] = it }
            else balance(o)?.let { balances += it }
        }
        val read = RevolutRead(txs.values.toList(), balances.takeIf { it.isNotEmpty() }?.sum())
        return read.takeIf { it.hasAny }
    }

    private fun walk(v: Any?, depth: Int, visit: (JSONObject) -> Unit) {
        if (depth > 8) return
        when (v) {
            is JSONObject -> {
                visit(v)
                // Dentro un movimento non si scende: il suo "account" ha un saldo che non è quello del conto.
                if (looksLikeTx(v)) return
                v.keys().forEach { walk(v.opt(it), depth + 1, visit) }
            }
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), depth + 1, visit)
        }
    }

    private fun looksLikeTx(o: JSONObject) =
        o.has("id") && o.opt("amount") is Number && (o.has("startedDate") || o.has("createdDate") || o.has("completedDate"))

    private fun tx(o: JSONObject, zone: ZoneId): BankTx? {
        val currency = o.optString("currency").uppercase()
        if (currency.isNotEmpty() && currency != CURRENCY) return null
        // Con le commissioni: è quello che è uscito davvero dal conto (cambi, prelievi oltre soglia).
        val amount = (o.opt("amountWithCharges") as? Number ?: o.opt("amount") as Number).toLong()
        val id = o.optString("legId").ifEmpty { o.optString("id") }.ifEmpty { return null }
        val at = listOf("startedDate", "createdDate", "completedDate").firstNotNullOfOrNull { k -> o.optLong(k).takeIf { it > 0 } } ?: return null
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val type = o.optString("type").uppercase()
        val state = o.optString("state").uppercase()
        val account = o.optJSONObject("account")?.optString("type").orEmpty()
        val merchant = o.optJSONObject("merchant")
        val text = (merchant?.optString("name")?.takeIf { it.isNotBlank() } ?: o.optString("description")).trim()
        // Un'entrata o un giro fra tasche proprie non è una spesa: se prima lo era (es. annullata e rifatta) si toglie.
        val spending = amount < 0 && !notSpending.containsMatchIn(type) && !notSpending.containsMatchIn(account) &&
            !(type == "TRANSFER" && internalText.containsMatchIn(text))
        if (!spending && state !in cancelled) return null
        val counts = spending && state in valid
        // Prima la categoria del movimento (quella scelta nell'app Revolut), poi il tag, poi l'esercente.
        val category = listOf(o.optString("category"), o.optString("tag"), merchant?.optString("category").orEmpty())
            .firstNotNullOfOrNull { revolutCategory(it) }
            ?: merchant?.optString("mcc")?.toIntOrNull()?.let(::mccCategory)
            ?: ExpenseCategory.guess(text)
        val name = when {
            // "To Mario Rossi" dei bonifici, in italiano come il resto dell'app.
            type == "TRANSFER" && text.startsWith("To ", ignoreCase = true) -> "A " + text.drop(3).trim()
            text.isNotEmpty() -> text
            type == "ATM" -> "Prelievo"
            else -> "Revolut"
        }
        return BankTx(id, -amount, name, category, date, counts)
    }

    /**
     * Il saldo della tasca in euro del conto principale, quello che l'app Revolut mostra in alto
     * (già senza i pagamenti in attesa). Serve il tipo CURRENT scritto: le tasche dei salvadanai
     * (risposta money-boxes) non hanno tipo e facevano credere che i risparmi fossero il saldo.
     */
    private fun balance(o: JSONObject): Long? {
        if (o.opt("balance") !is Number || !o.has("currency")) return null
        if (o.optString("currency").uppercase() != CURRENCY) return null
        if (o.optString("type").uppercase() != "CURRENT") return null
        if (o.optBoolean("closed") || o.optString("state").uppercase().let { it.isNotEmpty() && it != "ACTIVE" }) return null
        return o.optLong("balance")
    }

    /** Le risposte che non vanno mai salvate: accesso, codici, dati della carta. */
    fun isPrivate(url: String): Boolean =
        Regex("""(?i)(signin|sign-in|login|auth|token|passcode|otp|verify|confirm|card(s)?/.*(pan|cvv|details)|sensitive|password)""").containsMatchIn(url)
}
