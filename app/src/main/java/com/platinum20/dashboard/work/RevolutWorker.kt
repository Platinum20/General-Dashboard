package com.platinum20.dashboard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.bank.RevolutLink
import com.platinum20.dashboard.bank.RevolutStore
import java.util.concurrent.TimeUnit

/**
 * Tiene viva la sessione web di Revolut anche con l'app chiusa: Revolut chiude la sessione dopo
 * un po' che nessuno la usa, e prima la si toccava solo con Home o Spese aperte. Ogni 15 minuti
 * (il minimo di Android) riapre la pagina nascosta e rilegge saldo e ultimi movimenti.
 */
class RevolutWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        RevolutStore.init(applicationContext)
        if (!RevolutStore.linked.value) {
            cancel(applicationContext)
            return Result.success()
        }
        // Scaduta: senza un nuovo accesso non si può fare niente; appena letta: non serve.
        if (RevolutStore.expired.value || !RevolutStore.isStale()) return Result.success()
        runCatching { RevolutLink.refresh(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "revolut_keepalive"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RevolutWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
