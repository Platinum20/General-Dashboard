package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.settings.AppSettings

/**
 * Musica e Video nella Home (1.634, Antonio: "lasciamo solo la Home"): le stesse file che stavano in Media, dentro due
 * card che scorrono di lato come Documenti, con le copertine grandi. Il titolo apre la pagina intera, il tasto a
 * destra la cronologia.
 */
@Composable
internal fun HomeMusicCard(open: (String) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    PodcastStore.init(context)
    val spotifyLinked by SpotifyAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val spotifyOn = spotifyLinked || MediaService.SPOTIFY in loggedIn
    // Il riproduttore di Spotify si prepara già da qui: al primo tocco la musica parte subito.
    LaunchedEffect(spotifyLinked) { if (spotifyLinked) com.platinum20.dashboard.media.spotify.SpotifyPlayer.start(context) }
    LaunchedEffect(spotifyOn) {
        if (spotifyOn) {
            SpotifyStore.refreshHome()
            SpotifyStore.refreshLibrary()
            SpotifyStore.refreshListened()
        }
    }
    CardSection(
        title = "Musica",
        onClick = { open(if (spotifyOn) Routes.MUSIC_DISCOVER else Routes.login(MediaService.SPOTIFY)) },
        trailing = { HistoryButton { open(Routes.MUSIC_ALL) } },
    ) {
        PerTeRow(
            onOpen = { open(Routes.spotifyItem(it)) },
            onOpenMix = { open(Routes.mix(it.id)) },
            onOpenPodcast = {
                PodcastOpen.set(it)
                open(Routes.PODCAST)
            },
            onOpenRadio = { open(Routes.RADIO) },
            onOpenLiked = { open(Routes.LIKED_TRACKS) },
            onDiscover = { open(Routes.MUSIC_DISCOVER) },
            onOpenSaved = { open(Routes.savedMedia(video = false)) },
            onOpenAll = { open(Routes.MUSIC_ALL) },
            inCard = true,
        )
    }
}

/** I video dei canali a cui sei iscritto, dal più nuovo, grandi; in testa i salvati. Senza YouTube un invito a collegarlo. */
@Composable
internal fun HomeVideoCard(open: (String) -> Unit) {
    val context = LocalContext.current
    YouTubeAuth.init(context)
    val youTubeLinked by YouTubeAuth.connected.collectAsState()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val youTubeOn = youTubeLinked || MediaService.YOUTUBE in loggedIn
    val hideLive by AppSettings.hideLive.collectAsState()
    val hideMembers by AppSettings.hideMembers.collectAsState()
    val subs by YouTubeStore.subscriptions.collectAsState()
    val subsMore by YouTubeStore.subscriptionsMore.collectAsState()
    val subsEnded by YouTubeStore.subscriptionsEnded.collectAsState()
    val savedVideos = rememberSavedMedia(video = true)
    LaunchedEffect(youTubeOn) { if (youTubeOn) YouTubeStore.refreshSubscriptions(context) }
    val subVideos = if (youTubeOn) pickFeed(subs.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos else emptyList()
    val subsRow = remember(subVideos, subsMore) {
        (MediaShelves.newestFirst(subVideos) + pickFeed(subsMore, hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos)
            .distinctBy { it.id }
    }
    CardSection(
        title = "Video",
        onClick = { open(if (youTubeOn) Routes.MEDIA_NEWS else Routes.login(MediaService.YOUTUBE)) },
        trailing = { HistoryButton { open(Routes.YOUTUBE_FEED) } },
    ) {
        // Dentro la card i video sono larghi quanto la card meno il margine: il prossimo spunta a destra.
        val big = bigVideoWidth() - 32.dp
        val bigHeight = big * 9f / 16f
        Column(Modifier.padding(bottom = 14.dp)) {
            if (!youTubeOn && savedVideos.isEmpty()) {
                // Un invito di una riga, non una card dentro la card.
                androidx.compose.material3.Text(
                    "Collega YouTube: i video dei canali che segui qui",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable { open(Routes.login(MediaService.YOUTUBE)) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                return@Column
            }
            // Il margine fuori dallo scorrimento: i video spariscono al margine della card, come in Documenti.
            LazyRow(state = rememberRowAtStart(), modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (savedVideos.isNotEmpty()) item(key = "saved") {
                    SavedTile(bigHeight, savedVideos, wide = true) { open(Routes.savedMedia(video = true)) }
                }
                items(subsRow, key = { "sub-" + it.id }) { v ->
                    Box(Modifier.width(big)) { VideoCard(v, onClick = { open(Routes.youtubeWatch(v.watchUrl)) }) }
                }
                if (youTubeOn && subsRow.isEmpty() && subs.error == null && (subs.data == null || subs.loading)) items(2, key = { "subs-wait-$it" }) {
                    Placeholder(big, bigHeight, shape = MaterialTheme.shapes.large)
                }
                if (youTubeOn && subsRow.isNotEmpty() && !subsEnded) item(key = "subs-more") {
                    LaunchedEffect(subsRow.size) { YouTubeStore.loadMoreSubscriptions(context) }
                    Placeholder(big, bigHeight, shape = MaterialTheme.shapes.large)
                }
            }
            val subsError = subs.error
            if (youTubeOn && subsRow.isEmpty() && subsError != null && !subs.loading) {
                val signIn = videoFeedNeedsSignIn(subsError)
                StatusLine(
                    LinkStatus(if (signIn) LinkStatus.Kind.EXPIRED else LinkStatus.Kind.STALE, if (signIn) "Accesso a YouTube scaduto" else "I video non sono arrivati"),
                    action = if (signIn) "Rientra" else "Riprova",
                    onAction = {
                        if (signIn) open(Routes.login(MediaService.YOUTUBE))
                        else YouTubeStore.refreshSubscriptions(context, force = true)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
