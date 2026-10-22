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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.weather.RainAlert
import com.platinum20.dashboard.weather.WeatherStore
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Ogni quarto d'ora guarda il nowcast e avvisa se sta per piovere (solo tra le 7 e le 23). */
class RainAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        if (!AppSettings.rainAlert.value) return Result.success()
        val now = LocalDateTime.now()
        if (now.hour !in RainAlert.FIRST_HOUR..RainAlert.LAST_HOUR) return Result.success()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }
            ?.let { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        // In sottofondo niente posizione nuova: l'ultima nota basta per il meteo della zona.
        val forecast = runCatching { WeatherStore.fetch(context, freshLocation = false) }.getOrNull()
            ?: return Result.success()
        val notice = RainAlert.decide(forecast.nowcast, now, last) ?: return Result.success()
        if (MorningWorker.canNotify(context)) {
            post(context, notice.text(forecast.place))
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, text: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.WEATHER).writeTo(intent)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("☔ Sta per piovere")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(context, 300, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .setAutoCancel(true)
            .setTimeoutAfter(TimeUnit.HOURS.toMillis(2))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK = "rain_alert"
        private const val CHANNEL = "rain"
        private const val NOTIFICATION_ID = 3
        private const val PREFS = "rain_alert"
        private const val KEY_LAST = "last_alert"

        /** Avvia il controllo ogni 15 minuti con la rete, o lo ferma se l'avviso è spento. */
        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            if (!AppSettings.rainAlert.value) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<RainAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL, "Avviso pioggia", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quando sta per piovere dove sei"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
