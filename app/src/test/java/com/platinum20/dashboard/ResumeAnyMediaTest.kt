package com.platinum20.dashboard

import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** "Continua" tiene qualunque media: Spotify e podcast si salvano con quello che serve per ripartire. */
@RunWith(RobolectricTestRunner::class)
class ResumeAnyMediaTest {
    @Test
    fun spotifyEPodcastSiSalvanoESiRileggono() {
        val spotify = ResumeItem(ResumeKind.SPOTIFY, "spotify:track:1", "Brano", "Artista", positionMs = 30_000, durationMs = 200_000, at = 10, link = "spotify:album:2")
        val podcast = ResumeItem(ResumeKind.PODCAST, "ep1", "Episodio", "Podcast", positionMs = 60_000, at = 20, link = "https://x/ep1.mp3", feed = "https://x/feed")
        assertEquals(spotify, ResumeItem.fromJson(JSONObject(spotify.toJson().toString())))
        assertEquals(podcast, ResumeItem.fromJson(JSONObject(podcast.toJson().toString())))
    }

    @Test
    fun ilPiuRecenteVinceQualunqueSiaIlTipo() {
        val video = ResumeItem(ResumeKind.YOUTUBE, "v", "Video", positionMs = 1_000, durationMs = 10_000, at = 5)
        val song = ResumeItem(ResumeKind.SPOTIFY, "spotify:track:1", "Brano", positionMs = 1_000, durationMs = 10_000, at = 9)
        assertEquals(song, Resume.pick(listOf(video, song)).first())
    }
}
