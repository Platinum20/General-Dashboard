package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtCaptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class YtCaptionsTest {
    @Test
    fun json3DiventaWebVtt() {
        val json = """{"events":[{"tStartMs":0,"dDurationMs":1500},{"tStartMs":1500,"dDurationMs":2000,"segs":[{"utf8":"Ciao "},{"utf8":"a tutti"}]},{"tStartMs":61000,"dDurationMs":1000,"segs":[{"utf8":"\n"}]}]}"""
        assertEquals("WEBVTT\n\n00:00:01.500 --> 00:00:03.500\nCiao a tutti\n\n", YtCaptions.json3ToVtt(json))
    }

    @Test
    fun rispostaVuotaNonVale() {
        assertNull(YtCaptions.json3ToVtt(""))
        assertNull(YtCaptions.json3ToVtt("""{"events":[]}"""))
        assertFalse(YtCaptions.isVtt(""))
        assertFalse(YtCaptions.isVtt("WEBVTT\n\n"))
        assertTrue(YtCaptions.isVtt("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nx"))
    }
}
