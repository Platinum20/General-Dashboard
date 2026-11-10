package com.platinum20.dashboard.autofill

import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.QuickParse
import java.time.LocalDate
import java.time.LocalDateTime

/** Da che tipo di app arriva un messaggio: decide cosa vale la pena cercarci dentro. */
enum class SourceKind { PAYMENT, MAIL, SMS, SHOP, CHAT, OTHER }

/** Un messaggio da leggere: una notifica o un SMS, già ridotto a testo. */
data class Incoming(
    val app: String,
    val kind: SourceKind,
    val title: String,
    val text: String,
    val at: LocalDateTime,
)

/** Qualcosa che Canvas ha trovato da solo e che può diventare un pacco o un promemoria. */
sealed interface Found {
    /** Lo stesso pacco visto due volte ha la stessa chiave. */
    val key: String

    data class NewParcel(val title: String, val code: String, val expected: LocalDate?) : Found {
        override val key get() = "p:" + code.ifEmpty { title.lowercase() }
    }

    data class Delivered(val code: String) : Found {
        override val key get() = "c:$code"
    }

    data class Deadline(val title: String, val due: LocalDateTime) : Found {
        override val key get() = "d:${title.lowercase()}:${due.toLocalDate()}"
    }
}

/**
 * Legge notifiche e SMS e ci trova pacchi in arrivo e scadenze.
 * Funzione pura, niente rete: tutto quello che legge resta sul telefono.
 * I soldi non si leggono più (1.438, Antonio): niente spese dalle notifiche e le app di banche e pagamenti si ignorano.
 */
object Extractor {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"
    private fun rx(p: String) = Regex("$B0(?:$p)$B1", RegexOption.IGNORE_CASE)

    // --- Pacchi ---

    private val parcelWords = rx(
        "spedit[oa]|spedizion[ei]|pacc(?:o|hi)|consegn[a-z]*|tracking|tracciament[oi]|corriere|shipped|shipment|delivery|" +
            "out for delivery|in transito|in arrivo|arriver[àa]|locker|punto di ritiro|lettera di vettura"
    )
    // "Sarà consegnato" è ancora in viaggio: conta solo il passato.
    private val deliveredWords = rx("(?<!sar[àa] |verr[àa] |essere |be |will be )(?:consegnat[oa]|delivered|ritirat[oa])|lasciato (?:in|al|presso)")
    private val codeAfterLabel = Regex(
        "(?:tracking(?: number| code| n\\.?)?|codice (?:di )?(?:tracciamento|spedizione|tracking)|numero (?:di )?(?:tracciamento|spedizione)|" +
            "lettera di vettura|ldv|n\\.? ?spedizione|spedizione n\\.?|spedizione numero)\\s*[:#n.°º]*\\s*([A-Z0-9]{8,30})",
        RegexOption.IGNORE_CASE,
    )
    private val expectedWords = rx("consegna (?:prevista|stimata|programmata)|arriver[àa]|in arrivo|arriving|expected|sar[àa] consegnat[oa]|in consegna")
    private val amazonShipped = Regex("(?:spedit[oa]|in consegna(?: oggi)?|in arrivo(?: oggi| domani)?|arriver[àa][^:]{0,20}|shipped|arriving[^:]{0,20})\\s*:\\s*(.+)", RegexOption.IGNORE_CASE)

    // --- Scadenze e appuntamenti ---

    private val billWords = rx("scad(?:e|enza|r[àa]|ono|uta)|entro (?:il|e non oltre)|da pagare|pagamento entro|termine ultimo|due date|due on")
    // Abbonamenti: il rinnovo automatico o la fine della prova gratuita, da sapere prima dell'addebito.
    private val renewalWords = rx(
        "si rinnover[àa]|verr[àa] rinnovat[oa]|rinnovo automatico|rinnovo (?:del|dell'|della)? ?(?:tuo )?(?:abbonamento|piano|iscrizione)|" +
            "prova(?: gratuita)? (?:termina|finisce|scade)|periodo di prova (?:termina|finisce|scade)|trial ends|renews on|will renew|subscription renews"
    )
    private val bookingWords = rx(
        "prenotazion[ei]|prenotat[oa]|appuntament[oi]|visita|volo|partenza|check.?in|biglietto|biglietti|evento|concerto|" +
            "udienza|esame|colloquio|reservation|booking|flight|appointment|ritiro"
    )
    private val dateToken = rx(
        "\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?|\\d{1,2} (?:gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre|" +
            "gen|feb|mar|apr|mag|giu|lug|ago|set|ott|nov|dic)|domani|dopodomani|luned[iì]|marted[iì]|mercoled[iì]|gioved[iì]|venerd[iì]|sabato|domenica"
    )

    fun extract(msg: Incoming): List<Found> {
        // Chat e app di banche o pagamenti non si leggono affatto.
        if (msg.kind == SourceKind.CHAT || msg.kind == SourceKind.PAYMENT) return emptyList()
        val full = clean("${msg.title}\n${msg.text}")
        if (full.isBlank()) return emptyList()
        val out = mutableListOf<Found>()

        parcel(msg, full)?.let { out += it }
        if (out.isEmpty()) deadline(msg, full)?.let { out += it }
        return out
    }

    // --- Da che app arriva ---

    private val chatApps = listOf(
        "com.whatsapp", "org.telegram", "org.thoughtcrime.securesms", "com.facebook.orca", "com.instagram", "com.discord",
        "com.snapchat", "com.viber", "jp.naver.line", "com.skype", "com.microsoft.teams", "com.slack", "com.google.android.apps.dynamite",
    )
    private val smsApps = listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms", "com.android.messaging", "com.textra")
    private val mailApps = listOf("com.google.android.gm", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail", "ch.protonmail", "com.samsung.android.email", "me.bluemail", "com.libero.mail", "it.italiaonline")
    private val shopApps = listOf(
        "com.amazon", "com.ebay", "com.zalando", "com.alibaba.aliexpresshd", "com.einnovation.temu", "com.zzkko", "com.vinted", "it.subito",
        "it.poste.posteitaliane", "posteitaliane.posteapp.appposteitaliane", "com.brt", "it.brt", "com.gls", "it.gls", "com.dhl", "de.dhl", "com.ups", "com.fedex",
        "it.sda", "com.inpost", "com.packlink", "com.parcelsapp",
    )
    private val paymentHints = listOf(
        "revolut", "satispay", "paypal", "wallet", "bank", "banca", "banco", "n26", "number26", "hype", "fineco", "intesa", "latuabanca",
        "unicredit", "bper", "ingdirect", "postepay", "bancoposta", "nexi", "amex", "americanexpress", "mediolanum", "widiba",
        "credem", "bnl", "isybank", "buddybank", "illimity", "webank", "chebanca", "it.mps.", "bancasella", "bancobpm", "youapp", "bunq", "com.wise",
        "transferwise", "curve", "lydia", "monzo", "vivid", "trade.republic", "traderepublic", "tinaba", "flowe", "paysafe", "klarna", "scalapay",
    )

    /** Il tipo di app dal nome del pacchetto: chat, banche e pagamenti si ignorano, negozi e corrieri si leggono. */
    fun kindOf(packageName: String): SourceKind {
        val p = packageName.lowercase()
        return when {
            chatApps.any { p.startsWith(it) } -> SourceKind.CHAT
            smsApps.any { p.startsWith(it) } -> SourceKind.SMS
            mailApps.any { p.startsWith(it) } -> SourceKind.MAIL
            paymentHints.any { p.contains(it) } -> SourceKind.PAYMENT
            shopApps.any { p.startsWith(it) } -> SourceKind.SHOP
            else -> SourceKind.OTHER
        }
    }
}
