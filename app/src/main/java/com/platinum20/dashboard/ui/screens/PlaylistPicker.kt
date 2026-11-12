package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
import com.platinum20.dashboard.media.spotify.SpotifyStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Mettere un brano in una playlist di Spotify, o crearne una (1.500). Tieni premuto un brano › "Aggiungi a una
 * playlist": sale il fumetto con le tue playlist come chip e "Nuova playlist"; il nome si scrive nel dialogo di
 * [PlaylistNameLayer], disegnato una volta sola sopra tutte le schermate.
 */
internal object PlaylistPicker {
    private val scope = MainScope()

    /** Il dialogo del nome è aperto: [Naming.track] = il brano da metterci dentro (null = playlist vuota). */
    data class Naming(val track: SpItem?)

    var naming by mutableStateOf<Naming?>(null)
        private set

    /** Il fumetto con le playlist dove si può scrivere: si leggono adesso, sono poche e cambiano. */
    fun open(context: Context, track: SpItem) {
        scope.launch {
            val result = SpotifyStore.editablePlaylists()
            val playlists = result.data
            if (playlists == null) {
                CaptureFeedback.tell(context, result.error ?: "Spotify non risponde, riprova")
                return@launch
            }
            val chips = SpotifyPlaylists.ordered(playlists, SpotifyStore.lastPlaylist).map { p ->
                BubbleChip(p.title) { add(context, p, track) }
            }
            val create = BubbleAction(Icons.Rounded.Add, "Nuova playlist", "Con ${track.title} dentro") { naming = Naming(track) }
            if (chips.isEmpty()) ActionBubble.show(create)
            else ActionBubble.show(BubbleAction(Icons.Rounded.PlaylistAdd, "Aggiungi ${track.title} a", chips = chips), create)
        }
    }

    /** Solo il nome: una playlist vuota, da riempire poi tenendo premuti i brani. */
    fun createEmpty() {
        naming = Naming(null)
    }

    fun close() {
        naming = null
    }

    private fun add(context: Context, playlist: SpItem, track: SpItem) {
        scope.launch {
            CaptureFeedback.tell(context, SpotifyStore.addToPlaylist(playlist, track), Icons.Rounded.PlaylistAdd)
        }
    }
}

/** Il dialogo "Nuova playlist": un campo per il nome e Crea. Va messo una volta, accanto agli altri fumetti. */
@Composable
internal fun PlaylistNameLayer() {
    val naming = PlaylistPicker.naming ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var name by remember(naming) { mutableStateOf("") }
    var busy by remember(naming) { mutableStateOf(false) }
    var problem by remember(naming) { mutableStateOf<String?>(null) }
    val clean = SpotifyPlaylists.cleanName(name)
    val ready = clean.isNotEmpty() && !busy

    fun create() {
        busy = true
        problem = null
        scope.launch {
            SpotifyStore.createPlaylist(clean, naming.track).fold(
                onSuccess = { made ->
                    haptic(Haptic.CONFIRM)
                    PlaylistPicker.close()
                    val text = naming.track?.let { "Creata ${made.title}, con ${it.title}" } ?: "Creata ${made.title}"
                    CaptureFeedback.tell(context, text, Icons.Rounded.PlaylistAdd)
                },
                onFailure = { problem = it.message },
            )
            busy = false
        }
    }

    CanvasDialog(
        onDismissRequest = { if (!busy) PlaylistPicker.close() },
        title = { Text("Nuova playlist") },
        text = {
            Column {
                naming.track?.let {
                    Text(
                        "Con dentro ${it.title}. È privata: la vedi solo tu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(SpotifyPlaylists.MAX_NAME) },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ready) create() }),
                    colors = canvasFieldColors(),
                    modifier = Modifier.fillMaxWidth().autoFocus(true),
                )
                problem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { ConfirmButton(enabled = ready, onClick = { create() }) { Text(if (busy) "Creo…" else "Crea") } },
        dismissButton = { DismissButton(enabled = !busy, onClick = { PlaylistPicker.close() }) { Text("Annulla") } },
    )
}
