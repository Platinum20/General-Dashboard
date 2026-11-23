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

/** Le notifiche degli appunti: cose rimaste dopo un evento (idea 28) e l'avviso del posto (idea 11). */
object NoteNotify {
    const val CHANNEL = "note_things"
    const val KEY_NOTE = "note_id"
    const val KEY_INDEX = "line_index"
    const val KEY_NOTIF = "notif_id"

    const val ACTION_TICK = "com.platinum20.dashboard.NOTE_TICK"
    const val ACTION_TO_TODO = "com.platinum20.dashboard.NOTE_TO_TODO"
    const val ACTION_ALL_DONE = "com.platinum20.dashboard.NOTE_ALL_DONE"

    fun leftoverId(key: String): Int = 8_000 + Math.floorMod(key.hashCode(), 900)
    fun placeId(noteId: Long): Int = 9_000 + Math.floorMod(noteId, 900L).toInt()

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Appunti", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cose rimaste dopo un evento e liste quando arrivi in un posto"
            }
        )
    }

    /** Un tasto delle notifiche degli appunti: spunta una riga, tutte, o le manda in Da fare. */
    fun action(context: Context, action: String, noteId: Long, notif: Int, index: Int = -1): PendingIntent {
        val intent = Intent(context, NoteActionReceiver::class.java).setAction(action)
            .putExtra(KEY_NOTE, noteId).putExtra(KEY_INDEX, index).putExtra(KEY_NOTIF, notif)
        val code = (action.hashCode() * 31 + noteId.hashCode()) * 31 + index
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Tocco sulla notifica: la nota, o la pagina dell'evento se [request] c'è. */
    fun open(context: Context, code: Int, request: OpenRequest): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        request.writeTo(intent)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Gli appunti di un evento cambiati: se hanno voci aperte e l'evento non è finito, alla fine arriva la notifica
     * delle cose rimaste. Senza voci aperte il giro si toglie.
     */
    fun scheduleLeftovers(context: Context, key: String, body: String, begin: Long, end: Long, now: Long = System.currentTimeMillis()) {
        val work = WorkManager.getInstance(context)
        val name = "note_left_$key"
        if (end <= now || AttachedNotes.openChecks(body).isEmpty()) {
            work.cancelUniqueWork(name)
            return
        }
        val request = OneTimeWorkRequestBuilder<LeftoverWorker>()
            .setInitialDelay(end - now, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(LeftoverWorker.KEY to key, LeftoverWorker.BEGIN to begin, LeftoverWorker.END to end))
            .build()
        work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Finito l'evento: se negli appunti ci sono voci aperte, "Riunione · 2 cose aperte" con Sposta in Da fare / Fatte. */
class LeftoverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val context = applicationContext
        NotesStore.init(context)
        val key = inputData.getString(KEY) ?: return Result.success()
        val note = NotesStore.attached(key) ?: return Result.success()
        val left = AttachedNotes.openChecks(AttachedNotes.body(note))
        if (left.isEmpty() || !MorningWorker.canNotify(context)) return Result.success()
        NoteNotify.ensureChannel(context)
        val id = NoteNotify.leftoverId(key)
        val title = NoteText.split(note.text).first
        val eventId = AttachedNotes.eventId(key)
        val begin = inputData.getLong(BEGIN, 0L)
        val end = inputData.getLong(END, 0L)
        val request = if (eventId != null && begin > 0) OpenRequest(OpenRequest.EVENT, url = "$eventId/$begin/$end") else OpenRequest(OpenRequest.NOTE, url = note.id.toString())
        val lines = left.joinToString(" · ") { it.text }
        val notification = NotificationCompat.Builder(context, NoteNotify.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("$title · ${AttachedNotes.leftCount(left.size)}")
            .setContentText(lines)
            .setStyle(NotificationCompat.BigTextStyle().bigText(left.joinToString("\n") { "☐ ${it.text}" }))
            .setContentIntent(NoteNotify.open(context, id, request))
            .addAction(0, "Sposta in Da fare", NoteNotify.action(context, NoteNotify.ACTION_TO_TODO, note.id, id))
            .addAction(0, "Fatte", NoteNotify.action(context, NoteNotify.ACTION_ALL_DONE, note.id, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return Result.success()
    }

    companion object {
        const val KEY = "key"
        const val BEGIN = "begin"
        const val END = "end"
    }
}

/** I tasti delle notifiche degli appunti, senza aprire l'app. */
class NoteActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(NoteNotify.KEY_NOTE, -1L)
        if (noteId < 0) return
        NotesStore.init(context)
        val notif = intent.getIntExtra(NoteNotify.KEY_NOTIF, 0)
        when (intent.action) {
            NoteNotify.ACTION_TICK -> {
                val index = intent.getIntExtra(NoteNotify.KEY_INDEX, -1)
                NotesStore.editAttachedBody(noteId) { AttachedNotes.tick(it, index) }
                // La notifica dal vivo si ridisegna con la cosa dopo (o senza riga).
                com.platinum20.dashboard.work.TripLiveWorker.now(context)
                return
            }
            NoteNotify.ACTION_TO_TODO -> {
                val body = NotesStore.note(noteId)?.let { NoteText.split(it.text).second }.orEmpty()
                AttachedNotes.openChecks(body).forEach { NotesStore.addTodo(it.text) }
                NotesStore.editAttachedBody(noteId) { AttachedNotes.withoutOpenChecks(it) }
            }
            NoteNotify.ACTION_ALL_DONE -> NotesStore.editAttachedBody(noteId) { AttachedNotes.tickAll(it) }
        }
        if (notif != 0) NotificationManagerCompat.from(context).cancel(notif)
    }
}
