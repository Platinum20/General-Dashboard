package com.platinum20.dashboard.adblock

/**
 * Un filtro di rete: "||ads.example.com^$third-party", "/banner/(qualsiasi)$image", "@@||example.com/ok.js", ...
 * Sintassi di Adblock Plus con le estensioni di uBlock Origin che hanno senso in una WebView.
 */
internal class NetworkFilter private constructor(
    val exception: Boolean,
    val important: Boolean,
    private val matchCase: Boolean,
    private val pattern: String,
    private val regex: Regex?,
    private val hostAnchor: Boolean,
    private val leftAnchor: Boolean,
    private val rightAnchor: Boolean,
    private val typeMask: Int,
    val party: Int,
    val domains: Array<String>?,
    private val notDomains: Array<String>?,
    private val toDomains: Array<String>?,
    private val notToDomains: Array<String>?,
    private val denyAllow: Array<String>?,
    /** Per le eccezioni di pagina ($document, $elemhide, ...): quali parti del blocco spegnere. */
    val pageFlags: Int,
) {
    /** Parola che deve comparire nell'indirizzo, per l'indice. null se il filtro va provato sempre. */
    val token: String? = if (regex != null) regexToken(regex.pattern) else pickToken()

    /** Per "||dominio^" senza altro: il dominio, da mettere nell'insieme dei domini bloccati. */
    val pureHost: String? = if (hostAnchor && regex == null && pattern.endsWith('^') &&
        pattern.length > 1 && pattern.dropLast(1).all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }
    ) pattern.dropLast(1) else null

    private val firstLiteral: String = pattern.takeWhile { it != '*' && it != '^' }

    override fun toString() = regex?.let { "/${it.pattern}/" } ?: ((if (hostAnchor) "||" else if (leftAnchor) "|" else "") + pattern)

    fun isPlainHostRule() = !exception && !important && typeMask == 0 && domains == null && notDomains == null &&
        toDomains == null && notToDomains == null && denyAllow == null && party != PARTY_FIRST && pageFlags == 0

    fun matches(r: Request): Boolean {
        if (pageFlags == 0) {
            if (typeMask != 0) {
                if (r.type == RequestType.UNKNOWN) {
                    if (typeMask and RequestType.UNKNOWN_MASK == 0) return false
                } else if (typeMask and r.type.bit == 0) {
                    return false
                }
            }
            if (party == PARTY_THIRD && !r.thirdParty) return false
            if (party == PARTY_FIRST && (r.thirdParty || r.pageHost.isEmpty())) return false
        }
        if (domains != null && (r.pageHost.isEmpty() || domains.none { Domains.matches(r.pageHost, it) })) return false
        if (notDomains != null && r.pageHost.isNotEmpty() && notDomains.any { Domains.matches(r.pageHost, it) }) return false
        if (toDomains != null && toDomains.none { Domains.matches(r.host, it) }) return false
        if (notToDomains != null && notToDomains.any { Domains.matches(r.host, it) }) return false
        if (denyAllow != null && denyAllow.any { Domains.matches(r.host, it) }) return false
        return matchesUrl(r)
    }

    private fun matchesUrl(r: Request): Boolean {
        val url = if (matchCase) r.url else r.urlLower
        if (regex != null) return regex.containsMatchIn(url)
        if (hostAnchor) {
            // Il filtro può iniziare all'inizio del dominio o di un suo sottodominio.
            var p = r.hostStart
            while (p < r.hostEnd) {
                if (glob(url, p)) return true
                val dot = url.indexOf('.', p)
                if (dot < 0 || dot >= r.hostEnd - 1) return false
                p = dot + 1
            }
            return false
        }
        if (leftAnchor) return glob(url, 0)
        if (pattern.isEmpty()) return true
        if (firstLiteral.isEmpty()) {
            for (start in url.indices) if (glob(url, start)) return true
            return false
        }
        var from = 0
        while (true) {
            val idx = url.indexOf(firstLiteral, from)
            if (idx < 0) return false
            if (glob(url, idx)) return true
            from = idx + 1
        }
    }

    /** Confronto con * (qualsiasi sequenza) e ^ (separatore o fine dell'indirizzo). */
    private fun glob(url: String, start: Int): Boolean {
        val p = pattern
        var u = start
        var i = 0
        var starP = -1
        var starU = 0
        while (true) {
            if (i == p.length) {
                if (!rightAnchor || u == url.length) return true
            } else {
                val c = p[i]
                if (c == '*') {
                    starP = i
                    i++
                    starU = u
                    continue
                }
                if (u < url.length) {
                    val uc = url[u]
                    if (c == uc || (c == '^' && isSeparator(uc))) {
                        i++
                        u++
                        continue
                    }
                } else if (c == '^') {
                    i++
                    continue
                }
            }
            if (starP < 0 || starU >= url.length) return false
            starU++
            u = starU
            i = starP + 1
        }
    }

    private fun pickToken(): String? {
        var best: String? = null
        var bestScore = 0
        var i = 0
        val p = pattern
        while (i < p.length) {
            if (!Request.isTokenChar(p[i])) {
                i++
                continue
            }
            val start = i
            while (i < p.length && Request.isTokenChar(p[i])) i++
            val end = i
            // La parola deve essere intera anche nell'indirizzo: non attaccata a un * né tagliata dai bordi.
            val leftOk = if (start == 0) hostAnchor || leftAnchor else p[start - 1] != '*'
            val rightOk = if (end == p.length) rightAnchor else p[end] != '*'
            if (!leftOk || !rightOk) continue
            val token = p.substring(start, end)
            val score = if (token in commonTokens) 1 else token.length + 1
            if (score > bestScore) {
                best = token
                bestScore = score
            }
        }
        return best
    }

    companion object {
        /**
         * Una parola che compare per forza negli indirizzi che combaciano con l'espressione regolare,
         * delimitata da caratteri fissi (es. "locopelis" in ^https:\/\/www\.locopelis\.com\/). null se non c'è.
         */
        fun regexToken(src: String): String? {
            // Unità: un carattere fisso, START/END (inizio e fine dell'indirizzo) o UNKNOWN (qualsiasi cosa).
            val units = ArrayList<Char>()
            val unknown = '\u0000'
            val start = '\u0001'
            val end = '\u0002'
            var depth = 0
            var i = 0
            while (i < src.length) {
                val c = src[i]
                when {
                    c == '\\' && i + 1 < src.length -> {
                        val e = src[i + 1]
                        units += if (e.isLetterOrDigit()) unknown else e
                        i += 2
                        continue
                    }
                    c == '[' -> {
                        var j = i + 1
                        while (j < src.length && src[j] != ']') j += if (src[j] == '\\') 2 else 1
                        units += unknown
                        i = j + 1
                        continue
                    }
                    c == '(' -> { depth++; units += unknown }
                    c == ')' -> { depth--; units += unknown }
                    c == '|' -> if (depth == 0) return null else units += unknown
                    c == '^' && i == 0 -> units += start
                    c == '$' && i == src.length - 1 -> units += end
                    c == '?' || c == '*' || c == '+' -> { if (units.isNotEmpty()) units[units.size - 1] = unknown; units += unknown }
                    c == '{' -> {
                        if (units.isNotEmpty()) units[units.size - 1] = unknown
                        val close = src.indexOf('}', i)
                        units += unknown
                        i = if (close < 0) src.length else close + 1
                        continue
                    }
                    c == '.' || c == '^' || c == '$' -> units += unknown
                    depth > 0 -> units += unknown
                    else -> units += c.lowercaseChar()
                }
                i++
            }
            var best: String? = null
            var k = 0
            while (k < units.size) {
                if (!Request.isTokenChar(units[k])) {
                    k++
                    continue
                }
                val s = k
                while (k < units.size && Request.isTokenChar(units[k])) k++
                val before = if (s == 0) unknown else units[s - 1]
                val after = if (k == units.size) unknown else units[k]
                fun boundary(u: Char) = u == start || u == end || (u != unknown && !Request.isTokenChar(u))
                if (!boundary(before) || !boundary(after)) continue
                val token = String(units.subList(s, k).toCharArray())
                if (best == null || (best in commonTokens && token !in commonTokens) ||
                    (token.length > best.length && (token !in commonTokens || best in commonTokens))
                ) best = token
            }
            return best
        }

        const val PARTY_ANY = 0
        const val PARTY_FIRST = 1
        const val PARTY_THIRD = 2

        const val PAGE_DOCUMENT = 1
        const val PAGE_ELEMHIDE = 2
        const val PAGE_GENERICHIDE = 4
        const val PAGE_SPECIFICHIDE = 8

        /** Tutti i tipi tranne pagina principale e popup (da SUBDOCUMENT a OTHER). */
        private const val ALL_TYPES = 2046

        private val commonTokens = setOf(
            "http", "https", "www", "com", "net", "org", "js", "html", "php", "jpg", "png", "gif", "css", "static",
            "cdn", "assets", "images", "img", "media", "api", "it", "de", "fr", "uk",
        )

        private val typeBits = mapOf(
            "script" to RequestType.SCRIPT.bit, "image" to RequestType.IMAGE.bit,
            "stylesheet" to RequestType.STYLESHEET.bit, "css" to RequestType.STYLESHEET.bit,
            "xmlhttprequest" to RequestType.XHR.bit, "xhr" to RequestType.XHR.bit,
            "subdocument" to RequestType.SUBDOCUMENT.bit, "frame" to RequestType.SUBDOCUMENT.bit,
            "media" to RequestType.MEDIA.bit, "font" to RequestType.FONT.bit,
            "ping" to RequestType.PING.bit, "beacon" to RequestType.PING.bit,
            "websocket" to RequestType.WEBSOCKET.bit, "other" to RequestType.OTHER.bit,
            "object" to RequestType.OTHER.bit, "object-subrequest" to RequestType.OTHER.bit,
            "document" to RequestType.DOCUMENT.bit, "doc" to RequestType.DOCUMENT.bit,
            "popup" to RequestType.POPUP.bit,
        )

        /** Opzioni che non cambiano cosa si blocca, o che una WebView non può applicare ma il cui filtro resta valido. */
        private val ignoredOptions = setOf("reason", "_", "mp4", "empty")

        /** Se la riga è un $badfilter, il filtro che annulla (la stessa riga senza "badfilter"). */
        fun badFilterTarget(line: String): String? {
            val trimmed = line.trim()
            val dollar = optionsStart(trimmed)
            if (dollar < 0) return null
            val options = trimmed.substring(dollar + 1).split(',')
            if (options.none { it.trim() == "badfilter" }) return null
            val rest = options.filter { it.trim() != "badfilter" }
            return trimmed.substring(0, dollar) + if (rest.isEmpty()) "" else "$" + rest.joinToString(",")
        }

        private fun optionsStart(s: String): Int {
            // Espressione regolare: le opzioni iniziano al primo "/$" seguito dal nome di un'opzione
            // ("/re/$script"); "/re$/" e "/a\/$/" non hanno opzioni.
            if (s.length > 2 && s.startsWith('/')) {
                var from = 1
                while (true) {
                    val idx = s.indexOf("/$", from)
                    if (idx < 0) break
                    val next = s.getOrNull(idx + 2)
                    if (next != null && (next.isLetter() || next == '~' || next == '_')) return idx + 1
                    from = idx + 1
                }
                if (s.endsWith('/')) return -1
            }
            return s.lastIndexOf('$')
        }

        fun parse(line: String): NetworkFilter? {
            var s = line
            var exception = false
            if (s.startsWith("@@")) {
                exception = true
                s = s.substring(2)
            }
            var optionsRaw: String? = null
            val dollar = optionsStart(s)
            if (dollar >= 0) {
                optionsRaw = s.substring(dollar + 1)
                s = s.substring(0, dollar)
            }

            var important = false
            var matchCase = false
            var typeMask = 0
            var notTypeMask = 0
            var party = PARTY_ANY
            var domains: Array<String>? = null
            var notDomains: Array<String>? = null
            var toDomains: Array<String>? = null
            var notToDomains: Array<String>? = null
            var denyAllow: Array<String>? = null
            var pageFlags = 0

            if (optionsRaw != null) {
                for (rawOption in optionsRaw.split(',')) {
                    val option = rawOption.trim()
                    if (option.isEmpty()) continue
                    val negated = option.startsWith('~')
                    val name = option.removePrefix("~").substringBefore('=').lowercase()
                    val value = option.substringAfter('=', "")
                    when {
                        name == "third-party" || name == "3p" || name == "strict3p" ->
                            party = if (negated) PARTY_FIRST else PARTY_THIRD
                        name == "first-party" || name == "1p" || name == "strict1p" ->
                            party = if (negated) PARTY_THIRD else PARTY_FIRST
                        name == "important" -> important = true
                        name == "match-case" -> matchCase = true
                        name == "all" -> typeMask = typeMask or ALL_TYPES or RequestType.DOCUMENT.bit
                        name in typeBits -> if (negated) notTypeMask = notTypeMask or typeBits.getValue(name)
                        else typeMask = typeMask or typeBits.getValue(name)
                        name == "domain" || name == "from" -> {
                            val (inc, exc) = splitDomains(value) ?: return null
                            domains = inc
                            notDomains = exc
                        }
                        name == "to" -> {
                            val (inc, exc) = splitDomains(value) ?: return null
                            toDomains = inc
                            notToDomains = exc
                        }
                        name == "denyallow" -> denyAllow = splitDomains(value)?.first ?: return null
                        name == "redirect" -> if (exception) return null
                        name == "elemhide" || name == "ehide" -> pageFlags = pageFlags or PAGE_ELEMHIDE
                        name == "generichide" || name == "ghide" -> pageFlags = pageFlags or PAGE_GENERICHIDE
                        name == "specifichide" || name == "shide" -> pageFlags = pageFlags or PAGE_SPECIFICHIDE
                        name == "badfilter" -> return null
                        name in ignoredOptions -> Unit
                        // csp, removeparam, header, redirect-rule, ...: non applicabili in una WebView,
                        // il filtro si scarta come farebbe un blocco che non li conosce.
                        else -> return null
                    }
                }
            }

            // $document sulle eccezioni spegne il blocco per tutta la pagina; sui blocchi non serve
            // (la pagina principale non si blocca mai), resta solo il resto dei tipi.
            if (typeMask and RequestType.DOCUMENT.bit != 0) {
                if (exception && typeMask == RequestType.DOCUMENT.bit) pageFlags = pageFlags or PAGE_DOCUMENT
                typeMask = typeMask and RequestType.DOCUMENT.bit.inv()
                if (typeMask == 0 && pageFlags == 0) return null
            }
            if (typeMask and RequestType.POPUP.bit != 0) {
                typeMask = typeMask and RequestType.POPUP.bit.inv()
                if (typeMask == 0 && pageFlags == 0) return null
            }
            if (notTypeMask != 0) typeMask = (if (typeMask == 0) ALL_TYPES else typeMask) and notTypeMask.inv()
            if (pageFlags != 0 && !exception) return null

            var hostAnchor = false
            var leftAnchor = false
            var rightAnchor = false
            var regex: Regex? = null
            if (s.length > 2 && s.startsWith('/') && s.endsWith('/')) {
                regex = runCatching {
                    Regex(s.substring(1, s.length - 1), if (matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
                }.getOrNull() ?: return null
            } else {
                if (s.startsWith("||")) {
                    hostAnchor = true
                    s = s.substring(2)
                } else if (s.startsWith('|')) {
                    leftAnchor = true
                    s = s.substring(1)
                }
                if (s.endsWith('|')) {
                    rightAnchor = true
                    s = s.dropLast(1)
                }
                if (!hostAnchor && !leftAnchor) s = s.trimStart('*')
                if (!rightAnchor) s = s.trimEnd('*')
                if (!matchCase) s = s.lowercase()
            }
            // Un filtro vuoto senza condizioni bloccherebbe tutto.
            if (regex == null && s.isEmpty() && domains == null && toDomains == null && pageFlags == 0) return null

            return NetworkFilter(
                exception, important, matchCase, s, regex, hostAnchor, leftAnchor, rightAnchor, typeMask, party,
                domains, notDomains, toDomains, notToDomains, denyAllow, pageFlags,
            )
        }

        /** "a.com|~b.a.com" -> ([a.com], [b.a.com]). null se ci sono domini come espressioni regolari. */
        private fun splitDomains(value: String): Pair<Array<String>?, Array<String>?>? {
            val include = ArrayList<String>()
            val exclude = ArrayList<String>()
            for (raw in value.split('|')) {
                val d = raw.trim().lowercase()
                if (d.isEmpty()) continue
                if (d.startsWith('/') || d.startsWith("~/")) return null
                if (d.startsWith('~')) exclude += d.substring(1) else include += d
            }
            return (include.takeIf { it.isNotEmpty() }?.toTypedArray()) to (exclude.takeIf { it.isNotEmpty() }?.toTypedArray())
        }

        /** Carattere separatore per ^: tutto tranne lettere, cifre e _ - . % */
        fun isSeparator(c: Char): Boolean =
            !(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-' || c == '.' || c == '%')
    }
}
