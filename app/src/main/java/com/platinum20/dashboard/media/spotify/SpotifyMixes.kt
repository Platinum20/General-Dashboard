package com.platinum20.dashboard.media.spotify

import kotlin.random.Random

/** Un "Mix per te": i brani di un artista che ascolti di più, mescolati ad altri tuoi brani. */
data class SpMix(
    val id: String,
    val title: String,
    /** Gli artisti che ci sono dentro, i primi tre. */
    val subtitle: String,
    val image: String,
    val tracks: List<SpItem>,
)

/**
 * I Daily Mix di Spotify non arrivano alle app personali (dal 2026 le playlist fatte da Spotify
 * rispondono 403), quindi li facciamo noi: uno per ognuno dei tuoi artisti più ascoltati, con i
 * suoi brani tra quelli che hai ascoltato, salvato o messo tra i preferiti, più altri tuoi brani.
 * L'ordine cambia ogni giorno, come i Daily Mix.
 */
object SpotifyMixes {
    const val MAX_MIXES = 6
    const val MIX_SIZE = 30
    /** Sotto questo numero di brani dell'artista il mix sarebbe solo "altri brani": si salta. */
    const val MIN_ARTIST_TRACKS = 2

    fun build(artists: List<SpItem>, pool: List<SpItem>, epochDay: Long): List<SpMix> {
        val tracks = pool.filter { it.kind == SpKind.TRACK }.distinctBy { it.id }
        if (tracks.isEmpty()) return emptyList()
        return artists.filter { it.kind == SpKind.ARTIST }.distinctBy { it.id }.mapNotNull { artist ->
            val own = tracks.filter { artist.title in artistsOf(it) }
            if (own.size < MIN_ARTIST_TRACKS) return@mapNotNull null
            val random = Random(epochDay * 31 + artist.id.hashCode())
            // Metà dell'artista e metà degli altri, così è un mix e non la sua discografia.
            val others = (tracks - own.toSet()).shuffled(random).take((MIX_SIZE - own.size.coerceAtMost(MIX_SIZE / 2)).coerceAtLeast(0))
            val mixed = (own.shuffled(random).take(MIX_SIZE / 2) + others).shuffled(random).take(MIX_SIZE)
            // Il mix parte sempre da un brano dell'artista che gli dà il nome.
            val first = mixed.first { artist.title in artistsOf(it) }
            val ordered = listOf(first) + (mixed - first)
            val names = (listOf(artist.title) + ordered.flatMap { artistsOf(it) }).distinct().take(3)
            SpMix(
                id = "mix-${artist.id}",
                title = "Mix di ${artist.title}",
                subtitle = names.joinToString(", ") + " e altri",
                image = artist.image,
                tracks = ordered,
            )
        }.take(MAX_MIXES)
    }

    /** Gli artisti di un brano: nel sottotitolo, separati da virgola. */
    private fun artistsOf(track: SpItem): List<String> = track.subtitle.split(", ").map { it.trim() }.filter { it.isNotBlank() }
}
