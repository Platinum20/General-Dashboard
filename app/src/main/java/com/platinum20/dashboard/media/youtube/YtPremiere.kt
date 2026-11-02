package com.platinum20.dashboard.media.youtube

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.media.youtube.player.NewPipeDownloader
import com.platinum20.dashboard.work.MorningWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Il video è un'anteprima (o una diretta in programma) non ancora iniziata: NewPipe non dà flussi. */
class YtUpcomingException(val startsAt: Instant?) :
    IllegalStateException("L'anteprima " + (startsAt?.let { "inizia " + YtPremiere.whenText(it) } ?: "non è ancora iniziata"))

/**
 * Anteprime di YouTube (Antonio: "se c'è un'anteprima dà errore"): prima dell'inizio YouTube non dà
 * flussi, quindi si legge l'orario dalla pagina del video e si mostra un'attesa con il conto alla rovescia.
 * Quando parte è una diretta (HLS), dopo un video normale: li gestisce già il player.
 */
object YtPremiere {
    /** Orario d'inizio scritto nella pagina del video, se è un'anteprima in arrivo; null se non lo è. */
    suspend fun check(id: String): YtUpcomingException? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://www.youtube.com/watch?v=$id&hl=it&gl=IT").openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", NewPipeDownloader.USER_AGENT)
                setRequestProperty("Accept-Language", "it-IT,it;q=0.9")
                // Senza questi cookie in Europa arriva la pagina del consenso, non quella del video.
                setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+")
            }
            try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.let(::parse)
    }

    /** Dalla pagina del video: anteprima in arrivo (con l'orario, se c'è) oppure null. */
    internal fun parse(html: String): YtUpcomingException? {
        if (!Regex(""""isUpcoming"\s*:\s*true""").containsMatchIn(html)) return null
        val scheduled = Regex(""""scheduledStartTime"\s*:\s*"(\d+)"""").find(html)?.groupValues?.get(1)?.toLongOrNull()
            ?.let { Instant.ofEpochSecond(it) }
        val start = scheduled ?: Regex(""""startTimestamp"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        return YtUpcomingException(start)
    }

    private val time = DateTimeFormatter.ofPattern("H:mm", Locale.ITALIAN)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", Locale.ITALIAN)

    /** "oggi alle 20:00", "domani alle 9:30", "giovedì alle 20:00", "3 nov alle 20:00". */
    fun whenText(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val at = start.atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.toLocalDate())
        val day = when (days) {
            0L -> "oggi"
            1L -> "domani"
            in 2..6 -> weekday.format(at)
            else -> dayMonth.format(at).removeSuffix(".")
        }
        return "$day alle ${time.format(at)}"
    }

    /** Sull'anteprima nei feed: "ANTEPRIMA · 20:00" oggi, "ANTEPRIMA · DOMANI", "ANTEPRIMA · 3 NOV". */
    fun badge(start: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (start == null || !start.isAfter(now)) return "ANTEPRIMA"
        val at = start.atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), at.toLocalDate())
        val day = when (days) {
            0L -> time.format(at)
            1L -> "DOMANI"
            else -> dayMonth.format(at).removeSuffix(".").uppercase(Locale.ITALIAN)
        }
        return "ANTEPRIMA · $day"
    }

    /** Quanto manca: "2 g 3 h", "1 h 05 min", "04:09"; null quando l'orario è passato. */
    fun countdown(start: Instant, now: Instant = Instant.now()): String? {
        val left = Duration.between(now, start)
        if (left.isNegative || left.isZero) return null
        val s = left.seconds
        return when {
            s >= 86_400 -> "${s / 86_400} g ${s % 86_400 / 3600} h"
            s >= 3600 -> "${s / 3600} h %02d min".format(s % 3600 / 60)
            else -> "%02d:%02d".format(s / 60, s % 60)
        }
    }

    // --- Promemoria ---

    private fun work(id: String) = "premiere_$id"

    /** Avvisa con una notifica quando l'anteprima [id] comincia; toccandola si apre il video. */
    fun remind(context: Context, id: String, title: String, at: Instant) {
        val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<PremiereReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(PremiereReminderWorker.KEY_ID to id, PremiereReminderWorker.KEY_TITLE to title))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(work(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(work(id))
    }

    /** Promemoria già messo per [id]. */
    fun reminded(context: Context, id: String): Flow<Boolean> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(work(id))
            .map { list -> list.any { it.state == WorkInfo.State.ENQUEUED } }
}

/** La notifica "Sta iniziando l'anteprima" all'orario scelto. */
class PremiereReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val context = applicationContext
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        if (!MorningWorker.canNotify(context)) return Result.success()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Anteprime di YouTube", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi quando inizia un'anteprima che aspetti"
            }
        )
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        OpenRequest(OpenRequest.PLAY, url = "https://www.youtube.com/watch?v=$id").writeTo(open)
        val notificationId = 5000 + (id.hashCode() and 0xFFFFF)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sta iniziando l'anteprima")
            .setContentText(title.ifBlank { "Il video che aspettavi" })
            .setContentIntent(PendingIntent.getActivity(context, notificationId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return Result.success()
    }

    companion object {
        const val KEY_ID = "video_id"
        const val KEY_TITLE = "title"
        private const val CHANNEL = "premieres"
    }
}
