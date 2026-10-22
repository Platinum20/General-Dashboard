package com.platinum20.dashboard.adblock

/**
 * Filtri cosmetici: "##.banner" (generici), "sito.it##.box-adv" (specifici), "sito.it#@#.ad" (eccezioni),
 * filtri procedurali di uBlock Origin ("##div:has-text(Sponsor)") e scriptlet ("sito.it##+js(set, x, false)").
 */
internal class Cosmetic private constructor(
    private val genericByKey: HashMap<String, ArrayList<String>>,
    private val highlyGeneric: List<String>,
    private val specific: HashMap<String, ArrayList<Entry>>,
    private val exceptions: HashMap<String, HashSet<String>>,
    private val globalScriptlets: List<Entry>,
) {

    class Entry(
        val kind: Int,
        val selector: String,
        /** Dichiarazioni CSS per i filtri :style, argomenti per gli scriptlet. */
        val style: String?,
        val args: List<String>?,
        val notDomains: Array<String>?,
        /** Chiave con cui un'eccezione #@# annulla questo filtro. */
        val key: String,
    )

    fun forHost(host: String, specific: Boolean, generic: Boolean): PageFilters {
        val keys = Domains.lookupKeys(host)
        val excepted = HashSet<String>()
        for (k in keys) exceptions[k]?.let { excepted += it }
        val noScriptlets = SCRIPTLET_KEY_PREFIX in excepted

        val hide = ArrayList<String>()
        val styles = ArrayList<Pair<String, String>>()
        val procedural = ArrayList<String>()
        val scriptlets = ArrayList<List<String>>()
        fun take(entry: Entry) {
            if (entry.key in excepted) return
            if (entry.notDomains != null && entry.notDomains.any { Domains.matches(host, it) }) return
            when (entry.kind) {
                HIDE -> if (specific) hide += entry.selector
                STYLE -> if (specific) styles += entry.selector to entry.style!!
                PROCEDURAL -> if (specific) procedural += entry.selector
                SCRIPTLET -> if (!noScriptlets) scriptlets += entry.args!!
            }
        }
        for (k in keys) specificFor(k)?.forEach(::take)
        globalScriptlets.forEach(::take)

        val high = when {
            !generic -> emptyList()
            excepted.isEmpty() -> highlyGeneric
            else -> highlyGeneric.filter { it !in excepted }
        }
        return PageFilters(hide.distinct(), styles, procedural.distinct(), scriptlets.distinctBy { it }, high, generic)
    }

    private fun specificFor(key: String): List<Entry>? = specific[key]

    fun generic(host: String, keys: Collection<String>): List<String> {
        if (genericByKey.isEmpty()) return emptyList()
        var excepted: HashSet<String>? = null
        val out = ArrayList<String>()
        for (key in keys) {
            val selectors = genericByKey[key] ?: continue
            if (excepted == null) {
                excepted = HashSet()
                for (k in Domains.lookupKeys(host)) exceptions[k]?.let { excepted += it }
            }
            for (s in selectors) if (s !in excepted) out += s
        }
        return out
    }

    class Builder {
        private val genericByKey = HashMap<String, ArrayList<String>>()
        private val highlyGeneric = LinkedHashSet<String>()
        private val specific = HashMap<String, ArrayList<Entry>>()
        private val exceptions = HashMap<String, HashSet<String>>()
        private val globalExceptions = HashSet<String>()
        private val globalScriptlets = ArrayList<Entry>()

        /** Aggiunge una riga cosmetica; false se non è supportata. */
        fun add(line: String, trusted: Boolean): Boolean {
            val (markerAt, marker) = findMarker(line) ?: return false
            if ('$' in marker || '%' in marker) return false
            val exception = '@' in marker
            val body = line.substring(markerAt + marker.length).trim()
            if (body.isEmpty() || body.startsWith('^')) return false

            val include = ArrayList<String>()
            val exclude = ArrayList<String>()
            val domainPart = line.substring(0, markerAt)
            if (domainPart.isNotBlank()) {
                for (raw in domainPart.split(',')) {
                    val d = raw.trim().lowercase()
                    if (d.isEmpty()) continue
                    if (d.startsWith('/') || d.startsWith("~/")) return false
                    if (d.startsWith('~')) exclude += d.substring(1) else include += d
                }
            }

            val entry = parseBody(body, trusted, exclude.takeIf { it.isNotEmpty() }?.toTypedArray()) ?: return false

            if (exception) {
                if (include.isEmpty()) globalExceptions += entry.key
                else include.forEach { exceptions.getOrPut(it) { HashSet() } += entry.key }
                return true
            }
            if (include.isEmpty()) {
                when (entry.kind) {
                    HIDE -> {
                        // "~sito.it##.ad" è un filtro generico con un'eccezione per sito.it.
                        exclude.forEach { exceptions.getOrPut(it) { HashSet() } += entry.key }
                        val key = genericKey(entry.selector)
                        if (key == null) highlyGeneric += entry.selector
                        else genericByKey.getOrPut(key) { ArrayList(1) } += entry.selector
                    }
                    SCRIPTLET -> globalScriptlets += entry
                    // Procedurali e :style generici: troppo costosi su ogni pagina, uBO stesso li sconsiglia.
                    else -> return false
                }
                return true
            }
            include.forEach { specific.getOrPut(it) { ArrayList(2) } += entry }
            return true
        }

        fun build(): Cosmetic {
            if (globalExceptions.isNotEmpty()) {
                highlyGeneric.removeAll(globalExceptions)
                genericByKey.values.forEach { it.removeAll(globalExceptions) }
                specific.values.forEach { list -> list.removeAll { it.key in globalExceptions } }
                globalScriptlets.removeAll { it.key in globalExceptions }
            }
            return Cosmetic(genericByKey, highlyGeneric.toList(), specific, exceptions, globalScriptlets)
        }

        private fun parseBody(body: String, trusted: Boolean, notDomains: Array<String>?): Entry? {
            if (body.startsWith("+js(") && body.endsWith(")")) {
                val args = parseScriptletArgs(body.substring(4, body.length - 1))
                if (args.isEmpty() || args[0].isEmpty()) {
                    // "#@#+js()" spegne tutti gli scriptlet del sito.
                    return Entry(SCRIPTLET, "", null, emptyList(), notDomains, SCRIPTLET_KEY_PREFIX)
                }
                val name = args[0].removeSuffix(".js")
                if (!trusted && (name.startsWith("trusted-") || name in trustedOnly)) return null
                val normalized = listOf(name) + args.drop(1)
                return Entry(SCRIPTLET, "", null, normalized, notDomains, scriptletKey(normalized))
            }
            if ('{' in body || '}' in body) return null
            val styleMatch = styleRegex.matchEntire(body)
            if (styleMatch != null) {
                val selector = styleMatch.groupValues[1].trim()
                val style = styleMatch.groupValues[2].trim()
                if (selector.isEmpty() || style.isEmpty() || isUnsafeStyle(style)) return null
                if (isProcedural(selector)) return Entry(PROCEDURAL, body, null, null, notDomains, body)
                return Entry(STYLE, selector, style, null, notDomains, body)
            }
            if (isProcedural(body)) return Entry(PROCEDURAL, body, null, null, notDomains, body)
            return Entry(HIDE, body, null, null, notDomains, body)
        }
    }

    companion object {
        const val HIDE = 0
        const val STYLE = 1
        const val PROCEDURAL = 2
        const val SCRIPTLET = 3
        private const val SCRIPTLET_KEY_PREFIX = "+js:"

        private val markers = listOf("#@?#", "#@$#", "#@%#", "#@#", "#?#", "#$#", "#%#", "##")
        private val styleRegex = Regex("""^(.*):style\((.*)\)$""")
        private val trustedOnly = setOf("rpnt", "replace-node-text", "rmnt-trusted")
        private val proceduralOps = listOf(
            ":has-text(", ":-abp-contains(", ":contains(", ":upward(", ":xpath(", ":matches-css(",
            ":matches-css-before(", ":matches-css-after(", ":min-text-length(", ":-abp-has(", ":matches-path(",
            ":watch-attr(", ":matches-attr(", ":matches-prop(", ":others(", ":if(", ":if-not(", ":remove()",
            ":nth-ancestor(", ":matches-media(", ":shadow(", ":remove-attr(", ":remove-class(",
        )

        fun isCosmetic(line: String): Boolean = findMarker(line) != null

        private fun findMarker(line: String): Pair<Int, String>? {
            var from = 0
            while (true) {
                val idx = line.indexOf('#', from)
                if (idx < 0) return null
                val marker = markers.firstOrNull { line.startsWith(it, idx) }
                if (marker != null) {
                    val prefix = line.substring(0, idx)
                    // Un filtro di rete con un # nell'indirizzo non è cosmetico.
                    if (prefix.any { it == '/' || it == '|' || it == '^' || it == '$' || it == '=' || it == ' ' } &&
                        !prefix.trimStart().startsWith("/")
                    ) return null
                    return idx to marker
                }
                from = idx + 1
            }
        }

        fun isProcedural(selector: String): Boolean = proceduralOps.any { selector.contains(it) }

        private fun isUnsafeStyle(style: String): Boolean {
            val s = style.lowercase()
            return "url(" in s || "expression" in s || "image-set" in s || "\\" in s
        }

        /** ".banner > div" -> ".banner": il filtro può valere solo se nella pagina c'è quella classe. */
        fun genericKey(selector: String): String? {
            if (selector.length < 2 || (selector[0] != '#' && selector[0] != '.')) return null
            var i = 1
            while (i < selector.length && (selector[i].isLetterOrDigit() || selector[i] == '-' || selector[i] == '_')) i++
            if (i == 1 || (i < selector.length && selector[i] == '\\')) return null
            if (hasTopLevelComma(selector)) return null
            return selector.substring(0, i)
        }

        private fun hasTopLevelComma(s: String): Boolean {
            var depth = 0
            var quote = 0.toChar()
            for (c in s) {
                when {
                    quote != 0.toChar() -> if (c == quote) quote = 0.toChar()
                    c == '"' || c == '\'' -> quote = c
                    c == '(' || c == '[' -> depth++
                    c == ')' || c == ']' -> depth--
                    c == ',' && depth == 0 -> return true
                }
            }
            return false
        }

        fun scriptletKey(args: List<String>) = SCRIPTLET_KEY_PREFIX + args.joinToString("\u0001")

        /** Argomenti separati da virgole; "\," è una virgola vera; tra apici le virgole non dividono. */
        fun parseScriptletArgs(inner: String): List<String> {
            val out = ArrayList<String>()
            val n = inner.length
            var i = 0
            while (true) {
                while (i < n && inner[i] == ' ') i++
                if (i < n && (inner[i] == '\'' || inner[i] == '"' || inner[i] == '`')) {
                    val q = inner[i]
                    val quoted = StringBuilder()
                    var j = i + 1
                    var closed = false
                    while (j < n) {
                        if (inner[j] == '\\' && j + 1 < n && inner[j + 1] == q) {
                            quoted.append(q)
                            j += 2
                            continue
                        }
                        if (inner[j] == q) {
                            closed = true
                            break
                        }
                        quoted.append(inner[j])
                        j++
                    }
                    if (closed) {
                        var k = j + 1
                        while (k < n && inner[k] == ' ') k++
                        if (k >= n) {
                            out += quoted.toString()
                            return out
                        }
                        if (inner[k] == ',') {
                            out += quoted.toString()
                            i = k + 1
                            continue
                        }
                    }
                    // Apice non chiuso o seguito da altro: è testo normale.
                }
                val sb = StringBuilder()
                while (i < n) {
                    val c = inner[i]
                    if (c == '\\' && i + 1 < n && inner[i + 1] == ',') {
                        sb.append(',')
                        i += 2
                        continue
                    }
                    if (c == ',') break
                    sb.append(c)
                    i++
                }
                out += sb.toString().trim()
                if (i >= n) return out
                i++
            }
        }
    }
}
