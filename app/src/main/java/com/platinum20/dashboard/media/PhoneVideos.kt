package com.platinum20.dashboard.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un video salvato sul telefono (registrazioni, download), letto da MediaStore. */
data class PhoneVideo(
    val uri: String,
    val title: String,
    val durationMs: Long,
    /** Quando è stato aggiunto, in secondi come in MediaStore. */
    val addedAtSec: Long,
)

/** I video del telefono per la pagina Video: letti solo con il permesso, mai copiati. */
object PhoneVideos {
    const val MAX = 12

    /** Il permesso da chiedere: da Android 13 solo i video, prima tutta la memoria in lettura. */
    val permission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    /** Cosa fa la card d'invito: chiedere il permesso o, dopo un rifiuto definitivo, aprire le impostazioni. */
    enum class Invite { ASK, OPEN_SETTINGS }

    /**
     * Dopo un rifiuto, se Android non mostrerebbe più la richiesta ([rationale] false),
     * il tocco deve portare alle impostazioni dell'app, altrimenti non succederebbe niente.
     */
    fun invite(deniedOnce: Boolean, rationale: Boolean): Invite =
        if (deniedOnce && !rationale) Invite.OPEN_SETTINGS else Invite.ASK

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Gli ultimi video aggiunti, dal più recente. Vuoto senza permesso o se la lettura non va. */
    suspend fun load(context: Context, limit: Int = 30): List<PhoneVideo> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
        )
        runCatching {
            context.contentResolver.query(
                collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val duration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val added = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                buildList {
                    while (c.moveToNext() && size < limit) {
                        add(
                            PhoneVideo(
                                uri = ContentUris.withAppendedId(collection, c.getLong(id)).toString(),
                                title = cleanTitle(c.getString(name).orEmpty()),
                                durationMs = c.getLong(duration),
                                addedAtSec = c.getLong(added),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /**
     * I video del telefono da mostrare sotto i Recenti: senza quelli già nei Recenti
     * (stesso indirizzo), dal più nuovo, al massimo [max].
     */
    fun forLibrary(phone: List<PhoneVideo>, recent: List<VideoEntry>, max: Int = MAX): List<PhoneVideo> {
        val seen = recent.mapTo(HashSet()) { it.uri }
        return phone.asSequence()
            .filter { it.uri !in seen }
            .distinctBy { it.uri }
            .sortedByDescending { it.addedAtSec }
            .take(max)
            .toList()
    }

    /** "VID_20261021_183012.mp4" → "VID_20261021_183012"; "vacanze.mov" → "vacanze". */
    fun cleanTitle(displayName: String): String =
        displayName.substringBeforeLast('.', displayName).ifBlank { "Video" }

    /** Riga sotto la miniatura: "3:12 · oggi", "1:02:03 · ieri", "0:45 · 12 ott". */
    fun caption(video: PhoneVideo, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = Instant.ofEpochSecond(video.addedAtSec).atZone(zone).toLocalDate()
        val whenText = when (day) {
            today -> "oggi"
            today.minusDays(1) -> "ieri"
            else -> day.format(
                DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", Locale.ITALIAN),
            ).replace(".", "")
        }
        return if (video.durationMs > 0) "${durationText(video.durationMs)} · $whenText" else whenText
    }

    fun durationText(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
