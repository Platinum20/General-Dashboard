package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
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

/** Da dove si arriva in "Radio e podcast": quella metà della pagina sta in cima. */
enum class ListenTab { RADIO, PODCAST }

/**
 * Radio e podcast in un'unica pagina che scorre, senza pillole: radio preferite e recenti, i tuoi
 * podcast (anche quelli salvati su Spotify) con gli ultimi episodi, le radio più ascoltate.
 * Cerca = trascinare giù (Cerca ovunque già su Radio e podcast).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(start: ListenTab, onBack: () -> Unit) {
    val context = LocalContext.current
    RadioStore.init(context)
    PodcastStore.init(context)
    var open by remember { mutableStateOf<Podcast?>(null) }
    BackHandler(enabled = open != null) { open = null }
    val radio by RadioStore.now.collectAsState()
    val podcast by PodcastStore.now.collectAsState()
    val popular by RadioStore.popular.collectAsState()
    val favorites by RadioStore.favorites.collectAsState()
    val recent by RadioStore.recent.collectAsState()
    val followedHere by PodcastStore.followed.collectAsState()
    val spotify by PodcastStore.spotify.collectAsState()
    val followed = remember(followedHere, spotify) { PodcastStore.merge(followedHere, spotify) }
    val latest by PodcastStore.latest.collectAsState()
    val positions by PodcastStore.positions.collectAsState()
    val search by PodcastStore.search.collectAsState()
    var idea by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        RadioStore.loadPopular()
        PodcastStore.loadLatest()
    }
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
            val page = open
            if (page != null) {
                PodcastPage(page, podcast)
                return@Column
            }
            val podcasts: LazyListScope.() -> Unit = {
                podcastSections(
                    followed = followed,
                    latest = latest,
                    now = podcast,
                    positions = positions,
                    ideas = search.takeIf { idea != null },
                    onIdea = { text ->
                        idea = text
                        PodcastStore.search(text)
                    },
                    onOpen = { open = it },
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (start == ListenTab.PODCAST) podcasts()
                radioSections(popular, favorites, recent, radio)
                if (start == ListenTab.RADIO) podcasts()
            }
        }
    }
}
