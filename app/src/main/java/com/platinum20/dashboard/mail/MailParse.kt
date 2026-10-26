package com.platinum20.dashboard.mail

import com.platinum20.dashboard.autofill.Incoming
import com.platinum20.dashboard.autofill.SourceKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64

/** Una mail già ridotta a testo: chi la manda, l'oggetto e il corpo leggibile. */
data class MailMessage(
    val id: String,
    val from: String,
    val fromAddress: String,
    val subject: String,
    val snippet: String,
    val at: Long,
    val labels: Set<String>,
    val body: String,
) {
    val unread: Boolean get() = "UNREAD" in labels
}

/** Lettura delle risposte di Gmail: funzioni pure, provate nei test. */
object MailParse {
    /** Le categorie che non contengono mai pacchi, bollette o prenotazioni vere. */
    private val noise = setOf("CATEGORY_PROMOTIONS", "CATEGORY_SOCIAL", "CATEGORY_FORUMS", "SPAM", "TRASH", "DRAFT", "SENT", "CHAT")

    /** Le mail da leggere per pacchi, spese e scadenze arrivate dopo [sinceMs]. */
    fun scanQuery(sinceMs: Long): String =
        "after:${sinceMs / 1000} -category:promotions -category:social -category:forums -in:chats -in:sent -in:drafts"

    /** Le mail che contano: importanti per Gmail, non lette, in arrivo negli ultimi giorni. */
    const val IMPORTANT_QUERY = "in:inbox is:important is:unread newer_than:3d -category:promotions -category:social -category:forums"

    fun ids(list: JSONObject): List<String> {
        val arr = list.optJSONArray("messages") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotEmpty() } }
    }

    fun message(json: JSONObject): MailMessage {
        val payload = json.optJSONObject("payload") ?: JSONObject()
        val headers = headers(payload)
        val from = headers["from"].orEmpty()
        val labels = json.optJSONArray("labelIds")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        return MailMessage(
            id = json.optString("id"),
            from = senderName(from),
            fromAddress = senderAddress(from),
            subject = headers["subject"].orEmpty().trim().ifEmpty { "(senza oggetto)" },
            snippet = unescape(json.optString("snippet")).trim(),
            at = json.optString("internalDate").toLongOrNull() ?: 0L,
            labels = labels,
            body = tidyBody(bodyText(payload)),
        )
    }

    /** Promozioni, social e spam non si leggono: lì le "scadenze" sono offerte. */
    fun worthScanning(m: MailMessage): Boolean = m.labels.none { it in noise }

    private fun headers(payload: JSONObject): Map<String, String> {
        val arr = payload.optJSONArray("headers") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .associate { it.optString("name").lowercase() to it.optString("value") }
    }

    /** Il testo semplice se c'è, altrimenti l'HTML ripulito. Le parti annidate (multipart) si cercano tutte. */
    fun bodyText(payload: JSONObject): String {
        findPart(payload, "text/plain")?.let { return it }
        findPart(payload, "text/html")?.let { return htmlToText(it) }
        return ""
    }

    private fun findPart(part: JSONObject, mime: String): String? {
        if (part.optString("mimeType").startsWith(mime, ignoreCase = true)) {
            val data = part.optJSONObject("body")?.optString("data").orEmpty()
            if (data.isNotEmpty()) return decode(data)
        }
        val parts = part.optJSONArray("parts") ?: return null
        for (i in 0 until parts.length()) {
            val child = parts.optJSONObject(i) ?: continue
            // Gli allegati non sono il corpo, anche se sono testo.
            if (child.optString("filename").isNotEmpty()) continue
            findPart(child, mime)?.let { return it }
        }
        return null
    }

    fun decode(data: String): String =
        runCatching { String(Base64.getUrlDecoder().decode(data.trimEnd('=').replace('+', '-').replace('/', '_')), Charsets.UTF_8) }.getOrDefault("")

    private val blockTags = Regex("(?i)<\\s*(?:br|/p|/div|/tr|/li|/h[1-6]|/table|hr)[^>]*>")
    private val cellTags = Regex("(?i)<\\s*/t[dh]\\s*>")
    private val hidden = Regex("(?is)<(style|script|head|title)[^>]*>.*?</\\1\\s*>")
    private val comments = Regex("(?s)<!--.*?-->")
    private val anyTag = Regex("<[^>]+>")

    fun htmlToText(html: String): String = unescape(
        html.replace(comments, " ")
            .replace(hidden, " ")
            .replace(blockTags, "\n")
            .replace(cellTags, " ")
            .replace(anyTag, " ")
    )

    private val entities = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&euro;" to "€", "&agrave;" to "à", "&egrave;" to "è", "&eacute;" to "é", "&igrave;" to "ì", "&ograve;" to "ò", "&ugrave;" to "ù",
        "&rsquo;" to "’", "&lsquo;" to "‘", "&ldquo;" to "“", "&rdquo;" to "”", "&hellip;" to "…", "&ndash;" to "–", "&mdash;" to "—",
    )
    private val numeric = Regex("&#(x?)([0-9a-fA-F]{1,6});")

    fun unescape(s: String): String {
        var out = s
        entities.forEach { (k, v) -> out = out.replace(k, v, ignoreCase = true) }
        return numeric.replace(out) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
        }
    }

    /** Si ferma alla risposta citata e toglie le righe vuote ripetute: resta la mail vera. */
    fun tidyBody(raw: String): String {
        val lines = raw.replace("\r", "").replace(' ', ' ').lines()
        val kept = mutableListOf<String>()
        for (line in lines) {
            val t = line.replace(Regex("[ \\t]+"), " ").trim()
            if (t.startsWith(">")) continue
            if (Regex("^(?:Il giorno .{4,120} ha scritto:|On .{4,120} wrote:|-----Original Message-----|-------- Messaggio originale --------)$").matches(t)) break
            if (t.isEmpty() && kept.lastOrNull()?.isEmpty() != false) continue
            kept += t
        }
        return kept.joinToString("\n").trim()
    }

    /** "Amazon.it" <conferma@amazon.it> diventa Amazon.it; senza nome resta l'indirizzo. */
    fun senderName(from: String): String {
        val name = from.substringBefore('<').trim().trim('"', '\'', ' ')
        if (name.isNotEmpty() && '@' !in name) return name
        return senderAddress(from).substringBefore('@').ifEmpty { from.trim() }
    }

    fun senderAddress(from: String): String =
        Regex("<([^>]+)>").find(from)?.groupValues?.get(1)?.trim() ?: from.trim().takeIf { '@' in it }.orEmpty()

    /**
     * La mail come messaggio per l'estrattore delle notifiche: l'oggetto in prima riga (diventa il titolo
     * del promemoria) e solo l'inizio del corpo, dove stanno codice, importo e data; i piè di pagina portano solo rumore.
     */
    fun incoming(m: MailMessage, zone: ZoneId = ZoneId.systemDefault()): Incoming {
        val body = m.body.ifEmpty { m.snippet }.take(BODY_CHARS)
        return Incoming(
            app = m.from.ifEmpty { "Gmail" },
            kind = SourceKind.MAIL,
            title = m.subject,
            text = m.subject + "\n" + body,
            at = LocalDateTime.ofInstant(Instant.ofEpochMilli(m.at), zone),
        )
    }

    const val BODY_CHARS = 1500
}
