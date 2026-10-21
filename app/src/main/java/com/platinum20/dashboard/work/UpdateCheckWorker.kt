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
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.update.GitHubRelease
import com.platinum20.dashboard.update.UpdateRepository
import com.platinum20.dashboard.update.compareVersions
import java.util.concurrent.TimeUnit

/** Controlla ogni tanto le release su GitHub e avvisa quando ne esce una nuova. */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        if (!AppSettings.updateNotify.value) return Result.success()

        val releases = runCatching { UpdateRepository(context).fetchReleases() }
            .getOrElse { return if (runAttemptCount < 2) Result.retry() else Result.success() }
        val latest = releases.firstOrNull()
            ?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 && it.apkUrl != null }
            ?: return Result.success()

        // Un solo avviso per versione: se l'hai già ricevuto non te lo ripetiamo ogni giro.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == latest.version) return Result.success()
        if (MorningWorker.canNotify(context)) {
            post(context, latest)
            prefs.edit().putString(KEY_NOTIFIED, latest.version).apply()
        }
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun post(context: Context, release: GitHubRelease) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.UPDATES).writeTo(intent)

        // Le prime righe del changelog, senza titoli e simboli del Markdown.
        val notes = release.notes.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { "• " + it.removePrefix("- ").removePrefix("* ").replace("**", "") }
            .take(5)
        val text = "Installata: ${BuildConfig.VERSION_NAME}. Tocca per aggiornare."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Nuova versione ${release.version} disponibile")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(text, "") + notes).joinToString("\n").trim()))
            .setContentIntent(
                PendingIntent.getActivity(context, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val WORK = "update_check"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 2
        private const val PREFS = "update_check"
        private const val KEY_NOTIFIED = "notified_version"

        /** Avvia il controllo periodico (ogni 6 ore, con la rete), o lo ferma se disattivato. */
        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            if (!AppSettings.updateNotify.value) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Toglie l'avviso quando l'utente apre la schermata Aggiornamenti. */
        fun dismiss(context: Context) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(CHANNEL, "Nuove versioni", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Avviso quando esce un aggiornamento dell'app"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
