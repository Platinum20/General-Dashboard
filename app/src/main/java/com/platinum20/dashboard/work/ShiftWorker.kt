package com.platinum20.dashboard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.shifts.CreatorsLink
import com.platinum20.dashboard.shifts.ShiftStore
import java.util.concurrent.TimeUnit

/**
 * Rilegge i turni anche con l'app chiusa, ogni 15 minuti (il minimo di Android): un turno messo o
 * tolto dall'agenzia arriva nel calendario e nella notifica del mattino senza aprire Canvas.
 */
class ShiftWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ShiftStore.init(applicationContext)
        if (!ShiftStore.linked.value) {
            cancel(applicationContext)
            return Result.success()
        }
        // Anche se sembra scaduto si riprova, una volta l'ora: se il sito riprende la sessione torna da solo.
        if (ShiftStore.expired.value && !retryDue(applicationContext)) return Result.success()
        runCatching { CreatorsLink.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "shifts_refresh"
        private const val RETRY_MS = 60 * 60 * 1000L

        /** Se è passata un'ora dall'ultimo tentativo con l'accesso scaduto (e lo segna). */
        private fun retryDue(context: Context): Boolean {
            val prefs = context.getSharedPreferences("shifts_retry", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("at", 0) < RETRY_MS) return false
            prefs.edit().putLong("at", now).apply()
            return true
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ShiftWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
