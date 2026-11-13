package com.platinum20.dashboard.work

import android.annotation.SuppressLint
import android.app.ActivityManager
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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.net.HeavyDownloads
import com.platinum20.dashboard.update.ApkInstaller
import com.platinum20.dashboard.update.UpdateRepository
import com.platinum20.dashboard.update.compareVersions
import java.util.concurrent.TimeUnit

/**
 * Controlla spesso le release su GitHub. Quando Android lo permette scarica e installa
 * la nuova versione da solo, senza chiedere; altrimenti avvisa con una notifica.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        if (!AppSettings.updateNotify.value) return Result.success()

        val repository = UpdateRepository(context)
        val releases = runCatching { repository.fetchReleases() }
            .getOrElse { return if (runAttemptCount < 2) Result.retry() else Result.success() }
        com.platinum20.dashboard.update.UpdateText.markChecked(context)
        val latest = releases.firstOrNull()
            ?.takeIf { compareVersions(it.version, BuildConfig.VERSION_NAME) > 0 && it.apkUrl != null }
            ?: return Result.success()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val tries = if (prefs.getString(KEY_TRIED, null) == latest.version) prefs.getInt(KEY_TRIES, 0) else 0
        val now = System.currentTimeMillis()
        if (prefs.getString(KEY_SEEN, null) != latest.version) {
            prefs.edit().putString(KEY_SEEN, latest.version).putLong(KEY_SEEN_AT, now).apply()
        }
        val step = step(
            silent = ApkInstaller.canInstallSilently(context),
            inUse = inUse(),
            waitWifi = HeavyDownloads.blocked(context),
            tries = tries,
            notified = prefs.getString(KEY_NOTIFIED, null) == latest.version,
            waitedMinutes = (now - prefs.getLong(KEY_SEEN_AT, now)) / 60_000,
        )
        when (step) {
            Step.NOTHING, Step.WAIT -> Unit
            Step.NOTIFY -> notifyManual(context, latest.version, latest.notes)
            Step.NOTIFY_WAITING -> notifyManual(context, latest.version, latest.notes, keepTrying = true)
            Step.INSTALL -> {
                prefs.edit().putString(KEY_TRIED, latest.version).putInt(KEY_TRIES, tries + 1).apply()
                runCatching {
                    val apk = repository.downloadApk(latest) {}
                    ApkInstaller.install(context, apk, latest.version, fromUi = false)
                }
            }
        }
        return Result.success()
    }

    /** Vero se Canvas è sullo schermo o sta suonando: aggiornare la chiuderebbe sotto le dita. */
    private fun inUse(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return inUse(info.importance, com.platinum20.dashboard.media.Playback.now.value?.playing == true)
    }

    /** NOTIFY_WAITING = avvisa ma continua a provare in silenzio quando Canvas si libera. */
    enum class Step { NOTHING, WAIT, INSTALL, NOTIFY, NOTIFY_WAITING }

    companion object {
        private const val WORK = "update_check"
        private const val WORK_NOW = "update_check_now"
        internal const val CHECK_MINUTES = 15L
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 2
        private const val PREFS = "update_check"
        private const val KEY_NOTIFIED = "notified_version"
        private const val KEY_TRIED = "auto_version"
        private const val KEY_TRIES = "auto_tries"
        private const val KEY_SEEN = "seen_version"
        private const val KEY_SEEN_AT = "seen_at"
        internal const val MAX_TRIES = 3

        /**
         * Dopo quanto aspettare che Canvas si liberi prima di avvisare comunque: con il player o
         * un servizio sempre attivo "in uso" può durare per sempre, e l'aggiornamento non arrivava mai.
         */
        internal const val WAIT_NOTIFY_MINUTES = 60L

        /**
         * Solo schermo visibile o audio che suona contano come "in uso". Prima bastava
         * importance <= VISIBLE, ma il lettore di notifiche (sempre collegato da Android) tiene
         * Canvas a FOREGROUND_SERVICE tutto il giorno: l'aggiornamento automatico non partiva mai.
         */
        internal fun inUse(importance: Int, playing: Boolean): Boolean =
            playing ||
                importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

        /**
         * Cosa fare con una versione nuova. Si installa da sola se Android lo permette e Canvas
         * non è in uso (altrimenti si riprova al giro dopo); dopo [MAX_TRIES] tentativi falliti,
         * o senza installazione silenziosa, resta la notifica di sempre, una volta sola.
         */
        internal fun step(
            silent: Boolean,
            inUse: Boolean,
            waitWifi: Boolean,
            tries: Int,
            notified: Boolean,
            waitedMinutes: Long = 0,
        ): Step = when {
            silent && tries < MAX_TRIES -> when {
                !inUse && !waitWifi -> Step.INSTALL
                !notified && waitedMinutes >= WAIT_NOTIFY_MINUTES -> Step.NOTIFY_WAITING
                else -> Step.WAIT
            }
            notified -> Step.NOTHING
            else -> Step.NOTIFY
        }

        /** L'avviso "tocca per aggiornare", una volta per versione. */
        fun notifyManual(context: Context, version: String, notes: String = "", keepTrying: Boolean = false) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Se in silenzio non è andata, non la riscarichiamo a ogni giro.
            if (!keepTrying) prefs.edit().putString(KEY_TRIED, version).putInt(KEY_TRIES, MAX_TRIES).apply()
            if (!MorningWorker.canNotify(context)) return
            if (prefs.getString(KEY_NOTIFIED, null) == version) return
            // Le prime righe del changelog, senza titoli e simboli del Markdown.
            val lines = notes.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { "• " + it.removePrefix("- ").removePrefix("* ").replace("**", "") }
                .take(5)
            post(
                context,
                title = "Nuova versione $version disponibile",
                text = "Installata: ${BuildConfig.VERSION_NAME}. Tocca per aggiornare.",
                lines = lines,
            )
            prefs.edit().putString(KEY_NOTIFIED, version).apply()
        }

        /** Dopo l'aggiornamento: Canvas si è chiusa, così sai perché e cosa c'è di nuovo. */
        fun installed(context: Context) {
            AppSettings.init(context)
            if (!AppSettings.updateNotify.value || !MorningWorker.canNotify(context)) return
            post(
                context,
                title = "Canvas aggiornata alla ${BuildConfig.VERSION_NAME}",
                text = "Tocca per vedere le novità.",
                lines = emptyList(),
            )
        }

        @SuppressLint("MissingPermission")
        private fun post(context: Context, title: String, text: String, lines: List<String>) {
            ensureChannel(context)
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            OpenRequest(OpenRequest.UPDATES).writeTo(intent)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText((listOf(text, "") + lines).joinToString("\n").trim()))
                .setContentIntent(
                    PendingIntent.getActivity(context, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }

        /**
         * Avvia il controllo ogni 15 minuti (il minimo che Android concede, con la rete) più uno subito,
         * così l'avviso arriva appena esce una versione; lo ferma se disattivato.
         * UPDATE porta al nuovo intervallo anche chi aveva ancora il vecchio controllo ogni 6 ore.
         */
        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            if (!AppSettings.updateNotify.value) {
                wm.cancelUniqueWork(WORK)
                wm.cancelUniqueWork(WORK_NOW)
                return
            }
            val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(CHECK_MINUTES, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
            wm.enqueueUniqueWork(
                WORK_NOW, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(network).build(),
            )
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
