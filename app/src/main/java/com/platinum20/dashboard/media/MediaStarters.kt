package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.radio.RadioStation

/**
 * "Per iniziare" in Media al primo uso: qualche radio popolare da far partire con un tocco,
 * così la pagina non è fatta di quattro riquadri e poi due terzi di vuoto.
 */
object MediaStarters {
    /** Le stazioni da proporre: mai una che hai già tra recenti o preferite, mai due con lo stesso nome. */
    fun stations(
        popular: List<RadioStation>,
        recent: List<RadioStation>,
        favorites: List<RadioStation>,
        max: Int = 4,
    ): List<RadioStation> {
        val known = (recent + favorites).flatMap { listOf(it.id, key(it.name)) }.toSet()
        return popular
            .filter { it.streamUrl.isNotBlank() && it.id !in known && key(it.name) !in known }
            .distinctBy { key(it.name) }
            .take(max)
    }

    /** "RTL 102.5 " e "rtl 102.5" sono la stessa radio. */
    private fun key(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")
}
