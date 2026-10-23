package com.platinum20.dashboard.media.radio

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** La sveglia con la radio: a che ora, in che giorni e con quale stazione. */
data class AlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    /** true = solo dal lunedì al venerdì. */
    val weekdaysOnly: Boolean = true,
    val station: RadioStation? = null,
) {
    val timeLabel: String get() = "%d:%02d".format(hour, minute)
    val daysLabel: String get() = if (weekdaysOnly) "lun-ven" else "tutti i giorni"
}

/**
 * Sveglia con la radio: un allarme esatto (si vede nella barra di stato come le sveglie),
 * la stazione che parte a volume basso e sale in 30 secondi, e la suoneria di sistema
 * se dopo 20 secondi la radio non suona (mai una sveglia muta).
 */
object RadioAlarm {
    private const val PREFS = "radio_alarm"
    private const val KEY = "settings"
    private const val CHANNEL = "radio_alarm"
    private const val NOTIFICATION_ID = 3101
    const val ACTION_FIRE = "com.platinum20.dashboard.alarm.FIRE"
    const val ACTION_STOP = "com.platinum20.dashboard.alarm.STOP"
    const val ACTION_SNOOZE = "com.platinum20.dashboard.alarm.SNOOZE"
    const val SNOOZE_MINUTES = 10L

    private val scope = MainScope()
    private var ramp: Job? = null
    private var ringtone: Ringtone? = null

    private val _settings = MutableStateFlow(AlarmSettings())
    val settings: StateFlow<AlarmSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        _settings.value = runCatching { fromJson(JSONObject(raw)) }.getOrDefault(AlarmSettings())
    }

    fun update(context: Context, settings: AlarmSettings) {
        _settings.value = settings
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, toJson(settings).toString()).apply()
        schedule(context)
    }

    /** La prossima volta che suona dopo [now]: oggi se l'ora non è ancora passata, altrimenti il primo giorno valido. */
    fun next(now: ZonedDateTime, hour: Int, minute: Int, weekdaysOnly: Boolean): ZonedDateTime {
        var candidate = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        while (weekdaysOnly && candidate.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
            candidate = candidate.plusDays(1)
        }
        // Con l'ora legale le 2:30 possono non esistere: withHour sposta già all'ora valida.
        return candidate
    }

    /** Programma (o toglie) l'allarme secondo le impostazioni salvate. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = fireIntent(app)
        alarms.cancel(pending)
        val s = _settings.value
        if (!s.enabled || s.station == null) return
        val at = next(ZonedDateTime.now(), s.hour, s.minute, s.weekdaysOnly).toInstant().toEpochMilli()
        setAt(app, alarms, at, pending)
    }

    private fun setAt(context: Context, alarms: AlarmManager, at: Long, pending: PendingIntent) {
        val open = PendingIntent.getActivity(
            context, 401, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, open), pending)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 400, Intent(context, RadioAlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Suona: parte la stazione, il volume sale, e se la radio tace c'è la suoneria. */
    internal fun fire(context: Context) {
        val app = context.applicationContext
        init(app)
        val station = _settings.value.station
        schedule(app) // la volta dopo
        notify(app, station?.name ?: "Sveglia")
        if (station == null) {
            playRingtone(app)
            return
        }
        RadioStore.init(app)
        RadioStore.play(station)
        ramp?.cancel()
        ramp = scope.launch {
            RadioStore.setVolume(rampVolume(0))
            for (second in 1..30) {
                delay(1_000)
                RadioStore.setVolume(rampVolume(second * 1_000L))
                if (second == 20 && RadioStore.now.value?.playing != true) playRingtone(app)
            }
        }
    }

    /** Il volume della sveglia dopo [elapsedMs]: dal 10% al 100% in 30 secondi. */
    fun rampVolume(elapsedMs: Long): Float = (0.1f + 0.9f * (elapsedMs.coerceIn(0, 30_000) / 30_000f)).coerceIn(0.1f, 1f)

    internal fun stop(context: Context) {
        ramp?.cancel()
        ringtone?.stop()
        ringtone = null
        RadioStore.setVolume(1f)
        RadioStore.pause()
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    internal fun snooze(context: Context) {
        stop(context)
        val app = context.applicationContext
        val alarms = app.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
        // Il rinvio usa un suo intent, così la sveglia di domani resta programmata.
        val pending = PendingIntent.getBroadcast(
            app, 402, Intent(app, RadioAlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("snooze", true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAt(app, alarms, at, pending)
    }

    private fun playRingtone(context: Context) {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()?.also { it.play() }
    }

    private fun notify(context: Context, title: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sveglia con la radio", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "La sveglia che accende la radio"
                setSound(null, null)
            }
        )
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.MEDIA, source = RadioService.SOURCE_RADIO).writeTo(open)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sveglia · ${_settings.value.timeLabel}")
            .setContentText(title)
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(PendingIntent.getActivity(context, 403, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .addAction(action(context, "Ferma", ACTION_STOP, 404))
            .addAction(action(context, "Ancora $SNOOZE_MINUTES min", ACTION_SNOOZE, 405))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun action(context: Context, title: String, action: String, code: Int): Notification.Action {
        val intent = Intent(context, RadioAlarmReceiver::class.java).setAction(action)
        val pending = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_notification), title, pending).build()
    }

    fun toJson(s: AlarmSettings): JSONObject = JSONObject()
        .put("enabled", s.enabled).put("hour", s.hour).put("minute", s.minute).put("weekdays", s.weekdaysOnly)
        .apply { s.station?.let { put("station", it.toJson()) } }

    fun fromJson(o: JSONObject) = AlarmSettings(
        enabled = o.optBoolean("enabled"),
        hour = o.optInt("hour", 7).coerceIn(0, 23),
        minute = o.optInt("minute", 0).coerceIn(0, 59),
        weekdaysOnly = o.optBoolean("weekdays", true),
        station = o.optJSONObject("station")?.let(RadioStation::fromJson),
    )
}

/** Riceve l'allarme, "Ferma", "Ancora 10 min" e il riavvio del telefono (che cancella gli allarmi). */
class RadioAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RadioAlarm.ACTION_FIRE -> RadioAlarm.fire(context)
            RadioAlarm.ACTION_STOP -> RadioAlarm.stop(context)
            RadioAlarm.ACTION_SNOOZE -> RadioAlarm.snooze(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RadioAlarm.init(context)
                RadioAlarm.schedule(context)
            }
        }
    }
}
