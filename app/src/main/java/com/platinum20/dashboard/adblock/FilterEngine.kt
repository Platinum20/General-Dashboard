package com.platinum20.dashboard.adblock

/**
 * Motore dei filtri nello stile di uBlock Origin, senza dipendenze da Android.
 *
 * Legge le liste nella sintassi Adblock Plus / uBlock Origin (EasyList, EasyPrivacy, uBO...) e risponde a tre domande:
 * - una richiesta di rete va bloccata? ([match])
 * - quali elementi della pagina vanno nascosti e quali scriptlet vanno eseguiti? ([pageFilters])
 * - quali filtri "generici" valgono per le classi e gli id trovati nella pagina? ([genericSelectors])
 *
 * Si costruisce con [Builder] (anche in un thread a parte) e poi si usa solo in lettura, da qualsiasi thread.
 */
class FilterEngine private constructor(
    private val blockIndex: TokenIndex,
    private val importantIndex: TokenIndex,
    private val exceptionIndex: TokenIndex,
    private val blockedHosts: HashSet<String>,
    private val blockedHostsThirdParty: HashSet<String>,
    private val pageExceptions: TokenIndex,
    private val cosmetic: Cosmetic,
    /** Numero di regole caricate, per le impostazioni. */
    val ruleCount: Int,
) {

    enum class Verdict { NONE, BLOCK, ALLOW }

    /**
     * Decide su una richiesta. [pageUrl] è la pagina che la fa (vuota se non si sa).
     * Le richieste della pagina principale non si bloccano mai: si bloccano solo i pezzi che carica.
     */
    fun match(url: String, pageUrl: String, type: RequestType): Verdict {
        val request = Request.parse(url, pageUrl, type) ?: return Verdict.NONE
        val page = pageFlags(pageUrl)
        if (page and NetworkFilter.PAGE_DOCUMENT != 0) return Verdict.NONE
        if (importantIndex.find(request) != null) return Verdict.BLOCK
        val blocked = hostBlocked(request) || blockIndex.find(request) != null
        if (!blocked) return Verdict.NONE
        return if (exceptionIndex.find(request) != null) Verdict.ALLOW else Verdict.BLOCK
    }

    private fun hostBlocked(request: Request): Boolean {
        var h = request.host
        while (true) {
            if (h in blockedHosts) return true
            if (request.thirdParty && h in blockedHostsThirdParty) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    internal fun stats() = "blocco: ${blockIndex.stats()}\nimportanti: ${importantIndex.stats()}\n" +
        "eccezioni: ${exceptionIndex.stats()}\ndomini: ${blockedHosts.size}+${blockedHostsThirdParty.size}, pagina: ${pageExceptions.stats()}"

    @Volatile
    private var lastPage: Pair<String, Int>? = null

    /** Eccezioni che valgono per tutta la pagina ($document, $elemhide, $generichide...). */
    fun pageFlags(pageUrl: String): Int {
        if (pageUrl.isEmpty()) return 0
        lastPage?.let { if (it.first == pageUrl) return it.second }
        val request = Request.parse(pageUrl, pageUrl, RequestType.DOCUMENT) ?: return 0
        var flags = 0
        pageExceptions.forEachMatch(request) { flags = flags or it.pageFlags }
        lastPage = pageUrl to flags
        return flags
    }

    /** Cosa fare dentro una pagina (o un riquadro) di [host]. */
    fun pageFilters(pageUrl: String, host: String): PageFilters {
        val flags = pageFlags(pageUrl)
        if (flags and (NetworkFilter.PAGE_DOCUMENT or NetworkFilter.PAGE_ELEMHIDE) != 0) {
            return PageFilters(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), generic = false)
        }
        return cosmetic.forHost(
            host.lowercase(),
            specific = flags and NetworkFilter.PAGE_SPECIFICHIDE == 0,
            generic = flags and NetworkFilter.PAGE_GENERICHIDE == 0,
        )
    }

    /** Filtri generici per le classi (".nome") e gli id ("#nome") presenti nella pagina. */
    fun genericSelectors(host: String, keys: Collection<String>): List<String> =
        cosmetic.generic(host.lowercase(), keys)

    class Builder {
        private val blockIndex = TokenIndex()
        private val importantIndex = TokenIndex()
        private val exceptionIndex = TokenIndex()
        private val blockedHosts = HashSet<String>()
        private val blockedHostsThirdParty = HashSet<String>()
        private val pageExceptions = TokenIndex()
        private val cosmetic = Cosmetic.Builder()
        private val badFilters = HashSet<String>()
        private val pending = ArrayList<Pair<String, Boolean>>()
        private var count = 0

        /**
         * Aggiunge una lista. [trusted] per le liste di uBlock Origin e per quella dell'app:
         * solo queste possono usare gli scriptlet "trusted-".
         */
        fun addList(text: String, trusted: Boolean = false): Builder {
            pending += text to trusted
            // I $badfilter annullano filtri di altre liste: vanno raccolti prima di tutto il resto.
            for (line in Preprocessor.lines(text)) {
                if (line.contains("badfilter")) NetworkFilter.badFilterTarget(line)?.let { badFilters += it }
            }
            return this
        }

        fun build(): FilterEngine {
            for ((text, trusted) in pending) {
                for (line in Preprocessor.lines(text)) addLine(line, trusted)
            }
            pending.clear()
            return FilterEngine(
                blockIndex, importantIndex, exceptionIndex, blockedHosts, blockedHostsThirdParty,
                pageExceptions, cosmetic.build(), count,
            )
        }

        private fun addLine(raw: String, trusted: Boolean) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') return
            if (Cosmetic.isCosmetic(line)) {
                if (cosmetic.add(line, trusted)) count++
                return
            }
            // Liste in formato "hosts" (0.0.0.0 dominio).
            if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
                val host = line.substringAfter(' ').trim().lowercase()
                if (host.isNotEmpty() && host != "localhost" && ' ' !in host) {
                    blockedHosts += host
                    count++
                }
                return
            }
            if (badFilters.isNotEmpty() && line in badFilters) return
            val filter = NetworkFilter.parse(line) ?: return
            count++
            when {
                filter.pageFlags != 0 -> pageExceptions.add(filter)
                filter.exception -> exceptionIndex.add(filter)
                filter.important -> importantIndex.add(filter)
                filter.pureHost != null && filter.isPlainHostRule() ->
                    if (filter.party == NetworkFilter.PARTY_THIRD) blockedHostsThirdParty += filter.pureHost
                    else blockedHosts += filter.pureHost
                else -> blockIndex.add(filter)
            }
        }
    }
}

/** Tipo della richiesta, per i filtri con $script, $image, ... */
enum class RequestType(val bit: Int) {
    DOCUMENT(1), SUBDOCUMENT(2), SCRIPT(4), STYLESHEET(8), IMAGE(16), MEDIA(32), FONT(64),
    XHR(128), PING(256), WEBSOCKET(512), OTHER(1024), POPUP(2048),
    /** La WebView non dice il tipo: vale per tutti i filtri tranne quelli solo per pagine e riquadri. */
    UNKNOWN(0);

    companion object {
        val UNKNOWN_MASK = SCRIPT.bit or STYLESHEET.bit or IMAGE.bit or MEDIA.bit or FONT.bit or XHR.bit or
            PING.bit or WEBSOCKET.bit or OTHER.bit
    }
}

/** Cosa vale per una pagina: elementi da nascondere, stili, filtri procedurali, scriptlet. */
class PageFilters(
    val hide: List<String>,
    /** Coppie selettore / dichiarazioni CSS (filtri con :style). */
    val styles: List<Pair<String, String>>,
    /** Filtri procedurali (:has-text, :upward, :remove...), interpretati dallo script nella pagina. */
    val procedural: List<String>,
    /** Scriptlet: nome seguito dagli argomenti. */
    val scriptlets: List<List<String>>,
    /** Filtri generici che non dipendono da classi o id: valgono sempre. */
    val highlyGeneric: List<String>,
    /** false se la pagina ha un'eccezione $generichide. */
    val generic: Boolean,
)

internal class Request(
    val url: String,
    val urlLower: String,
    val host: String,
    val hostStart: Int,
    val hostEnd: Int,
    val pageHost: String,
    val type: RequestType,
    val thirdParty: Boolean,
) {
    private var tokensCache: List<String>? = null
    private var pageKeysCache: List<String>? = null

    /** La pagina e i suoi domini superiori, per i filtri $domain=. */
    val pageKeys: List<String>
        get() = pageKeysCache ?: Domains.lookupKeys(pageHost).also { pageKeysCache = it }

    /** Le parole dell'indirizzo (lettere, cifre, %), per cercare solo i filtri che possono combaciare. */
    val tokens: List<String>
        get() = tokensCache ?: tokenize(urlLower).also { tokensCache = it }

    companion object {
        fun parse(url: String, pageUrl: String, type: RequestType): Request? {
            val lower = url.lowercase()
            val schemeEnd = lower.indexOf("://")
            if (schemeEnd <= 0) return null
            var hostStart = schemeEnd + 3
            var hostEnd = hostStart
            while (hostEnd < lower.length && lower[hostEnd] != '/' && lower[hostEnd] != '?' && lower[hostEnd] != '#') hostEnd++
            // Via utente e porta: example.com in https://utente@example.com:8080/
            val at = lower.lastIndexOf('@', hostEnd - 1)
            if (at >= hostStart) hostStart = at + 1
            var portStart = hostEnd
            if (lower.getOrNull(hostStart) != '[') {
                val colon = lower.indexOf(':', hostStart)
                if (colon in hostStart until hostEnd) portStart = colon
            }
            val host = lower.substring(hostStart, portStart)
            if (host.isEmpty()) return null
            val pageHost = hostOf(pageUrl)
            return Request(
                url, lower, host, hostStart, portStart, pageHost, type,
                thirdParty = pageHost.isNotEmpty() && Domains.isThirdParty(host, pageHost),
            )
        }

        fun hostOf(url: String): String {
            val schemeEnd = url.indexOf("://")
            if (schemeEnd <= 0) return ""
            var start = schemeEnd + 3
            var end = start
            while (end < url.length && url[end] != '/' && url[end] != '?' && url[end] != '#') end++
            val at = url.lastIndexOf('@', end - 1)
            if (at >= start) start = at + 1
            val colon = url.indexOf(':', start)
            if (colon in start until end && url.getOrNull(start) != '[') end = colon
            return url.substring(start, end).lowercase()
        }

        fun isTokenChar(c: Char) = c in 'a'..'z' || c in '0'..'9' || c == '%'

        fun tokenize(s: String): List<String> {
            val out = ArrayList<String>()
            var i = 0
            while (i < s.length) {
                if (isTokenChar(s[i])) {
                    val start = i
                    while (i < s.length && isTokenChar(s[i])) i++
                    out += s.substring(start, i)
                } else {
                    i++
                }
            }
            return out
        }
    }
}

/** Filtri di rete raggruppati per una parola che deve per forza comparire nell'indirizzo. */
internal class TokenIndex {
    private val buckets = HashMap<String, ArrayList<NetworkFilter>>()
    /** Filtri senza parola ma limitati ad alcuni siti ($domain=): si provano solo su quei siti. */
    private val byPageDomain = HashMap<String, ArrayList<NetworkFilter>>()
    private val untokenized = ArrayList<NetworkFilter>()

    fun add(filter: NetworkFilter) {
        val token = filter.token
        val domains = filter.domains
        when {
            token != null -> buckets.getOrPut(token) { ArrayList(2) } += filter
            domains != null -> domains.forEach { byPageDomain.getOrPut(it) { ArrayList(2) } += filter }
            else -> untokenized += filter
        }
    }

    /** Per i test: quanti filtri senza parola e i gruppi più grandi. */
    fun stats(): String = "senza parola ${untokenized.size}, per sito ${byPageDomain.size}, gruppi ${buckets.size}, più grandi " +
        buckets.entries.sortedByDescending { it.value.size }.take(8).joinToString { "${it.key}=${it.value.size}" } +
        "\n  es. " + untokenized.shuffled(java.util.Random(1)).take(25).joinToString(" ")

    fun forEachMatch(request: Request, action: (NetworkFilter) -> Unit) {
        for (filter in untokenized) if (filter.matches(request)) action(filter)
        if (byPageDomain.isNotEmpty() && request.pageHost.isNotEmpty()) {
            for (key in request.pageKeys) byPageDomain[key]?.forEach { if (it.matches(request)) action(it) }
        }
        val seen = HashSet<String>()
        for (token in request.tokens) {
            if (!seen.add(token)) continue
            buckets[token]?.forEach { if (it.matches(request)) action(it) }
        }
    }

    fun find(request: Request): NetworkFilter? {
        for (filter in untokenized) if (filter.matches(request)) return filter
        if (byPageDomain.isNotEmpty() && request.pageHost.isNotEmpty()) {
            for (key in request.pageKeys) {
                val list = byPageDomain[key] ?: continue
                for (filter in list) if (filter.matches(request)) return filter
            }
        }
        if (buckets.isEmpty()) return null
        val seen = HashSet<String>()
        for (token in request.tokens) {
            if (!seen.add(token)) continue
            val bucket = buckets[token] ?: continue
            for (filter in bucket) if (filter.matches(request)) return filter
        }
        return null
    }
}

/** Le direttive !#if delle liste di uBlock Origin, valutate come le valuta uBO su Chrome per Android. */
internal object Preprocessor {
    private val env = mapOf(
        "ext_ublock" to true, "env_chromium" to true, "env_mobile" to true, "cap_user_stylesheet" to true,
        "env_firefox" to false, "env_safari" to false, "env_edge" to false, "env_mv3" to false, "ext_ubol" to false,
        "cap_html_filtering" to false, "cap_ipaddress" to false, "ext_abp" to false, "adguard" to false,
        "adguard_app_android" to false, "adguard_ext_chromium" to false, "false" to false,
    )

    fun lines(text: String): Sequence<String> = sequence {
        // Pila delle condizioni: si tiene una riga solo se tutte sono vere.
        val stack = ArrayList<Boolean>()
        var active = true
        for (line in text.lineSequence()) {
            if (line.startsWith("!#")) {
                when {
                    line.startsWith("!#if ") -> {
                        stack += evaluate(line.substring(5).trim())
                        active = stack.all { it }
                    }
                    line.startsWith("!#else") -> if (stack.isNotEmpty()) {
                        stack[stack.size - 1] = !stack.last()
                        active = stack.all { it }
                    }
                    line.startsWith("!#endif") -> if (stack.isNotEmpty()) {
                        stack.removeAt(stack.size - 1)
                        active = stack.all { it }
                    }
                }
                continue
            }
            if (active) yield(line)
        }
    }

    /** Espressioni con !, &&, || e parentesi. */
    fun evaluate(expr: String): Boolean {
        val tokens = Regex("""!|&&|\|\||\(|\)|[A-Za-z0-9_]+""").findAll(expr).map { it.value }.toList()
        var pos = 0
        fun or(): Boolean {
            fun unary(): Boolean {
                if (pos >= tokens.size) return false
                val t = tokens[pos++]
                return when (t) {
                    "!" -> !unary()
                    "(" -> or().also { if (pos < tokens.size && tokens[pos] == ")") pos++ }
                    else -> env[t] ?: false
                }
            }
            fun and(): Boolean {
                var v = unary()
                while (pos < tokens.size && tokens[pos] == "&&") {
                    pos++
                    v = unary() and v
                }
                return v
            }
            var v = and()
            while (pos < tokens.size && tokens[pos] == "||") {
                pos++
                v = and() or v
            }
            return v
        }
        return or()
    }
}
