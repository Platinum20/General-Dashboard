package com.platinum20.dashboard.adblock

/** JSON minimo per passare i filtri allo script nella pagina (senza org.json, così si prova anche fuori da Android). */
internal object Json {
    fun string(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                // U+2028 e U+2029 sono validi in JSON ma non sempre in JavaScript.
                c < ' ' || c == ' ' || c == ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun strings(list: List<String>): String = list.joinToString(",", "[", "]") { string(it) }

    fun page(filters: PageFilters): String = buildString {
        append("{\"hide\":").append(strings(filters.hide))
        append(",\"styles\":").append(filters.styles.joinToString(",", "[", "]") { "[" + string(it.first) + "," + string(it.second) + "]" })
        append(",\"proc\":").append(strings(filters.procedural))
        append(",\"js\":").append(filters.scriptlets.joinToString(",", "[", "]") { strings(it) })
        append(",\"high\":").append(strings(filters.highlyGeneric))
        append(",\"generic\":").append(filters.generic)
        append('}')
    }
}
