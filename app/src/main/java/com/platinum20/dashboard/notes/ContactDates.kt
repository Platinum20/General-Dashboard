package com.platinum20.dashboard.notes

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import java.text.Normalizer
import java.time.LocalDate
import java.time.MonthDay

/** Una data della rubrica (compleanno o anniversario), pronta per diventare una data "ogni anno". */
data class ContactDate(
    val name: String,
    val anniversary: Boolean,
    val monthDay: MonthDay,
    /** L'anno vero (di nascita, delle nozze), se la rubrica lo sa. */
    val year: Int?,
) {
    val title: String get() = if (anniversary) "Anniversario di $name" else "Compleanno di $name"

    /** La data da salvare: con l'anno vero se c'è, così Date dice "compie 30 anni"; senza, quello di oggi. */
    fun date(today: LocalDate = LocalDate.now()): LocalDate {
        val y = year ?: today.year
        // Il 29 febbraio senza anno bisestile diventa il 28.
        return if (monthDay.isValidYear(y)) monthDay.atYear(y) else LocalDate.of(y, 2, 28)
    }

    /** Chiave per riconoscere la stessa persona nello stesso giorno, anche tra due contatti doppi. */
    val key: String get() = "${ContactDates.normalize(title)}|$monthDay"
}

/** Compleanni e anniversari dalla rubrica del telefono: lettura, formati delle date e doppioni. */
object ContactDates {

    /**
     * Le date della rubrica arrivano in formati diversi a seconda dell'app che le ha scritte:
     * "1990-03-12", "--03-12" (senza anno), "19900312", "12.03.1990", "12/03/1990".
     * Restituisce giorno e anno (se c'è); null se non si capisce.
     */
    fun parseDate(raw: String): Pair<MonthDay, Int?>? {
        val s = raw.trim().substringBefore('T').substringBefore(' ')
        Regex("""--(\d{2})-?(\d{2})""").matchEntire(s)?.let { m ->
            return monthDay(m.groupValues[1], m.groupValues[2])?.let { it to null }
        }
        val ymd = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(s)?.groupValues?.drop(1)
            ?: Regex("""(\d{4})(\d{2})(\d{2})""").matchEntire(s)?.groupValues?.drop(1)
            ?: Regex("""(\d{1,2})[./](\d{1,2})[./](\d{4})""").matchEntire(s)?.groupValues?.drop(1)?.reversed()
            ?: return null
        val (y, mo, d) = ymd
        return monthDay(mo, d)?.let { it to year(y) }
    }

    private fun monthDay(month: String, day: String): MonthDay? = runCatching {
        MonthDay.of(month.toInt(), day.toInt())
    }.getOrNull()

    /** Gli anni impossibili (1604 di iOS per "senza anno", anni futuri) valgono come "anno sconosciuto". */
    private fun year(raw: String): Int? = raw.toIntOrNull()?.takeIf { it in 1900..LocalDate.now().year }

    /**
     * Le date da proporre: senza doppioni tra loro (lo stesso contatto salvato su due account)
     * e con [ContactCandidate.already] vero per quelle che in Date ci sono già.
     */
    fun candidates(found: List<ContactDate>, existing: List<Countdown>): List<ContactCandidate> =
        found.distinctBy { it.key }
            .sortedWith(compareBy<ContactDate> { it.monthDay }.thenBy { it.name })
            .map { ContactCandidate(it, already = isDuplicate(it, existing)) }

    /**
     * Già in Date: stesso titolo, oppure stesso giorno dell'anno con il nome della persona nel titolo
     * ("Compleanno Marta" scritto a mano il 12 marzo vale per Marta Rossi).
     */
    fun isDuplicate(item: ContactDate, existing: List<Countdown>): Boolean {
        val title = normalize(item.title)
        val first = normalize(item.name).substringBefore(' ')
        return existing.any { c ->
            val t = normalize(c.title)
            t == title || (MonthDay.from(c.date) == item.monthDay && first.length >= 2 && t.split(' ').contains(first))
        }
    }

    internal fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").replace(Regex("\\s+"), " ")

    /** Legge compleanni e anniversari dalla rubrica. Serve il permesso READ_CONTACTS. */
    fun read(context: Context): List<ContactDate> {
        val projection = arrayOf(ContactsContract.Data.DISPLAY_NAME, Event.START_DATE, Event.TYPE)
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} IN (?, ?)"
        val args = arrayOf(Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString(), Event.TYPE_ANNIVERSARY.toString())
        val out = mutableListOf<ContactDate>()
        runCatching {
            context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)?.trim().orEmpty()
                    val parsed = parseDate(c.getString(1).orEmpty())
                    if (name.isEmpty() || parsed == null) continue
                    val (md, year) = parsed
                    out += ContactDate(name, anniversary = c.getInt(2) == Event.TYPE_ANNIVERSARY, monthDay = md, year = year)
                }
            }
        }
        return out
    }
}

/** Una data della rubrica nell'elenco da importare; [already] se in Date c'è già. */
data class ContactCandidate(val date: ContactDate, val already: Boolean)
