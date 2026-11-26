package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.rounded.PlaylistPlay
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.PlayArrow
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
    // Le playlist appena create o riempite contano come ascoltate in quel momento (1.501).
    remember { SpotifyStore.loadTouched() }
    val touched by SpotifyStore.touched.collectAsState()
    return remember(history, watched, played, touched) {
        MediaMerge.heardAt(history, watched.mapValues { it.value.watchedAt }, played.map { (t, at) -> t.uri to at.toEpochMilli() } + touched.toList())
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection).pageFloor(),
        topBar = {
            TopLevelBar(
                title = if (videosOnly) "Video che ti piacciono" else "Brani che ti piacciono",
                subtitle = if (videosOnly) countLine(0, "", videos.size, "video") else countLine(tracks.size, "brani", 0, ""),
                scrollBehavior = bar, onBack = onBack,
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            val playingKey = rememberPlayingKey()
            FeedList(state, empty = if (videosOnly) "Nessun video con il Mi piace" else "Nessun brano che ti piace", spacing = 0.dp, edge = 0.dp) {
                if (entries.isNotEmpty()) item(key = "play") {
                    CanvasButton("Riproduci tutto", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = {
                        val now = System.currentTimeMillis()
                        CanvasPlayer.playList(context, entries.map { it.resume(now).let { r -> if (videosOnly) r else r.copy(link = LIKED_LINK) } })
                    }, primary = true)
                }
                if (entries.isNotEmpty()) mediaGap("play-gap")
                // Una card con le righe Canvas (1.563, strada A), come i brani di album e playlist.
                if (entries.isNotEmpty()) mediaGroup(
                    key = "liked",
                    title = if (videosOnly) "Video" else "Brani",
                    items = entries,
                    itemKey = { it.key },
                    trailing = { Text("${entries.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { _, entry ->
                    when (entry) {
                        is MixedEntry.Track -> MediaRow(
                            title = entry.item.title, line = entry.item.subtitle, image = entry.item.image,
                            trailing = entry.item.durationMs.takeIf { it > 0 }?.let(com.platinum20.dashboard.media.spotify.SpotifyPlaylists::clock),
                            now = entry.item.uri == playingKey,
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(entry.item) }) { playTracks(context, tracks, entry.item, LIKED_LINK) },
                        )
                        is MixedEntry.Video -> MediaRow(
                            title = entry.video.title, line = entry.video.channel, image = entry.video.thumbnail, wide = true,
                            now = entry.video.id == playingKey,
                            click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.youtube(entry.video) }) { onPlayVideo(entry.video.watchUrl) },
                        )
                    }
                }
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
    val state = Loadable(data = tracks.takeIf { mix != null }, loading = home.loading || library.loading, error = home.error)
    // Giro 2 (1.566): come album e playlist, la copertina grande col nome in cima e i brani sullo sfondo.
    run {
        Column(Modifier.fillMaxSize()) {
            val playingKey = rememberPlayingKey()
            val hero: @Composable () -> Unit = {
                MediaHero(
                    title = mix?.title ?: "Mix",
                    image = mix?.image.orEmpty(),
                    onBack = onBack,
                    mark = MediaMark(MediaType.MUSIC),
                    line = mix?.badge.orEmpty(),
                    detail = listOfNotNull("${tracks.size} brani".takeIf { tracks.isNotEmpty() }, MediaMarks.clock(tracks.sumOf { it.durationMs })).joinToString(" · "),
                    description = mix?.subtitle.orEmpty(),
                    onPlay = mix?.let { m -> { playTracks(context, tracks, null, mixLink(m)) } },
                    onShuffle = mix?.let { m -> { playTracks(context, tracks.shuffled(), null, mixLink(m)) } },
                    onMore = mix?.let { m -> { com.platinum20.dashboard.capture.SavedLinks.offer(com.platinum20.dashboard.capture.SavedLinks.mix(m.id, m.title, m.subtitle, m.image)) } },
                )
            }
            FeedList(state, empty = "Questo mix oggi non c'è più", spacing = 0.dp, edge = 0.dp, top = 0.dp, header = hero) {
                if (tracks.isNotEmpty()) mediaGroup(
                    key = "tracks",
                    title = "",
                    items = tracks,
                    itemKey = { it.uri },
                    trailing = { Text("${tracks.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { _, track ->
                    MediaRow(
                        title = track.title,
                        line = track.subtitle,
                        image = track.image,
                        trailing = track.durationMs.takeIf { it > 0 }?.let(com.platinum20.dashboard.media.spotify.SpotifyPlaylists::clock),
                        now = track.uri == playingKey,
                        click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(track) }) {
                            playTracks(context, tracks, track, mix?.let(::mixLink).orEmpty())
                        },
                    )
                }
            }
        }
    }
}

/**
 * "Scopri" dei video = solo la Home di YouTube (1.542, Antonio: "voglio letteralmente solo la home di youtube qui"):
 * le iscrizioni hanno già la loro fila in Media. Arrivando in fondo carica altri consigliati, senza fine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaNewsScreen(onBack: () -> Unit, onPlayVideo: (String) -> Unit) {
    val context = LocalContext.current
    val home by YouTubeStore.home.collectAsState()
    val ended by YouTubeStore.homeEnded.collectAsState()
    val hideLive by com.platinum20.dashboard.settings.AppSettings.hideLive.collectAsState()
    val hideMembers by com.platinum20.dashboard.settings.AppSettings.hideMembers.collectAsState()
    LaunchedEffect(Unit) { YouTubeStore.refreshHome(context) }
    val tips = remember(home.data, hideLive, hideMembers) {
        pickFeed(home.data.orEmpty(), hideShorts = true, hideLive = hideLive, hideMembers = hideMembers).videos
    }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.sharedItem(SharedKeys.VIDEO, rounded = true).nestedScroll(bar.nestedScrollConnection).pageFloor(),
        topBar = { TopLevelBar(title = "Scopri", subtitle = "Home di YouTube", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        val count = tips.size
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(Loadable(data = tips, loading = home.loading && count == 0, error = home.error.takeIf { count == 0 }), empty = "Niente da scoprire per ora", spacing = 0.dp, edge = 0.dp) {
                // Tutti i video in grande, uguali al primo (1.630, Antonio): niente più righe piccole in mezzo.
                if (tips.isNotEmpty()) mediaGroup(
                    key = "tips",
                    title = "",
                    items = tips,
                    itemKey = { it.id },
                    footer = if (!ended && home.data != null) ({
                        // Composto solo vicino al fondo: lì arrivano altri consigliati, come scorrendo la Home di YouTube.
                        LaunchedEffect(count) { YouTubeStore.loadMoreHome(context) }
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { LoadingPulse() }
                    }) else null,
                ) { _, v ->
                    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        VideoCard(v, onClick = { onPlayVideo(v.watchUrl) })
                    }
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
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.sharedItem(SharedKeys.MUSIC, rounded = true).nestedScroll(bar.nestedScrollConnection).pageFloor(),
        topBar = { TopLevelBar(title = "Scopri", subtitle = "Musica dai tuoi ascolti", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        val tile = coverTileWidth()
        val empty = fresh.isEmpty() && rows.isEmpty() && similarArtists.isEmpty() && similarTracks.isEmpty()
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(top = 8.dp, bottom = listBottom(96.dp)),
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
        LazyRow(state = rememberRowAtStart(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
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
 * La playlist "Salvati", sempre la prima della sua fila in Media (2026-11-22, Antonio: "i video e la musica salvati
 * dovrebbero stare in una playlist nella loro sezione, sempre come prima cosa"). Stessa forma della fila: quadrata in
 * Musica, larga in Video. Un tocco apre la pagina (niente parte da solo), tenendo premuto Riproduci/Casuale/Coda.
 * Senza niente di salvato la tessera non c'è.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun SavedTile(height: androidx.compose.ui.unit.Dp, saved: List<com.platinum20.dashboard.capture.SavedLinks.Later>, wide: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    MediaTile(
        height, wide = wide, image = "", title = "Salvati",
        author = savedCountLine(saved.size, video = wide), date = null, mark = null,
        click = Modifier.combinedClickable(onClick = onClick, onLongClick = { savedListActions(context, saved, video = wide, open = onClick) }),
    ) {
        SavedCover(video = wide, big = false, modifier = Modifier.matchParentSize())
    }
}

/**
 * La copertina fissa della playlist Salvati (2026-11-22, Antonio: "una copertina base, tipo i mix", non l'ultima cosa
 * salvata): il colore suo, il segnalibro in trasparenza e il nome grande in basso, come le copertine dei mix.
 */
@Composable
internal fun SavedCover(video: Boolean, big: Boolean, modifier: Modifier = Modifier) {
    val color = androidx.compose.ui.graphics.Color(if (video) SAVED_VIDEO_COLOR else SAVED_MUSIC_COLOR)
    val white = androidx.compose.ui.graphics.Color.White
    Box(
        modifier.background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(color.copy(alpha = 0.75f), color)),
        ),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.Bookmark, contentDescription = null,
            tint = white.copy(alpha = 0.22f),
            modifier = Modifier.align(Alignment.TopEnd).padding(if (big) 16.dp else 8.dp).size(if (big) 96.dp else 52.dp),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(if (big) 16.dp else 10.dp)) {
            Text(
                if (video) "VIDEO" else "MUSICA",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
                color = white.copy(alpha = 0.85f), maxLines = 1,
            )
            Text(
                "Salvati",
                style = (if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                    .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = white, maxLines = 1,
            )
        }
    }
}

/** I colori fissi delle due playlist Salvati, scuri come quelli dei mix perché la scritta bianca si legga. */
internal const val SAVED_MUSIC_COLOR = 0xFF2D4F8F
internal const val SAVED_VIDEO_COLOR = 0xFF9C3F32

/** La riga sotto il nome della playlist Salvati: "1 video", "5 video", "1 salvato", "8 salvati". */
internal fun savedCountLine(count: Int, video: Boolean): String = when {
    video -> "$count video"
    count == 1 -> "1 salvato"
    else -> "$count salvati"
}

/** Quello che dei salvati entra nella coda così com'è (album, playlist e video del telefono no), nell'ordine dei salvati. */
internal fun savedPlayable(items: List<ResumeItem>): List<ResumeItem> = items.filter { com.platinum20.dashboard.capture.SavedLinks.queueable(it) }

/** Fa partire tutti i salvati, dal primo o mescolati; se niente entra in coda, apre la pagina. */
internal fun playSaved(context: android.content.Context, saved: List<com.platinum20.dashboard.capture.SavedLinks.Later>, shuffle: Boolean, open: () -> Unit) {
    val items = savedPlayable(saved.map { it.item })
    if (items.isEmpty()) open() else CanvasPlayer.playList(context, if (shuffle) items.shuffled() else items)
}

/** Tenendo premuta la playlist Salvati: le stesse azioni di una playlist (regola della pressione lunga). */
internal fun savedListActions(context: android.content.Context, saved: List<com.platinum20.dashboard.capture.SavedLinks.Later>, video: Boolean, open: () -> Unit) {
    val playable = savedPlayable(saved.map { it.item })
    val icons = androidx.compose.material.icons.Icons.Rounded
    ActionBubble.show(
        BubbleHead("Playlist", "Salvati · " + savedCountLine(saved.size, video)),
        *listOfNotNull(
            // Riproduci (Ora · Casuale) e In coda: due righe divise in due, come ogni raccolta.
            if (playable.isEmpty()) null else choice(
                "Riproduci",
                *listOfNotNull(
                    BubbleChip("Ora", icon = icons.PlayArrow) { playSaved(context, saved, shuffle = false, open = open) },
                    if (playable.size < 2) null else BubbleChip("Casuale", icon = icons.Shuffle) { playSaved(context, saved, shuffle = true, open = open) },
                ).toTypedArray(),
            ),
            if (playable.isEmpty()) null else choice(
                "In coda",
                BubbleChip("Subito dopo", icon = icons.PlaylistPlay) {
                    playable.reversed().forEach(CanvasPlayer::addNext)
                    com.platinum20.dashboard.capture.CaptureFeedback.tell(context, "Dopo questo: ${playable.size} salvati", icons.PlaylistPlay)
                },
                BubbleChip("In fondo", icon = icons.QueueMusic) {
                    playable.forEach(CanvasPlayer::add)
                    com.platinum20.dashboard.capture.CaptureFeedback.tell(context, "In coda: ${playable.size} salvati", icons.QueueMusic)
                },
            ),
            Slot.open(open),
        ).toTypedArray(),
    )
}

/**
 * La pagina della playlist "Salvati" (1.490, come playlist dal 2026-11-22): in cima le copertine con Riproduci e
 * Casuale, sotto le cose salvate dalla più recente. Un tocco ne apre una ferma, tenendo premuto il fumetto dei media
 * (riproduci, dopo, in coda, togli). Restano anche negli Appunti di Home: è la stessa lista.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SavedMediaScreen(video: Boolean, onBack: () -> Unit, onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val saved = rememberSavedMedia(video)
    val playable = remember(saved) { savedPlayable(saved.map { it.item }) }
    val playingKey = rememberPlayingKey()
    Column(Modifier.fillMaxSize()) {
        val hero: @Composable () -> Unit = {
            MediaHero(
                title = "Salvati",
                image = "",
                onBack = onBack,
                line = if (video) "Video da vedere dopo" else "Musica e podcast per dopo",
                detail = savedCountLine(saved.size, video),
                onPlay = if (playable.isEmpty()) null else ({ playSaved(context, saved, shuffle = false) {} }),
                onShuffle = if (playable.size < 2) null else ({ playSaved(context, saved, shuffle = true) {} }),
                picture = {
                    SavedCover(video = video, big = true, modifier = Modifier.size(MediaPage.HERO_COVER).clip(MaterialTheme.shapes.large))
                },
            )
        }
        FeedList(Loadable(data = saved), empty = "Niente di salvato", spacing = 0.dp, edge = 0.dp, top = 0.dp, header = hero) {
            if (saved.isNotEmpty()) mediaGroup(
                key = "saved",
                title = "",
                items = saved,
                itemKey = { it.note.id },
            ) { _, l ->
                MediaRow(
                    title = l.item.title,
                    line = LaterText.subtitle(l.item),
                    image = l.item.coverImage(),
                    wide = video,
                    round = l.item.kind == ResumeKind.RADIO,
                    mark = MediaMarks.of(l.item.kind, l.item.durationMs),
                    now = l.item.key == playingKey,
                    click = Modifier.combinedClickable(
                        onClick = { com.platinum20.dashboard.capture.SavedLinks.open(context, SavedLink.Media(l.item), onOpenRoute) },
                        onLongClick = { savedMediaActions(context, l, onOpenRoute) },
                    ),
                )
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

/** In Scopri dei video uno ogni tanti è in grande (1.563), così la card non è un muro di righe. */

/**
 * La cronologia della musica (1.569, Antonio: "vorrei solo gli ascoltati di recente, cioè una cronologia
 * semplicemente"): i brani ascoltati su Spotify, dal più recente, per giorno come la Cronologia dei video. Niente
 * libreria, mix o playlist: quelli stanno nella fila Musica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicAllScreen(onBack: () -> Unit, @Suppress("UNUSED_PARAMETER") onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val played by SpotifyStore.played.collectAsState()
    val listened by SpotifyStore.listened.collectAsState()
    LaunchedEffect(Unit) { SpotifyStore.refreshListened(force = true) }
    val bar = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val playingKey = rememberPlayingKey()
    val today = remember { java.time.LocalDate.now() }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val state = Loadable(
        data = if (played.isEmpty() && listened.data == null) null else played,
        loading = listened.loading,
        error = if (played.isEmpty()) listened.error else null,
    )
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection).pageFloor(),
        topBar = { TopLevelBar(title = "Cronologia", subtitle = "Ascoltati di recente", scrollBehavior = bar, onBack = onBack) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            FeedList(state, empty = "Ancora niente ascoltato", spacing = 0.dp, edge = 0.dp) {
                // La linea del tempo della Cronologia dei video: "Oggi ——— ●", poi i brani con l'ora.
                HistoryDays.group(played, { it.second.toEpochMilli() }, zone, today).forEach { (day, list) ->
                    item(key = "dh$day") { DayLineHeader(day, now = day == "Oggi") }
                    list.forEach { (track, at) ->
                        item(key = "p-" + track.uri + at.toEpochMilli()) {
                            val time = at.atZone(zone).toLocalTime()
                            MediaRow(
                                title = track.title,
                                line = listOf(track.subtitle, "%d:%02d".format(time.hour, time.minute)).filter { it.isNotBlank() }.joinToString(" · "),
                                image = track.image, now = track.uri == playingKey,
                                click = Modifier.tapOrSave({ com.platinum20.dashboard.capture.SavedLinks.spotify(track) }) {
                                    playTracks(context, played.map { it.first }, track)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
