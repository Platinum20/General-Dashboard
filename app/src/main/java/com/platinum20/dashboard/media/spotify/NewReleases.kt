package com.platinum20.dashboard.media.spotify

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Un album o singolo uscito da poco di un artista che segui. */
data class SpRelease(val item: SpItem, val released: LocalDate)

/**
 * "Cosa è uscito dall'ultima volta" per la musica: dagli album degli artisti seguiti tiene
 * quelli degli ultimi [DAYS] giorni, senza doppioni (lo stesso album con più artisti), dal più nuovo.
 */
object NewReleases {
    const val DAYS = 28L
    /** Più di così non si chiede: un album a testa vuol dire una richiesta per artista. */
    const val MAX_ARTISTS = 60

    /**
     * La data di uscita secondo la precisione che dà Spotify: "day" è esatta, "month" vale il primo
     * del mese (ma solo se è il mese corrente o quello prima), "year" è troppo vaga e si scarta.
     */
    fun releaseDate(date: String, precision: String, today: LocalDate): LocalDate? = runCatching {
        when (precision.ifBlank { if (date.length == 10) "day" else if (date.length == 7) "month" else "year" }) {
            "day" -> LocalDate.parse(date)
            "month" -> YearMonth.parse(date).takeIf { !it.isBefore(YearMonth.from(today).minusMonths(1)) }?.atDay(1)
            else -> null
        }
    }.getOrNull()

    /** Gli album recenti dalle risposte di `artists/{id}/albums` di tutti gli artisti. */
    fun pick(responses: List<JSONObject?>, today: LocalDate): List<SpRelease> {
        val from = today.minusDays(DAYS)
        return responses.asSequence()
            .flatMap { it?.optJSONArray("items").objects().asSequence() }
            .mapNotNull { album ->
                val item = SpotifyApi.parseItem(album) ?: return@mapNotNull null
                val date = releaseDate(album.optString("release_date"), album.optString("release_date_precision"), today)
                    ?: return@mapNotNull null
                if (date.isBefore(from) || date.isAfter(today)) null else SpRelease(item, date)
            }
            .distinctBy { it.item.id }
            .sortedWith(compareByDescending<SpRelease> { it.released }.thenBy { it.item.title })
            .toList()
    }

    /** Una pagina di `me/following?type=artist`: gli id degli artisti e il cursore della pagina dopo. */
    fun parseFollowing(json: JSONObject?): Pair<List<String>, String?> {
        val artists = json?.optJSONObject("artists") ?: return emptyList<String>() to null
        val ids = artists.optJSONArray("items").objects().map { it.optString("id") }.filter { it.isNotBlank() }
        val after = artists.optJSONObject("cursors")?.optString("after")?.takeIf { it.isNotBlank() && it != "null" }
        return ids to after
    }

    /** Quante delle uscite non sono ancora state aperte. */
    fun unseen(list: List<SpRelease>, seen: Set<String>): Int = list.count { it.item.id !in seen }

    /** "oggi", "ieri", "3 giorni fa", "2 settimane fa". */
    fun ago(released: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(released, today)
        return when {
            days <= 0 -> "oggi"
            days == 1L -> "ieri"
            days < 14 -> "$days giorni fa"
            else -> "${days / 7} settimane fa"
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
