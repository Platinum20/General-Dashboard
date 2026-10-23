package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.podcast.Podcast
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioStore

/** Le due anime di "Radio e podcast": si passa dall'una all'altra con le pillole in alto. */
enum class ListenTab(val label: String) { PODCAST("Podcast"), RADIO("Radio") }

/**
 * Radio e podcast in un'unica sezione: tutto quello che si ascolta "parlato" o in diretta,
 * con i podcast seguiti qui e quelli salvati su Spotify messi insieme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(start: ListenTab, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(start) }
    var open by remember { mutableStateOf<Podcast?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val radio by RadioStore.now.collectAsState()
    val podcast by PodcastStore.now.collectAsState()
    Scaffold(
        topBar = {
            TopLevelBar(
                title = open?.title ?: "Radio e podcast",
                subtitle = when {
                    open != null -> open?.author?.ifBlank { null } ?: if (open?.fromSpotify == true) "Da Spotify" else null
                    radio?.playing == true -> "In diretta: ${radio?.station?.name}"
                    podcast?.playing == true -> "In ascolto: ${podcast?.episode?.podcastTitle}"
                    else -> "Ascolta, segui e riprendi da dove eri"
                },
                onBack = { if (open != null) open = null else onBack() },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            if (open == null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListenTab.entries.forEach { t -> CanvasChip(selected = tab == t, onClick = { tab = t }, label = t.label) }
                }
            }
            when (tab) {
                ListenTab.PODCAST -> PodcastBody(open, onOpen = { open = it })
                ListenTab.RADIO -> RadioBody()
            }
        }
    }
}
