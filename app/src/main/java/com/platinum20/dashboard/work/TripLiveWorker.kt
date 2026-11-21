package com.platinum20.dashboard.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.calendar.CalendarEvent
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.tickets.TicketActivity
import com.platinum20.dashboard.tickets.TicketQrStore
import com.platinum20.dashboard.trips.LiveCard
import com.platinum20.dashboard.trips.LivePhase
import com.platinum20.dashboard.trips.LiveTrip
import com.platinum20.dashboard.trips.TrainLive
import com.platinum20.dashboard.trips.TrackArt
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Il viaggio vivo (1.467): ogni 15 minuti guarda se fra poco parte un treno (o inizia un turno, se acceso);
 * da lì gira ogni minuto finché il viaggio non è finito, legge il ritardo ogni 2 minuti e tiene viva una notifica
 * sola per viaggio. Disegnata da noi come la proposta A (1.536).
 */
class TripLiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        // L'ultimo ritardo letto, anche da un giro precedente o prima di un riavvio: senza rete restano quegli orari.
        TrainLive.init(context)
        val trains = AppSettings.liveTrips.value
        val shifts = AppSettings.liveShifts.value
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shown = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty()
        val now = System.currentTimeMillis()

        val today = LocalDate.now()
        val events = if (!trains && !shifts) emptyList() else runCatching {
            CalendarRepository.eventsFrom(context, today.minusDays(1), days = 3).values.flatten().distinctBy { it.eventId }
        }.getOrDefault(emptyList())
        val active = events.filter { e ->
            if (e.trip != null) trains else shifts && e.eventId < -1
        }.filter { LiveTrip.phase(it, now, TrainLive.known(it.eventId, now)) != null }

        val posted = mutableSetOf<String>()
        if (MorningWorker.canNotify(context)) for (event in active) {
            var status = TrainLive.known(event.eventId, now)
            val trip = event.trip
            val lastRead = prefs.getLong("read_${event.eventId}", 0L)
            if (trip != null && now - lastRead >= READ_EVERY_MS) {
                prefs.edit().putLong("read_${event.eventId}", now).apply()
                status = TrainLive.refresh(event.eventId, trip, event.begin, event.end) ?: status
                // Il biglietto pronto prima di arrivare ai tornelli, anche senza rete in stazione.
                if (TicketQrStore.cached(context, event.eventId).isEmpty()) runCatching {
                    CalendarRepository.event(context, event.eventId)?.let { TicketQrStore.find(context, it) }
                }
            }
            val card = LiveTrip.card(event, now, status, checked = trip != null && lastRead > 0) ?: continue
            post(context, event, card, now)
            posted += event.eventId.toString()
        }
        // I treni già arrivati (sull'orario del biglietto) di cui non sappiamo com'è finita: una lettura ogni 15 minuti
        // finché non è chiusa, così il Calendario tiene il ritardo vero anche se l'abbiamo perso (2026-11-20).
        if (trains) for (event in events) {
            val trip = event.trip ?: continue
            if (event.eventId in active.map { it.eventId }) continue
            TrainLive.close(event.eventId, event.end, now)
            val known = TrainLive.known(event.eventId, now)
            if (!TrainLive.needsLastRead(known, event.end, now)) continue
            val lastRead = prefs.getLong("read_${event.eventId}", 0L)
            if (now - lastRead < LATE_READ_EVERY_MS) continue
            prefs.edit().putLong("read_${event.eventId}", now).apply()
            TrainLive.refresh(event.eventId, trip, event.begin, event.end)
        }
        // Le notifiche dei viaggi finiti (o spenti) se ne vanno.
        (shown - posted).forEach { NotificationManagerCompat.from(context).cancel(notificationId(it.toLong())) }
        prefs.edit().putStringSet(KEY_SHOWN, posted).apply()

        // Ogni minuto finché c'è un viaggio vivo, o uno che comincia entro il prossimo giro da 15 minuti.
        val soon = events.any { e -> (e.trip != null && trains || shifts && e.eventId < -1) && e.begin in now..now + LiveTrip.LEAD_MS + 16 * 60_000L }
        if (posted.isNotEmpty() || soon) tick(context, if (posted.isNotEmpty()) 1L else 5L, chained = inputData.getBoolean(KEY_TICK, false))
        return Result.success()
    }

    companion object {
        /** Disegna (o ridisegna) la notifica di [event]; [now] = l'ora da mostrare (la prova usa un orologio finto). */
    @SuppressLint("MissingPermission")
        fun post(context: Context, event: CalendarEvent, card: LiveCard, now: Long = System.currentTimeMillis()) {
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            OpenRequest(OpenRequest.EVENT, url = "${event.eventId}/${event.begin}/${event.end}").writeTo(open)
            val id = notificationId(event.eventId)
            val accent = if (card.late) LATE_COLOR else CANVAS_BLUE
            val text = marked(card.text, LiveCard.TEXT, card)
            // Niente elenco delle fermate né tasto "Fermate" (Antonio, 2026-11-21): la notifica dice solo l'essenziale.
            val body: List<CharSequence> = card.lines.mapIndexed { i, line -> marked(line, i, card) }
            // Disegnata da noi, come la proposta A (Antonio, 2026-11-20): la ProgressStyle di One UI non sa fare pallini
            // tondi, orari sotto la linea, barrato né più di due righe. Il sistema tiene icona, nome e "arriva tra".
            val small = android.widget.RemoteViews(context.packageName, R.layout.notif_trip_small).apply {
                setTextViewText(R.id.trip_title, card.title)
                setTextViewText(R.id.trip_text, text)
            }
            val big = android.widget.RemoteViews(context.packageName, R.layout.notif_trip_big).apply {
                setTextViewText(R.id.trip_title, card.title)
                setTextViewText(R.id.trip_next, card.next ?: "")
                setViewVisibility(R.id.trip_next, if (card.next == null) android.view.View.GONE else android.view.View.VISIBLE)
                setTextViewText(R.id.trip_sub, card.sub ?: "")
                setViewVisibility(R.id.trip_sub, if (card.sub == null) android.view.View.GONE else android.view.View.VISIBLE)
                card.track?.let { track ->
                    val arrived = card.phase == LivePhase.ON_BOARD && now >= track.end
                    runCatching { setImageViewBitmap(R.id.trip_track, TrackArt.draw(context, track.steps(now), accent, arrived)) }
                }
                setTextViewText(R.id.trip_depart, card.depart?.let { marked(it, LiveCard.DEPART, card) } ?: "")
                setTextViewText(R.id.trip_arrive, card.arrive?.let { marked(it, LiveCard.ARRIVE, card) } ?: "")
                setTextViewText(R.id.trip_lines, join(body))
                setViewVisibility(R.id.trip_lines, if (body.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE)
            }
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(card.title)
                .setContentText(text)
                // Nell'intestazione "arriva tra 3 h 54 min" (2026-11-20): il cronometro incollato al titolo non si leggeva.
                .setSubText(card.header?.let { marked(it, LiveCard.HEADER, card) })
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big)
                .setCustomHeadsUpContentView(small)
                // Arancio = attenzione (in ritardo o soppresso), altrimenti il blu della Canvas: un colore, un significato.
                .setColor(accent)
                .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // Col ritardo: un treno in ritardo di due ore non perde la notifica all'arrivo del biglietto (2026-11-20).
                .setTimeoutAfter((event.end + TrainLive.shiftMs(TrainLive.known(event.eventId, now)) + 2 * LiveTrip.AFTER_MS + 60 * 60_000L - now).coerceAtLeast(60_000L))
            if (card.phase == LivePhase.ON_BOARD || event.trip != null) {
                if (TicketQrStore.cached(context, event.eventId).isNotEmpty()) {
                    builder.addAction(0, "Biglietto", TicketActivity.pending(context, event.eventId))
                }
            }
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }

        /** Le righe della notifica aperta, una per riga, tenendo colori e barrati. */
        private fun join(lines: List<CharSequence>): CharSequence {
            val out = android.text.SpannableStringBuilder()
            lines.forEachIndexed { i, l -> if (i > 0) out.append('\n'); out.append(l) }
            return out
        }

        /** La riga [line] con i suoi pezzi arancio (ritardo) o barrati (l'orario del biglietto superato). */
        private fun marked(textLine: String, line: Int, card: LiveCard): CharSequence {
            val marks = card.marks.filter { it.line == line }
            if (marks.isEmpty()) return textLine
            val out = android.text.SpannableString(textLine)
            var from = 0
            for (m in marks) {
                val at = textLine.indexOf(m.text, from).takeIf { it >= 0 } ?: textLine.indexOf(m.text).takeIf { it >= 0 } ?: continue
                val span: Any = when (m.kind) {
                    com.platinum20.dashboard.trips.MarkKind.LATE -> android.text.style.ForegroundColorSpan(LATE_TEXT)
                    com.platinum20.dashboard.trips.MarkKind.STRUCK -> android.text.style.StrikethroughSpan()
                    com.platinum20.dashboard.trips.MarkKind.SOFT -> android.text.style.ForegroundColorSpan(PASSED_TEXT)
                }
                out.setSpan(span, at, at + m.text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                from = at + m.text.length
            }
            return out
        }

        /** La preferenza "Fermate aperte" di un viaggio. */
        fun stopsKey(eventId: Long) = "stops_$eventId"

        /** Ridisegna subito (dal tasto "Fermate"), senza toccare la catena dei giri da un minuto. */
        fun now(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<TripLiveWorker>().build())
        }

        private const val NOW = "trip_live_now"
        private const val WORK = "trip_live"
        private const val TICK = "trip_live_tick"
        private const val CHANNEL = "trip_live"
        const val PREFS = "trip_live"
        private const val KEY_SHOWN = "shown"
        private const val KEY_TICK = "tick"
        private const val READ_EVERY_MS = 2 * 60_000L
        private const val LATE_READ_EVERY_MS = 15 * 60_000L
        private const val NOTIFICATION_BASE = 7_000
        private const val CANVAS_BLUE = 0xFF3550E8.toInt()
        private const val LATE_COLOR = 0xFFD9572B.toInt()
        /** L'arancio del testo: più chiaro, si legge sia sul chiaro sia sullo scuro. */
        private const val LATE_TEXT = 0xFFF08A3C.toInt()
        /** Le fermate già passate, spente. */
        private const val PASSED_TEXT = 0xFF8A8A8A.toInt()

        fun notificationId(eventId: Long): Int = NOTIFICATION_BASE + Math.floorMod(eventId, 1_000L).toInt()

        /** Il giro ogni 15 minuti (senza rete richiesta: il conto alla rovescia vale anche offline). */
        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            if (!AppSettings.liveTrips.value && !AppSettings.liveShifts.value) {
                wm.cancelUniqueWork(WORK)
                wm.cancelUniqueWork(TICK)
                return
            }
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<TripLiveWorker>(15, TimeUnit.MINUTES).build())
            tick(context, 0L, chained = false)
        }

        /**
         * Il prossimo giro fra [minutes]. Da un giro della catena ([chained]) si mette in coda dopo sé stesso;
         * dal giro dei 15 minuti o dall'avvio solo se la catena non c'è già, così non ne nascono due.
         */
        private fun tick(context: Context, minutes: Long, chained: Boolean) {
            val request = OneTimeWorkRequestBuilder<TripLiveWorker>()
                .setInitialDelay(minutes, TimeUnit.MINUTES)
                .setInputData(androidx.work.workDataOf(KEY_TICK to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                TICK,
                if (chained) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Viaggio vivo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Treni e turni in arrivo: minuti alla partenza, ritardo, binario e biglietto"
                },
            )
        }
    }
}
