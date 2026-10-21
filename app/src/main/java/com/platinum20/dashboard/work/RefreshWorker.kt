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
import com.platinum20.dashboard.news.NewsRecap
import com.platinum20.dashboard.news.NewsStore
import com.platinum20.dashboard.weather.WeatherStore
import com.platinum20.dashboard.widget.DashboardWidget
import java.util.concurrent.TimeUnit

/** Aggiorna meteo e temi del giorno in background, per il widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val weather = runCatching { WeatherStore.fetch(applicationContext, freshLocation = false) }
        val news = runCatching {
            val result = NewsStore.load(force = true)
            DashboardWidget.saveRecap(applicationContext, NewsRecap.build(result.items))
        }
        DashboardWidget.updateAll(applicationContext)
        return if (weather.isFailure && news.isFailure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val PERIODIC = "dashboard_refresh"
        private const val ONCE = "dashboard_refresh_now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(1, TimeUnit.HOURS)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network).build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
