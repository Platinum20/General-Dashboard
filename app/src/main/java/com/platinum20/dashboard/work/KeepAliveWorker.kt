package com.platinum20.dashboard.work

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.platinum20.dashboard.shifts.CreatorsLink
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.sim.WindTreLink
import java.util.concurrent.TimeUnit

/**
 * Tiene vivo l'accesso web di WindTre e Creators Pro anche con l'app chiusa: il sito chiude la
 * sessione dopo un po' che nessuno la usa. Due giri insieme: il lavoro periodico di Android (ogni
 * 15 minuti, ma di notte Doze lo rimanda di ore, ed era lì che la sessione scadeva) e una sveglia
 * "anche a riposo" ([KeepAliveAlarm]) che lo fa partire subito. Con Canvas fuori dall'ottimizzazione
 * batteria ([askBattery]) la rete c'è anche col telefono fermo.
 */
class KeepAliveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        SimStore.init(app)
        ShiftStore.init(app)
        if (!anyLinked()) {
            update(app)
            return Result.success()
        }
        if (SimStore.linked.value && SimStore.isStale(within = SimStore.KEEP_MS)) {
            runCatching { WindTreLink.refresh(app) }
        }
        // Creators Pro: una rilettura ogni mezz'ora tiene il token in uso, così il sito non chiude la sessione di notte.
        if (ShiftStore.linked.value && !ShiftStore.expired.value && ShiftStore.readAt.value < System.currentTimeMillis() - SHIFT_KEEP_MS) {
            runCatching { CreatorsLink.refresh(app) }
        }
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "web_keepalive"
        private const val NOW = "web_keepalive_now"
        /** Il nome di un lavoro vecchio (fino alla 1.361): si toglie se è rimasto. */
        private const val OLD = "revolut_keepalive"
        private const val PREFS = "keepalive"
        private const val KEY_ASKED = "batteryAsked"
        /** La sveglia: sotto i 15 minuti Android la allunga comunque a riposo. */
        const val ALARM_MS = 15 * 60 * 1000L

        /** Ogni quanto rileggere i turni dal giro, se nessun altro l'ha fatto. */
        private const val SHIFT_KEEP_MS = 30 * 60 * 1000L

        private fun anyLinked() = SimStore.linked.value || ShiftStore.linked.value

        /** Accende o spegne il giro secondo cosa è collegato. */
        fun update(context: Context) {
            val app = context.applicationContext
            SimStore.init(app)
            ShiftStore.init(app)
            val wm = WorkManager.getInstance(app)
            wm.cancelUniqueWork(OLD)
            if (anyLinked()) {
                val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(online())
                    .build()
                wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
                KeepAliveAlarm.arm(app)
            } else {
                wm.cancelUniqueWork(PERIODIC)
                KeepAliveAlarm.disarm(app)
            }
        }

        /** Un giro subito (dalla sveglia): "urgente" dove Android lo permette, così Doze non lo rimanda. */
        fun runNow(context: Context) {
            val builder = OneTimeWorkRequestBuilder<KeepAliveWorker>().setConstraints(online())
            // Sotto Android 12 un lavoro urgente vorrebbe una notifica in primo piano: lì basta il normale.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, builder.build())
        }

        private fun online() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Una volta sola, con qualcosa collegato: chiede ad Android di lasciare Canvas fuori
         * dall'ottimizzazione batteria, senza la quale col telefono fermo non c'è rete per tenere viva la sessione.
         */
        @SuppressLint("BatteryLife")
        fun askBattery(activity: Activity) {
            SimStore.init(activity)
            ShiftStore.init(activity)
            if (!anyLinked()) return
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_ASKED, false)) return
            val power = activity.getSystemService(PowerManager::class.java) ?: return
            if (power.isIgnoringBatteryOptimizations(activity.packageName)) return
            prefs.edit().putBoolean(KEY_ASKED, true).apply()
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName)),
                )
            }
        }
    }
}

/** La sveglia del giro: parte anche col telefono a riposo, fa partire il giro e si rimette. */
class KeepAliveAlarm : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        SimStore.init(app)
        ShiftStore.init(app)
        if (!SimStore.linked.value && !ShiftStore.linked.value) return
        if (intent.action == ACTION) KeepAliveWorker.runNow(app)
        arm(app)
    }

    companion object {
        private const val ACTION = "com.platinum20.dashboard.KEEP_ALIVE"

        private fun intent(context: Context) = PendingIntent.getBroadcast(
            context, 7301,
            Intent(context, KeepAliveAlarm::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun arm(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + KeepAliveWorker.ALARM_MS,
                intent(context),
            )
        }

        fun disarm(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(intent(context))
        }
    }
}
