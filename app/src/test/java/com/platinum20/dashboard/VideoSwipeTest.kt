package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.VideoSwipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSwipeTest {
    @Test
    fun volumeFollowsTheFinger() {
        assertEquals(15, VideoSwipe.volumeAfter(0, 15, 1000f, 1000f))
        assertEquals(10, VideoSwipe.volumeAfter(7, 15, 200f, 1000f))
        assertEquals(4, VideoSwipe.volumeAfter(7, 15, -200f, 1000f))
        assertEquals(0, VideoSwipe.volumeAfter(3, 15, -900f, 1000f))
        assertEquals(15, VideoSwipe.volumeAfter(14, 15, 900f, 1000f))
    }

    @Test
    fun swipeUpOpensFullscreen() {
        assertTrue(VideoSwipe.opensFullscreen(-120f, 0f, 100f))
        assertTrue(VideoSwipe.opensFullscreen(-20f, -2_000f, 100f))
        assertFalse(VideoSwipe.opensFullscreen(-20f, 0f, 100f))
        assertFalse(VideoSwipe.opensFullscreen(150f, 2_000f, 100f))
    }

    @Test
    fun onlyVolumeKeysAreTaken() {
        assertTrue(com.platinum20.dashboard.ui.screens.FullscreenVolume.isVolumeKey(android.view.KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(com.platinum20.dashboard.ui.screens.FullscreenVolume.isVolumeKey(android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(com.platinum20.dashboard.ui.screens.FullscreenVolume.isVolumeKey(android.view.KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun pinchFillsAndFits() {
        assertEquals(true, VideoSwipe.pinchFill(1.3f, filled = false))
        assertEquals(null, VideoSwipe.pinchFill(1.05f, filled = false))
        assertEquals(null, VideoSwipe.pinchFill(1.3f, filled = true))
        assertEquals(false, VideoSwipe.pinchFill(0.8f, filled = true))
        assertEquals(null, VideoSwipe.pinchFill(0.8f, filled = false))
    }
}
