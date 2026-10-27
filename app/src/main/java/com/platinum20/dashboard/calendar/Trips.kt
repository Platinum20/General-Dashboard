package com.platinum20.dashboard.calendar

/**
 * I viaggi in treno che Google Calendar crea dalle mail (Antonio): spesso due eventi per lo stesso viaggio
 * ("Train to Pescara Centrale" e "Viaggio Taranto-Pescara Centrale, Treno Intercity 612, …"). Qui diventano
 * uno solo, e sulla linea del tempo due punti: la partenza e l'arrivo. Logica pura, per i test.
 */
object Trips {
    private val trainTo = Regex("""^\s*Train to (.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val viaggio = Regex("""^\s*Viaggio\s+(.+?)\s*-\s*(.+?)\s*,.*\bTreno\b""", RegexOption.IGNORE_CASE)
    private val trenoPer = Regex("""^\s*Treno\s+(?:per|a)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)

    /** Dove va il treno, se il titolo è quello di un viaggio in treno; null altrimenti. */
    fun destination(title: String): String? =
        trainTo.find(title)?.groupValues?.get(1)
            ?: viaggio.find(title)?.groupValues?.get(2)
            ?: trenoPer.find(title)?.groupValues?.get(1)

    /** Il titolo pulito: "Treno per Pescara Centrale". */
    fun title(raw: String): String = destination(raw)?.let { "Treno per $it" } ?: raw

    /**
     * Un evento per viaggio (si tiene quello con i dettagli, "Viaggio …"), poi ogni viaggio diventa due punti:
     * "Partenza treno per X" all'ora di partenza e "Arrivo treno a X" all'ora di arrivo, con lo stesso id
     * (toccandoli si apre lo stesso viaggio).
     */
    fun shape(events: List<CalendarEvent>): List<CalendarEvent> {
        val trains = events.filter { !it.allDay && destination(it.title) != null }
        if (trains.isEmpty()) return events
        val kept = trains.groupBy { it.begin / MINUTE to it.end / MINUTE }.values.map { same ->
            same.firstOrNull { viaggio.containsMatchIn(it.title) } ?: same.first()
        }
        val out = events.filter { it !in trains }.toMutableList()
        kept.forEach { e ->
            val to = destination(e.title)!!
            out += e.copy(title = "Partenza treno per $to", end = e.begin)
            if (e.end > e.begin) out += e.copy(title = "Arrivo treno a $to", begin = e.end)
        }
        return out.sortedBy { it.begin }
    }

    /** Le righe utili di un biglietto, lette dal titolo e dalle note: treno, carrozza, posto, PNR. */
    fun details(title: String, description: String): List<Pair<String, String>> {
        val text = "$title\n$description"
        return listOfNotNull(
            Regex("""Treno:?\s*((?:Intercity|Frecciarossa|Frecciargento|Frecciabianca|Regionale(?: Veloce)?|Italo|IC|FR|RV|R)\s*\d+)""", RegexOption.IGNORE_CASE)
                .find(text)?.let { "Treno" to it.groupValues[1] },
            Regex("""Carr(?:ozza|\.)?\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.let { "Carrozza" to it.groupValues[1] },
            Regex("""Post[oi]\s*,?\s*(\d+[A-Z]?(?:\s*-\s*\d+[A-Z]?)*)""", RegexOption.IGNORE_CASE).find(text)?.let { "Posto" to it.groupValues[1] },
            Regex("""PNR\s*:?\s*([A-Z0-9]{5,8})""", RegexOption.IGNORE_CASE).find(text)?.let { "PNR" to it.groupValues[1].uppercase() },
        )
    }

    /**
     * Le note senza i testi automatici di Google ("Per visualizzare informazioni dettagliate…", "Questo evento
     * è stato creato a partire da un'email…") e i loro link: restano solo le note vere.
     */
    fun cleanDescription(description: String): String =
        description.split(Regex("""\n\s*\n"""))
            .filterNot { p ->
                val t = p.trim()
                t.startsWith("Per visualizzare informazioni dettagliate", ignoreCase = true) ||
                    t.startsWith("Questo evento è stato creato", ignoreCase = true) ||
                    t.startsWith("To see detailed information", ignoreCase = true) ||
                    t.startsWith("This event was created", ignoreCase = true)
            }
            .joinToString("\n\n")
            .trim()

    private const val MINUTE = 60_000L
}
