package com.platinum20.dashboard.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val color: Int,
)

/** Eventi dei calendari del telefono (Google, Samsung, Outlook…), letti dal provider di sistema. */
object CalendarRepository {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Eventi del giorno indicato, prima quelli di tutto il giorno e poi in ordine di orario. */
    suspend fun eventsOn(context: Context, day: LocalDate = LocalDate.now()): List<CalendarEvent> =
        eventsFrom(context, day, days = 1)[day].orEmpty()

    /** Eventi dei [days] giorni da [first] in poi, giorno per giorno (un evento di più giorni compare in ognuno). */
    suspend fun eventsFrom(context: Context, first: LocalDate, days: Int): Map<LocalDate, List<CalendarEvent>> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyMap()
        val zone = ZoneId.systemDefault()
        val start = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = first.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - DAY_MS)
            ContentUris.appendId(it, end + DAY_MS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DISPLAY_COLOR,
        )
        val events = mutableListOf<CalendarEvent>()
        runCatching {
            // Solo i calendari visibili nell'app Calendario, senza gli inviti rifiutati.
            val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
                "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    events += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Senza titolo)",
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) == 1,
                        location = c.getString(5).orEmpty(),
                        color = c.getInt(6),
                    )
                }
            }
        }
        // I treni di Gmail: un viaggio solo, con partenza e arrivo.
        CalendarDays.group(Trips.shape(events), first, days, zone)
    }

    /** Tutto quello che serve per la scheda di un evento; null se non esiste più. */
    suspend fun event(context: Context, id: Long): EventDetail? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.DURATION,
        )
        runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), projection, null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val begin = c.getLong(1)
                val end = if (c.isNull(2)) begin + CalendarDays.durationMs(c.getString(11)) else c.getLong(2)
                EventDetail(
                    id = id,
                    title = c.getString(0).orEmpty(),
                    begin = begin,
                    end = end,
                    allDay = c.getInt(3) == 1,
                    location = c.getString(4).orEmpty(),
                    description = c.getString(5).orEmpty(),
                    recurring = !c.getString(6).isNullOrBlank(),
                    calendarId = c.getLong(7),
                    calendarName = c.getString(8).orEmpty(),
                    writable = c.getInt(9) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    color = c.getInt(10),
                )
            }
        }.getOrNull()
    }

    /** I calendari in cui si può scrivere, il principale per primo. */
    suspend fun writableCalendars(context: Context): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        val list = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
                while (c.moveToNext()) {
                    list += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty().ifBlank { c.getString(2).orEmpty() },
                        account = c.getString(2).orEmpty(),
                        color = c.getInt(3),
                        primary = c.getInt(4) == 1,
                    )
                }
            }
        }
        list.sortedByDescending { it.primary }
    }

    /** Crea o aggiorna l'evento; restituisce il suo id, null se il calendario ha rifiutato. */
    suspend fun save(context: Context, draft: EventDraft, zone: ZoneId = ZoneId.systemDefault()): Long? = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext null
        val values = ContentValues()
        draft.toValues(zone).forEach { (key, value) ->
            when (value) {
                null -> values.putNull(key)
                is Long -> values.put(key, value)
                is Int -> values.put(key, value)
                else -> values.put(key, value.toString())
            }
        }
        runCatching {
            val id = draft.id
            if (id == null) {
                context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
            } else {
                val rows = context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), values, null, null,
                )
                id.takeIf { rows > 0 }
            }
        }.getOrNull()
    }

    /** Elimina l'evento (per quelli che si ripetono, tutta la serie). */
    suspend fun delete(context: Context, id: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext false
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
        }.getOrDefault(false)
    }

    private const val DAY_MS = 24 * 60 * 60_000L
}
