package com.platinum20.dashboard

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.youtube.player.NewPipeDownloader
import com.platinum20.dashboard.media.youtube.player.YtResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class YtLinksTest {
    @Test
    fun idDaTuttiITipiDiLink() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id",
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abc123",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "https://www.youtube.com/watch?v=$id&t=42s",
        ).forEach { assertEquals(it, id, YtResolver.videoId(it)) }
    }

    @Test
    fun linkSenzaVideo() {
        assertNull(YtResolver.videoId("https://www.youtube.com/@canale"))
        assertNull(YtResolver.videoId("https://www.youtube.com/watch?v=corto"))
        assertNull(YtResolver.videoId("https://example.com/"))
    }

    @Test
    fun userAgentSecondoIlClient() {
        assertTrue(YtResolver.userAgentFor("https://rr1.googlevideo.com/videoplayback?itag=18&c=ANDROID&x=1").startsWith("com.google.android.youtube/"))
        assertTrue(YtResolver.userAgentFor("https://rr1.googlevideo.com/videoplayback?c=ANDROID_VR").startsWith("com.google.android.youtube/"))
        assertTrue(YtResolver.userAgentFor("https://rr1.googlevideo.com/videoplayback?c=IOS&x=1").startsWith("com.google.ios.youtube/"))
        assertEquals(NewPipeDownloader.USER_AGENT, YtResolver.userAgentFor("https://rr1.googlevideo.com/videoplayback?itag=18"))
        assertEquals(NewPipeDownloader.USER_AGENT, YtResolver.userAgentFor("https://rr1.googlevideo.com/videoplayback?c=WEB"))
    }

    @Test
    fun rottaDelPlayerCodificaIlLink() {
        val link = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"
        val route = Routes.youtubeWatch(link)
        assertTrue(route.startsWith("media/watch?url="))
        assertTrue('&' !in route.substringAfter("url="))
        assertEquals(link, Uri.decode(route.substringAfter("url=")))
        assertEquals("media/watch", Routes.youtubeWatch())
    }
}
