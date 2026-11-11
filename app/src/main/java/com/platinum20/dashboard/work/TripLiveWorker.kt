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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Il viaggio vivo (1.467): ogni 15 minuti guarda se fra poco parte un treno (o inizia un turno, se acceso);
 * da lì gira ogni minuto finché il viaggio non è finito, legge il ritardo ogni 2 minuti e tiene viva una notifica
 * sola per viaggio. Su Android 16+ chiede di stare in alto sulla schermata di blocco (Live Update).
 */
class TripLiveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
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
        }.filter { LiveTrip.phase(it, now, TrainLive.fresh(it.eventId, now)) != null }

        val posted = mutableSetOf<String>()
        if (MorningWorker.canNotify(context)) for (event in active) {
            var status = TrainLive.fresh(event.eventId, now)
            val trip = event.trip
            val lastRead = prefs.getLong("read_${event.eventId}", 0L)
            if (trip != null && now - lastRead >= READ_EVERY_MS) {
                prefs.edit().putLong("read_${event.eventId}", now).apply()
                status = TrainLive.refresh(event.eventId, trip, event.begin) ?: status
                // Il biglietto pronto prima di arrivare ai tornelli, anche senza rete in stazione.
                if (TicketQrStore.cached(context, event.eventId).isEmpty()) runCatching {
                    CalendarRepository.event(context, event.eventId)?.let { TicketQrStore.find(context, it) }
                }
            }
            val card = LiveTrip.card(event, now, status, checked = trip != null && lastRead > 0) ?: continue
            post(context, event, card)
            posted += event.eventId.toString()
        }
        // Le notifiche dei viaggi finiti (o spenti) se ne vanno.
        (shown - posted).forEach { NotificationManagerCompat.from(context).cancel(notificationId(it.toLong())) }
        prefs.edit().putStringSet(KEY_SHOWN, posted).apply()

        // Ogni minuto finché c'è un viaggio vivo, o uno che comincia entro il prossimo giro da 15 minuti.
        val soon = events.any { e -> (e.trip != null && trains || shifts && e.eventId < -1) && e.begin in now..now + LiveTrip.LEAD_MS + 16 * 60_000L }
        if (posted.isNotEmpty() || soon) tick(context, if (posted.isNotEmpty()) 1L else 5L, chained = inputData.getBoolean(KEY_TICK, false))
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, event: CalendarEvent, card: LiveCard) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.EVENT, url = "${event.eventId}/${event.begin}/${event.end}").writeTo(open)
        val id = notificationId(event.eventId)
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(card.title)
            .setContentText(card.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(card.text))
            .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(card.countdownTo)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter((event.end + 2 * LiveTrip.AFTER_MS + 60 * 60_000L - System.currentTimeMillis()).coerceAtLeast(60_000L))
        card.progress?.let { builder.setProgress(100, it, false) }
        if (card.phase == LivePhase.ON_BOARD || event.trip != null) {
            if (TicketQrStore.cached(context, event.eventId).isNotEmpty()) {
                builder.addAction(0, "Biglietto", TicketActivity.pending(context, event.eventId))
            }
        }
        // Android 16+: Live Update in alto sulla schermata di blocco, con l'orario nel chip della barra di stato.
        builder.extras.putBoolean(EXTRA_PROMOTED, true)
        builder.extras.putCharSequence(EXTRA_CHIP, card.chip)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    companion object {
        private const val WORK = "trip_live"
        private const val TICK = "trip_live_tick"
        private const val CHANNEL = "trip_live"
        private const val PREFS = "trip_live"
        private const val KEY_SHOWN = "shown"
        private const val KEY_TICK = "tick"
        private const val READ_EVERY_MS = 2 * 60_000L
        private const val NOTIFICATION_BASE = 7_000
        /** `Notification.EXTRA_REQUEST_PROMOTED_ONGOING` e `EXTRA_SHORT_CRITICAL_TEXT` (API 36), scritti a mano. */
        private const val EXTRA_PROMOTED = "android.requestPromotedOngoing"
        private const val EXTRA_CHIP = "android.shortCriticalText"

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
