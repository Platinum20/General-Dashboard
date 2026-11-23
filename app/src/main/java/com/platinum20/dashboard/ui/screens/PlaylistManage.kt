package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpPlaylistInfo
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
import com.platinum20.dashboard.media.spotify.SpotifyStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Gestire le tue playlist di Spotify (1.507, Antonio: "poter cancellare le playlist e comunque gestirle"). Nella pagina
 * della playlist il tasto "Altro" accanto a Riproduci apre il fumetto: Modifica (nome, descrizione, privata),
 * Riordina e togli brani, Elimina. Quelle che segui ma non sono tue si possono solo togliere dalla libreria.
 */
internal object PlaylistManage {
    private val scope = MainScope()

    /** Per pagina: i dettagli e se si possono cambiare (letti all'apertura, aggiornati dopo una modifica). */
    val infos = mutableStateMapOf<String, Pair<SpPlaylistInfo, Boolean>>()

    /** Il dialogo "Modifica playlist" aperto su questi dettagli. */
    var editing by mutableStateOf<SpPlaylistInfo?>(null)
        private set

    /** Le playlist in modalità "Riordina e togli brani". */
    val arranging = mutableStateMapOf<String, Boolean>()

    suspend fun load(playlistId: String) {
        SpotifyStore.playlistInfo(playlistId)?.let { infos[playlistId] = it }
    }

    /** Il nome come lo sai adesso: dopo "Modifica" cambia subito anche il titolo della pagina. */
    fun title(item: SpItem): String = infos[item.id]?.first?.name?.takeIf { it.isNotBlank() } ?: item.title

    fun closeEditor() {
        editing = null
    }

    /** Il fumetto di "Altro": lo stesso del tieni premuto, senza Apri (ci sei già). [onGone] = non c'è più: si torna indietro. */
    fun openMenu(context: Context, playlist: SpItem, onGone: () -> Unit) {
        val known = infos[playlist.id]
        val canEdit = known?.second == true
        val name = title(playlist)
        val actions = buildList {
            if (canEdit) {
                add(act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Modifica", "nome, descrizione, privata") { editing = known?.first })
                add(act(BubbleGroup.CAMBIA, Icons.Outlined.SwapVert, "Riordina e togli brani", "trascini o tocchi per togliere") {
                    arranging[playlist.id] = true
                })
                add(Slot.delete(confirm = "Tocca ancora: sparisce da Spotify, anche sul PC") { delete(context, playlist, name, onGone) })
            } else if (SpotifyStore.inLibrary(playlist.id)) {
                add(Slot.remove { delete(context, playlist, name, onGone) })
            }
        }
        if (actions.isEmpty()) CaptureFeedback.tell(context, "Questa playlist non è tua: puoi solo ascoltarla")
        else ActionBubble.show(BubbleHead("Playlist", name), *actions.toTypedArray())
    }

    /** Apre "Modifica playlist" sui dettagli già letti. */
    fun edit(playlistId: String) {
        editing = infos[playlistId]?.first
    }

    fun delete(context: Context, playlist: SpItem, name: String, onGone: () -> Unit) {
        scope.launch {
            val error = SpotifyStore.deletePlaylist(playlist)
            if (error != null) return@launch CaptureFeedback.tell(context, error)
            arranging.remove(playlist.id)
            onGone()
            // Spotify la tiene: "Annulla" la rimette nella libreria com'era, coi suoi brani.
            CaptureFeedback.tell(context, "Eliminata $name", Icons.Rounded.PlaylistRemove, "Annulla") {
                SpotifyStore.restorePlaylist(playlist.copy(title = name))?.let { CaptureFeedback.tell(context, it) }
            }
        }
    }

    /** Tocco su un brano mentre si riordina: portarlo in cima o in fondo senza trascinarlo, o toglierlo dalla playlist. */
    fun trackMenu(context: Context, playlist: SpItem, track: SpItem, index: Int, count: Int) {
        val name = title(playlist)
        val chips = buildList {
            if (index > 0) add(BubbleChip("In cima") { move(context, playlist, index, 0) })
            if (index < count - 1) add(BubbleChip("In fondo") { move(context, playlist, index, count - 1) })
        }
        ActionBubble.show(
            BubbleHead("Brano", track.title),
            *listOfNotNull(
                chips.takeIf { it.isNotEmpty() }?.let { choice("Sposta", *it.toTypedArray()) },
                Slot.remove { remove(context, playlist, track, index, name) },
            ).toTypedArray(),
        )
    }

    fun move(context: Context, playlist: SpItem, from: Int, to: Int) {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            SpotifyStore.moveInPlaylist(playlist, from, to)?.let { CaptureFeedback.tell(context, it) }
        }
    }

    private fun remove(context: Context, playlist: SpItem, track: SpItem, index: Int, name: String) {
        scope.launch {
            val error = SpotifyStore.removeFromPlaylist(playlist, track)
            if (error != null) return@launch CaptureFeedback.tell(context, error)
            CaptureFeedback.tell(context, "Tolto da $name", Icons.Rounded.PlaylistRemove, "Annulla") {
                SpotifyStore.putBack(playlist, track, index)?.let { CaptureFeedback.tell(context, it) }
            }
        }
    }

    suspend fun save(before: SpPlaylistInfo, name: String, description: String, public: Boolean): String? {
        val error = SpotifyStore.updatePlaylist(before, name, description, public)
        if (error == null) load(before.id)
        return error
    }
}

/** Il dialogo "Modifica playlist": nome, descrizione e privata. Disegnato da [PlaylistNameLayer], una volta sola. */
@Composable
internal fun PlaylistEditLayer() {
    val before = PlaylistManage.editing ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var name by remember(before) { mutableStateOf(before.name) }
    var description by remember(before) { mutableStateOf(before.description) }
    var hidden by remember(before) { mutableStateOf(!before.public) }
    var busy by remember(before) { mutableStateOf(false) }
    var problem by remember(before) { mutableStateOf<String?>(null) }
    val ready = SpotifyPlaylists.cleanName(name).isNotEmpty() && !busy

    fun save() {
        busy = true
        problem = null
        scope.launch {
            val error = PlaylistManage.save(before, name, description, public = !hidden)
            busy = false
            if (error != null) {
                problem = error
                return@launch
            }
            haptic(Haptic.CONFIRM)
            PlaylistManage.closeEditor()
            CaptureFeedback.tell(context, "Salvata ${SpotifyPlaylists.cleanName(name)}", Icons.Rounded.LibraryMusic)
        }
    }

    CanvasDialog(
        onDismissRequest = { if (!busy) PlaylistManage.closeEditor() },
        title = { Text("Modifica playlist") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SpotifyPlaylists.MAX_NAME) },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = canvasFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(SpotifyPlaylists.MAX_DESCRIPTION) },
                    label = { Text("Descrizione") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = canvasFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Privata", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (hidden) "La vedi solo tu" else "Chiunque col link la trova",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = hidden, onCheckedChange = hapticToggle { v: Boolean -> hidden = v }, enabled = !busy)
                }
                problem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { ConfirmButton(enabled = ready, onClick = { save() }) { Text(if (busy) "Salvo…" else "Salva") } },
        dismissButton = { DismissButton(enabled = !busy, onClick = { PlaylistManage.closeEditor() }) { Text("Annulla") } },
    )
}
