package com.platinum20.dashboard.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
import com.platinum20.dashboard.net.DiskCache
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import com.platinum20.dashboard.weather.WeatherCodes
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.weather.asTemp
import com.platinum20.dashboard.widget.DashboardWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Notifica del mattino: meteo, la giornata come in "Adesso" e le cose da fare. Le notizie no. */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        DiskCache.init(context)
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
        NotesStore.init(context)
        val notes = NotesStore.data.value
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone)
        val calendar = CalendarRepository.hasPermission(context)
        // Le stesse righe di "Adesso" nella Home: tutta la giornata, orari assoluti (la notifica resta lì ore).
        val adesso = Briefing.build(
            now = LocalDateTime.now(zone),
            forecast = forecast,
            today = if (calendar) runCatching { CalendarRepository.eventsOn(context, day) }.getOrNull() else null,
            tomorrow = null,
            tasks = notes.openTasks,
            countdowns = notes.countdowns,
            zone = zone,
            max = Briefing.CARD_MAX,
            relative = false,
            habits = notes.habits,
            parcels = notes.parcels,
            fixedExpenses = notes.fixedExpenses,
            withTomorrow = false,
            wholeDay = true,
        )

        val title = if (forecast != null) {
            val c = forecast.current
            "${WeatherCodes.emoji(c.code, c.isDay)} ${c.temperature.asTemp()} a ${forecast.place}, ${WeatherCodes.description(c.code).lowercase()}"
        } else {
            "Buongiorno!"
        }
        val groups = MorningText.groups(
            today = forecast?.today,
            air = forecast?.air,
            adesso = adesso,
            tasks = notes.openTasks,
            endOfDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        // A notifica chiusa si legge una riga sola: quella più utile di "Adesso", come nella Home.
        val summary = adesso.firstOrNull { it.kind != BriefKind.WEATHER }?.text
            ?: groups.firstOrNull()?.lines?.firstOrNull() ?: "Apri Canvas per il riepilogo della giornata."

        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.HOME).writeTo(intent)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(render(groups)))
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

    /**
     * Gruppi staccati da una riga vuota a mezza altezza, titoli in grassetto. Una riga davvero vuota
     * Android la toglie dalle notifiche: lo spazio non separabile invece resta.
     */
    private fun render(groups: List<MorningGroup>): CharSequence {
        val text = SpannableStringBuilder()
        groups.forEachIndexed { i, group ->
            if (i > 0) {
                val from = text.length
                text.append("\u00A0\n")
                text.setSpan(RelativeSizeSpan(0.6f), from, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            group.header?.let {
                val from = text.length
                text.append(it).append('\n')
                text.setSpan(StyleSpan(Typeface.BOLD), from, from + it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            group.lines.forEach { line -> text.append(if (group.header != null) "• $line" else line).append('\n') }
        }
        while (text.isNotEmpty() && text.last() == '\n') text.delete(text.length - 1, text.length)
        return text
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
                description = "Meteo, impegni e cose da fare ogni mattina"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
