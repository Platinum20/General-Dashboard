package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpotifyAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyClientIdTest {
    @Test fun blankMeansDefault() {
        assertEquals(SpotifyAuth.DEFAULT_CLIENT_ID, SpotifyAuth.normalizeClientId("  "))
    }

    @Test fun pastedIdIsCleaned() {
        assertEquals("802a1bc467c041538598820b98e20f63", SpotifyAuth.normalizeClientId(" 802A1BC467C04153 8598820b98e20f63\n"))
    }

    @Test fun wrongIdIsRejected() {
        assertNull(SpotifyAuth.normalizeClientId("abc"))
        assertNull(SpotifyAuth.normalizeClientId("802a1bc467c041538598820b98e20f6z"))
    }
}
