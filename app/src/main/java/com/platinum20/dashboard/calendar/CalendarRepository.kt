package com.platinum20.dashboard.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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

    /** Eventi del giorno indicato, prima quelli di tutto il giorno e poi in ordine di orario. */
    suspend fun eventsOn(context: Context, day: LocalDate = LocalDate.now()): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

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
                    val allDay = c.getInt(4) == 1
                    val begin = c.getLong(2)
                    val finish = c.getLong(3)
                    // Gli eventi di tutto il giorno sono salvati in UTC: li confrontiamo per data, non per ora.
                    val inDay = if (allDay) {
                        val first = Instant.ofEpochMilli(begin).atOffset(ZoneOffset.UTC).toLocalDate()
                        val last = Instant.ofEpochMilli(finish).atOffset(ZoneOffset.UTC).toLocalDate()
                        !day.isBefore(first) && day.isBefore(maxOf(last, first.plusDays(1)))
                    } else {
                        begin < end && finish > start
                    }
                    if (!inDay) continue
                    events += CalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(Senza titolo)",
                        begin = begin,
                        end = finish,
                        allDay = allDay,
                        location = c.getString(5).orEmpty(),
                        color = c.getInt(6),
                    )
                }
            }
        }
        events.distinctBy { it.eventId to it.begin }
            .sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
    }

    fun openEvent(context: Context, event: CalendarEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openCalendar(context: Context) {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }.build()
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun newEvent(context: Context) {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private const val DAY_MS = 24 * 60 * 60_000L
}
