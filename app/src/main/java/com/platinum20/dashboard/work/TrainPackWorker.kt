package com.platinum20.dashboard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.calendar.CalendarRepository
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.trips.TrainPack
import com.platinum20.dashboard.trips.TrainPackStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Lo zaino del treno (1.468): ogni ora, solo col Wi-Fi (rete che non consuma dati), prepara i treni che partono
 * entro un giorno e butta quello dei viaggi finiti. Coi dati mobili non parte mai.
 */
class TrainPackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        TrainPackStore.init(context)
        TrainPackStore.clean()
        if (!AppSettings.trainPack.value) return Result.success()
        val now = System.currentTimeMillis()
        val trains = runCatching {
            CalendarRepository.eventsFrom(context, LocalDate.now(), days = 2).values.flatten().distinctBy { it.eventId }
        }.getOrDefault(emptyList())
            .filter { it.trip != null && it.begin in now..now + TrainPack.LOOKAHEAD_MS }
            .sortedBy { it.begin }
        for (train in trains) runCatching { TrainPackStore.pack(context, train) }
        return Result.success()
    }

    companion object {
        private const val WORK = "train_pack"

        fun schedule(context: Context) {
            AppSettings.init(context)
            val wm = WorkManager.getInstance(context)
            val request = PeriodicWorkRequestBuilder<TrainPackWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            // Anche spento gira: così butta i file vecchi. Con lo zaino spento non scarica niente.
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
