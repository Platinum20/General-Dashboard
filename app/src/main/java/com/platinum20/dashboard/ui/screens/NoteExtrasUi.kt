package com.platinum20.dashboard.ui.screens

import android.Manifest
import android.content.Context
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.notes.LineReminder
import com.platinum20.dashboard.notes.NotePlace
import com.platinum20.dashboard.notes.NotePlaces
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.weather.LocationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Due azioni dell'editor delle note (Antonio, 2026-11-23): "Ricordamelo" sulla riga del cursore (idea 16) e il
 * posto della nota (idea 11). Stanno qui per non allargare NoteEditor: [NoteExtrasHost] disegna i dialoghi e chiede
 * i permessi, [lineAction] e [placeAction] danno le righe del fumetto "Altro".
 */
@Stable
internal class NoteExtras(val context: Context) {
    internal var pickLine by mutableStateOf<Triple<Long, String, String>?>(null)
    internal var searchFor by mutableStateOf<Long?>(null)
    internal var scope: CoroutineScope? = null
    internal var askLocation: ((() -> Unit) -> Unit)? = null
    internal var askAlways: (() -> Unit)? = null

    /**
     * "Ricordamelo" (PDF Fila-unica): due righe divise in due, tutte col nome. Stasera e Domani per la riga del
     * cursore (se c'è), Scegli giorno, e In un posto per tutta la nota.
     */
    fun remindChoice(noteId: () -> Long?, title: String, body: String, cursor: Int, current: Long?): BubbleAction {
        val line = LineReminder.lineAt(body, cursor)
        val now = LocalDateTime.now()
        val place = current?.let { NotePlaces.of(it) }
        return choice(
            if (line != null) "Ricordamelo · ${line.take(28)}" else "Ricordamelo",
            *listOfNotNull(
                line?.let { l -> LineReminder.tonight(now)?.let { at -> BubbleChip("Stasera 19", icon = Icons.Outlined.Bedtime) { noteId()?.let { remind(it, title, l, at) } } } },
                line?.let { l -> LineReminder.tomorrowMorning(now).let { at -> BubbleChip("Domani 9", icon = Icons.Outlined.WbSunny) { noteId()?.let { remind(it, title, l, at) } } } },
                line?.let { l -> BubbleChip("Scegli giorno", icon = Icons.Outlined.CalendarMonth) { noteId()?.let { pickLine = Triple(it, title, l) } } },
                // Niente fumetto dal fumetto: il chip apre subito "Dove te lo ricordo?" (Qui adesso, Cerca, Togli).
                BubbleChip(place?.name ?: "In un posto", selected = place != null, icon = Icons.Outlined.Place) { noteId()?.let { id -> askLocation?.invoke { searchFor = id } } },
            ).toTypedArray(),
        )
    }

    /** La riga diventa un promemoria con l'ora e il link alla nota: la notifica apre la nota. */
    fun remind(noteId: Long, title: String, line: String, at: LocalDateTime) {
        val zone = ZoneId.systemDefault()
        val task = NotesStore.addTaskAt(line, at.atZone(zone).toInstant().toEpochMilli())
        NotesStore.setTaskNotes(task.id, LineReminder.link(title, noteId))
        CaptureFeedback.tell(context, "Te lo ricordo " + LineReminder.whenText(at, LocalDate.now(zone)), Icons.Outlined.NotificationsActive, "Annulla") {
            NotesStore.deleteTask(task.id)
        }
    }

    /** Il posto della nota: quando ci arrivi, la notifica con la lista. Qui adesso o Cerca, e Togli se c'è già. */
    fun placeBubble(noteId: () -> Long?, current: Long?) {
        val place = current?.let { NotePlaces.of(it) }
        ActionBubble.show(
            BubbleHead("In un posto", place?.name ?: "la lista ti arriva quando sei lì"),
            *listOfNotNull(
                choice(
                    "Dove",
                    BubbleChip("Qui adesso", icon = Icons.Outlined.MyLocation) { noteId()?.let { id -> askLocation?.invoke { here(id) } } },
                    BubbleChip("Cerca", icon = Icons.Outlined.Search) { noteId()?.let { id -> askLocation?.invoke { searchFor = id } } },
                ),
                place?.let { p ->
                    Slot.remove {
                        NotePlaces.remove(context, p.noteId)
                        CaptureFeedback.tell(context, "Posto tolto", Icons.Outlined.Place, "Annulla") { NotePlaces.set(context, p) }
                    }
                },
            ).toTypedArray(),
        )
    }

    internal fun here(noteId: Long) {
        scope?.launch {
            val loc = LocationProvider.current(context)
            if (loc == null) {
                CaptureFeedback.tell(context, "Non trovo dove sei: prova Cerca un posto", Icons.Outlined.Place)
                return@launch
            }
            val name = withContext(Dispatchers.IO) { addressName(loc.latitude, loc.longitude) } ?: "questo posto"
            save(NotePlace(noteId, name, loc.latitude, loc.longitude))
        }
    }

    internal fun save(place: NotePlace) {
        NotePlaces.set(context, place)
        CaptureFeedback.tell(context, "Te lo ricordo quando arrivi a ${place.name}", Icons.Outlined.Place)
        if (!NotePlaces.canWatchClosed(context)) askAlways?.invoke()
    }

    /** Il nome corto di un punto: la via o il negozio, se il telefono lo sa; se no la città. */
    @Suppress("DEPRECATION")
    private fun addressName(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        val a = runCatching { Geocoder(context, Locale.ITALIAN).getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull() ?: return null
        return a.featureName?.takeIf { it.any(Char::isLetter) && it != a.thoroughfare } ?: a.thoroughfare ?: a.locality
    }

    /** Cerca col geocoder del telefono: i primi risultati come righe del fumetto. */
    @Suppress("DEPRECATION")
    internal fun search(noteId: Long, query: String) {
        scope?.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { if (Geocoder.isPresent()) Geocoder(context, Locale.ITALIAN).getFromLocationName(query, 5).orEmpty() else emptyList() }
                    .getOrDefault(emptyList())
            }
            if (found.isEmpty()) {
                CaptureFeedback.tell(context, "Non trovo \"$query\"", Icons.Outlined.Search)
                return@launch
            }
            ActionBubble.show(
                BubbleHead("Posti trovati", query),
                *found.map { a ->
                    val name = a.featureName?.takeIf { it.any(Char::isLetter) } ?: a.thoroughfare ?: a.locality ?: query
                    val where = listOfNotNull(a.thoroughfare?.takeIf { it != name }, a.locality?.takeIf { it != name }).joinToString(", ").ifEmpty { "qui intorno" }
                    BubbleAction(Icons.Outlined.Place, name, where) { save(NotePlace(noteId, name, a.latitude, a.longitude)) }
                }.toTypedArray(),
            )
        }
    }
}

/** I dialoghi e i permessi di [NoteExtras]: va messo una volta nella pagina dell'editor. */
@Composable
internal fun rememberNoteExtras(): NoteExtras {
    val context = LocalContext.current
    val extras = remember { NoteExtras(context) }
    extras.scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) pending?.invoke()
        else CaptureFeedback.tell(context, "Serve la posizione per avvisarti nel posto", Icons.Outlined.Place)
        pending = null
    }
    val alwaysLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) CaptureFeedback.tell(context, "Ad app chiusa ti avviso solo con \"Consenti sempre\"", Icons.Outlined.Place)
    }
    extras.askLocation = { run ->
        if (NotePlaces.canWatch(context)) run() else {
            pending = run
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    extras.askAlways = {
        if (android.os.Build.VERSION.SDK_INT >= 29) alwaysLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    extras.pickLine?.let { (id, title, line) ->
        DateTimePickDialog(null, onDismiss = { extras.pickLine = null }) { ms ->
            extras.pickLine = null
            extras.remind(id, title, line, LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()))
        }
    }
    extras.searchFor?.let { id ->
        var query by remember { mutableStateOf("") }
        CanvasDialog(
            onDismissRequest = { extras.searchFor = null },
            title = { Text("Dove te lo ricordo?") },
            text = {
                val place = NotePlaces.of(id)
                androidx.compose.foundation.layout.Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Coop via Roma, Milano") },
                        singleLine = true,
                        colors = canvasFieldColors(),
                        modifier = Modifier.fillMaxWidth().autoFocus(),
                    )
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { extras.searchFor = null; extras.here(id) }) { Text("Qui adesso") }
                        if (place != null) TextButton(onClick = {
                            extras.searchFor = null
                            NotePlaces.remove(context, place.noteId)
                            CaptureFeedback.tell(context, "Posto tolto", Icons.Outlined.Place, "Annulla") { NotePlaces.set(context, place) }
                        }) { Text("Togli ${place.name}", color = MaterialTheme.colorScheme.error, maxLines = 1) }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = query.isNotBlank(), onClick = {
                    extras.searchFor = null
                    extras.search(id, query.trim())
                }) { Text("Cerca") }
            },
            dismissButton = { TextButton(onClick = { extras.searchFor = null }) { Text("Annulla") } },
        )
    }
    return extras
}
