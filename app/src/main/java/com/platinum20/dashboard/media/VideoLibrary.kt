package com.platinum20.dashboard.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class VideoEntry(
    val uri: String,
    val title: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val openedAt: Long = System.currentTimeMillis(),
) {
    val isLocal: Boolean get() = uri.startsWith(ContentResolver.SCHEME_CONTENT) || uri.startsWith("file:")

    /** Avanzamento da 0 a 1, per la barra dei video lasciati a metà. */
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Video aperti di recente, con il punto in cui li hai lasciati. */
object VideoLibrary {
    private const val PREFS = "videos"
    private const val KEY_RECENT = "recent"
    private const val MAX_RECENT = 30

    private lateinit var prefs: SharedPreferences
    private val _recent = MutableStateFlow<List<VideoEntry>>(emptyList())
    val recent: StateFlow<List<VideoEntry>> = _recent.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _recent.value = runCatching {
            val array = JSONArray(prefs.getString(KEY_RECENT, "[]"))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                VideoEntry(
                    uri = o.getString("uri"),
                    title = o.optString("title"),
                    positionMs = o.optLong("position"),
                    durationMs = o.optLong("duration"),
                    openedAt = o.optLong("openedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Aggiunge (o riporta in cima) un video e restituisce la voce salvata, con l'eventuale punto di ripresa. */
    fun open(context: Context, uri: Uri): VideoEntry {
        val key = uri.toString()
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            // Il permesso di lettura resta anche dopo il riavvio, così i recenti si riaprono.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val existing = _recent.value.firstOrNull { it.uri == key }
        val entry = (existing ?: VideoEntry(uri = key, title = displayName(context, uri)))
            .copy(openedAt = System.currentTimeMillis())
        save(listOf(entry) + _recent.value.filter { it.uri != key })
        return entry
    }

    fun updatePosition(uri: String, positionMs: Long, durationMs: Long) {
        // Quasi finito: la prossima volta riparte dall'inizio.
        val position = if (durationMs > 0 && durationMs - positionMs < 5_000) 0 else positionMs
        save(_recent.value.map {
            if (it.uri == uri) it.copy(positionMs = position, durationMs = durationMs.coerceAtLeast(0)) else it
        })
    }

    fun remove(uri: String) = save(_recent.value.filter { it.uri != uri })

    fun clear() = save(emptyList())

    private fun save(list: List<VideoEntry>) {
        val trimmed = list.take(MAX_RECENT)
        _recent.value = trimmed
        val array = JSONArray()
        trimmed.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("title", it.title)
                    .put("position", it.positionMs)
                    .put("duration", it.durationMs)
                    .put("openedAt", it.openedAt)
            )
        }
        prefs.edit().putString(KEY_RECENT, array.toString()).apply()
    }

    private fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
                }
            }
        }
        return uri.lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: uri.host
            ?: "Video"
    }
}
