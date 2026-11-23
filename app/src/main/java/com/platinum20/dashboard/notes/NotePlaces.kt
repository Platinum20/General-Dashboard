package com.platinum20.dashboard.notes

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.R
import com.platinum20.dashboard.work.MorningWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Il posto di una nota: quando ci arrivi, la notifica con la lista (idea 11, Antonio 2026-11-23). */
data class NotePlace(val noteId: Long, val name: String, val lat: Double, val lon: Double)

/**
 * I posti legati alle note. L'avviso usa gli avvisi di vicinanza del telefono (LocationManager), niente servizi
 * esterni; si rimettono a ogni avvio dell'app e del telefono, perché il sistema li dimentica al riavvio.
 */
object NotePlaces {
    private const val PREFS = "note_places"
    private const val KEY = "places"
    private const val KEY_LAST = "last_"
    /** Quanto vicino conta come "sei lì" (metri): un supermercato, non un quartiere. */
    const val RADIUS_M = 150f
    /** Un avviso per posto ogni tanto: entrare e uscire dal parcheggio non deve suonare tre volte. */
    const val QUIET_MS = 3 * 60 * 60_000L
    const val ACTION_NEAR = "com.platinum20.dashboard.NOTE_PLACE_NEAR"

    private val _places = MutableStateFlow<List<NotePlace>>(emptyList())
    val places: StateFlow<List<NotePlace>> = _places.asStateFlow()
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        _places.value = parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
    }

    fun of(noteId: Long): NotePlace? = _places.value.firstOrNull { it.noteId == noteId }

    fun set(context: Context, place: NotePlace) {
        init(context)
        save(context, _places.value.filter { it.noteId != place.noteId } + place)
        registerAll(context)
    }

    fun remove(context: Context, noteId: Long) {
        init(context)
        unregister(context, noteId)
        save(context, _places.value.filter { it.noteId != noteId })
    }

    private fun save(context: Context, list: List<NotePlace>) {
        _places.value = list
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, serialize(list)).apply()
    }

    fun canWatch(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Ad app chiusa il telefono avvisa solo con "Consenti sempre" (Android 10 e dopo). */
    fun canWatchClosed(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun intent(context: Context, noteId: Long): PendingIntent {
        val i = Intent(context, NotePlaceReceiver::class.java).setAction(ACTION_NEAR).putExtra(NoteNotify.KEY_NOTE, noteId)
        // Mutabile: il sistema ci aggiunge "sto entrando / uscendo".
        return PendingIntent.getBroadcast(context, Math.floorMod(noteId, 100_000L).toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    /** Rimette tutti gli avvisi (all'avvio, dopo un riavvio, dopo un posto nuovo); le note sparite perdono il posto. */
    @SuppressLint("MissingPermission")
    fun registerAll(context: Context) {
        init(context)
        if (!canWatch(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        NotesStore.init(context)
        val alive = _places.value.filter { NotesStore.note(it.noteId) != null }
        if (alive.size != _places.value.size) save(context, alive)
        alive.forEach { p ->
            runCatching { lm.addProximityAlert(p.lat, p.lon, RADIUS_M, -1L, intent(context, p.noteId)) }
        }
    }

    private fun unregister(context: Context, noteId: Long) {
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        runCatching { lm.removeProximityAlert(intent(context, noteId)) }
    }

    /** Si può avvisare adesso? Una volta ogni [QUIET_MS] per nota. */
    fun shouldTell(context: Context, noteId: Long, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST + noteId, 0L) < QUIET_MS) return false
        prefs.edit().putLong(KEY_LAST + noteId, now).apply()
        return true
    }

    internal fun serialize(list: List<NotePlace>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.noteId).put("name", it.name).put("lat", it.lat).put("lon", it.lon)) }
    }.toString()

    internal fun parse(raw: String?): List<NotePlace> = runCatching {
        val a = JSONArray(raw ?: return emptyList())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            NotePlace(o.getLong("id"), o.optString("name"), o.getDouble("lat"), o.getDouble("lon"))
        }
    }.getOrDefault(emptyList())

    /** Il testo dell'avviso: "3 da fare: latte, pane, caffè"; null se nella nota non resta niente da fare. */
    fun text(body: String): String? {
        val open = AttachedNotes.open(body)
        if (open.isEmpty()) return null
        val shown = open.take(4).joinToString(", ") { it.text.replaceFirstChar { c -> c.lowercase() } }
        return "${open.size} da fare: $shown" + if (open.size > 4) ", …" else ""
    }
}

/** Arrivi nel posto di una nota: la notifica con la lista, "Apri" e "Fatto tutto". Al riavvio rimette gli avvisi. */
class NotePlaceReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotePlaces.ACTION_NEAR) {
            NotePlaces.registerAll(context)
            return
        }
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val noteId = intent.getLongExtra(NoteNotify.KEY_NOTE, -1L)
        NotesStore.init(context)
        NotePlaces.init(context)
        val note = NotesStore.note(noteId) ?: return
        val place = NotePlaces.of(noteId) ?: return
        val (title, body) = NoteText.split(note.text)
        val text = NotePlaces.text(body) ?: return
        if (!MorningWorker.canNotify(context) || !NotePlaces.shouldTell(context, noteId)) return
        NoteNotify.ensureChannel(context)
        val id = NoteNotify.placeId(noteId)
        val name = NoteFormat.plain(title).ifBlank { "la tua nota" }
        val notification = NotificationCompat.Builder(context, NoteNotify.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("Sei a ${place.name}: $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(NoteNotify.open(context, id, OpenRequest(OpenRequest.NOTE, url = noteId.toString())))
            .addAction(0, "Apri", NoteNotify.open(context, id + 1, OpenRequest(OpenRequest.NOTE, url = noteId.toString())))
            .addAction(0, "Fatto tutto", NoteNotify.action(context, NoteNotify.ACTION_ALL_DONE, noteId, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
