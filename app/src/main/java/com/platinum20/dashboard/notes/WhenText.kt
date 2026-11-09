package com.platinum20.dashboard.notes

/**
 * Le stesse parole per eventi, promemoria e date (1.359, Antonio: "che abbiano le stesse cose"):
 * quando si ripete e quando avvisa si leggono allo stesso modo in tutte le schede. Pura, per i test.
 */
object WhenText {

    fun repeat(repeat: Repeat?): String = repeat?.let { "Si ripete " + it.label } ?: "Non si ripete"

    /** La regola di un evento del calendario (RRULE) con le parole dei promemoria. */
    fun repeatOfRule(rrule: String): String? {
        if (rrule.isBlank()) return null
        val freq = rrule.split(';').firstOrNull { it.startsWith("FREQ=") }?.substringAfter('=')
        val r = when (freq) {
            "DAILY" -> Repeat.DAILY
            "WEEKLY" -> Repeat.WEEKLY
            "MONTHLY" -> Repeat.MONTHLY
            "YEARLY" -> Repeat.YEARLY
            else -> null
        }
        val every = rrule.split(';').firstOrNull { it.startsWith("INTERVAL=") }?.substringAfter('=')?.toIntOrNull() ?: 1
        return if (r == null || every != 1) "Si ripete" else repeat(r)
    }

    /** L'avviso di un promemoria o di una data. */
    fun alert(task: TaskItem): String = when (task.alert) {
        Alert.NONE -> "Nessun avviso"
        Alert.ON_TIME -> if (task.allDay) "Avviso il giorno stesso alle 9" else "Avviso all'ora"
        Alert.DAY_BEFORE -> if (task.allDay) "Avviso il giorno prima alle 9" else "Avviso il giorno prima"
    }

    /** Gli avvisi di un evento del calendario (minuti prima), con le stesse parole. */
    fun alert(minutes: List<Int>): String {
        val list = minutes.filter { it >= 0 }.distinct().sorted()
        if (list.isEmpty()) return "Nessun avviso"
        return "Avviso " + list.joinToString(" e ") { before(it) }
    }

    private fun before(min: Int): String = when {
        min == 0 -> "all'ora"
        min % (24 * 60) == 0 -> (min / (24 * 60)).let { if (it == 1) "il giorno prima" else "$it giorni prima" }
        min % 60 == 0 -> (min / 60).let { if (it == 1) "1 ora prima" else "$it ore prima" }
        else -> "$min minuti prima"
    }
}
