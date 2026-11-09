package com.platinum20.dashboard.mail

import java.nio.charset.Charset
import java.util.Base64

/**
 * Le mail grezze (RFC 822) come arrivano da IMAP, ridotte a intestazioni e testo leggibile.
 * Il messaggio è una stringa ISO-8859-1: ogni carattere è un byte, il charset vero si applica alla fine.
 * Funzioni pure, provate nei test; tollera mail tagliate (si scarica solo l'inizio).
 */
object MimeParse {
    /** Intestazioni in minuscolo, righe ripiegate unite, la prima vince. Valori già decodificati (=?UTF-8?…). */
    fun headers(raw: String): Map<String, String> {
        val head = split(raw).first
        val out = linkedMapOf<String, String>()
        var name: String? = null
        val value = StringBuilder()
        fun flush() {
            name?.let { n -> if (n !in out) out[n] = decodeWords(value.toString().trim()) }
        }
        for (line in head.split('\n')) {
            val l = line.removeSuffix("\r")
            if (l.isEmpty()) continue
            if ((l[0] == ' ' || l[0] == '\t') && name != null) {
                value.append(' ').append(l.trim())
                continue
            }
            flush()
            val colon = l.indexOf(':')
            if (colon <= 0) { name = null; value.clear(); continue }
            name = l.substring(0, colon).trim().lowercase()
            value.clear().append(l.substring(colon + 1).trim())
        }
        flush()
        return out
    }

    /** Il testo semplice se c'è, altrimenti l'HTML ripulito; gli allegati non sono il corpo. */
    fun bodyText(raw: String): String {
        val parts = mutableListOf<Pair<String, String>>()
        collect(raw, parts, depth = 0)
        parts.firstOrNull { it.first == "text/plain" && it.second.isNotBlank() }?.let { return it.second }
        parts.firstOrNull { it.first == "text/html" }?.let { return MailParse.htmlToText(it.second) }
        return ""
    }

    /** L'HTML della mail così com'è (per le immagini), null se non c'è. */
    fun htmlBody(raw: String): String? {
        val parts = mutableListOf<Pair<String, String>>()
        collect(raw, parts, depth = 0)
        return parts.firstOrNull { it.first == "text/html" }?.second
    }

    private fun collect(entity: String, out: MutableList<Pair<String, String>>, depth: Int) {
        if (depth > 6) return
        val headers = headers(entity)
        val (type, params) = contentType(headers["content-type"])
        val body = split(entity).second
        if (type.startsWith("multipart/")) {
            val boundary = params["boundary"] ?: return
            for (child in parts(body, boundary)) collect(child, out, depth + 1)
            return
        }
        if (type != "text/plain" && type != "text/html") return
        val disposition = headers["content-disposition"].orEmpty().lowercase()
        if (disposition.startsWith("attachment") || "filename" in disposition) return
        val bytes = transferDecode(body, headers["content-transfer-encoding"])
        out += type to charset(bytes, params["charset"])
    }

    /** Un allegato o un'immagine della mail: tipo in minuscolo, nome del file (se c'è) e byte veri. */
    class Part(val type: String, val name: String, val bytes: ByteArray)

    /**
     * Le immagini e i PDF della mail, allegati o dentro il testo (il QR di un biglietto sta in uno dei due).
     * L'HTML delle mail resta fuori: si legge con [bodyText].
     */
    fun binaryParts(raw: String): List<Part> {
        val out = mutableListOf<Part>()
        collectBinary(raw, out, depth = 0)
        return out
    }

    private fun collectBinary(entity: String, out: MutableList<Part>, depth: Int) {
        if (depth > 6) return
        val headers = headers(entity)
        val (type, params) = contentType(headers["content-type"])
        val body = split(entity).second
        if (type.startsWith("multipart/")) {
            val boundary = params["boundary"] ?: return
            for (child in parts(body, boundary)) collectBinary(child, out, depth + 1)
            return
        }
        val name = params["name"] ?: Regex("filename\\*?=\"?([^\";]+)", RegexOption.IGNORE_CASE)
            .find(headers["content-disposition"].orEmpty())?.groupValues?.get(1).orEmpty()
        // Molti mittenti mandano il PDF come "application/octet-stream": conta anche il nome.
        val pdf = type == "application/pdf" || (type == "application/octet-stream" && name.lowercase().endsWith(".pdf"))
        if (!type.startsWith("image/") && !pdf) return
        val bytes = transferDecode(body, headers["content-transfer-encoding"])
        if (bytes.isNotEmpty()) out += Part(if (pdf) "application/pdf" else type, name, bytes)
    }

    /** Le parti tra i separatori "--boundary"; l'ultima può mancare della chiusura se la mail è tagliata. */
    fun parts(body: String, boundary: String): List<String> {
        val marker = "--$boundary"
        val out = mutableListOf<String>()
        var current: StringBuilder? = null
        for (line in body.split('\n')) {
            val l = line.removeSuffix("\r")
            val t = l.trimEnd()
            if (t == marker || t == "$marker--") {
                current?.let { out += it.toString() }
                if (t != marker) return out
                current = StringBuilder()
                continue
            }
            current?.append(l)?.append('\n')
        }
        current?.let { out += it.toString() }
        return out
    }

    /** "text/html; charset=\"utf-8\"" → tipo in minuscolo e parametri. */
    fun contentType(value: String?): Pair<String, Map<String, String>> {
        if (value.isNullOrBlank()) return "text/plain" to emptyMap()
        val pieces = value.split(';')
        val params = pieces.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().lowercase() to p.substring(eq + 1).trim().trim('"')
        }.toMap()
        return pieces[0].trim().lowercase() to params
    }

    private fun split(raw: String): Pair<String, String> {
        val crlf = raw.indexOf("\r\n\r\n")
        val lf = raw.indexOf("\n\n")
        return when {
            crlf >= 0 && (lf < 0 || crlf <= lf) -> raw.substring(0, crlf) to raw.substring(crlf + 4)
            lf >= 0 -> raw.substring(0, lf) to raw.substring(lf + 2)
            else -> raw to ""
        }
    }

    fun transferDecode(body: String, encoding: String?): ByteArray = when (encoding?.trim()?.lowercase()) {
        "base64" -> {
            val clean = body.filter { (it in 'A'..'Z') || (it in 'a'..'z') || (it in '0'..'9') || it == '+' || it == '/' || it == '=' }
            // Mail tagliata: si buttano gli ultimi caratteri spaiati invece di perdere tutto.
            runCatching { Base64.getDecoder().decode(clean.substring(0, clean.length - clean.length % 4)) }.getOrDefault(ByteArray(0))
        }
        "quoted-printable" -> quotedPrintable(body, underscoreSpace = false)
        else -> body.toByteArray(Charsets.ISO_8859_1)
    }

    fun quotedPrintable(s: String, underscoreSpace: Boolean): ByteArray {
        val out = java.io.ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '=' && i + 1 < s.length && (s[i + 1] == '\n' || s[i + 1] == '\r') -> {
                    i += if (s[i + 1] == '\r' && i + 2 < s.length && s[i + 2] == '\n') 3 else 2
                    continue
                }
                c == '=' && i + 2 < s.length && s.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                    out.write(s.substring(i + 1, i + 3).toInt(16))
                    i += 3
                    continue
                }
                c == '_' && underscoreSpace -> out.write(' '.code)
                else -> out.write(c.code and 0xFF)
            }
            i++
        }
        return out.toByteArray()
    }

    private fun charset(bytes: ByteArray, name: String?): String {
        val cs = runCatching { Charset.forName(name?.trim()?.ifEmpty { null } ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, cs)
    }

    private val encodedWord = Regex("=\\?([^?]+)\\?([bBqQ])\\?([^?]*)\\?=")
    private val betweenWords = Regex("(\\?=)\\s+(=\\?)")

    /** Intestazioni come "=?UTF-8?Q?Il_tuo_ordine_=C3=A8_partito?=": testo vero, parole codificate attaccate. */
    fun decodeWords(value: String): String {
        val joined = value.replace(betweenWords, "$1$2")
        val decoded = encodedWord.replace(joined) { m ->
            val (cs, enc, text) = m.destructured
            val bytes = if (enc.equals("B", true)) {
                runCatching { Base64.getDecoder().decode(text) }.getOrNull()
            } else quotedPrintable(text, underscoreSpace = true)
            bytes?.let { charset(it, cs.substringBefore('*')) } ?: m.value
        }
        // Intestazioni senza codifica ma in UTF-8 crudo: i byte vanno riletti.
        return if (decoded.any { it.code in 0x80..0xFF } && decoded.all { it.code <= 0xFF }) {
            val bytes = decoded.toByteArray(Charsets.ISO_8859_1)
            val utf = String(bytes, Charsets.UTF_8)
            if ('�' in utf) decoded else utf
        } else decoded
    }
}
