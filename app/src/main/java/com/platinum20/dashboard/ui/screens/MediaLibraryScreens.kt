package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

/** Una voce di una lista che mescola Spotify e YouTube (1.485). */
internal sealed interface MixedEntry {
    val key: String

    data class Track(val item: SpItem) : MixedEntry {
        override val key: String get() = "s-" + item.uri
    }

    data class Video(val video: YtVideo) : MixedEntry {
        override val key: String get() = "y-" + video.id
    }
}

/** Quando hai ascoltato o guardato l'ultima volta ogni cosa (1.488): per mettere le liste miste nell'ordine di ascolto. */
@Composable
internal fun rememberHeardAt(): Map<String, Long> {
    val context = LocalContext.current
    remember { com.platinum20.dashboard.media.MediaHistory.init(context); com.platinum20.dashboard.media.youtube.YtHistory.init(context) }
    val history by com.platinum20.dashboard.media.MediaHistory.items.collectAsState()
    val watched by com.platinum20.dashboard.media.youtube.YtHistory.items.collectAsState()
    val played by SpotifyStore.played.collectAsState()
    return remember(history, watched, played) {
        MediaMerge.heardAt(history, watched.mapValues { it.value.watchedAt }, played.map { (t, at) -> t.uri to at.toEpochMilli() })
    }
}

internal val MixedEntry.timeKey: String get() = when (this) {
    is MixedEntry.Track -> item.uri
    is MixedEntry.Video -> video.id
}

/** La chiave per [rememberHeardAt] di una voce di Album e playlist. */
internal fun playlistKey(e: Any): String = if (e is SpItem) e.uri else (e as YtPlaylist).id

/** Una voce nella coda unica: i brani suonano con Spotify, i video col player di YouTube, uno dopo l'altro. */
internal fun MixedEntry.resume(now: Long): ResumeItem = when (this) {
    is MixedEntry.Track -> ResumeItem(ResumeKind.SPOTIFY, item.uri, item.title, item.subtitle, item.image, durationMs = item.durationMs, at = now)
    is MixedEntry.Video -> ResumeItem(ResumeKind.YOUTUBE, video.id, video.title, video.channel, video.thumbnail, durationMs = video.durationSec * 1000L, at = now)
}

/**
 * "Mi piace" (1.485, Antonio: le cose che in Spotify e YouTube fanno lo stesso stanno in una voce sola): i brani che
 * ti piacciono e i video con il Mi piace insieme, in ordine di ascolto (1.488); "Riproduci tutto" li mette nella coda unica.
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
    val heard = rememberHeardAt()
    val entries = MediaMerge.byRecency(tracks.map { MixedEntry.Track(it) } + videos.map { MixedEntry.Video(it) }) { heard[it.timeKey] }
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
                        is MixedEntry.Track -> TrackLine(entry.item, onClick = { playTracks(context, tracks, entry.item) })
                        is MixedEntry.Video -> VideoLine(entry.video, onClick = { onPlayVideo(entry.video.watchUrl) })
                    }
                }
            }
        }
    }
}

/**
 * "Playlist" (1.485): le playlist di Spotify e di YouTube e gli album salvati in una lista sola; ognuna si apre nella
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
    val heard = rememberHeardAt()
    val entries: List<Any> = MediaMerge.byRecency(fromSpotify + fromYouTube) { heard[playlistKey(it)] }
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
                        is SpItem -> TrackLine(e, onClick = { onOpenSpotify(e) })
                        is YtPlaylist -> PlaylistMediaLine(e, onClick = { onOpenYouTube(e) })
                    }
                }
            }
        }
    }
}

/** "412 brani · 86 video"; una parte sola se l'altra è zero, vuoto se tutte e due. */
internal fun countLine(a: Int, aName: String, b: Int, bName: String): String =
    listOfNotNull(a.takeIf { it > 0 }?.let { "$it $aName" }, b.takeIf { it > 0 }?.let { "$it $bName" }).joinToString(" · ")

/** Quante tessere al massimo in una fila di Media prima di "Tutti": il resto è nella pagina. */
private const val ROW_MAX = 20

/**
 * "Album e playlist" sotto Per te (1.486, Antonio: "una riga sua"): le playlist di Spotify e di YouTube e gli album
 * salvati, in ordine di ascolto; il titolo e l'ultima tessera aprono la pagina con tutte.
 */
@Composable
internal fun AlbumsRow(onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val spotify by SpotifyStore.playlists.collectAsState()
    val library by SpotifyStore.library.collectAsState()
    val youtube by YouTubeStore.playlists.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshPlaylists()
        YouTubeStore.refreshPlaylists(context)
    }
    val heard = rememberHeardAt()
    val all: List<Any> = MediaMerge.byRecency(spotify.data.orEmpty() + library.data?.albums.orEmpty() + youtube.data.orEmpty()) { heard[playlistKey(it)] }
    if (all.isEmpty()) return
    val tile = coverTileWidth()
    val open = { onOpenRoute(com.platinum20.dashboard.Routes.SPOTIFY_FEED) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenableHeader("Album e playlist", onOpen = open, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(all.take(ROW_MAX), key = { e -> if (e is SpItem) "s-" + e.uri else "y-" + (e as YtPlaylist).id }) { e ->
                when (e) {
                    is SpItem -> MediaTile(
                        tile, wide = false, image = e.image, title = e.title, author = e.subtitle.substringBefore(" · "), date = null,
                        mark = MediaMarks.of(e), click = Modifier.clickable { onOpenRoute(com.platinum20.dashboard.Routes.spotifyItem(e)) },
                    )
                    is YtPlaylist -> MediaTile(
                        tile, wide = true, image = e.thumbnail, title = e.title,
                        author = if (e.count > 0) "YouTube · ${e.count} video" else "YouTube", date = null,
                        mark = MediaMark(MediaType.VIDEO), click = Modifier.clickable { onOpenRoute(com.platinum20.dashboard.Routes.youtubePlaylist(e)) },
                    )
                }
            }
            if (all.size > ROW_MAX) item(key = "albums-all") { AllTile(tile, "Tutte", "${all.size} in tutto", open) }
        }
    }
}

/** "Mi piace" sotto Per te (1.486): brani e video che ti piacciono, in ordine di ascolto; un tocco li fa partire. */
@Composable
internal fun LikedRow(onOpenRoute: (String) -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    YtFavorites.init(context)
    val library by SpotifyStore.library.collectAsState()
    val liked by YouTubeStore.liked.collectAsState()
    val mine by YtFavorites.items.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshLiked(context) }
    val tracks = library.data?.liked.orEmpty()
    val videos = (mine + liked.data.orEmpty()).distinctBy { it.id }
    val heard = rememberHeardAt()
    val all = MediaMerge.byRecency(tracks.map { MixedEntry.Track(it) } + videos.map { MixedEntry.Video(it) }) { heard[it.timeKey] }
    if (all.isEmpty()) return
    val tile = coverTileWidth()
    val open = { onOpenRoute(com.platinum20.dashboard.Routes.YOUTUBE_LIKED) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenableHeader("Mi piace", onOpen = open, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(all.take(ROW_MAX), key = { it.key }) { e ->
                when (e) {
                    is MixedEntry.Track -> MediaTile(
                        tile, wide = false, image = e.item.image, title = e.item.title, author = e.item.subtitle, date = null,
                        mark = MediaMarks.of(e.item),
                        click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(e.item) }) { playTracks(context, tracks, e.item) },
                    )
                    is MixedEntry.Video -> VideoTile(e.video, tile) { onPlayVideo(e.video.watchUrl) }
                }
            }
            if (all.size > ROW_MAX) item(key = "liked-all") { AllTile(tile, "Tutti", countLine(tracks.size, "brani", videos.size, "video"), open) }
        }
    }
}

/**
 * La pagina di un mix (1.486, Antonio: "prima la pagina con Riproduci, come le playlist"): la copertina, Riproduci e i
 * brani; un tocco su un brano fa partire il mix da lì.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixScreen(mixId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val home by SpotifyStore.home.collectAsState()
    val library by SpotifyStore.library.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshHome()
        SpotifyStore.refreshLibrary()
    }
    val today = remember { java.time.LocalDate.now() }
    val mix = remember(home.data, library.data) { mixesOf(home.data, library.data, today).firstOrNull { it.id == mixId } }
    val tracks = mix?.tracks.orEmpty()
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = mix?.title ?: "Mix",
                subtitle = listOfNotNull("Mix", "${tracks.size} brani".takeIf { tracks.isNotEmpty() }, MediaMarks.clock(tracks.sumOf { it.durationMs })).joinToString(" · "),
                scrollBehavior = bar,
                onBack = onBack,
            )
        },
    ) { inner ->
        val state = Loadable(data = tracks.takeIf { mix != null }, loading = home.loading || library.loading, error = home.error)
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = "Questo mix oggi non c'è più") {
                if (mix != null) item(key = "head") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(mix.image, MaterialTheme.shapes.medium, 96.dp, MediaMark(MediaType.MUSIC))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mix.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Spacer(Modifier.height(8.dp))
                            CanvasButton("Riproduci", onClick = { playTracks(context, tracks, null) }, primary = true)
                        }
                    }
                }
                items(tracks, key = { "t-" + it.uri }) { track -> ItemRow(track, onClick = { playTracks(context, tracks, track) }) }
            }
        }
    }
}

/**
 * "Tutte le novità" (1.486, Antonio: in fondo alla fila, il resto in verticale come prima): i video grandi e gli
 * episodi dal più nuovo, con il segno ULTIMA VISITA.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaNewsScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val rows = com.platinum20.dashboard.media.MediaFeedNow.rows
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Novità", subtitle = MediaShelves.title("Video ed episodi", rows.fresh.size), scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        @Composable
        fun Entry(entry: com.platinum20.dashboard.media.FeedEntry) = when (entry) {
            is com.platinum20.dashboard.media.FeedEntry.Video -> VideoCard(entry.video, onClick = { onPlayVideo(entry.video.watchUrl) })
            is com.platinum20.dashboard.media.FeedEntry.Podcast -> EpisodeLine(entry.episode)
        }
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(Loadable(data = rows.fresh + rows.older), empty = "Nessuna novità") {
                items(rows.fresh, key = { "f-" + it.key }) { Entry(it) }
                if (rows.fresh.isNotEmpty() && rows.older.isNotEmpty()) item(key = "marker") { NewsVisitMarker("Ultima visita") }
                items(rows.older, key = { "o-" + it.key }) { Entry(it) }
            }
        }
    }
}

/** Un episodio nell'elenco delle novità: copertina quadrata con simbolo e durata, titolo, podcast e quando a destra. */
@Composable
private fun EpisodeLine(episode: com.platinum20.dashboard.media.podcast.Episode) {
    Row(
        Modifier.fillMaxWidth().bouncyPress(0.97f).clickable { com.platinum20.dashboard.media.podcast.PodcastStore.play(episode) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(episode.image, MaterialTheme.shapes.medium, 96.dp, MediaMark(MediaType.PODCAST, MediaMarks.minutes(episode.durationSec * 1000)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    episode.podcastTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                com.platinum20.dashboard.media.MediaStories.episodeAgo(episode, System.currentTimeMillis())?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
