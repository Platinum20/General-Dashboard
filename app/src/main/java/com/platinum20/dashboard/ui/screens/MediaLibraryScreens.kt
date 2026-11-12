package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.MediaMerge
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtFavorites
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo

/** Una voce di una lista che mescola Spotify e YouTube (1.484). */
internal sealed interface MixedEntry {
    val key: String

    data class Track(val item: SpItem) : MixedEntry {
        override val key: String get() = "s-" + item.uri
    }

    data class Video(val video: YtVideo) : MixedEntry {
        override val key: String get() = "y-" + video.id
    }
}

/** Una voce nella coda unica: i brani suonano con Spotify, i video col player di YouTube, uno dopo l'altro. */
internal fun MixedEntry.resume(now: Long): ResumeItem = when (this) {
    is MixedEntry.Track -> ResumeItem(ResumeKind.SPOTIFY, item.uri, item.title, item.subtitle, item.image, durationMs = item.durationMs, at = now)
    is MixedEntry.Video -> ResumeItem(ResumeKind.YOUTUBE, video.id, video.title, video.channel, video.thumbnail, durationMs = video.durationSec * 1000L, at = now)
}

/**
 * "Mi piace" (1.484, Antonio: le cose che in Spotify e YouTube fanno lo stesso stanno in una voce sola): i brani che
 * ti piacciono e i video con il Mi piace insieme, a turno dai più recenti; "Riproduci tutto" li mette nella coda unica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLikedScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    YtFavorites.init(context)
    val library by SpotifyStore.library.collectAsState()
    val liked by YouTubeStore.liked.collectAsState()
    val mine by YtFavorites.items.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshLibrary()
        YouTubeStore.refreshLiked(context)
    }
    val tracks = library.data?.liked.orEmpty()
    val videos = (mine + liked.data.orEmpty()).distinctBy { it.id }
    val entries = MediaMerge.alternate(tracks.map { MixedEntry.Track(it) }, videos.map { MixedEntry.Video(it) })
    val state = Loadable(
        data = entries.takeIf { library.data != null || liked.data != null || mine.isNotEmpty() },
        loading = library.loading || liked.loading,
        error = if (library.data == null && liked.data == null) library.error ?: liked.error else null,
    )
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Mi piace", subtitle = countLine(tracks.size, "brani", videos.size, "video"), scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = "Nessun brano o video con il Mi piace") {
                if (entries.isNotEmpty()) item(key = "play") {
                    CanvasButton("Riproduci tutto", onClick = {
                        val now = System.currentTimeMillis()
                        CanvasPlayer.playList(context, entries.map { it.resume(now) })
                    }, primary = true)
                }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is MixedEntry.Track -> ItemRow(entry.item, onClick = { playTracks(context, tracks, entry.item) })
                        is MixedEntry.Video -> VideoRow(entry.video, onClick = { onPlayVideo(entry.video.watchUrl) })
                    }
                }
            }
        }
    }
}

/**
 * "Playlist" (1.484): le playlist di Spotify e di YouTube e gli album salvati in una lista sola; ognuna si apre nella
 * sua pagina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlaylistsScreen(onBack: () -> Unit, onOpenSpotify: (SpItem) -> Unit, onOpenYouTube: (YtPlaylist) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val spotify by SpotifyStore.playlists.collectAsState()
    val library by SpotifyStore.library.collectAsState()
    val youtube by YouTubeStore.playlists.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshPlaylists()
        SpotifyStore.refreshLibrary()
        YouTubeStore.refreshPlaylists(context)
    }
    val fromSpotify = spotify.data.orEmpty() + library.data?.albums.orEmpty()
    val fromYouTube = youtube.data.orEmpty()
    val entries: List<Any> = MediaMerge.alternate(fromSpotify, fromYouTube)
    val state = Loadable(
        data = entries.takeIf { spotify.data != null || youtube.data != null },
        loading = spotify.loading || youtube.loading,
        error = if (spotify.data == null && youtube.data == null) spotify.error ?: youtube.error else null,
    )
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Playlist", subtitle = "Spotify e YouTube, album salvati", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = "Nessuna playlist") {
                items(entries, key = { e -> if (e is SpItem) "s-" + e.uri else "y-" + (e as YtPlaylist).id }) { e ->
                    when (e) {
                        is SpItem -> ItemRow(e, onClick = { onOpenSpotify(e) })
                        is YtPlaylist -> PlaylistLine(e, onClick = { onOpenYouTube(e) }, padded = false)
                    }
                }
            }
        }
    }
}

/** "412 brani · 86 video"; una parte sola se l'altra è zero, vuoto se tutte e due. */
internal fun countLine(a: Int, aName: String, b: Int, bName: String): String =
    listOfNotNull(a.takeIf { it > 0 }?.let { "$it $aName" }, b.takeIf { it > 0 }?.let { "$it $bName" }).joinToString(" · ")
