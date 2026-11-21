package com.platinum20.dashboard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.media.SavedDownloads
import com.platinum20.dashboard.settings.AppSettings
import java.util.concurrent.TimeUnit

/**
 * Scarica i video e gli episodi salvati (2026-11-21, Antonio: al posto dello zaino del treno). Parte appena salvi
 * qualcosa ([now]); con "Scarica solo col Wi-Fi" acceso aspetta una rete che non consuma dati. Il giro ogni ora
 * recupera quello rimasto indietro e butta i file delle cose tolte dai salvati.
 */
class SavedDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { SavedDownloads.sync(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK = "saved_downloads"
        private const val NOW = "saved_downloads_now"
        /** Il vecchio giro dello zaino del treno: si spegne. */
        private const val OLD_WORK = "train_pack"

        private fun network(): NetworkType =
            if (AppSettings.wifiOnlyDownloads.value) NetworkType.UNMETERED else NetworkType.CONNECTED

        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(OLD_WORK)
            val request = PeriodicWorkRequestBuilder<SavedDownloadWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network()).build())
                .build()
            // UPDATE: se cambia "Scarica solo col Wi-Fi", il giro prende la rete giusta.
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** Appena salvi o togli qualcosa: allinea i file, alla prima rete adatta. */
        fun now(context: Context) {
            AppSettings.init(context)
            val request = OneTimeWorkRequestBuilder<SavedDownloadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(network()).build())
                .build()
            // In coda dietro a quello che sta già scaricando, che non si interrompe.
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
