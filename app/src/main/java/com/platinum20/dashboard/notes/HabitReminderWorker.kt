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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Quando ricordare un'abitudine: la parte senza Android, provata da HabitReminderTest. */
object HabitReminder {
    /** Quanto sposta l'avviso "Più tardi". */
    val LATER: Duration = Duration.ofHours(1)

    /**
     * Va ricordata in [day]? Non se è già fatta; per le settimanali non se l'obiettivo
     * della settimana è già raggiunto (si riparte il lunedì).
     */
    fun due(habit: Habit, day: LocalDate): Boolean =
        !habit.isDone(day) && (!habit.weekly || habit.weekCount(day) < habit.perWeek)

    /** Il prossimo avviso dopo [now]: oggi se l'ora non è passata e serve, altrimenti il primo giorno utile. */
    fun next(habit: Habit, now: LocalDateTime): LocalDateTime? {
        val at = habit.remindAt ?: return null
        for (i in 0L..7L) {
            val day = now.toLocalDate().plusDays(i)
            val time = day.atTime(at)
            if (time.isAfter(now) && due(habit, day)) return time
        }
        return null
    }

    /** In coda alla riga dell'abitudine in Note: " · avviso alle 21:00", vuoto se non c'è. */
    fun suffix(habit: Habit): String = habit.remindAt?.let { " · avviso alle %d:%02d".format(it.hour, it.minute) }.orEmpty()

    /** Il testo della notifica: la serie in gioco o quante ne mancano questa settimana. */
    fun text(habit: Habit, today: LocalDate): String = when {
        habit.weekly -> "${habit.weekCount(today)} su ${habit.perWeek} questa settimana"
        habit.streak(today) > 0 -> "${habit.streak(today)} di fila: non perdere la serie"
        else -> "Non ancora fatta oggi"
    }
}

/** Avvisa all'ora scelta se l'abitudine non è ancora fatta, con "Fatto" e "Più tardi". */
class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val context = applicationContext
        NotesStore.init(context)
        val id = inputData.getLong(KEY_ID, -1L)
        val habit = NotesStore.habit(id) ?: return Result.success()
        val today = LocalDate.now()
        if (habit.remindAt != null && HabitReminder.due(habit, today) && MorningWorker.canNotify(context)) {
            notify(context, habit, today)
        }
        // Il prossimo giro (domani, o lunedì se l'obiettivo della settimana è raggiunto).
        schedule(context, habit, from = LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, habit: Habit, today: LocalDate) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Abitudini", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Promemoria delle abitudini all'ora scelta"
            }
        )
        val code = notificationId(habit.id)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.NOTES).writeTo(open)
        val done = Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(KEY_ID, habit.id)
        val later = Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_LATER).putExtra(KEY_ID, habit.id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.title)
            .setContentText(HabitReminder.text(habit, today))
            .setContentIntent(PendingIntent.getActivity(context, code, open, flags))
            .addAction(0, "Fatto", PendingIntent.getBroadcast(context, code, done, flags))
            .addAction(0, "Più tardi", PendingIntent.getBroadcast(context, code, later, flags))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(code, notification)
    }

    companion object {
        const val KEY_ID = "habit_id"
        const val ACTION_DONE = "com.platinum20.dashboard.HABIT_DONE"
        const val ACTION_LATER = "com.platinum20.dashboard.HABIT_LATER"
        private const val CHANNEL = "habits"

        // Lontano dagli id dei promemoria (1000 + …) per non sovrascriverne le notifiche.
        fun notificationId(id: Long): Int = 0x40000000 + (id and 0x0FFFFFFF).toInt()

        private fun workName(id: Long) = "habit_reminder_$id"

        /** WorkManager rimette da solo il lavoro dopo il riavvio del telefono. */
        fun schedule(context: Context, habit: Habit, from: LocalDateTime = LocalDateTime.now()) {
            val at = HabitReminder.next(habit, from) ?: return cancel(context, habit.id)
            enqueue(context, habit.id, Duration.between(LocalDateTime.now(), at))
        }

        fun later(context: Context, id: Long) = enqueue(context, id, HabitReminder.LATER)

        private fun enqueue(context: Context, id: Long, delay: Duration) {
            val request = OneTimeWorkRequestBuilder<HabitReminderWorker>()
                .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to id))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, id: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))
            NotificationManagerCompat.from(context).cancel(notificationId(id))
        }
    }
}

/** "Fatto" e "Più tardi" della notifica, senza aprire l'app. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(HabitReminderWorker.KEY_ID, -1L)
        if (id < 0) return
        NotesStore.init(context)
        NotificationManagerCompat.from(context).cancel(HabitReminderWorker.notificationId(id))
        when (intent.action) {
            // Segnarla fatta rimette anche il prossimo avviso (NotesStore.syncHabitReminder).
            HabitReminderWorker.ACTION_DONE -> NotesStore.markHabitDone(id)
            HabitReminderWorker.ACTION_LATER -> HabitReminderWorker.later(context, id)
        }
    }
}
