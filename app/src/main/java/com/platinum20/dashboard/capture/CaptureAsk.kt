package com.platinum20.dashboard.capture

import android.content.Context
import android.content.Intent
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.docs.DocKind
import com.platinum20.dashboard.docs.Document
import com.platinum20.dashboard.docs.DocumentsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.notes.Parcel
import com.platinum20.dashboard.notes.TaskItem
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftParse
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.weather.Forecast
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import androidx.compose.material.icons.outlined.QuestionAnswer
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Di che cosa chiede la domanda. */
enum class AskKind { NEXT_SHIFT, MONTH_SHIFTS, NEXT_TRAIN, SIM, WEATHER, DOCUMENT, PARCEL, AGENDA, WHEN }

/** La domanda capita: il tipo, il giorno se lo dice ("domani", "sabato"), un nome ("con Marco"), le parole da cercare. */
data class Question(val kind: AskKind, val day: LocalDate? = null, val name: String? = null, val words: String = "")

/** La risposta: una riga, e dove portarti con "Apri" (null = niente da aprire). */
data class Answer(val text: String, val open: OpenRequest? = null)

/** Tutto quello che serve per rispondere, preso dagli store: così la risposta è pura e si prova nei test. */
data class AskData(
    val now: LocalDateTime,
    val zone: ZoneId,
    val shifts: List<Shift> = emptyList(),
    /** Eventi dei prossimi giorni (treni e turni compresi), come li mostra il Calendario. */
    val events: List<CalendarEvent> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val parcels: List<Parcel> = emptyList(),
    val sim: SimStatus? = null,
    val forecast: Forecast? = null,
    val docs: List<Document> = emptyList(),
)

/**
 * Chiedi al "+" (1.473): una frase che è una domanda ("quando ho il prossimo turno con Marco?", "quanti giga ho?",
 * "che tempo fa sabato?") non si salva: il + risponde con quello che Canvas sa già, sul telefono, con regole
 * scritte qui (niente IA, niente rete). Se non capisce, la frase si salva come sempre. Il tipo non si chiede mai.
 */
object Ask {
    private val starters = Regex(
        """^(quando|quanti|quante|quanto|che tempo|cosa ho|che ho|che impegni|dove|a che ora|piove|piovera|devo prendere|dov'e|com'e|come sara)\b""",
    )
    private val hour = DateTimeFormatter.ofPattern("H:mm")
    private val dayName = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)

    /** È una domanda? Finisce col punto di domanda o comincia come una domanda; corta e su una riga. */
    fun isQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 100 || '\n' in t) return false
        return t.endsWith("?") || starters.containsMatchIn(norm(t))
    }

    /** Cosa chiede; null se non è una domanda o non è tra quelle che Canvas sa. */
    fun parse(text: String, today: LocalDate): Question? {
        if (!isQuestion(text)) return null
        val t = norm(text).removeSuffix("?").trim()
        val day = day(t, today)
        val name = Regex("""\bcon ([a-zàèéìòù]+)""").find(t)?.groupValues?.get(1)?.takeIf { it !in setOf("il", "la", "lo", "chi") }
        return when {
            Regex("""\b(turn[oi]|lavoro|lavorare|lavorato)\b""").containsMatchIn(t) ->
                if (Regex("""\b(quant[ei]|ore)\b""").containsMatchIn(t) && Regex("""\b(mese|ore|turni)\b""").containsMatchIn(t)) Question(AskKind.MONTH_SHIFTS)
                else Question(AskKind.NEXT_SHIFT, day, name)
            Regex("""\b(treno|treni|parto|partenza)\b""").containsMatchIn(t) -> Question(AskKind.NEXT_TRAIN, day)
            Regex("""\b(giga|gb|internet|traffico|minuti|sms|sim|rinnovo|offerta)\b""").containsMatchIn(t) -> Question(AskKind.SIM)
            Regex("""\b(tempo|meteo|piove|pioggia|piovera|ombrello|caldo|freddo|gradi|temperatura|sole)\b""").containsMatchIn(t) ->
                Question(AskKind.WEATHER, day ?: today)
            Regex("""\b(scade|scadono|scadenza)\b""").containsMatchIn(t) && docKind(t) != null -> Question(AskKind.DOCUMENT, words = docKind(t)!!.name)
            Regex("""\b(pacc[oh]i?|spedizione|corriere|amazon)\b""").containsMatchIn(t) -> Question(AskKind.PARCEL)
            Regex("""\b(cosa ho|che ho|impegni|cosa devo|che devo|programma)\b""").containsMatchIn(t) -> Question(AskKind.AGENDA, day ?: today)
            t.startsWith("quando") || t.startsWith("quanti anni") || t.startsWith("a che ora") ->
                Question(AskKind.WHEN, words = subject(t)).takeIf { it.words.isNotBlank() }
            else -> null
        }
    }

    /** La risposta alla domanda con i dati di adesso; null se non c'è niente da dire (allora la frase si salva). */
    fun answer(q: Question, d: AskData): Answer? {
        val today = d.now.toLocalDate()
        val nowMs = d.now.atZone(d.zone).toInstant().toEpochMilli()
        return when (q.kind) {
            AskKind.NEXT_SHIFT -> {
                val next = d.shifts.filter { it.end > nowMs }.sortedBy { it.start }
                    .filter { s -> q.name == null || s.mates.any { norm(it).startsWith(q.name) } || norm(s.title).contains(q.name) }
                    .firstOrNull { s -> q.day == null || date(s.start, d.zone) == q.day }
                if (next == null) return Answer(
                    when {
                        q.name != null -> "Nessun turno in arrivo con ${q.name.cap()}"
                        q.day != null -> "${dayWord(q.day, today).cap()} niente turni"
                        else -> "Nessun turno in arrivo"
                    },
                )
                val title = ShiftParse.creators(next.title).takeIf { it.isNotEmpty() }
                    ?.let { " su " + it.joinToString(", ") { n -> ShiftParse.firstName(n) } }.orEmpty()
                val with = next.mates.takeIf { it.isNotEmpty() }?.let { " · " + ShiftParse.company(it.map(ShiftParse::firstName)) }.orEmpty()
                Answer(
                    "Turno$title ${whenText(next.start, next.end, d)}$with",
                    OpenRequest(OpenRequest.EVENT, url = "${next.eventId}/${next.start}/${next.end}"),
                )
            }
            AskKind.MONTH_SHIFTS -> {
                // Solo questo mese, mai confronti col mese prima (regola di Antonio).
                val month = d.shifts.filter { val day = date(it.start, d.zone); day.year == today.year && day.month == today.month }
                val done = month.filter { it.end <= nowMs }
                val hours = done.sumOf { it.end - it.start } / 3_600_000.0
                val left = month.size - done.size
                val monthName = today.format(DateTimeFormatter.ofPattern("MMMM", Locale.ITALIAN))
                Answer(
                    "A $monthName ${done.size} turni, ${fmt(hours)} ore" + if (left > 0) " · ancora $left" else "",
                    OpenRequest(OpenRequest.NOTES),
                )
            }
            AskKind.NEXT_TRAIN -> {
                val next = d.events.filter { it.trip != null && it.end > nowMs }.sortedBy { it.begin }
                    .firstOrNull { q.day == null || date(it.begin, d.zone) == q.day }
                    ?: return Answer(if (q.day != null) "${dayWord(q.day, today).cap()} nessun treno" else "Nessun treno in arrivo")
                val trip = next.trip!!
                val seat = com.platinum20.dashboard.calendar.Trips.ticketLine(trip)?.let { " · $it" }.orEmpty()
                Answer(
                    "Treno per ${trip.to} ${whenText(next.begin, next.end, d)}$seat",
                    OpenRequest(OpenRequest.EVENT, url = "${next.eventId}/${next.begin}/${next.end}"),
                )
            }
            AskKind.SIM -> {
                val s = d.sim?.takeIf { it.hasAny } ?: return Answer("La SIM non è collegata", OpenRequest(OpenRequest.SIM))
                val parts = listOfNotNull(
                    s.data?.let { if (it.unlimited) "Giga illimitati" else "${fmt(it.left)} GB" + (it.total?.let { t -> " di ${fmt(t)}" } ?: "") },
                    s.minutes?.let { if (it.unlimited) "minuti illimitati" else "${fmt(it.left)} minuti" },
                    s.renewal?.let { "rinnovo ${dayWord(it, today)}" },
                )
                val old = s.at > 0 && nowMs - s.at > 6 * 3_600_000L
                Answer(parts.joinToString(" · ").cap() + if (old) " (letto ${dayWord(date(s.at, d.zone), today)} alle ${hour.format(Instant.ofEpochMilli(s.at).atZone(d.zone))})" else "", OpenRequest(OpenRequest.SIM))
            }
            AskKind.WEATHER -> {
                val f = d.forecast ?: return Answer("Il meteo non è ancora pronto", OpenRequest(OpenRequest.WEATHER))
                val day = q.day ?: today
                val p = f.daily.firstOrNull { it.date == day } ?: return Answer("Il meteo arriva solo a 7 giorni", OpenRequest(OpenRequest.WEATHER))
                val rain = if (p.precipitationProbability >= 30) " · pioggia ${p.precipitationProbability}%" else ""
                Answer(
                    "${dayWord(day, today).cap()} ${WeatherCodes.description(p.code).lowercase()}, ${p.max.toInt()}° / ${p.min.toInt()}°$rain",
                    OpenRequest(OpenRequest.WEATHER),
                )
            }
            AskKind.DOCUMENT -> {
                val kind = runCatching { DocKind.valueOf(q.words) }.getOrNull() ?: return null
                val doc = d.docs.firstOrNull { it.kind == kind }
                    ?: return Answer("Non hai ancora fotografato ${article(kind)}${kind.label.lowercase()}")
                val exp = doc.expiry ?: return Answer("${kind.label}: la scadenza non si legge dalla foto")
                val days = ChronoUnit.DAYS.between(today, exp)
                Answer(
                    "${kind.label}: " + (if (days < 0) "scaduta il " else "scade il ") + exp.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN)) +
                        if (days >= 0) " · ${relative(days)}" else "",
                    doc.countdownId?.let { OpenRequest(OpenRequest.ITEM, url = it.toString(), source = "TASK") },
                )
            }
            AskKind.PARCEL -> {
                val open = d.parcels.filter { !it.delivered }.sortedBy { it.expected ?: LocalDate.MAX }
                when {
                    open.isEmpty() -> Answer("Nessun pacco in arrivo")
                    open.size == 1 -> open[0].let { p ->
                        Answer(p.title + (p.expected?.let { " · arriva ${dayWord(it, today)}" } ?: " · in viaggio"), OpenRequest(OpenRequest.ITEM, url = p.id.toString(), source = "PARCEL"))
                    }
                    else -> Answer("${open.size} pacchi in arrivo: " + open.take(3).joinToString(", ") { p -> p.title + (p.expected?.let { " ${dayWord(it, today)}" }.orEmpty()) }, OpenRequest(OpenRequest.NOTES))
                }
            }
            AskKind.AGENDA -> {
                val day = q.day ?: today
                val events = d.events.filter { date(it.begin, d.zone) == day }.sortedBy { it.begin }
                val tasks = d.tasks.filter { t -> !t.done && t.nextDay(today, d.zone) == day }
                val items = events.map { e ->
                    (if (e.allDay) "" else hour.format(Instant.ofEpochMilli(e.begin).atZone(d.zone)) + " ") + eventName(e)
                } + tasks.map { it.text }
                if (items.isEmpty()) return Answer("${dayWord(day, today).cap()} niente in calendario", OpenRequest(OpenRequest.NOTES))
                Answer("${dayWord(day, today).cap()}: " + items.take(4).joinToString(", ") + if (items.size > 4) " e altre ${items.size - 4}" else "", OpenRequest(OpenRequest.NOTES))
            }
            AskKind.WHEN -> {
                val words = q.words.split(' ').filter { it.length >= 3 }
                if (words.isEmpty()) return null
                fun hits(text: String) = norm(text).let { n -> words.all { n.contains(it) } }
                val task = d.tasks.filter { !it.done && hits(it.text) }.mapNotNull { t -> t.nextDay(today, d.zone)?.let { t to it } }.minByOrNull { it.second }
                val event = d.events.filter { it.end > nowMs && hits(eventName(it) + " " + it.title) }.minByOrNull { it.begin }
                val eventDay = event?.let { date(it.begin, d.zone) }
                when {
                    task != null && (eventDay == null || !task.second.isAfter(eventDay)) -> {
                        val (t, day) = task
                        val age = t.yearsAtNext(today, d.zone)?.let { " · compie $it anni" }.orEmpty()
                        Answer("${t.text}: ${dayLong(day, today)}$age", OpenRequest(OpenRequest.ITEM, url = t.id.toString(), source = "TASK"))
                    }
                    event != null -> Answer("${eventName(event)}: ${whenText(event.begin, event.end, d, allDay = event.allDay)}", OpenRequest(OpenRequest.EVENT, url = "${event.eventId}/${event.begin}/${event.end}"))
                    else -> null
                }
            }
        }
    }

    /** "domani alle 7:00–13:00", "sabato 15 novembre alle 6:40". */
    private fun whenText(begin: Long, end: Long, d: AskData, allDay: Boolean = false): String {
        val start = Instant.ofEpochMilli(begin).atZone(d.zone)
        val day = dayLong(start.toLocalDate(), d.now.toLocalDate())
        if (allDay) return day
        return "$day alle ${start.format(hour)}–${Instant.ofEpochMilli(end).atZone(d.zone).format(hour)}"
    }

    /** "oggi", "domani", "sabato 15 novembre" (oltre una settimana, la data). */
    private fun dayLong(day: LocalDate, today: LocalDate): String = when (val n = ChronoUnit.DAYS.between(today, day)) {
        0L -> "oggi"
        1L -> "domani"
        in 2..6 -> day.format(DateTimeFormatter.ofPattern("EEEE d", Locale.ITALIAN))
        else -> day.format(dayName) + if (n > 0) " · ${relative(n)}" else ""
    }

    private fun dayWord(day: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, day)) {
        0L -> "oggi"
        1L -> "domani"
        -1L -> "ieri"
        in 2..6 -> day.format(DateTimeFormatter.ofPattern("EEEE", Locale.ITALIAN))
        else -> day.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN))
    }

    private fun relative(days: Long): String = when (days) {
        0L -> "oggi"
        1L -> "domani"
        else -> "tra $days giorni"
    }

    private fun eventName(e: CalendarEvent): String = com.platinum20.dashboard.ui.screens.RowText.event(e)

    /** Il giorno detto nella frase: oggi, domani, dopodomani, un giorno della settimana (il prossimo, oggi compreso). */
    internal fun day(t: String, today: LocalDate): LocalDate? {
        if (Regex("""\bdopodomani\b""").containsMatchIn(t)) return today.plusDays(2)
        if (Regex("""\bdomani\b""").containsMatchIn(t)) return today.plusDays(1)
        if (Regex("""\b(oggi|stasera|stamattina|stanotte)\b""").containsMatchIn(t)) return today
        val names = listOf("lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica")
        names.forEachIndexed { i, n ->
            if (Regex("""\b$n\b""").containsMatchIn(t)) {
                val target = DayOfWeek.of(i + 1)
                var d = today
                while (d.dayOfWeek != target) d = d.plusDays(1)
                return d
            }
        }
        return null
    }

    private fun docKind(t: String): DocKind? = when {
        "patente" in t -> DocKind.LICENSE
        "passaporto" in t -> DocKind.PASSPORT
        "identita" in t || Regex("""\bcarta\b""").containsMatchIn(t) -> DocKind.ID_CARD
        "tessera" in t || "sanitaria" in t -> DocKind.HEALTH_CARD
        "libretto" in t -> DocKind.VEHICLE
        "assicurazione" in t -> DocKind.INSURANCE
        "permesso" in t || "soggiorno" in t -> DocKind.RESIDENCE
        else -> null
    }

    private fun article(k: DocKind) = when (k) {
        DocKind.LICENSE, DocKind.ID_CARD, DocKind.HEALTH_CARD, DocKind.INSURANCE -> "la "
        DocKind.PASSPORT, DocKind.VEHICLE, DocKind.RESIDENCE -> "il "
        DocKind.OTHER -> "il "
    }

    /** Di cosa si chiede il quando: "quando è il compleanno di marta" → "compleanno marta". */
    private fun subject(t: String): String = t
        .replace(Regex("""^(quando|quanti anni|a che ora)\s*"""), "")
        .replace(Regex("""\b(e|è|ho|c'è|ce|c e|il|lo|la|i|gli|le|l|di|del|della|dello|dei|delle|da|a|al|alla|in|mi|si|un|una|compie|ha|sono|devo|arriva|scade|prossim[oa]|fa)\b"""), " ")
        .replace(Regex("""[^a-z0-9 ]"""), " ")
        .replace(Regex("""\s+"""), " ").trim()

    /** Minuscolo, senza accenti e apostrofi tipografici: "Lunedì" → "lunedi". */
    internal fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ITALIAN).replace('’', '\''), Normalizer.Form.NFD)
        .replace(Regex("""\p{M}"""), "")

    private fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ITALIAN, "%.1f", v)

    private fun String.cap() = replaceFirstChar { it.uppercase() }
}

/** Chiedi al "+" con i dati veri: legge gli store, risponde, e mostra la risposta nel fumetto con "Apri". */
internal object CaptureAsk {
    /** La risposta alla frase, o null se non è una domanda che Canvas sa (allora si salva come sempre). */
    suspend fun answer(context: Context, text: String): Answer? {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val q = Ask.parse(text, now.toLocalDate()) ?: return null
        val needsEvents = q.kind in setOf(AskKind.NEXT_TRAIN, AskKind.AGENDA, AskKind.WHEN)
        ShiftStore.init(context)
        NotesStore.init(context)
        val data = AskData(
            now = now,
            zone = zone,
            shifts = ShiftStore.shifts.value,
            events = if (needsEvents) runCatching {
                CalendarRepository.eventsFrom(context, now.toLocalDate(), days = 60).values.flatten().distinctBy { it.eventId to it.begin }
            }.getOrDefault(emptyList()) else emptyList(),
            tasks = NotesStore.data.value.tasks,
            parcels = NotesStore.data.value.parcels,
            sim = if (q.kind == AskKind.SIM) { SimStore.init(context); SimStore.status.value } else null,
            forecast = if (q.kind == AskKind.WEATHER) WeatherStore.cached(context) else null,
            docs = if (q.kind == AskKind.DOCUMENT) { DocumentsStore.init(context); DocumentsStore.docs.value } else emptyList(),
        )
        return Ask.answer(q, data)
    }

    /** La risposta nel fumetto in basso, con "Apri" che porta alla pagina giusta. */
    fun show(context: Context, answer: Answer) {
        val open = answer.open
        CaptureFeedback.tell(
            context,
            answer.text,
            icon = androidx.compose.material.icons.Icons.Outlined.QuestionAnswer,
            actionLabel = if (open != null) "Apri" else null,
            action = open?.let { request ->
                suspend {
                    val intent = Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    request.writeTo(intent)
                    context.startActivity(intent)
                }
            },
        )
    }
}
