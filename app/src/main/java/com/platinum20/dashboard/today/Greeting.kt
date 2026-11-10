package com.platinum20.dashboard.today

/** Il saluto in cima a Oggi e la riga del meteo di "Adesso". */
object Greeting {
    /** "Buonanotte" fino alle 5, poi "Buongiorno": chi si sveglia presto non si sente dire buonanotte. */
    fun forHour(hour: Int): String = when (hour) {
        in 5..12 -> "Buongiorno"
        in 13..17 -> "Buon pomeriggio"
        in 18..23 -> "Buonasera"
        else -> "Buonanotte"
    }

    /** Spazio che non va a capo: "pioggia 48%" e "22° / 10°" restano su una riga sola. */
    const val NBSP = ' '

    /** Le parti della riga del meteo unite da " · ", senza mai spezzare una parola dal suo numero. */
    fun weatherLine(vararg parts: String?): String =
        parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ") { it.replace(' ', NBSP) }

    /** Il titolo della Home (1.402): "Martedì 10", corto perché stia su una riga anche col carattere grande. */
    fun dateTitle(day: java.time.LocalDate): String =
        day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d", java.util.Locale.ITALIAN))
            .replaceFirstChar { it.uppercase() }

    /** La riga piccola sopra: il saluto e il mese, che il titolo corto non dice ("Buonanotte · novembre"). */
    fun subtitle(hour: Int, day: java.time.LocalDate): String =
        forHour(hour) + " · " + day.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.ITALIAN)

}
