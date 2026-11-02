package com.platinum20.dashboard.work

import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.weather.AirQuality
import com.platinum20.dashboard.weather.DailyPoint
import com.platinum20.dashboard.weather.asTemp

/** Un gruppo della notifica del mattino: titolo in grassetto (o nessuno) e le sue righe. */
data class MorningGroup(val header: String?, val lines: List<String>)

/**
 * Il testo della notifica del mattino, diviso in gruppi staccati: meteo, "Oggi" (le stesse righe
 * di Adesso nella Home) e le cose da fare senza scadenza oggi. Niente notizie: stanno in News.
 * Funzione pura, così si prova senza telefono.
 */
object MorningText {
    /** Quante cose da fare si elencano prima di "…e altre N". */
    const val TASKS = 3

    /** Da questo indice (EAQI, "Moderata") l'aria merita una riga; buona o discreta non dice niente. */
    const val AIR_WORTH = 40

    fun groups(
        today: DailyPoint?,
        air: AirQuality?,
        adesso: List<BriefLine>,
        tasks: List<TaskItem>,
        /** Fine di oggi (millisecondi): i promemoria entro stasera sono già in "Oggi". */
        endOfDay: Long,
    ): List<MorningGroup> {
        val weather = mutableListOf<String>()
        today?.let { d ->
            weather += "Max ${d.max.asTemp()} · min ${d.min.asTemp()}" +
                if (d.precipitationProbability >= 30) " · pioggia ${d.precipitationProbability}%" else ""
        }
        // Pioggia e UV vengono da Adesso ma sono meteo: stanno qui, non in "Oggi".
        adesso.filter { it.kind == BriefKind.RAIN || it.kind == BriefKind.WEATHER }.forEach { weather += it.text }
        air?.takeIf { it.europeanAqi >= AIR_WORTH }?.let { weather += "Aria ${it.label.lowercase()}" }

        val day = adesso.filter { it.kind != BriefKind.RAIN && it.kind != BriefKind.WEATHER }.map { it.text }

        // Le altre cose da fare: senza scadenza o più avanti (scaduti e di oggi sono già in "Oggi").
        val open = tasks.filter { !it.done && (it.dueAt == null || it.dueAt >= endOfDay) }
        val todo = open.take(TASKS).map { it.text } + listOfNotNull(more(open.size))

        return listOfNotNull(
            weather.takeIf { it.isNotEmpty() }?.let { MorningGroup(null, it) },
            day.takeIf { it.isNotEmpty() }?.let { MorningGroup("Oggi", it) },
            todo.takeIf { it.isNotEmpty() }?.let { MorningGroup("Da fare", it) },
        )
    }

    /** "…e un'altra" / "…e altre N" quando la lista è più lunga delle righe mostrate. */
    fun more(total: Int): String? = when (val rest = total - TASKS) {
        in Int.MIN_VALUE..0 -> null
        1 -> "…e un'altra"
        else -> "…e altre $rest"
    }
}
