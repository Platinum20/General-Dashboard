package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.262: lo stato letto dal server vale anche quando Spotify chiama il brano con un altro uri. */
class SpotifyAliasTest {
    private fun reading(uri: String, linked: String = "") = SpotifyPlayer.Reading(uri, false, 0, 1000, 0, linked)

    @Test
    fun ourTrackByUriLinkOrAlias() {
        assertTrue(reading("spotify:track:a").isFor("spotify:track:a", null))
        assertTrue(reading("spotify:track:b", linked = "spotify:track:a").isFor("spotify:track:a", null))
        assertTrue(reading("spotify:track:b").isFor("spotify:track:a", "spotify:track:b"))
    }

    @Test
    fun anotherTrackIsNotOurs() {
        // Senza alias, o con un alias diverso, un altro brano resta "Spotify è andato avanti da solo".
        assertFalse(reading("spotify:track:c").isFor("spotify:track:a", null))
        assertFalse(reading("spotify:track:c").isFor("spotify:track:a", "spotify:track:b"))
    }
}
