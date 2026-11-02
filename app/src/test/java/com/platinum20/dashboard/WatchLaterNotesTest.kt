package com.platinum20.dashboard

import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Più tardi" in Video = i video di YouTube che stanno nelle note, dal più recente. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchLaterNotesTest {
    @Test
    fun videoSalvatiELinkIncollati() {
        val saved = SavedLinks.noteText(SavedLinks.youtube(YtVideo("abcdefghijk", "Un video", "Canale", "", null)))
        val notes = listOf(
            Note(1, saved, createdAt = 100),
            Note(2, "Da vedere\nhttps://youtu.be/ZZZZZZZZZZZ?si=x", createdAt = 300),
            Note(3, "https://www.youtube.com/watch?v=YYYYYYYYYYY", createdAt = 200),
            Note(4, "Comprare il latte", createdAt = 400),
            Note(5, saved, createdAt = 50),
        )
        val videos = SavedLinks.youtubeVideos(notes)
        assertEquals(listOf("ZZZZZZZZZZZ", "YYYYYYYYYYY", "abcdefghijk"), videos.map { it.id })
        assertEquals(listOf("Da vedere", "Video di YouTube", "Un video"), videos.map { it.title })
        assertEquals("Canale", videos.last().channel)
    }
}
