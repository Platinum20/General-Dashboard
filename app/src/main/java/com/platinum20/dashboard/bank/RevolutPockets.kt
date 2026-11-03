package com.platinum20.dashboard.bank

import org.json.JSONArray
import org.json.JSONObject

/** Una tasca in euro del conto: [main] è il conto vero e proprio, le altre sono salvadanai. */
data class Pocket(val id: String, val name: String, val cents: Long, val main: Boolean)

/** Quello che una risposta dice delle tasche: l'elenco completo (wallet) e/o i nomi dei salvadanai. */
data class PocketRead(val pockets: List<Pocket>? = null, val names: Map<String, String> = emptyMap())

/**
 * Le tasche per la card Revolut della Home (separato da [RevolutParse], che pensa alle spese).
 * La risposta wallet le ha tutte, con tipo CURRENT o SAVINGS, ma i salvadanai lì si chiamano
 * "MB:<id>": il nome vero ("Risparmi") arriva dalla risposta money-boxes, con l'id della tasca.
 */
object RevolutPockets {
    private const val CURRENCY = "EUR"
    private val kinds = setOf("CURRENT", "SAVINGS")

    fun read(body: String): PocketRead? {
        val root = runCatching { if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body) }.getOrNull() ?: return null
        val pockets = LinkedHashMap<String, Pocket>()
        val names = LinkedHashMap<String, String>()
        walk(root, 0) { o ->
            pocket(o)?.let { pockets[it.id] = it }
            // money-boxes: {"name":"Risparmi", "pocket":{"id":"p2", ...}}
            val inner = o.optJSONObject("pocket")
            val name = o.optString("name").trim()
            if (inner != null && name.isNotEmpty() && inner.optString("id").isNotEmpty()) names[inner.optString("id")] = name
        }
        if (pockets.isEmpty() && names.isEmpty()) return null
        return PocketRead(pockets.values.toList().takeIf { it.isNotEmpty() }, names)
    }

    private fun walk(v: Any?, depth: Int, visit: (JSONObject) -> Unit) {
        if (depth > 8) return
        when (v) {
            is JSONObject -> {
                // Un movimento ha dentro il suo conto con il saldo di allora: non è una tasca di adesso.
                // Si riconosce dall'importo: anche tasche e salvadanai hanno createdDate (1.339 li saltava).
                if (looksLikeTx(v)) return
                visit(v)
                v.keys().forEach { walk(v.opt(it), depth + 1, visit) }
            }
            is JSONArray -> for (i in 0 until v.length()) walk(v.opt(i), depth + 1, visit)
        }
    }

    private fun looksLikeTx(o: JSONObject) =
        o.opt("amount") is Number && (o.has("startedDate") || o.has("createdDate") || o.has("completedDate"))

    private fun pocket(o: JSONObject): Pocket? {
        val id = o.optString("id").ifEmpty { return null }
        if (o.opt("balance") !is Number || o.optString("currency").uppercase() != CURRENCY) return null
        val type = o.optString("type").uppercase()
        if (type !in kinds) return null
        if (o.optBoolean("closed") || o.optString("state").uppercase().let { it.isNotEmpty() && it != "ACTIVE" }) return null
        val raw = o.optString("name").trim().takeIf { it.isNotEmpty() && !it.startsWith("MB:") && it != "null" }
        val main = type == "CURRENT"
        return Pocket(id, raw ?: if (main) "Conto" else "Salvadanaio", o.optLong("balance"), main)
    }

    /** I nomi veri sui salvadanai, il conto per primo, poi i salvadanai dal più pieno. */
    fun named(pockets: List<Pocket>, names: Map<String, String>): List<Pocket> =
        pockets.map { p -> if (!p.main) names[p.id]?.let { p.copy(name = it) } ?: p else p }
            .sortedWith(compareByDescending<Pocket> { it.main }.thenByDescending { it.cents })

    fun total(pockets: List<Pocket>): Long = pockets.sumOf { it.cents }

    fun toJson(pockets: List<Pocket>): String = JSONArray(pockets.map {
        JSONObject().put("id", it.id).put("name", it.name).put("cents", it.cents).put("main", it.main)
    }).toString()

    fun fromJson(raw: String?): List<Pocket> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { Pocket(it.getString("id"), it.getString("name"), it.getLong("cents"), it.getBoolean("main")) }
        }
    }.getOrDefault(emptyList())

    fun namesToJson(names: Map<String, String>): String = JSONObject(names as Map<*, *>).toString()

    fun namesFromJson(raw: String?): Map<String, String> = runCatching {
        val o = JSONObject(raw ?: return emptyMap())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())
}
