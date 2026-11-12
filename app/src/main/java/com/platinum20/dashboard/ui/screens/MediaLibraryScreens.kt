package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.media.coverImage
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
import com.platinum20.dashboard.capture.SavedLink
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.filled.Favorite
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

/** Una voce nella coda unica: i brani suonano con Spotify, i video col player di YouTube, uno dopo l'altro. */
internal fun MixedEntry.resume(now: Long): ResumeItem = when (this) {
    is MixedEntry.Track -> ResumeItem(ResumeKind.SPOTIFY, item.uri, item.title, item.subtitle, item.image, durationMs = item.durationMs, at = now)
    is MixedEntry.Video -> ResumeItem(ResumeKind.YOUTUBE, video.id, video.title, video.channel, video.thumbnail, durationMs = video.durationSec * 1000L, at = now)
}

/**
 * "Brani che ti piacciono" o "Video che ti piacciono" (1.488, Antonio: "dividi brani da video"): una pagina per parte,
 * in ordine di ascolto; "Riproduci tutto" li mette nella coda unica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaLikedScreen(videosOnly: Boolean, onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    YtFavorites.init(context)
    val library by SpotifyStore.library.collectAsState()
    val liked by YouTubeStore.liked.collectAsState()
    val mine by YtFavorites.items.collectAsState()
    LaunchedEffect(videosOnly) {
        if (videosOnly) YouTubeStore.refreshLiked(context) else SpotifyStore.refreshLibrary()
    }
    val tracks = library.data?.liked.orEmpty()
    val videos = (mine + liked.data.orEmpty()).distinctBy { it.id }
    val heard = rememberHeardAt()
    val entries = MediaMerge.byRecency(if (videosOnly) videos.map { MixedEntry.Video(it) } else tracks.map { MixedEntry.Track(it) }) { heard[it.timeKey] }
    val source = if (videosOnly) liked else library
    val state = Loadable(
        data = entries.takeIf { source.data != null || (videosOnly && mine.isNotEmpty()) },
        loading = source.loading,
        error = if (source.data == null) source.error else null,
    )
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = if (videosOnly) "Video che ti piacciono" else "Brani che ti piacciono",
                subtitle = if (videosOnly) countLine(0, "", videos.size, "video") else countLine(tracks.size, "brani", 0, ""),
                scrollBehavior = bar, onBack = onBack,
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = if (videosOnly) "Nessun video con il Mi piace" else "Nessun brano che ti piace") {
                if (entries.isNotEmpty()) item(key = "play") {
                    CanvasButton("Riproduci tutto", onClick = {
                        val now = System.currentTimeMillis()
                        CanvasPlayer.playList(context, entries.map { it.resume(now).let { r -> if (videosOnly) r else r.copy(link = LIKED_LINK) } })
                    }, primary = true)
                }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is MixedEntry.Track -> TrackLine(entry.item, onClick = { playTracks(context, tracks, entry.item, LIKED_LINK) })
                        is MixedEntry.Video -> VideoLine(entry.video, onClick = { onPlayVideo(entry.video.watchUrl) })
                    }
                }
            }
        }
    }
}

/**
 * "Playlist" (1.488, Antonio: quelle di YouTube no): le playlist di Spotify e gli album salvati, dall'ultimo ascoltato;
 * ognuna si apre nella sua pagina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlaylistsScreen(onBack: () -> Unit, onOpenSpotify: (SpItem) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val spotify by SpotifyStore.playlists.collectAsState()
    val library by SpotifyStore.library.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshPlaylists()
        SpotifyStore.refreshLibrary()
    }
    val heard = rememberHeardAt()
    val entries = MediaMerge.byRecency((spotify.data.orEmpty() + library.data?.albums.orEmpty()).distinctBy { it.uri }) { heard[it.uri] }
    val state = Loadable(data = entries.takeIf { spotify.data != null }, loading = spotify.loading, error = if (spotify.data == null) spotify.error else null)
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Playlist", subtitle = "Playlist e album salvati", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = "Nessuna playlist") {
                items(entries, key = { "s-" + it.uri }) { e -> TrackLine(e, onClick = { onOpenSpotify(e) }) }
            }
        }
    }
}

/** "412 brani · 86 video"; una parte sola se l'altra è zero, vuoto se tutte e due. */
internal fun countLine(a: Int, aName: String, b: Int, bName: String): String =
    listOfNotNull(a.takeIf { it > 0 }?.let { "$it $aName" }, b.takeIf { it > 0 }?.let { "$it $bName" }).joinToString(" · ")


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
    val made by com.platinum20.dashboard.media.spotify.MadeStore.mixes.collectAsState()
    LaunchedEffect(Unit) { com.platinum20.dashboard.media.spotify.MadeStore.refresh(context) }
    val mix = remember(home.data, library.data, made) { mixesOf(home.data, library.data, today, made).firstOrNull { it.id == mixId } }
    val tracks = mix?.tracks.orEmpty()
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = mix?.title ?: "Mix",
                subtitle = listOfNotNull(mix?.badge ?: "Mix", "${tracks.size} brani".takeIf { tracks.isNotEmpty() }, MediaMarks.clock(tracks.sumOf { it.durationMs })).joinToString(" · "),
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
                            CanvasButton("Riproduci", onClick = { playTracks(context, tracks, null, mix?.let(::mixLink).orEmpty()) }, primary = true)
                        }
                    }
                }
                items(tracks, key = { "t-" + it.uri }) { track -> ItemRow(track, onClick = { playTracks(context, tracks, track, mix?.let(::mixLink).orEmpty()) }) }
            }
        }
    }
}

/**
 * "Scopri" dei video (1.488, Antonio: "la home sarebbe novità, scopri, la stessa cosa"): in cima i video nuovi delle
 * iscrizioni dall'ultima visita, poi la Home di YouTube che arrivando in fondo carica altri consigliati, senza fine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaNewsScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    val subs by YouTubeStore.subscriptions.collectAsState()
    val home by YouTubeStore.home.collectAsState()
    val ended by YouTubeStore.homeEnded.collectAsState()
    val hideLive by com.platinum20.dashboard.settings.AppSettings.hideLive.collectAsState()
    val hideMembers by com.platinum20.dashboard.settings.AppSettings.hideMembers.collectAsState()
    LaunchedEffect(Unit) {
        YouTubeStore.refreshSubscriptions(context)
        YouTubeStore.refreshHome(context)
    }
    val since = com.platinum20.dashboard.media.MediaFeedNow.since
    val fresh = remember(subs.data, since, hideLive, hideMembers) {
        pickFeed(subs.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos
            .filter { com.platinum20.dashboard.media.MediaFresh.isNew(it.published, since) }
            .sortedByDescending { it.published }
    }
    val tips = remember(home.data, fresh, hideLive, hideMembers) {
        val shown = fresh.map { it.id }.toSet()
        pickFeed(home.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos.filter { it.id !in shown }
    }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Scopri", subtitle = MediaShelves.title("Video", fresh.size), scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        val count = fresh.size + tips.size
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(Loadable(data = fresh + tips, loading = (home.loading || subs.loading) && count == 0, error = home.error.takeIf { count == 0 }), empty = "Niente da scoprire per ora") {
                if (fresh.isNotEmpty()) {
                    item(key = "fresh-h") { SectionHeader("Nuovi dalle iscrizioni") }
                    items(fresh, key = { "f-" + it.id }) { v -> VideoCard(v, onClick = { onPlayVideo(v.watchUrl) }) }
                }
                if (tips.isNotEmpty()) {
                    item(key = "tips-h") { SectionHeader("Per te su YouTube") }
                    items(tips, key = { "t-" + it.id }) { v -> VideoCard(v, onClick = { onPlayVideo(v.watchUrl) }) }
                }
                if (!ended && home.data != null) item(key = "more") {
                    // Composto solo vicino al fondo: lì arrivano altri consigliati, come scorrendo la Home di YouTube.
                    LaunchedEffect(count) { YouTubeStore.loadMoreHome(context) }
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingPulse() }
                }
            }
        }
    }
}

/**
 * "Scopri" della musica (1.488, Antonio: la prima idea): le nuove uscite degli artisti che segui, poi per ogni tuo genere
 * gli artisti che non ascolti ancora e le sue playlist. Tutte file orizzontali; un tocco apre la pagina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDiscoverScreen(onBack: () -> Unit, onOpen: (SpItem) -> Unit) {
    val context = LocalContext.current
    SpotifyAuth.init(context)
    val releases by SpotifyStore.releases.collectAsState()
    val seen by SpotifyStore.seenReleases.collectAsState()
    val discover by SpotifyStore.discover.collectAsState()
    LaunchedEffect(Unit) {
        SpotifyStore.refreshReleases()
        SpotifyStore.refreshListened()
        SpotifyStore.refreshDiscover()
        com.platinum20.dashboard.media.spotify.MadeStore.refresh(context)
    }
    // Simili da Last.fm (1.491): artisti che non ascolti ancora e brani rimasti fuori da Discover Weekly.
    val similarArtists by com.platinum20.dashboard.media.spotify.MadeStore.similarArtists.collectAsState()
    val similarTracks by com.platinum20.dashboard.media.spotify.MadeStore.similarTracks.collectAsState()
    remember { com.platinum20.dashboard.media.lastfm.LastFm.init(context) }
    val lastFmKey by com.platinum20.dashboard.media.lastfm.LastFm.key.collectAsState()
    val today = remember { java.time.LocalDate.now() }
    val fresh = MediaShelves.freshFirst(releases.data.orEmpty()) { it.item.id !in seen }
    val rows = discover.data.orEmpty()
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Scopri", subtitle = "Musica dai tuoi ascolti", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        val tile = coverTileWidth()
        val empty = fresh.isEmpty() && rows.isEmpty() && similarArtists.isEmpty() && similarTracks.isEmpty()
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (empty && (releases.loading || discover.loading)) item(key = "loading") { PlaceholderList(Modifier.padding(horizontal = 16.dp), label = "Cerco musica nuova") }
            if (empty && !releases.loading && !discover.loading) item(key = "empty") {
                Text(
                    discover.error ?: "Ascolta un po' di musica: Scopri parte da lì",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (fresh.isNotEmpty()) item(key = "releases") {
                DiscoverRow("Nuove uscite", "Degli artisti che segui") {
                    items(fresh, key = { "r-" + it.item.id }) { release ->
                        MediaTile(
                            tile, wide = false, image = release.item.image, title = release.item.title,
                            author = release.item.subtitle.substringBefore(" · "), date = com.platinum20.dashboard.media.spotify.NewReleases.ago(release.released, today),
                            mark = MediaMarks.of(release.item),
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(release.item) }) {
                                SpotifyStore.markReleaseSeen(release.item.id)
                                onOpen(release.item)
                            },
                        ) { if (release.item.id !in seen) NewDot(Modifier.align(Alignment.TopEnd)) }
                    }
                }
            }
            if (similarArtists.isNotEmpty()) item(key = "sim-artists") {
                DiscoverRow("Artisti simili", "A quelli che ascolti di più") {
                    items(similarArtists, key = { "sa-" + it.uri }) { artist ->
                        MediaTile(
                            tile, wide = false, image = artist.image, title = artist.title, author = "Artista",
                            date = null, mark = MediaMarks.of(artist), click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(artist) }) { onOpen(artist) }, round = true,
                        )
                    }
                }
            }
            if (similarTracks.isNotEmpty()) item(key = "sim-tracks") {
                DiscoverRow("Brani simili", "Ai tuoi preferiti, che non hai ancora sentito") {
                    items(similarTracks, key = { "st-" + it.uri }) { track ->
                        MediaTile(
                            tile, wide = false, image = track.image, title = track.title, author = track.subtitle.substringBefore(" · "),
                            date = null, mark = MediaMarks.of(track),
                            click = Modifier.clickable { playTracks(context, similarTracks, track) },
                        )
                    }
                }
            }
            if (lastFmKey.isBlank()) item(key = "lastfm") {
                Text(
                    "Per artisti e brani simili, Discover Weekly e i Daily Mix metti la chiave di Last.fm in Impostazioni › Media",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(rows, key = { "d-" + it.title }) { row ->
                DiscoverRow(row.title, row.why) {
                    items(row.items, key = { "i-" + it.uri }) { item ->
                        MediaTile(
                            tile, wide = false, image = item.image, title = item.title,
                            author = if (item.kind == com.platinum20.dashboard.media.spotify.SpKind.ARTIST) "Artista" else item.subtitle.substringBefore(" · "),
                            date = null, mark = MediaMarks.of(item), click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(item) }) { onOpen(item) },
                            round = item.kind == com.platinum20.dashboard.media.spotify.SpKind.ARTIST,
                        )
                    }
                }
            }
        }
    }
}

/** Una fila di Scopri: titolo, il perché sotto, le tessere di lato. */
@Composable
private fun DiscoverRow(title: String, why: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionHeader(title)
            Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Le cose salvate per dopo di una fila di Media (1.490): i video per Video, il resto per Musica; dalla più recente. */
@Composable
internal fun rememberSavedMedia(video: Boolean): List<com.platinum20.dashboard.capture.SavedLinks.Later> {
    val context = LocalContext.current
    remember { com.platinum20.dashboard.notes.NotesStore.init(context) }
    val data by com.platinum20.dashboard.notes.NotesStore.data.collectAsState()
    return remember(data.notes, video) {
        com.platinum20.dashboard.capture.SavedLinks.later(data.notes).filter { com.platinum20.dashboard.capture.SavedLinks.isVideo(it.item) == video }
    }
}

/**
 * La tessera "Salvati · N", prima di tutte nella sua fila (1.490, Antonio: le cose salvate per dopo stanno in Media, e
 * la tessera sparisce quando non c'è niente). Stessa forma della fila: quadrata in Musica, larga in Video.
 */
@Composable
internal fun SavedTile(height: androidx.compose.ui.unit.Dp, count: Int, wide: Boolean, onClick: () -> Unit) {
    MediaTile(height, wide = wide, image = "", title = "Salvati · $count", author = "Per dopo", date = null, mark = null, click = Modifier.clickable(onClick = onClick)) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.Bookmark, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp),
        )
    }
}

/**
 * "Salvati" (1.490): le cose salvate per dopo, una sotto l'altra dalla più recente. Un tocco la apre ferma, tenendo
 * premuto la togli (con Annulla). Vuota, si torna indietro.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SavedMediaScreen(video: Boolean, onBack: () -> Unit, onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val saved = rememberSavedMedia(video)
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Salvati", subtitle = if (video) "Video da vedere dopo" else "Musica e podcast per dopo", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(Loadable(data = saved), empty = "Niente di salvato") {
                items(saved, key = { "s-" + it.note.id }) { l ->
                    MediaLine(
                        wide = video, image = l.item.coverImage(), title = l.item.title, author = LaterText.subtitle(l.item),
                        mark = MediaMarks.of(l.item.kind, l.item.durationMs),
                        click = Modifier.combinedClickable(
                            onClick = { com.platinum20.dashboard.capture.SavedLinks.open(context, SavedLink.Media(l.item), onOpenRoute) },
                            onLongClick = { com.platinum20.dashboard.capture.SavedLinks.forget(l.note, "Tolto: ${l.item.title}") },
                        ),
                        round = l.item.kind == ResumeKind.RADIO,
                    )
                }
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

/**
 * La fila Musica per intero, in verticale (1.492, Antonio: "cliccando sul titolo ... la versione completa che scorre
 * verticalmente"): le stesse voci nello stesso ordine, senza il limite della fila, tutte con la stessa riga `MediaLine`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAllScreen(onBack: () -> Unit, onOpenRoute: (String) -> Unit) {
    val saved = rememberSavedMedia(video = false)
    val (entries, podcasts) = rememberPerTe()
    val context = LocalContext.current
    val played by SpotifyStore.played.collectAsState()
    LaunchedEffect(Unit) { SpotifyStore.refreshListened() }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val openPodcast: (com.platinum20.dashboard.media.podcast.Podcast?) -> Unit = {
        PodcastOpen.set(it)
        onOpenRoute(com.platinum20.dashboard.Routes.PODCAST)
    }
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        topBar = { TopLevelBar(title = "Musica", subtitle = "Dall'ultima che hai ascoltato", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(Loadable(data = Unit), empty = "") {
                if (saved.isNotEmpty()) item(key = "saved") {
                    MediaLine(
                        wide = false, image = "", title = "Salvati", author = "${saved.size} da ascoltare dopo", mark = null,
                        click = Modifier.clickable { onOpenRoute(com.platinum20.dashboard.Routes.savedMedia(video = false)) },
                    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
                // Una playlist vuota da riempire: i brani si aggiungono tenendoli premuti (1.498).
                item(key = "new-playlist") {
                    MediaLine(
                        wide = false, image = "", title = "Nuova playlist", author = "Poi tieni premuto un brano per aggiungerlo", mark = null,
                        click = Modifier.clickable { PlaylistPicker.createEmpty() },
                    ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                }
                items(entries, key = { it.key }) { e ->
                    when (e) {
                        is PerTe.Mix -> MediaLine(
                            wide = false, image = e.mix.image, title = e.mix.title, author = "Mix · " + e.mix.subtitle, mark = MediaMark(MediaType.MUSIC),
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.mix(e.mix.id, e.mix.title, e.mix.subtitle, e.mix.image) }) {
                                onOpenRoute(com.platinum20.dashboard.Routes.mix(e.mix.id))
                            },
                        )
                        is PerTe.Item -> MediaLine(
                            wide = false, image = e.item.image, title = e.item.title, author = e.item.subtitle.substringBefore(" · "), mark = MediaMarks.of(e.item),
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(e.item) }) {
                                onOpenRoute(com.platinum20.dashboard.Routes.spotifyItem(e.item))
                            },
                        )
                        is PerTe.Liked -> MediaLine(
                            wide = false, image = "", title = "Brani che ti piacciono", author = "${e.count} brani", mark = null,
                            click = Modifier.clickable { onOpenRoute(com.platinum20.dashboard.Routes.LIKED_TRACKS) },
                        ) {
                            androidx.compose.material3.Icon(
                                androidx.compose.material.icons.Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is PerTe.Artist -> MediaLine(
                            wide = false, image = e.artist.image, title = e.artist.title, author = "Artista", mark = null, round = true,
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(e.artist) }) {
                                onOpenRoute(com.platinum20.dashboard.Routes.spotifyItem(e.artist))
                            },
                        )
                        is PerTe.Show -> MediaLine(
                            wide = false, image = e.podcast.artwork, title = e.podcast.title,
                            author = e.podcast.author.ifBlank { if (e.podcast.fromSpotify) "Spotify" else "Podcast" }, mark = MediaMark(MediaType.PODCAST),
                            click = Modifier.clickable { openPodcast(e.podcast) },
                        )
                    }
                }
                item(key = "podcast-all") {
                    MediaLine(
                        wide = false, image = "", title = "I tuoi podcast",
                        author = if (podcasts.isNotEmpty()) "${podcasts.size} che segui" else "Scegline qualcuno", mark = MediaMark(MediaType.PODCAST),
                        click = Modifier.clickable { openPodcast(null) },
                    )
                }
                item(key = "radio-all") {
                    MediaLine(
                        wide = false, image = "", title = "Radio", author = "Preferite e più ascoltate", mark = MediaMark(MediaType.RADIO),
                        click = Modifier.clickable { onOpenRoute(com.platinum20.dashboard.Routes.RADIO) },
                    )
                }
                // I brani ascoltati stanno qui, nella cronologia della musica, non nella fila (1.496, Antonio).
                if (played.isNotEmpty()) {
                    item(key = "played-h") { SectionHeader("Ascoltati di recente") }
                    items(played, key = { (t, at) -> "p-" + t.uri + at.toEpochMilli() }) { (track, _) ->
                        TrackLine(track, onClick = { playTracks(context, played.map { it.first }, track) })
                    }
                }
            }
        }
    }
}
