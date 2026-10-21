package com.platinum20.dashboard.notes

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.work.MorningWorker
import java.util.concurrent.TimeUnit

/** Avvisa quando scade un promemoria, con il tasto "Fatto" direttamente nella notifica. */
class TaskReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val context = applicationContext
        NotesStore.init(context)
        val id = inputData.getLong(KEY_ID, -1L)
        val task = NotesStore.task(id) ?: return Result.success()
        if (task.done || !MorningWorker.canNotify(context)) return Result.success()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Promemoria", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi alla scadenza delle cose da fare"
            }
        )

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.NOTES).writeTo(open)
        val done = Intent(context, TaskDoneReceiver::class.java).putExtra(KEY_ID, id)

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Promemoria")
            .setContentText(task.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.text))
            .setContentIntent(
                PendingIntent.getActivity(context, notificationId(id), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(
                0,
                "Fatto",
                PendingIntent.getBroadcast(context, notificationId(id), done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(id), notification)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "task_id"
        private const val CHANNEL = "tasks"

        fun notificationId(id: Long): Int = 1000 + (id and 0x3FFFFFFF).toInt()

        private fun workName(id: Long) = "task_reminder_$id"

        fun schedule(context: Context, task: TaskItem) {
            val due = task.dueAt ?: return
            val delay = (due - System.currentTimeMillis()).coerceAtLeast(0)
            // Una scadenza già passata da più di un'ora non va più notificata.
            if (due < System.currentTimeMillis() - 60 * 60_000L) {
                cancel(context, task.id)
                return
            }
            val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to task.id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, id: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
            NotificationManagerCompat.from(context).cancel(notificationId(id))
        }
    }
}

/** Il tasto "Fatto" della notifica: spunta il promemoria senza aprire l'app. */
class TaskDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(TaskReminderWorker.KEY_ID, -1L)
        if (id < 0) return
        NotesStore.init(context)
        NotesStore.setTaskDone(id)
        NotificationManagerCompat.from(context).cancel(TaskReminderWorker.notificationId(id))
    }
}
