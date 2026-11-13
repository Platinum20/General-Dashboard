package com.platinum20.dashboard

import com.platinum20.dashboard.media.GoToCreator
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyApi
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtWatched
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoToCreatorTest {
    private fun item(kind: ResumeKind, key: String) = ResumeItem(kind, key, "t", at = 0)

    @Test fun tracksAndAlbumsGoToTheArtistVideosToTheChannel() {
        assertEquals(GoToCreator.Target.ARTIST, GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "spotify:track:abc")))
        assertEquals(GoToCreator.Target.ARTIST, GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "spotify:album:abc")))
        assertEquals(GoToCreator.Target.CHANNEL, GoToCreator.targetOf(item(ResumeKind.YOUTUBE, "dQw4w9WgXcQ")))
    }

    @Test fun nothingToOpenForPlaylistsMixesRadioAndEpisodes() {
        assertNull(GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "spotify:playlist:abc")))
        assertNull(GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "spotify:artist:abc")))
        assertNull(GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "mix:daily1")))
        assertNull(GoToCreator.targetOf(item(ResumeKind.SPOTIFY, "spotify:episode:abc")))
        assertNull(GoToCreator.targetOf(item(ResumeKind.RADIO, "station")))
        assertNull(GoToCreator.targetOf(item(ResumeKind.VIDEO, "content://x")))
    }

    @Test fun knownChannelComesFromFeedOrHistory() {
        val feed = listOf(YtVideo("v1", "Titolo", "Canale Uno", "UC1", null))
        val watched = listOf(YtWatched("v2", "Altro", "Canale Due", "", 0, 0, 0, "UC2"))
        assertEquals("UC1", GoToCreator.knownChannel("v1", feed, watched)?.id)
        assertEquals("Canale Due", GoToCreator.knownChannel("v2", feed, watched)?.title)
        assertNull(GoToCreator.knownChannel("v3", feed, watched))
    }

    @Test fun firstArtistOfTrackJson() {
        val json = JSONObject(
            """{"type":"track","id":"t1","artists":[{"type":"artist","id":"a1","name":"Mina","uri":"spotify:artist:a1"},{"type":"artist","id":"a2","name":"Celentano"}]}""",
        )
        val artist = SpotifyApi.firstArtistOf(json)
        assertEquals("a1", artist?.id)
        assertEquals(SpKind.ARTIST, artist?.kind)
        assertEquals("Mina", artist?.title)
        assertNull(SpotifyApi.firstArtistOf(JSONObject("""{"type":"track","id":"t1"}""")))
    }
}
