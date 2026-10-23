package com.platinum20.dashboard.news

/**
 * Scelte di impaginazione delle News, separate dalla UI per poterle provare:
 * quando una foto vale la pena di essere mostrata e come dire che una fonte non risponde.
 */
object NewsLayout {
    // Immagini che i feed mettono quando l'articolo non ne ha una: loghi, segnaposto, pixel di tracciamento.
    private val placeholderHints = listOf("placeholder", "default", "logo", "blank", "spacer", "pixel", "no-image", "noimage")

    /** L'indirizzo della foto da mostrare, o null: la card resta solo testo invece di un riquadro grigio. */
    fun usableImage(url: String?): String? {
        val clean = url?.trim().orEmpty()
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) return null
        val path = clean.substringBefore('?').lowercase()
        if (path.endsWith(".svg") || path.endsWith(".gif")) return null
        val file = path.substringAfterLast('/')
        if (placeholderHints.any { it in file }) return null
        return clean
    }

    /** Il chip discreto accanto ai filtri ("1 fonte non risponde"), null se rispondono tutte. */
    fun failureChip(failed: Int): String? = when {
        failed <= 0 -> null
        failed == 1 -> "1 fonte non risponde"
        else -> "$failed fonti non rispondono"
    }

    /** Il dettaglio che si legge toccando il chip: solo i nomi, il codice d'errore non dice niente a chi legge. */
    fun failureDetail(names: List<String>): String =
        names.joinToString() + if (names.size == 1) " non risponde, riprovo al prossimo aggiornamento"
        else " non rispondono, riprovo al prossimo aggiornamento"
}
