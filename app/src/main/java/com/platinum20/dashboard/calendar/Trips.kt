package com.platinum20.dashboard.calendar

/**
 * I viaggi in treno che Google Calendar crea dalle mail (Antonio): spesso due eventi per lo stesso viaggio
 * ("Train to Pescara Centrale" e "Viaggio Taranto-Pescara Centrale, Treno Intercity 612, …"). Qui diventano
 * uno solo, e sulla linea del tempo due punti: la partenza e l'arrivo. Logica pura, per i test.
 */
/** Un viaggio in treno letto dagli eventi: [from] null se non si sa da dove parte. */
data class Trip(val from: String?, val to: String, val train: String?)

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
     * Un evento per viaggio (si tiene quello con i dettagli, "Viaggio …"), con il titolo pulito e il [Trip]:
     * da dove, per dove, quale treno. Sulla linea del tempo diventa una riga sola, partenza e arrivo uniti
     * da una linea (Antonio: prima erano due righe staccate, "Partenza…" e "Arrivo…", e non si capiva il viaggio).
     */
    fun shape(events: List<CalendarEvent>): List<CalendarEvent> {
        val trains = events.filter { !it.allDay && destination(it.title) != null }
        if (trains.isEmpty()) return events
        val kept = trains.groupBy { it.begin / MINUTE to it.end / MINUTE }.values.map { same ->
            val main = same.firstOrNull { viaggio.containsMatchIn(it.title) } ?: same.first()
            val to = destination(main.title)!!
            val from = same.firstNotNullOfOrNull { viaggio.find(it.title)?.groupValues?.get(1) }
                // "Train to X" di Google ha come luogo la stazione di partenza.
                ?: same.map { it.location.trim() }.firstOrNull { it.isNotEmpty() && !it.equals(to, ignoreCase = true) }
            val train = same.firstNotNullOfOrNull { trainName(it.title) }
            main.copy(title = "Treno per $to", trip = Trip(from, to, train))
        }
        return (events.filter { it !in trains } + kept).sortedBy { it.begin }
    }

    /**
     * Il viaggio di un solo evento (la sua scheda): la partenza dal titolo "Viaggio A-B", o dalle note del
     * biglietto ("partenza da Taranto"), o dal luogo. Null se non è un treno.
     */
    fun tripOf(title: String, location: String = "", description: String = ""): Trip? {
        val to = destination(title) ?: return null
        val from = viaggio.find(title)?.groupValues?.get(1)
            ?: departure.find(description)?.groupValues?.get(1)?.trim()
            ?: location.trim().takeIf { it.isNotEmpty() && !it.equals(to, ignoreCase = true) }
        return Trip(from, to, trainName(title) ?: trainName(description))
    }

    /** Il treno scritto nel titolo o nelle note: "Intercity 612", "Frecciarossa 9542". */
    fun trainName(text: String): String? = trainRegex.find(text)?.groupValues?.get(1)?.replace(Regex("""\s+"""), " ")

    /** Quanto dura il viaggio: "4 h 32 min", "2 h", "45 min". */
    fun duration(begin: Long, end: Long): String {
        val minutes = ((end - begin) / MINUTE).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** Una spesa fatta per un treno (Trenitalia, Italo, Trenord…): sulla linea ha l'icona del treno. */
    fun isTrainMerchant(name: String): Boolean = merchant.containsMatchIn(name)

    /** Le righe utili di un biglietto, lette dal titolo e dalle note: treno, carrozza, posto, PNR. */
    fun details(title: String, description: String): List<Pair<String, String>> {
        val text = "$title\n$description"
        return listOfNotNull(
            trainName(text)?.let { "Treno" to it },
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

    private val trainRegex = Regex("""Treno:?\s*((?:Intercity|Frecciarossa|Frecciargento|Frecciabianca|Regionale(?: Veloce)?|Italo|IC|FR|RV|R)\s*\d+)""", RegexOption.IGNORE_CASE)
    private val departure = Regex("""partenza da\s+(.+?)\s+Ore\b""", RegexOption.IGNORE_CASE)
    private val merchant = Regex("""\b(trenitalia|italo|trenord|frecciarossa|tren[io])\b""", RegexOption.IGNORE_CASE)
    private const val MINUTE = 60_000L
}
