package com.platinum20.dashboard.media.radio

/**
 * Quale radio va nel blocco grande in cima alla pagina Radio (1.572, come le pagine di Spotify e YouTube) e cosa dice
 * la riga sotto il nome: quella che suona, se no la preferita, l'ultima ascoltata, la più ascoltata in Italia.
 */
object RadioHero {
    enum class Why { ON_AIR, FAVORITE, RECENT, POPULAR }

    data class Pick(val station: RadioStation, val why: Why)

    fun pick(playing: RadioStation?, favorites: List<RadioStation>, recent: List<RadioStation>, popular: List<RadioStation>): Pick? =
        playing?.let { Pick(it, Why.ON_AIR) }
            ?: favorites.firstOrNull()?.let { Pick(it, Why.FAVORITE) }
            ?: recent.firstOrNull()?.let { Pick(it, Why.RECENT) }
            ?: popular.firstOrNull()?.let { Pick(it, Why.POPULAR) }

    fun line(why: Why): String = when (why) {
        Why.ON_AIR -> "In onda adesso"
        Why.FAVORITE -> "La tua preferita"
        Why.RECENT -> "L'ultima che hai ascoltato"
        Why.POPULAR -> "La più ascoltata in Italia"
    }

    /** Sotto: cosa trasmette mentre suona (se la radio lo manda), altrimenti il genere. */
    fun detail(why: Why, track: String?, tags: String): String =
        track?.takeIf { why == Why.ON_AIR && it.isNotBlank() } ?: RadioGenre.of(tags) ?: "Radio"
}
