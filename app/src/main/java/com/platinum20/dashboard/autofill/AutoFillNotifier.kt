package com.platinum20.dashboard.autofill

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.R
import com.platinum20.dashboard.notes.Expenses
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Le parole per mostrare una proposta, uguali in Oggi e nella notifica. */
object SuggestionText {
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ITALIAN)
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ITALIAN)

    fun title(found: Found): String = when (found) {
        is Found.NewParcel -> found.title
        is Found.Spent -> "${Expenses.format(found.cents)} · ${found.merchant}"
        is Found.Deadline -> found.title
        is Found.Delivered -> "Consegnato ${found.code}"
    }

    fun detail(s: Suggestion): String {
        val what = when (val f = s.found) {
            is Found.NewParcel -> listOfNotNull("Pacco", f.code.ifEmpty { null }, f.expected?.let { "atteso " + day.format(it) }).joinToString(" · ")
            is Found.Spent -> "Spesa · ${f.category.label}"
            is Found.Deadline -> "Promemoria · " + (if (f.due.hour == 9 && f.due.minute == 0) day.format(f.due) else dayTime.format(f.due))
            is Found.Delivered -> "Pacco"
        }
        return "$what · da ${s.app}"
    }
}

/** Una notifica silenziosa per ogni cosa trovata, con Aggiungi / Ignora (o Annulla se è già salvata). */
object AutoFillNotifier {
    private const val CHANNEL = "autofill"
    private const val ACTION_ACCEPT = "com.platinum20.dashboard.autofill.ACCEPT"
    private const val ACTION_DISMISS = "com.platinum20.dashboard.autofill.DISMISS"
    private const val ACTION_UNDO = "com.platinum20.dashboard.autofill.UNDO"
    const val KEY_ID = "suggestion_id"

    private fun notificationId(id: Long): Int = 50_000 + (id and 0xFFFFFF).toInt()

    fun show(context: Context, items: List<Suggestion>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Trovati da soli", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Pacchi, spese e scadenze che Canvas ha letto nelle notifiche"
            }
        )
        items.forEach { s ->
            val open = PendingIntent.getActivity(
                context, notificationId(s.id), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            fun action(name: String) = PendingIntent.getBroadcast(
                context, notificationId(s.id) + name.hashCode(),
                Intent(context, AutoFillReceiver::class.java).setAction(name).putExtra(KEY_ID, s.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val added = s.state == SuggestionState.ADDED
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_quick_add)
                .setContentTitle(if (added) "Aggiunto: " + SuggestionText.title(s.found) else SuggestionText.title(s.found))
                .setContentText(SuggestionText.detail(s))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(CHANNEL)
                .setPriority(NotificationCompat.PRIORITY_LOW)
            if (added) builder.addAction(0, "Annulla", action(ACTION_UNDO))
            else builder.addAction(0, "Aggiungi", action(ACTION_ACCEPT)).addAction(0, "Ignora", action(ACTION_DISMISS))
            runCatching { manager.notify(notificationId(s.id), builder.build()) }
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(id))
    }

    fun handle(context: Context, intent: Intent) {
        AutoFill.init(context)
        val id = intent.getLongExtra(KEY_ID, -1L)
        when (intent.action) {
            ACTION_ACCEPT -> AutoFill.accept(id)
            ACTION_DISMISS -> AutoFill.dismiss(id)
            ACTION_UNDO -> AutoFill.undo(id)
        }
        cancel(context, id)
    }
}

class AutoFillReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AutoFillNotifier.handle(context, intent)
}

/** Rilegge gli SMS degli ultimi giorni: pagamenti con carta, corrieri, appuntamenti. */
object SmsScanner {
    const val DAYS = 30L

    fun canRead(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Da chiamare fuori dal thread principale. Restituisce quante cose nuove ha trovato. */
    fun scan(context: Context): Int {
        AutoFill.init(context)
        if (!canRead(context)) return 0
        val since = System.currentTimeMillis() - DAYS * 24 * 3600_000L
        var found = 0
        val cursor: Cursor = context.contentResolver.query(
            Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), "date >= ?", arrayOf(since.toString()), "date ASC",
        ) ?: return 0
        cursor.use { c ->
            while (c.moveToNext()) {
                val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(2)), ZoneId.systemDefault())
                val msg = Incoming("SMS", SourceKind.SMS, c.getString(0).orEmpty(), c.getString(1).orEmpty(), at)
                found += AutoFill.offer(msg, notify = false).size
            }
        }
        AutoFill.smsScannedAt = System.currentTimeMillis()
        return found
    }
}
