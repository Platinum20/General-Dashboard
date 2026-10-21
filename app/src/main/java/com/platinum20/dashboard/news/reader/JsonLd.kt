package com.platinum20.dashboard.news.reader

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Le informazioni sull'articolo che i giornali inseriscono come dati strutturati schema.org. */
internal data class JsonLdArticle(
    val headline: String?,
    val body: String,
    val author: String?,
    val image: String?,
    /** false se la pagina dichiara l'articolo riservato agli abbonati. */
    val free: Boolean,
)

internal object JsonLd {

    private val ARTICLE_TYPES = setOf(
        "article", "newsarticle", "reportagenewsarticle", "analysisnewsarticle", "opinionnewsarticle",
        "reviewnewsarticle", "backgroundnewsarticle", "blogposting", "liveblogposting", "socialmediaposting",
    )

    fun articles(json: String): List<JsonLdArticle> {
        val root = runCatching { JSONTokener(json.trim()).nextValue() }.getOrNull() ?: return emptyList()
        val found = mutableListOf<JsonLdArticle>()
        collect(root, found)
        return found
    }

    /** Anche gli articoli senza articleBody possono dichiararsi a pagamento. */
    fun declaresPaid(json: String): Boolean {
        val root = runCatching { JSONTokener(json.trim()).nextValue() }.getOrNull() ?: return false
        var paid = false
        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    if (isArticle(node)) {
                        when (val flag = node.opt("isAccessibleForFree")) {
                            is Boolean -> if (!flag) paid = true
                            is String -> if (flag.equals("false", ignoreCase = true)) paid = true
                        }
                    }
                    visit(node.opt("@graph"))
                    visit(node.opt("mainEntity"))
                }
            }
        }
        visit(root)
        return paid
    }

    private fun collect(node: Any?, out: MutableList<JsonLdArticle>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out)
            is JSONObject -> {
                if (isArticle(node)) toArticle(node)?.let { out += it }
                collect(node.opt("@graph"), out)
                collect(node.opt("mainEntity"), out)
            }
        }
    }

    private fun isArticle(obj: JSONObject): Boolean = when (val type = obj.opt("@type")) {
        is String -> type.lowercase() in ARTICLE_TYPES
        is JSONArray -> (0 until type.length()).any { type.optString(it).lowercase() in ARTICLE_TYPES }
        else -> false
    }

    private fun toArticle(obj: JSONObject): JsonLdArticle? {
        val body = obj.optString("articleBody").ifBlank { obj.optString("text") }
        if (body.isBlank()) return null
        val free = when (val flag = obj.opt("isAccessibleForFree")) {
            is Boolean -> flag
            is String -> !flag.equals("false", ignoreCase = true)
            else -> true
        }
        return JsonLdArticle(
            headline = obj.optString("headline").takeIf { it.isNotBlank() },
            body = body,
            author = name(obj.opt("author")),
            image = imageUrl(obj.opt("image")),
            free = free,
        )
    }

    private fun name(node: Any?): String? = when (node) {
        is String -> node.takeIf { it.isNotBlank() && !it.startsWith("http") }
        is JSONObject -> node.optString("name").takeIf { it.isNotBlank() }
        is JSONArray -> (0 until node.length()).mapNotNull { name(node.opt(it)) }.joinToString(", ").takeIf { it.isNotBlank() }
        else -> null
    }

    private fun imageUrl(node: Any?): String? = when (node) {
        is String -> node.takeIf { it.startsWith("http") }
        is JSONObject -> node.optString("url").takeIf { it.startsWith("http") }
        is JSONArray -> (0 until node.length()).firstNotNullOfOrNull { imageUrl(node.opt(it)) }
        else -> null
    }
}
