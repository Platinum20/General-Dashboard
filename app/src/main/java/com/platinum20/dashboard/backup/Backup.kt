package com.platinum20.dashboard.backup

import android.content.Context
import android.net.Uri
import com.platinum20.dashboard.notes.NotesData
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.weather.GeoPlace
import com.platinum20.dashboard.weather.WeatherStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Il contenuto di un file di backup già letto e controllato. */
data class BackupContent(
    val notes: NotesData,
    /** Le preferenze di AppSettings (card di Oggi, tema, notifica del mattino…). */
    val settings: Map<String, Any?>,
    /** Località del meteo scelta a mano, null se era automatica. */
    val place: GeoPlace?,
    val createdAt: Long,
)

/**
 * Backup ed esportazione: promemoria, abitudini, spese, note, pacchi e date in un solo file JSON,
 * con le impostazioni utili. Le funzioni di [encode]/[decode] sono pure, così si provano senza telefono.
 */
object Backup {
    private const val APP = "canvas"
    const val FORMAT = 1
    private const val PREFS = "backup"
    private const val KEY_LAST = "last_export"

    fun fileName(day: LocalDate): String = "canvas-backup-$day.json"

    fun encode(notesJson: String, settings: Map<String, Any?>, place: GeoPlace?, createdAt: Long): String =
        JSONObject()
            .put("app", APP)
            .put("format", FORMAT)
            .put("createdAt", createdAt)
            .put("notes", JSONObject(notesJson))
            .put("settings", JSONObject().apply {
                settings.forEach { (key, value) -> typed(value)?.let { put(key, it) } }
            })
            .apply {
                if (place != null) {
                    put("place", JSONObject().put("name", place.name).put("lat", place.latitude).put("lon", place.longitude))
                }
            }
            .toString(2)

    /** Il file letto, oppure null se non è un backup di Canvas o è di una versione più nuova. */
    fun decode(raw: String): BackupContent? = runCatching {
        val json = JSONObject(raw)
        if (json.optString("app") != APP) return null
        if (json.optInt("format", 0) !in 1..FORMAT) return null
        val notes = json.optJSONObject("notes") ?: return null
        val settings = json.optJSONObject("settings") ?: JSONObject()
        BackupContent(
            notes = NotesStore.parse(notes.toString()),
            settings = settings.keys().asSequence().associateWith { untyped(settings.getJSONObject(it)) }
                .filterValues { it != null },
            place = json.optJSONObject("place")?.let {
                GeoPlace(it.getString("name"), "", it.getDouble("lat"), it.getDouble("lon"))
            },
            createdAt = json.optLong("createdAt"),
        )
    }.getOrNull()

    /** "42 promemoria, 3 abitudini, 18 spese": cosa c'è nel backup, prima di sostituire. */
    fun summary(d: NotesData): String {
        fun count(n: Int, one: String, many: String) = if (n == 0) null else "$n ${if (n == 1) one else many}"
        return listOfNotNull(
            count(d.tasks.size, "promemoria", "promemoria"),
            count(d.habits.size, "abitudine", "abitudini"),
            count(d.expenses.size, "spesa", "spese"),
            count(d.notes.size, "nota", "note"),
            count(d.parcels.size, "pacco", "pacchi"),
            count(d.countdowns.size, "data", "date"),
        ).joinToString(", ").ifEmpty { "Nessun dato" }
    }

    // Le preferenze hanno tipi diversi (Int, Long, Float…): li scriviamo esplicitamente,
    // altrimenti un Long piccolo tornerebbe Int e Android rifiuterebbe di leggerlo.
    private fun typed(value: Any?): JSONObject? = when (value) {
        is String -> JSONObject().put("t", "s").put("v", value)
        is Int -> JSONObject().put("t", "i").put("v", value)
        is Long -> JSONObject().put("t", "l").put("v", value)
        is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
        is Boolean -> JSONObject().put("t", "b").put("v", value)
        is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.filterIsInstance<String>().sorted()))
        else -> null
    }

    private fun untyped(o: JSONObject): Any? = when (o.optString("t")) {
        "s" -> o.optString("v")
        "i" -> o.optInt("v")
        "l" -> o.optLong("v")
        "f" -> o.optDouble("v").toFloat()
        "b" -> o.optBoolean("v")
        "set" -> o.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        else -> null
    }

    /** Scrive il backup nel file scelto dall'utente. */
    suspend fun export(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            NotesStore.init(context)
            AppSettings.init(context)
            val now = System.currentTimeMillis()
            val text = encode(NotesStore.exportJson(), AppSettings.snapshot(), WeatherStore.manualPlace(context), now)
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            prefs(context).edit().putLong(KEY_LAST, now).apply()
        }.isSuccess
    }

    /** Legge un file scelto dall'utente, senza toccare nulla. */
    suspend fun read(context: Context, uri: Uri): BackupContent? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }.getOrNull()?.let(::decode)
    }

    /** Sostituisce dati e impostazioni con quelli del backup. */
    fun restore(context: Context, content: BackupContent) {
        NotesStore.init(context)
        AppSettings.init(context)
        NotesStore.replaceAll(content.notes)
        AppSettings.restore(content.settings)
        if (content.place != WeatherStore.manualPlace(context)) WeatherStore.setManualPlace(context, content.place)
    }

    /** Quando è stato fatto l'ultimo backup (millisecondi), null se mai. */
    fun lastExport(context: Context): Long? =
        prefs(context).getLong(KEY_LAST, 0L).takeIf { it > 0 }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
