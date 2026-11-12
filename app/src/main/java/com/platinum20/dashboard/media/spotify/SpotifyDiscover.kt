package com.platinum20.dashboard.media.spotify

/** Una fila di "Scopri" della musica: un titolo, il perché e le tessere. */
data class SpDiscoverRow(val title: String, val why: String, val items: List<SpItem>)

/**
 * "Scopri" della musica (1.488, Antonio: "le uscite degli artisti che ascolto, artisti simili e i miei generi"). Le app
 * personali non possono chiedere a Spotify gli artisti correlati né i consigli: si cercano gli artisti e le playlist
 * dei generi dei tuoi artisti più ascoltati, togliendo chi conosci già.
 */
object SpotifyDiscover {
    const val GENRES = 3

    /** I generi dei tuoi artisti più ascoltati, dal più presente, ognuno con l'artista che lo porta: (genere, artista). */
    fun genres(pairs: List<Pair<String, String>>, max: Int = GENRES): List<Pair<String, String>> =
        pairs.filter { it.first.isNotBlank() }
            .groupBy { it.first.lowercase() }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Pair<String, String>>>> { it.value.size }.thenBy { pairs.indexOfFirst { p -> p.first.lowercase() == it.key } })
            .take(max)
            .map { (_, list) -> list.first().first to list.first().second }

    /** Gli artisti trovati per un genere, senza quelli che ascolti già (per id o per nome). */
    fun newArtists(found: List<SpItem>, known: Set<String>): List<SpItem> =
        found.filter { it.kind == SpKind.ARTIST && it.id !in known && it.title.lowercase() !in known }.distinctBy { it.id }

    /** "post-punk" → "Post punk", come lo si dice. */
    fun label(genre: String): String = genre.replace('-', ' ').replaceFirstChar { it.uppercase() }
}
