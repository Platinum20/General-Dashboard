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

    /** Le categorie di Revolut (tag e categoria dell'esercente) sulle nostre; il resto lo indovina il nome. */
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
        val amount = o.optLong("amount")
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
        val tags = listOf(o.optString("tag"), o.optString("category"), merchant?.optString("category").orEmpty()).joinToString(" ").lowercase()
        val category = categories.firstOrNull { it.first.containsMatchIn(tags) }?.second
            ?: ExpenseCategory.guess(text)
        val name = when {
            text.isNotEmpty() -> text
            type == "ATM" -> "Prelievo"
            else -> "Revolut"
        }
        return BankTx(id, -amount, name, category, date, counts)
    }

    /** Il saldo di una tasca in euro del conto principale (non salvadanai, non chiuse). */
    private fun balance(o: JSONObject): Long? {
        if (o.opt("balance") !is Number || !o.has("currency")) return null
        if (o.optString("currency").uppercase() != CURRENCY) return null
        val type = o.optString("type").uppercase()
        if (type.isNotEmpty() && type != "CURRENT") return null
        if (o.optBoolean("closed") || o.optString("state").uppercase().let { it.isNotEmpty() && it != "ACTIVE" }) return null
        return o.optLong("balance")
    }

    /** Le risposte che non vanno mai salvate: accesso, codici, dati della carta. */
    fun isPrivate(url: String): Boolean =
        Regex("""(?i)(signin|sign-in|login|auth|token|passcode|otp|verify|confirm|card(s)?/.*(pan|cvv|details)|sensitive|password)""").containsMatchIn(url)
}
