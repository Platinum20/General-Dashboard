package com.platinum20.dashboard.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.widget.DashboardWidget
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Notifica del mattino: meteo, impegni, promemoria e temi del giorno. */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        val test = inputData.getBoolean(KEY_TEST, false)
        val settings = AppSettings.morning.value
        if (!test && !settings.enabled) return Result.success()

        // Se Android ci ha svegliato con molto ritardo (telefono spento, risparmio energetico) saltiamo il giro.
        val late = Duration.between(LocalTime.of(settings.hour, settings.minute), LocalTime.now()).toMinutes()
        if (test || late in -10..180) post(context)
        if (!test) schedule(context)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private suspend fun post(context: Context) {
        val forecast = runCatching { WeatherStore.fetch(context, freshLocation = false) }.getOrNull()
            ?: WeatherStore.cached(context)
        val themes = runCatching { NewsRecap.build(NewsStore.load(force = true).items) }.getOrDefault(emptyList())
        if (themes.isNotEmpty()) DashboardWidget.saveRecap(context, themes)
        val events = runCatching { CalendarRepository.eventsOn(context) }.getOrDefault(emptyList())
        NotesStore.init(context)
        val tasks = NotesStore.data.value.openTasks

        val title = if (forecast != null) {
            val c = forecast.current
            "${WeatherCodes.emoji(c.code, c.isDay)} ${c.temperature.asTemp()} a ${forecast.place}, ${WeatherCodes.description(c.code).lowercase()}"
        } else {
            "Buongiorno!"
        }
        val lines = mutableListOf<String>()
        forecast?.today?.let { d ->
            lines += "Oggi max ${d.max.asTemp()}, min ${d.min.asTemp()}" +
                if (d.precipitationProbability >= 30) ", pioggia ${d.precipitationProbability}%" else ""
        }
        forecast?.rainAlert?.let { lines += "☔ $it" }
        forecast?.air?.let { lines += "Aria: ${it.label.lowercase()}" }
        if (events.isNotEmpty()) {
            val time = DateTimeFormatter.ofPattern("HH:mm")
            val zone = ZoneId.systemDefault()
            lines += ""
            lines += "📅 " + if (events.size == 1) "1 impegno" else "${events.size} impegni"
            events.take(3).forEach { e ->
                val at = if (e.allDay) "tutto il giorno" else Instant.ofEpochMilli(e.begin).atZone(zone).format(time)
                lines += "• $at ${e.title}"
            }
        }
        if (tasks.isNotEmpty()) {
            lines += ""
            lines += "✅ " + if (tasks.size == 1) "1 cosa da fare" else "${tasks.size} cose da fare"
            tasks.take(3).forEach { lines += "• ${it.text}" }
        }
        if (themes.isNotEmpty()) {
            lines += ""
            lines += "📰 Il punto del giorno"
            themes.take(3).forEach { lines += "• ${it.headline.title}" }
        }
        val summary = lines.firstOrNull { it.isNotBlank() } ?: "Apri la Dashboard per il riepilogo della giornata."

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.HOME).writeTo(intent)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n").trim()))
            .setContentIntent(
                PendingIntent.getActivity(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val WORK = "morning_brief"
        private const val WORK_TEST = "morning_brief_test"
        private const val KEY_TEST = "test"
        private const val CHANNEL = "morning"
        private const val NOTIFICATION_ID = 1

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** Programma la prossima notifica all'orario scelto, o la annulla se disattivata. */
        fun schedule(context: Context) {
            AppSettings.init(context)
            val settings = AppSettings.morning.value
            val wm = WorkManager.getInstance(context)
            if (!settings.enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(settings.hour, settings.minute)
            if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMillis()
            val request = OneTimeWorkRequestBuilder<MorningWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun sendTest(context: Context) {
            val request = OneTimeWorkRequestBuilder<MorningWorker>()
                .setInputData(workDataOf(KEY_TEST to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_TEST, ExistingWorkPolicy.REPLACE, request)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL, "Notifica del mattino", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Meteo, impegni e notizie ogni mattina"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
