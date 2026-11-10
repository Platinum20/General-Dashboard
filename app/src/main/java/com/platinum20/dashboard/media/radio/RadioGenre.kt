package com.platinum20.dashboard.media.radio

/**
 * Il genere di una radio in italiano, da mettere sotto il nome al posto dei tag in inglese del servizio
 * ("news talk · Italy · 64 kbps" → "Notizie e parlato"). Una tabella breve: quello che non conosce lo salta.
 */
object RadioGenre {
    private val table = linkedMapOf(
        "news" to "notizie", "notizie" to "notizie", "informazione" to "notizie",
        "talk" to "parlato", "public radio" to "", "sport" to "sport", "sports" to "sport", "calcio" to "sport",
        "classical" to "classica", "classica" to "classica", "culture" to "cultura", "cultura" to "cultura",
        "jazz" to "jazz", "blues" to "blues", "rock" to "rock", "pop" to "pop", "dance" to "dance",
        "electronic" to "elettronica", "house" to "dance", "hip hop" to "hip hop", "hiphop" to "hip hop", "rap" to "hip hop",
        "rnb" to "soul e R&B", "r&b" to "soul e R&B", "soul" to "soul e R&B", "reggae" to "reggae", "metal" to "metal",
        "italian" to "musica italiana", "italiana" to "musica italiana", "italian music" to "musica italiana",
        "hits" to "successi", "top 40" to "successi", "top40" to "successi", "chart" to "successi",
        "oldies" to "successi di ieri", "60s" to "anni '60", "70s" to "anni '70", "80s" to "anni '80", "90s" to "anni '90",
        "lounge" to "lounge", "chillout" to "lounge", "ambient" to "lounge",
        "religious" to "religiosa", "catholic" to "religiosa", "christian" to "religiosa",
        "kids" to "per bambini", "children" to "per bambini", "local" to "locale", "community" to "locale",
    )

    /** "Notizie e parlato", "Classica e cultura", "Pop"; null se non riconosce niente. */
    fun of(tags: String): String? {
        val found = tags.lowercase().split(',', ';', '/')
            .map { it.trim() }
            .flatMap { tag -> listOfNotNull(table[tag]) + if (tag in table) emptyList() else tag.split(' ').mapNotNull { table[it] } }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(2)
        if (found.isEmpty()) return null
        return found.joinToString(" e ").replaceFirstChar { it.uppercase() }
    }

    /** Le iniziali per il logo che manca: "Radio 24" → "R2", "Rai Radio 1" → "RR". */
    fun initials(name: String): String {
        val words = name.split(' ', '-', '.').filter { it.isNotBlank() }
        return words.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "R" }
    }
}
