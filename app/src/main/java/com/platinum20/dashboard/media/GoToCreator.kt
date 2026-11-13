package com.platinum20.dashboard.media

import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtFavorites
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * "Vai all'artista" e "Vai al canale" del tieni premuto (1.518, Antonio): da un brano o un album di Spotify
 * alla pagina dell'artista, da un video di YouTube alla pagina del canale. Le tessere conoscono solo la chiave
 * del media: il canale si cerca prima nei video già in memoria, l'artista (e il canale che manca) si chiede al tocco.
 */
object GoToCreator {
    /** Cosa si apre da una cosa tenuta premuta. */
    enum class Target { ARTIST, CHANNEL }

    /** Il bersaglio di [item], o null se non ha un autore da aprire (radio, podcast, playlist, mix, video del telefono). */
    fun targetOf(item: ResumeItem): Target? = when {
        item.kind == ResumeKind.YOUTUBE && item.key.isNotBlank() -> Target.CHANNEL
        item.kind == ResumeKind.SPOTIFY && spotifyParts(item.key) != null -> Target.ARTIST
        else -> null
    }

    /** ("track"|"album", id) da un uri di Spotify; null per tutto il resto. */
    fun spotifyParts(key: String): Pair<String, String>? {
        val parts = key.split(":")
        if (parts.size != 3 || parts[0] != "spotify" || parts[2].isBlank()) return null
        return if (parts[1] == "track" || parts[1] == "album") parts[1] to parts[2] else null
    }

    /** Il canale di [videoId] tra i video già noti (iscrizioni, Mi piace, cronologia), senza rete. */
    fun knownChannel(videoId: String, videos: List<YtVideo>, watched: Collection<YtWatched>): YtChannel? {
        videos.firstOrNull { it.id == videoId && it.channelId.isNotBlank() }?.let { return YtChannel(it.channelId, it.channel, it.channelAvatar) }
        watched.firstOrNull { it.id == videoId && it.channelId.isNotBlank() }?.let { return YtChannel(it.channelId, it.channel, it.channelAvatar) }
        return null
    }

    /** Fuori dal fumetto: il fumetto si chiude subito, la ricerca continua. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Trova la pagina e la apre con [open]; [fail] dice in italiano perché no. */
    fun open(item: ResumeItem, open: (String) -> Unit, fail: (String) -> Unit) {
        when (targetOf(item)) {
            Target.CHANNEL -> {
                val known = knownChannel(
                    item.key,
                    YouTubeStore.subscriptions.value.data.orEmpty() + YouTubeStore.subscriptionsMore.value + YtFavorites.items.value,
                    YtHistory.items.value.values,
                )
                if (known != null) return open(Routes.youtubeChannel(known))
                scope.launch {
                    val channel = runCatching { YtResolver.channelOf(item.key) }.getOrNull()
                    if (channel != null) open(Routes.youtubeChannel(channel)) else fail("Non trovo il canale di questo video")
                }
            }
            Target.ARTIST -> {
                val (kind, id) = spotifyParts(item.key) ?: return
                scope.launch {
                    val result = SpotifyStore.firstArtist(kind, id)
                    val artist = result.data
                    when {
                        artist != null -> open(Routes.spotifyItem(artist))
                        result.error != null -> fail(result.error)
                        else -> fail("Spotify non dice chi è l'artista")
                    }
                }
            }
            null -> Unit
        }
    }
}
