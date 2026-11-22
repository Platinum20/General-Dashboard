package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.videoFeedNeedsSignIn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFeedSignInTest {
    @Test fun accessoScadutoChiedeDiRientrare() {
        assertTrue(videoFeedNeedsSignIn("Accesso a YouTube scaduto, accedi di nuovo"))
        assertTrue(videoFeedNeedsSignIn("HTTP 401"))
    }

    @Test fun reteLentaChiedeDiRiprovare() {
        assertFalse(videoFeedNeedsSignIn("YouTube non è raggiungibile, riprova tra poco."))
    }
}
