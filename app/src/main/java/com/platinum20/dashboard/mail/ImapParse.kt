package com.platinum20.dashboard.mail

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una risposta IMAP senza tag: il testo, con i letterali ({n} byte) tolti e messi in [literals];
 * nel testo al loro posto resta il segnaposto [mark].
 */
data class ImapLine(val text: String, val literals: List<String>) {
    companion object {
        fun mark(i: Int) = "\u0000$i\u0000"
    }
}

/** Lettura delle risposte IMAP di Gmail: funzioni pure, provate nei test. */
object ImapParse {
    /** Stringa IMAP tra virgolette: barre e virgolette vanno precedute da una barra. */
    fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /** "* SEARCH 4 9 12" → gli UID. */
    fun search(lines: List<ImapLine>): List<Long> = lines
        .filter { it.text.startsWith("* SEARCH", ignoreCase = true) }
        .flatMap { it.text.substring(8).trim().split(' ').mapNotNull { n -> n.toLongOrNull() } }

    /** La cartella "Tutti i messaggi" (nome tradotto: si riconosce da \All), altrimenti la Posta in arrivo. */
    fun allMailbox(lines: List<ImapLine>): String {
        val line = lines.firstOrNull { it.text.startsWith("* LIST", true) && "\\All" in it.text.substringBefore(')') }
            ?: return "INBOX"
        val name = line.literals.lastOrNull()?.takeIf { line.text.trimEnd().endsWith(ImapLine.mark(line.literals.size - 1)) }
        if (name != null) return quote(name)
        val rest = line.text.substringAfter(')').trim()
        // Dopo le opzioni: il separatore ("/" o NIL), poi il nome, già nella forma che il server vuole indietro.
        val afterSep = if (rest.startsWith("\"")) rest.substring(rest.indexOf('"', 1) + 1).trim() else rest.substringAfter(' ').trim()
        return afterSep.ifEmpty { "INBOX" }
    }

    /** Una risposta FETCH ridotta a quello che serve. */
    data class Fetched(
        val uid: Long,
        val gmailId: String,
        val labels: Set<String>,
        val at: Long,
        val raw: String?,
    )

    private val uidRe = Regex("\\bUID (\\d+)", RegexOption.IGNORE_CASE)
    private val msgIdRe = Regex("X-GM-MSGID (\\d+)", RegexOption.IGNORE_CASE)
    private val flagsRe = Regex("\\bFLAGS \\(([^)]*)\\)", RegexOption.IGNORE_CASE)
    private val labelsRe = Regex("X-GM-LABELS \\(([^)]*)\\)", RegexOption.IGNORE_CASE)
    private val dateRe = Regex("INTERNALDATE \"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val bodyRe = Regex("BODY\\[\\](?:<\\d+>)? \u0000(\\d+)\u0000", RegexOption.IGNORE_CASE)
    private val internalDate = DateTimeFormatter.ofPattern("d-MMM-yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun fetched(lines: List<ImapLine>): List<Fetched> = lines.filter { " FETCH " in it.text.uppercase() }.mapNotNull { line ->
        val t = line.text
        val uid = uidRe.find(t)?.groupValues?.get(1)?.toLongOrNull() ?: return@mapNotNull null
        val msgId = msgIdRe.find(t)?.groupValues?.get(1)?.toULongOrNull()?.toString(16).orEmpty()
        val flags = flagsRe.find(t)?.groupValues?.get(1).orEmpty()
        val gmLabels = labelsRe.find(t)?.groupValues?.get(1).orEmpty()
        val at = dateRe.find(t)?.groupValues?.get(1)?.trim()?.let { d ->
            runCatching { ZonedDateTime.parse(d, internalDate).toInstant().toEpochMilli() }.getOrNull()
        } ?: 0L
        val raw = bodyRe.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { line.literals.getOrNull(it) }
        Fetched(uid, msgId, labels(flags, gmLabels), at, raw)
    }

    /** Flag e etichette IMAP con i nomi delle etichette dell'API Gmail, così il resto non cambia. */
    fun labels(flags: String, gmLabels: String): Set<String> {
        val out = mutableSetOf<String>()
        if (!flags.contains("\\Seen", ignoreCase = true)) out += "UNREAD"
        val map = mapOf(
            "\\Important" to "IMPORTANT", "\\Inbox" to "INBOX", "\\Sent" to "SENT",
            "\\Draft" to "DRAFT", "\\Trash" to "TRASH", "\\Spam" to "SPAM", "\\Starred" to "STARRED",
        )
        map.forEach { (k, v) -> if (gmLabels.contains(k, ignoreCase = true)) out += v }
        return out
    }

    /** La mail scaricata come [MailMessage], con lo stesso id esadecimale dell'API Gmail. */
    fun message(f: Fetched): MailMessage? {
        val raw = f.raw ?: return null
        val h = MimeParse.headers(raw)
        val from = h["from"].orEmpty()
        val body = MailParse.tidyBody(MimeParse.bodyText(raw))
        return MailMessage(
            id = f.gmailId.ifEmpty { "imap${f.uid}" },
            from = MailParse.senderName(from),
            fromAddress = MailParse.senderAddress(from),
            subject = h["subject"].orEmpty().trim().ifEmpty { "(senza oggetto)" },
            snippet = body.replace('\n', ' ').take(160),
            at = f.at,
            labels = f.labels,
            body = body,
        )
    }

    /** Errore di accesso di Gmail: password per app sbagliata, revocata o IMAP spento. */
    fun authFailed(tagged: String): Boolean =
        "AUTHENTICATIONFAILED" in tagged.uppercase() || "Invalid credentials".lowercase() in tagged.lowercase() ||
            "Application-specific password required".lowercase() in tagged.lowercase()
}
