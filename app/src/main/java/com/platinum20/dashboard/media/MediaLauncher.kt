package com.platinum20.dashboard.media

import android.content.Context
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.podcast.PodcastStore
import com.platinum20.dashboard.media.radio.RadioStore
import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import com.platinum20.dashboard.media.youtube.player.YtPlayer

/** Fa partire una voce (di Continua, di una nota, della coda) con il player giusto. */
object MediaLauncher {
    /**
     * Avvia [item] e restituisce la schermata da aprire per vederlo (o null).
     * [autoplay] falso: si carica fermo, in attesa del play. [single]: un brano di Spotify senza il suo album.
     */
    fun start(context: Context, item: ResumeItem, autoplay: Boolean = true, single: Boolean = false): String? {
        val from = if (item.unfinished) item.positionMs else 0
        return when (item.kind) {
            ResumeKind.VIDEO -> Routes.video(item.key)
            ResumeKind.YOUTUBE -> {
                YtPlayer.play(context, Resume.youtubeLink(item), autoplay = autoplay)
                Routes.youtubeWatch(Resume.youtubeLink(item))
            }
            ResumeKind.MUSIC -> {
                YtPlayer.play(context, Resume.youtubeLink(item), audioOnly = true, autoplay = autoplay)
                Routes.PLAYER
            }
            ResumeKind.SPOTIFY -> {
                SpotifyPlayer.resume(
                    context, item.key, item.link.takeUnless { single }, from, autoplay,
                    title = item.title, subtitle = item.subtitle, artwork = item.image,
                )
                Routes.PLAYER
            }
            ResumeKind.RADIO -> {
                item.station?.let { RadioStore.play(it, autoplay) }
                Routes.PLAYER
            }
            ResumeKind.PODCAST -> {
                PodcastStore.init(context)
                PodcastStore.play(
                    Episode(
                        id = item.key, title = item.title, audioUrl = item.link, publishedAt = 0,
                        durationSec = item.durationMs / 1000, description = "", image = item.image,
                        podcastTitle = item.subtitle, feedUrl = item.feed,
                    ),
                    autoplay,
                )
                Routes.PLAYER
            }
        }
    }
}
