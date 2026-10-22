package com.platinum20.dashboard.adblock

/**
 * Domini e sottodomini come li intendono le liste di filtri.
 * Senza la Public Suffix List completa: bastano i suffissi a due livelli più diffusi.
 */
internal object Domains {

    private val twoLevelSuffixes = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp", "ne.jp",
        "or.jp", "com.br", "com.mx", "com.ar", "com.tr", "co.in", "co.za", "com.cn", "com.hk", "com.tw", "com.sg",
        "co.kr", "com.ua", "com.pl", "com.es", "com.pt", "co.il", "gov.it", "edu.it",
        "github.io", "blogspot.com", "appspot.com", "herokuapp.com", "pages.dev", "vercel.app", "netlify.app",
        "web.app", "firebaseapp.com", "cloudfront.net", "azurewebsites.net",
    )

    fun isIp(host: String): Boolean = host.contains(':') || host.all { it.isDigit() || it == '.' }

    /** Il "dominio principale": news.example.co.uk -> example.co.uk. */
    fun registrable(host: String): String {
        if (isIp(host)) return host
        val lastDot = host.lastIndexOf('.')
        if (lastDot <= 0) return host
        val secondDot = host.lastIndexOf('.', lastDot - 1)
        if (secondDot < 0) return host
        val lastTwo = host.substring(secondDot + 1)
        if (lastTwo !in twoLevelSuffixes) return lastTwo
        val thirdDot = host.lastIndexOf('.', secondDot - 1)
        return if (thirdDot < 0) host else host.substring(thirdDot + 1)
    }

    /** Il suffisso pubblico: example.co.uk -> co.uk, example.com -> com. */
    private fun publicSuffix(host: String): String {
        val reg = registrable(host)
        return reg.substringAfter('.', "")
    }

    fun isThirdParty(requestHost: String, pageHost: String): Boolean =
        pageHost.isNotEmpty() && registrable(requestHost) != registrable(pageHost)

    /** [host] è [domain] o un suo sottodominio. "google.*" vale per google.it, google.com, ... */
    fun matches(host: String, domain: String): Boolean {
        if (domain.endsWith(".*")) {
            val base = domain.substring(0, domain.length - 2)
            val suffix = publicSuffix(host)
            if (suffix.isEmpty()) return false
            val name = host.removeSuffix(".$suffix")
            return name == base || name.endsWith(".$base")
        }
        return host == domain || (host.length > domain.length && host.endsWith(domain) && host[host.length - domain.length - 1] == '.')
    }

    /**
     * Le chiavi con cui cercare i filtri di una pagina: il nome e ogni dominio superiore,
     * più le forme "nome.*" (www.google.it -> www.google.it, google.it, it, www.google.*, google.*).
     */
    fun lookupKeys(host: String): List<String> {
        val keys = ArrayList<String>(8)
        var h = host
        while (true) {
            keys += h
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        if (!isIp(host)) {
            val suffix = publicSuffix(host)
            if (suffix.isNotEmpty() && host.length > suffix.length + 1) {
                var name = host.substring(0, host.length - suffix.length - 1)
                while (true) {
                    keys += "$name.*"
                    val dot = name.indexOf('.')
                    if (dot < 0) break
                    name = name.substring(dot + 1)
                }
            }
        }
        return keys
    }
}
