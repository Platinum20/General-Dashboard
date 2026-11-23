package com.platinum20.dashboard.trips

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.widget.Toast
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * La sveglia veloce dei tasti "5 min · 10 min · 15 min" in fondo alla notifica del turno (Antonio, 2026-11-23).
 * Una sveglia vera: allarme esatto (l'icona della sveglia nella barra di stato) e, quando scade, una notifica
 * sul canale sveglie che suona col suono della sveglia finché non la fermi. Il suono lo fa il sistema, così suona
 * anche se Android ha chiuso l'app, e col telefono in vibrazione o silenzioso: usa il volume della sveglia (USAGE_ALARM),
 * non quello della suoneria, proprio come la sveglia dell'orologio. Una sola alla volta: un nuovo tasto sposta quella già messa.
 */
object QuickAlarm {
    val MINUTES = listOf(5, 10, 15)
    const val ACTION_SET = "com.platinum20.dashboard.quickalarm.SET"
    const val ACTION_FIRE = "com.platinum20.dashboard.quickalarm.FIRE"
    const val ACTION_STOP = "com.platinum20.dashboard.quickalarm.STOP"
    const val ACTION_CANCEL = "com.platinum20.dashboard.quickalarm.CANCEL"
    const val EXTRA_MINUTES = "minutes"
    const val SNOOZE_MINUTES = 5
    private const val CHANNEL_RING = "quick_alarm_ring"
    private const val CHANNEL_SET = "quick_alarm_set"
    private const val NOTIFICATION_ID = 3201
    private const val PREFS = "quick_alarm"
    private const val KEY_AT = "at"

    private val hour = DateTimeFormatter.ofPattern("H:mm")

    /** Quando suona: fra [minutes] minuti da [now], al minuto pieno (niente secondi che spostano l'orario detto). */
    fun ringAt(now: Long, minutes: Int): Long = (now / 60_000L + minutes) * 60_000L

    /** "Sveglia alle 18:35 · fra 10 min": la conferma, una riga. */
    fun confirm(at: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): String =
        "Sveglia alle ${label(at, zone)} · fra $minutes min"

    fun label(at: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(at).atZone(zone).format(hour)

    /** I tasti della notifica: "5 min", "10 min", "15 min" (Antonio, 2026-11-23: niente "Sveglia" sul primo). */
    @Suppress("UNUSED_PARAMETER")
    fun buttonTitle(minutes: Int, first: Boolean = false): String = "$minutes min"

    /** Il tasto "Sveglia fra N min" per una notifica. */
    fun button(context: Context, minutes: Int, first: Boolean): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), buttonTitle(minutes, first), setIntent(context, minutes)).build()

    fun setIntent(context: Context, minutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 3210 + minutes,
        Intent(context, QuickAlarmReceiver::class.java).setAction(ACTION_SET).putExtra(EXTRA_MINUTES, minutes),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun set(context: Context, minutes: Int) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val at = ringAt(System.currentTimeMillis(), minutes)
        val open = PendingIntent.getActivity(app, 3202, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val fire = fireIntent(app)
        alarms.cancel(fire)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), fire)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_AT, at).apply()
        val text = confirm(at, minutes)
        notifySet(app, at)
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }

    internal fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(app))
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_AT).apply()
        app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        Toast.makeText(app, "Sveglia tolta", Toast.LENGTH_SHORT).show()
    }

    internal fun stop(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Il telefono riavviato cancella gli allarmi: se la sveglia non è ancora passata si rimette. */
    internal fun restore(context: Context) {
        val app = context.applicationContext
        val at = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0L)
        if (at <= System.currentTimeMillis()) return
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val open = PendingIntent.getActivity(app, 3202, Intent(app, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), fireIntent(app))
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fireIntent(app))
    }

    private fun fullScreen(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 3207,
        Intent(context, QuickAlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 3203, Intent(context, QuickAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, title: String, action: String, code: Int): Notification.Action {
        val pending = PendingIntent.getBroadcast(
            context, code, Intent(context, QuickAlarmReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), title, pending).build()
    }

    /** Messa: una notifica silenziosa con l'ora e "Togli", così si vede e si annulla. */
    @SuppressLint("MissingPermission")
    private fun notifySet(context: Context, at: Long) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SET, "Sveglia messa", NotificationManager.IMPORTANCE_LOW).apply {
                description = "La sveglia veloce già messa, con il tasto per toglierla"
                setSound(null, null)
            }
        )
        val notification = Notification.Builder(context, CHANNEL_SET)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("Sveglia alle ${label(at)}")
            .setContentText("Suona fra poco")
            .setWhen(at)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .addAction(action(context, "Togli", ACTION_CANCEL, 3204))
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    /** Suona: notifica sul canale sveglie, suono della sveglia in loop (insistente) finché non si tocca "Ferma". */
    @SuppressLint("MissingPermission")
    internal fun fire(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_AT).apply()
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        val sound = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RING, "Sveglia veloce", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "La sveglia dei tasti 5 / 10 / 15 min della notifica del turno"
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val stop = PendingIntent.getBroadcast(
            app, 3205, Intent(app, QuickAlarmReceiver::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(app, CHANNEL_RING)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(app.getColor(R.color.notification_accent))
            .setContentTitle("Sveglia")
            .setContentText("Sono le ${label(System.currentTimeMillis())}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setDeleteIntent(stop)
            // A tutto schermo come la sveglia di sistema (telefono bloccato o schermo spento); sbloccato = heads-up.
            .setFullScreenIntent(fullScreen(app), true)
            .setContentIntent(fullScreen(app))
            .addAction(action(app, "Ferma", ACTION_STOP, 3206))
            .addAction(button(app, SNOOZE_MINUTES, first = false).let {
                Notification.Action.Builder(it.getIcon(), "Ancora $SNOOZE_MINUTES min", it.actionIntent).build()
            })
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}

/** I tasti della notifica del turno, l'allarme che scade, "Ferma"/"Togli" e il riavvio del telefono. */
class QuickAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuickAlarm.ACTION_SET -> QuickAlarm.set(context, intent.getIntExtra(QuickAlarm.EXTRA_MINUTES, 5))
            QuickAlarm.ACTION_FIRE -> QuickAlarm.fire(context)
            QuickAlarm.ACTION_STOP -> QuickAlarm.stop(context)
            QuickAlarm.ACTION_CANCEL -> QuickAlarm.cancel(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> QuickAlarm.restore(context)
        }
    }
}
