package com.platinum20.dashboard.autofill

import com.platinum20.dashboard.notes.Carrier
import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.notes.QuickParse
import java.math.BigDecimal
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

/** Qualcosa che Canvas ha trovato da solo e che può diventare un pacco, una spesa o un promemoria. */
sealed interface Found {
    /** Lo stesso pacco o la stessa spesa visti due volte hanno la stessa chiave. */
    val key: String

    data class NewParcel(val title: String, val code: String, val expected: LocalDate?) : Found {
        override val key get() = "p:" + code.ifEmpty { title.lowercase() }
    }

    data class Delivered(val code: String) : Found {
        override val key get() = "c:$code"
    }

    data class Spent(val cents: Long, val merchant: String, val category: ExpenseCategory, val date: LocalDate, val at: LocalDateTime) : Found {
        // L'ora al minuto distingue due caffè uguali nello stesso giorno, non la stessa notifica ripetuta.
        override val key get() = "e:$cents:${merchant.lowercase()}:${at.withSecond(0).withNano(0)}"
    }

    data class Deadline(val title: String, val due: LocalDateTime) : Found {
        override val key get() = "d:${title.lowercase()}:${due.toLocalDate()}"
    }
}

/**
 * Legge notifiche e SMS e ci trova pacchi in arrivo, spese e scadenze.
 * Funzione pura, niente rete: tutto quello che legge resta sul telefono.
 */
object Extractor {
    private const val B0 = "(?<![\\p{L}\\d])"
    private const val B1 = "(?![\\p{L}\\d])"
    private fun rx(p: String) = Regex("$B0(?:$p)$B1", RegexOption.IGNORE_CASE)

    // --- Spese ---

    private val number = "\\d{1,3}(?:[.  ]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?"
    private val amount = Regex(
        "(?:€|EUR|euro)\\s?-?($number)|($number)\\s?(?:€|EUR|euro)$B1",
        RegexOption.IGNORE_CASE,
    )
    private val paid = rx(
        "hai pagato|pagat[oa]|pagamento|paid|spent|spesa di|spesa con|addebit[oa]t?[oa]?|addebito|acquisto|purchase|payment|" +
            "transazione|operazione con carta|utilizzo carta|prelievo|withdrawal|hai speso|you spent|card used|carta utilizzata"
    )
    // Soldi che entrano, pagamenti falliti o ancora da approvare, codici: niente spesa.
    private val notSpent = rx(
        "hai ricevuto|ricevut[oi] da|received|you got|accredit[a-z]*|rimbors[a-z]*|refund[a-z]*|rifiutat[oa]|declined|" +
            "non (?:è )?riuscit[oa]|failed|non andat[oa] a buon fine|annullat[oa]|top.?up|ricaric[a-z]* (?:il |del )?(?:tuo )?conto|" +
            "aggiunt[oi] (?:al conto|denaro)|added money|bonifico in (?:entrata|arrivo)|autorizz[a-z]*|approva|conferma il pagamento|" +
            "otp|codice (?:di )?(?:sicurezza|verifica|conferma)|password|cashback|stipendio|salary|richiesta di pagamento|payment request|" +
            "ti ha inviato|sent you|hai inviato|you sent|da pagare|scad[a-z]*|si rinnover[àa]|verr[àa] (?:rinnovat|addebitat)[oa]|prova gratuita|will renew|renews on"
    )
    private val receipt = rx("ricevuta|scontrino|conferma (?:d(?:ell)?'|del )?(?:ordine|acquisto|pagamento)|il tuo ordine|order confirmation|receipt|grazie per il (?:tuo )?acquisto")
    private val merchantAfter = Regex(
        "$B0(?:presso|at|a|da|c/o|from|su|in)\\s+([\\p{L}\\d][\\p{L}\\d&'.*\\-/ ]{1,40}?)(?=\\s*(?:[,;!\\n(]|\\.(?:\\s|$)|$| il | in data | on | con | with | per | alle | -|\\d{1,2}[/:.]\\d{2}))",
        RegexOption.IGNORE_CASE,
    )

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
        if (msg.kind == SourceKind.CHAT) return emptyList()
        val full = clean("${msg.title}\n${msg.text}")
        if (full.isBlank()) return emptyList()
        val out = mutableListOf<Found>()

        parcel(msg, full)?.let { out += it }
        if (out.isEmpty()) expense(msg, full)?.let { out += it }
        if (out.isEmpty()) deadline(msg, full)?.let { out += it }
        return out
    }

    /** Un importo in euro scritto come capita: "12,50", "1.234,56", "1,234.56", "12.50". */
    fun parseAmount(raw: String): Long? {
        val s = raw.replace(" ", "").replace(" ", "")
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalAt = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 || lastComma >= 0 -> {
                val at = maxOf(lastDot, lastComma)
                // Tre cifre dopo il separatore: sono le migliaia ("1.200").
                if (s.length - at - 1 in 1..2) at else -1
            }
            else -> -1
        }
        val digits = if (decimalAt < 0) s.filter { it.isDigit() }
        else s.substring(0, decimalAt).filter { it.isDigit() } + "." + s.substring(decimalAt + 1)
        return runCatching { BigDecimal(digits).movePointRight(2).toLong() }.getOrNull()?.takeIf { it > 0 }
    }

    private fun expense(msg: Incoming, text: String): Found.Spent? {
        if (notSpent.containsMatchIn(text)) return null
        if (!paid.containsMatchIn(text)) {
            // Nelle mail basta una ricevuta; altrove ci vuole la parola del pagamento.
            if (!(msg.kind == SourceKind.MAIL && receipt.containsMatchIn(text))) return null
        }
        if (msg.kind == SourceKind.MAIL && !receipt.containsMatchIn(text) && !paid.containsMatchIn(msg.title)) return null
        val m = amount.find(text) ?: return null
        val cents = parseAmount(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null
        if (cents > 5_000_000) return null
        val merchant = merchantAfter.findAll(text)
            .map { tidyName(it.groupValues[1]) }
            .firstOrNull { it.length >= 2 && !paid.matches(it) && amount.find(it) == null && it.any { c -> c.isLetter() } }
            ?: tidyName(msg.title).takeIf { it.isNotEmpty() && it.length <= 40 && !paid.containsMatchIn(it) && !it.equals(msg.app, true) && amount.find(it) == null }
            ?: msg.app
        return Found.Spent(cents, merchant, ExpenseCategory.guess(merchant), msg.at.toLocalDate(), msg.at)
    }

    private fun parcel(msg: Incoming, text: String): Found? {
        val code = codeAfterLabel.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { c -> c.any { it.isDigit() } }
            ?: Carrier.findCode(text)
        val isAmazon = msg.app.contains("amazon", true)
        if (code != null) {
            if (deliveredWords.containsMatchIn(text)) return Found.Delivered(code)
            if (!parcelWords.containsMatchIn(text) && msg.kind != SourceKind.SHOP) return null
            val carrier = Carrier.detect(code)
            val who = if (carrier != Carrier.OTHER) carrier.label else msg.app
            return Found.NewParcel("Pacco $who", code, expected(text, msg.at))
        }
        // Amazon spesso non mostra il codice: basta il nome dell'articolo.
        if (isAmazon && !deliveredWords.containsMatchIn(text)) {
            val item = amazonShipped.find(text)?.groupValues?.get(1)?.lineSequence()?.firstOrNull()?.let(::itemName)
            if (!item.isNullOrBlank()) return Found.NewParcel(item, "", expected(text, msg.at))
        }
        return null
    }

    /** "Consegna prevista il 24 ottobre", "arriverà domani": la data attesa, se c'è. */
    private fun expected(text: String, now: LocalDateTime): LocalDate? {
        val m = expectedWords.find(text) ?: return null
        val window = text.substring(m.range.first, minOf(text.length, m.range.last + 40))
        if (Regex("${B0}oggi$B1|today", RegexOption.IGNORE_CASE).containsMatchIn(window) && !dateToken.containsMatchIn(window)) return now.toLocalDate()
        if (!dateToken.containsMatchIn(window)) return null
        return QuickParse.parse(window.replace(Regex("\\d{1,2}[:.]\\d{2}"), ""), now).due?.toLocalDate()
            ?.takeIf { !it.isBefore(now.toLocalDate()) && it.isBefore(now.toLocalDate().plusDays(60)) }
    }

    private fun deadline(msg: Incoming, text: String): Found.Deadline? {
        if (msg.kind == SourceKind.PAYMENT) return null
        val trigger = renewalWords.find(text) ?: billWords.find(text) ?: bookingWords.find(text) ?: return null
        val renewal = renewalWords.containsMatchIn(text)
        // La data va cercata vicino alla parola chiave, non in tutta la mail.
        val window = text.substring(maxOf(0, trigger.range.first - 30), minOf(text.length, trigger.range.last + 60))
        val dateMatch = dateToken.find(window) ?: return null
        val parsed = QuickParse.parse(window.substring(maxOf(0, dateMatch.range.first - 4)), msg.at).due ?: return null
        if (!parsed.isAfter(msg.at) || parsed.isAfter(msg.at.plusDays(400))) return null
        // Senza anno una data passata diventa quella dell'anno prossimo: troppo lontana, è quasi sempre il passato.
        val hasYear = Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}|\\d{4}").containsMatchIn(window)
        if (!hasYear && parsed.isAfter(msg.at.plusDays(200))) return null
        val subject = msg.text.lineSequence().map { clean(it) }.firstOrNull { it.isNotEmpty() } ?: clean(msg.title)
        val from = listOf(msg.app, msg.title).map { tidyName(it) }
            .firstOrNull { it.isNotEmpty() && it.length <= 24 && !it.equals(subject, true) && !genericApps.matches(it) }
        return Found.Deadline(shortTitle(subject, from, renewal).ifEmpty { msg.app }, parsed)
    }

    private val genericApps = Regex("gmail|outlook|posta|mail|e-?mail|messaggi|sms|yahoo mail|proton ?mail", RegexOption.IGNORE_CASE)
    private val subjectPrefix = Regex(
        "^(?:(?:re|r|fwd?|i|inoltro|scadenza|rinnovo|promemoria|reminder|avviso|importante|attenzione|info)\\s*:\\s*)+",
        RegexOption.IGNORE_CASE,
    )
    /** Un nome di servizio: parole che iniziano con la maiuscola ("Metal", "Claude Pro", "Disney+"). */
    private const val NAME = "([\\p{Lu}\\d][\\p{L}\\d+&.'’-]*(?: [\\p{Lu}\\d][\\p{L}\\d+&.'’-]*){0,2})"
    // Le parole chiave senza maiuscole/minuscole, il nome invece sì: deve restare maiuscolo.
    private val trialOf = Regex("(?i:prova(?: gratuita)?|periodo di prova|trial)(?: (?i:di|a|al|per|del|della|dello|of|on|for))? $NAME")
    private val subscriptionOf = Regex("(?i:abbonamento|iscrizione|piano|subscription)(?: (?i:a|al|all'|di|del|per|to))? $NAME")
    private val endingWords = rx("annullat[oa]|disdett[oa]|cancellat[oa]|cancel+ed|terminat?[oa]?|termina|finisce|scad[a-z]*|sospes[oa]|ends?")
    private val possessive = Regex("^(?:il tuo|la tua|i tuoi|le tue|il vostro|la vostra|your)\\s+", RegexOption.IGNORE_CASE)
    private val dangling = Regex("(?:\\s+(?:il|lo|la|l'|i|gli|le|per|entro|del|della|dal|di|da|alle|a|in|on|by|for|at|e|ed|,|-|:|·))+$", RegexOption.IGNORE_CASE)

    /**
     * L'oggetto di una mail diventa un titolo corto da una riga, senza "Scadenza:" (lo dice già il conto alla rovescia):
     * "La tua prova gratuita di Metal" diventa "Fine prova Metal", "Il tuo abbonamento a Claude Pro è stato annullato"
     * diventa "Claude Pro termina". Vale anche per i titoli già salvati dalle versioni prima.
     */
    fun shortTitle(raw: String, from: String? = null, renewal: Boolean = false): String {
        val s = clean(raw).replace("…", " ").trim().replace(subjectPrefix, "").replace(possessive, "").trim()
        fun name(m: MatchResult) = m.groupValues[1].trim(' ', '.', ',', '-').replaceFirstChar { it.uppercase() }
        trialOf.find(s)?.takeIf { val n = it.groupValues[1]; !n.first().isDigit() && endingWords.find(n)?.range?.first != 0 }
            ?.let { return "Fine prova ${name(it)}" }
        subscriptionOf.find(s)?.let { m ->
            val n = name(m)
            return when {
                renewal || s.contains("rinnov", true) || s.contains("renew", true) -> "Rinnovo $n"
                endingWords.containsMatchIn(s) -> "$n termina"
                else -> "Abbonamento $n"
            }
        }
        Regex("${B0}(bolletta|fattura|bollo|multa|rata|canone)$B1", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val what = m.value.lowercase().replaceFirstChar { it.uppercase() }
            if (from != null) return "$what $from"
        }
        if (renewal && !s.contains("rinnov", true)) {
            // Un oggetto che è solo il nome del servizio ("Spotify Premium") dice di più del mittente.
            val isName = s.length <= 24 && s.split(' ').all { w -> w.firstOrNull()?.let { it.isUpperCase() || it.isDigit() } == true }
            (if (isName) s else from)?.let { return "Rinnovo $it" }
        }
        // Il resto: via "il tuo", via la data (la dice già la riga sotto) e quello che viene dopo una virgola.
        var t = s
        dateToken.find(t)?.takeIf { it.range.first > 3 }?.let { t = t.substring(0, it.range.first) }
        t = t.split(Regex("\\s*[,;|!?(\\[]\\s*|\\s+[-–—]\\s+|:\\s+")).firstOrNull { it.isNotBlank() }.orEmpty().trim()
        t = t.replace(dangling, "").trim()
        return shorten(t).replaceFirstChar { it.uppercase() }
    }

    /** Al massimo 32 caratteri, tagliati fra due parole e senza puntini: deve stare su una riga. */
    private fun shorten(s: String, max: Int = 32): String {
        if (s.length <= max) return s
        val cut = s.take(max + 1).substringBeforeLast(' ').ifEmpty { s.take(max) }
        return cut.replace(dangling, "").trim()
    }

    /**
     * Il nome di un articolo come lo scrivono i negozi ("STAMPATEK Tazza Regalo Personalizzata…“") diventa
     * corto e pulito ("Tazza regalo"): via virgolette, marca in maiuscolo e tutto quello dopo le prime parole.
     */
    fun itemName(raw: String): String {
        var s = clean(raw).replace(Regex("[\"“”„«»‘’`]|…|\\.{3}"), " ").replace(Regex("\\s+"), " ").trim(' ', '.', '-', ',', ':')
        s = s.split(Regex("\\s*[,;|(\\[/]\\s*|\\s+[-–—]\\s+|\\s+(?:con|per|with|for)\\s+", RegexOption.IGNORE_CASE))
            .firstOrNull { it.isNotBlank() }.orEmpty().trim()
        var words = s.split(' ').filter { it.isNotBlank() }
        // "STAMPATEK Tazza": la marca tutta maiuscola in testa non dice cos'è.
        if (words.size > 1 && words[0].length >= 3 && words[0].all { !it.isLetter() || it.isUpperCase() } && words[1].any { it.isLowerCase() }) {
            words = words.drop(1)
        }
        // I codici dei modelli ("WH-1000XM5") e le misure non servono a riconoscerlo.
        words = words.takeWhile { w -> w.none { it.isDigit() } }.ifEmpty { words.take(1) }
        // Due parole che dicono cos'è bastano ("Tazza regalo", "Lampada da tavolo"): dopo di solito c'è la marca.
        var content = 0
        words = words.takeWhile { w -> (content < 2).also { if (it && w.lowercase() !in linkWords) content++ } }
        return words.mapIndexed { i, w ->
            // "Regalo" è una parola, "Sony" o "Bluetooth" sono nomi: le parole italiane finiscono quasi sempre in vocale.
            val titleCase = w.length > 1 && w[0].isUpperCase() && w.drop(1).all { !it.isLetter() || it.isLowerCase() } &&
                w.last().lowercaseChar() in "aeiouàèéìòù"
            when {
                i == 0 && w.all { !it.isLetter() || it.isUpperCase() } && w.length > 3 -> w.lowercase().replaceFirstChar { it.uppercase() }
                i == 0 -> w.replaceFirstChar { it.uppercase() }
                titleCase -> w.lowercase()
                else -> w
            }
        }.joinToString(" ").replace(dangling, "")
    }

    private val linkWords = setOf("da", "di", "del", "della", "dei", "a", "in", "e", "of", "the")

    private val emoji = Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]")

    private fun clean(s: String) = s.replace(emoji, " ").replace(Regex("[ \\t ]+"), " ").trim()

    /** "LIDL ITALIA S.R.L." diventa "Lidl Italia", "Amazon.it*AB12CD" diventa "Amazon.it". */
    fun tidyName(raw: String): String {
        var s = clean(raw).substringBefore('*').trim(' ', '.', '-', ',', ':')
        s = s.replace(Regex("\\s+(?:s\\.?r\\.?l\\.?s?|s\\.?p\\.?a\\.?|s\\.?n\\.?c\\.?|s\\.?a\\.?s\\.?|ltd|gmbh|inc)\\.?$", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("\\s+\\d{4,}.*$"), "").trim()
        if (s.length > 3 && s == s.uppercase() && s.any { it.isLetter() }) {
            s = s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        return s.take(40).trim()
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

    /** Il tipo di app dal nome del pacchetto: le chat si ignorano, banche e negozi si leggono. */
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
