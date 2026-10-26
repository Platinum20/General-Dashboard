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
import com.platinum20.dashboard.mail.GmailAuth
import com.platinum20.dashboard.mail.MailStore
import java.util.concurrent.TimeUnit

/** Legge le mail nuove di Gmail ogni ora, e subito quando arriva una notifica di Gmail. */
class MailWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        GmailAuth.init(applicationContext)
        if (!GmailAuth.connected.value) return Result.success()
        val result = runCatching { MailStore.sync(applicationContext) }
        return if (result.isFailure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "gmail_sync"
        private const val ONCE = "gmail_sync_now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MailWorker>(1, TimeUnit.HOURS).setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            // Più mail di fila: una lettura sola, un attimo dopo l'ultima.
            val request = OneTimeWorkRequestBuilder<MailWorker>().setConstraints(network)
                .setInitialDelay(20, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(ONCE)
        }
    }
}
