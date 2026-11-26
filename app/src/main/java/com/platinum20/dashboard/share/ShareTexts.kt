package com.platinum20.dashboard.share

import com.platinum20.dashboard.notes.NoteFormat
import com.platinum20.dashboard.notes.NoteLines
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/**
 * Cosa esce da Copia e Manda a… (Antonio 2026-11-26, PDF "Condividi"): una piccola scheda in testo, uguale per
 * tutti i fumetti. Il titolo sulla prima riga, poi le cose utili una per riga, alla fine il link se ce n'è uno che
 * si apre anche fuori da Canvas. Copia e Manda a… danno sempre lo stesso testo. Funzioni pure, provate nei test.
 */
object ShareTexts {
    private val IT = Locale.ITALIAN

    /** Le righe non vuote, una sotto l'altra. */
    fun lines(vararg parts: String?): String =
        parts.mapNotNull { it?.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** L'anteprima sotto «Copia»: le prime due righe su una, accorciata. */
    fun preview(text: String, max: Int = 46): String {
        val one = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · ")
        return if (one.length > max) one.take(max - 1).trimEnd() + "…" else one
    }

    /** "Venerdì 27 novembre" (con l'anno se non è quest'anno). */
    fun day(date: LocalDate, today: LocalDate): String {
        val name = date.dayOfWeek.getDisplayName(TextStyle.FULL, IT).replaceFirstChar { it.titlecase(IT) }
        val month = date.month.getDisplayName(TextStyle.FULL, IT)
        return "$name ${date.dayOfMonth} $month" + if (date.year != today.year) " ${date.year}" else ""
    }

    /** "20:30" */
    fun clock(ms: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        return "${t.hour}:${t.minute.toString().padStart(2, '0')}"
    }

    /**
     * Quando: "Venerdì 27 novembre, 20:30–22:30"; tutto il giorno solo il giorno (o "dal … al …").
     * [arrow] = "→" per i viaggi. Gli eventi tutto il giorno del calendario hanno la fine esclusiva, in UTC.
     */
    fun whenLine(begin: Long, end: Long, allDay: Boolean, zone: ZoneId, today: LocalDate, arrow: String = "–"): String {
        if (allDay) {
            val z = if (begin % 86_400_000L == 0L) ZoneOffset.UTC else zone
            val from = Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
            val to = if (end > begin) Instant.ofEpochMilli(end - 1).atZone(z).toLocalDate() else from
            return if (to.isAfter(from)) "Da ${day(from, today).lowercase()} a ${day(to, today).lowercase()}" else day(from, today)
        }
        val from = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        val start = "${day(from, today)}, ${clock(begin, zone)}"
        if (end <= begin) return start
        val to = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
        val sep = if (arrow == "–") "–" else " $arrow "
        return if (to == from) "$start$sep${clock(end, zone)}" else "$start $arrow ${day(to, today)}, ${clock(end, zone)}"
    }

    private val linkText = Regex("""\[([^\]\n]*)]\(([^)\s]+)\)""")

    private fun place(where: String?) = where?.trim()?.takeIf { it.isNotEmpty() }?.let { "📍 $it" }

    /** Un evento: titolo, quando, dove. */
    fun event(title: String, begin: Long, end: Long, allDay: Boolean, location: String, zone: ZoneId, today: LocalDate): String =
        lines(title, whenLine(begin, end, allDay, zone, today), place(location))

    /** Un turno: con chi e quando, mai il guadagno. Dei compagni solo il nome. */
    fun shift(begin: Long, end: Long, mates: List<String>, zone: ZoneId, today: LocalDate): String {
        val names = mates.map { it.trim().substringBefore(' ') }.filter { it.isNotEmpty() }
        val with = when (names.size) {
            0 -> null
            1 -> "Con ${names[0]}"
            else -> "Con " + names.dropLast(1).joinToString(", ") + " e " + names.last()
        }
        return lines("Turno Creators Pro", whenLine(begin, end, false, zone, today), with)
    }

    /** Un treno: tratta, orari, treno · carrozza · posto. */
    fun train(from: String?, to: String, train: String?, coach: String?, seat: String?, begin: Long, end: Long, zone: ZoneId, today: LocalDate): String {
        val route = if (from.isNullOrBlank()) "Treno per $to" else "Treno ${from.trim()} → ${to.trim()}"
        val ticket = listOfNotNull(
            train?.trim()?.takeIf { it.isNotEmpty() },
            coach?.trim()?.takeIf { it.isNotEmpty() }?.let { "Carrozza $it" },
            seat?.trim()?.takeIf { it.isNotEmpty() }?.let { "Posto $it" },
        ).joinToString(" · ")
        return lines(route, whenLine(begin, end, false, zone, today, arrow = "→"), ticket)
    }

    /** Un promemoria: testo, giorno (e ora), luogo. Senza data resta solo il testo. */
    fun task(text: String, dueAt: Long?, allDay: Boolean, place: String, zone: ZoneId, today: LocalDate): String {
        val whenText = dueAt?.let { due ->
            val d = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
            if (allDay) day(d, today) else "${day(d, today)}, ${clock(due, zone)}"
        }
        return lines(text, whenText, place(place))
    }

    /** Un pacco: nome, corriere · codice, il link per seguirlo. */
    fun parcel(title: String, carrier: String, code: String, url: String?): String =
        lines(title, listOf(carrier, code).filter { it.isNotBlank() }.joinToString(" · "), url)

    /** Brano, video, episodio: titolo, chi, link. */
    fun media(title: String, by: String, url: String?): String = lines(title, by, url)

    /** Una notizia: titolo, fonte, link. */
    fun article(title: String, source: String, url: String): String = lines(title, source, url)

    /** Una mail: chi, oggetto e giorno in alto, poi il testo. */
    fun mail(from: String, subject: String, at: Long, body: String, zone: ZoneId, today: LocalDate): String {
        val head = lines(
            from.takeIf { it.isNotBlank() }?.let { "Da: $it" },
            subject.takeIf { it.isNotBlank() }?.let { "Oggetto: $it" },
            at.takeIf { it > 0 }?.let { day(Instant.ofEpochMilli(it).atZone(zone).toLocalDate(), today) },
        )
        return listOf(head, body.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    /**
     * Il testo di una nota fuori da Canvas: senza segni, le spunte come ☐ / ☑, i titoletti senza "##", i pezzi
     * che fuori non si vedono (foto, vocali) tolti.
     */
    fun note(text: String): String {
        val plain = NoteFormat.plain(text)
        val out = NoteLines.parse(plain).mapNotNull { l ->
            val content = plain.substring(l.contentStart.coerceAtMost(l.end), l.end)
            val indent = "  ".repeat(l.level)
            when (l.kind) {
                NoteLines.Kind.CHECK -> indent + (if (l.done) "☑ " else "☐ ") + content
                NoteLines.Kind.HEADING -> content
                NoteLines.Kind.IMAGE, NoteLines.Kind.AUDIO, NoteLines.Kind.BOARD -> null
                NoteLines.Kind.DIVIDER -> "———"
                NoteLines.Kind.CALLOUT -> NoteLines.calloutEmoji(plain.substring(l.start, l.end)) + " " + content
                NoteLines.Kind.PAGE -> plain.substring(l.start, l.end).let { linkText.find(it)?.groupValues?.get(1) ?: it }
                NoteLines.Kind.BOOKMARK -> plain.substring(l.start, l.end).let { linkText.find(it)?.groupValues?.get(2) ?: it }
                else -> plain.substring(l.start, l.end)
            }
        }
        return out.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }

    /** L'indirizzo di Spotify che si apre anche fuori ("spotify:album:ID" → open.spotify.com/album/ID). */
    fun spotifyUrl(uri: String): String? {
        val parts = uri.split(":")
        if (parts.size != 3 || parts[0] != "spotify") return null
        if (parts[1] !in setOf("track", "album", "playlist", "artist", "show", "episode") || parts[2].isBlank()) return null
        return "https://open.spotify.com/${parts[1]}/${parts[2]}"
    }

    /** L'invito da calendario (.ics) che va insieme all'evento in Manda a…: si aggiunge con un tocco. */
    fun ics(uid: String, title: String, begin: Long, end: Long, allDay: Boolean, location: String, now: Long): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")
        fun utc(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).let {
            "%04d%02d%02dT%02d%02d%02dZ".format(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second)
        }
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().let { "%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth) }
        val stop = if (end > begin) end else begin + if (allDay) 86_400_000L else 3_600_000L
        return listOfNotNull(
            "BEGIN:VCALENDAR",
            "VERSION:2.0",
            "PRODID:-//Canvas//IT",
            "BEGIN:VEVENT",
            "UID:$uid",
            "DTSTAMP:${utc(now)}",
            if (allDay) "DTSTART;VALUE=DATE:${date(begin)}" else "DTSTART:${utc(begin)}",
            if (allDay) "DTEND;VALUE=DATE:${date(stop)}" else "DTEND:${utc(stop)}",
            "SUMMARY:${esc(title)}",
            location.trim().takeIf { it.isNotEmpty() }?.let { "LOCATION:${esc(it)}" },
            "END:VEVENT",
            "END:VCALENDAR",
        ).joinToString("\r\n") + "\r\n"
    }
}
