package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.Resume
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.VideoEntry
import com.platinum20.dashboard.media.youtube.player.YtResolver
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.Briefing
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ResumeTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    private fun yt(id: String, at: Long, pos: Long = 0, dur: Long = 0) =
        ResumeItem(ResumeKind.YOUTUBE, id, "Video $id", "Canale", positionMs = pos, durationMs = dur, at = at)

    private fun radio(at: Long) = ResumeItem(ResumeKind.RADIO, "r1", "Radio Deejay", "Radio", at = at)

    @Test
    fun ilPiuRecenteVince() {
        val video = ResumeItem.fromVideo(VideoEntry("content://v/1", "Vacanze", 60_000, 600_000, openedAt = now - 3 * hour))
        val list = Resume.pick(listOf(radio(now - hour), video, yt("aaaaaaaaaaa", now - 2 * hour, 30_000, 900_000)))
        assertEquals(listOf(ResumeKind.RADIO, ResumeKind.YOUTUBE, ResumeKind.VIDEO), list.map { it.kind })
    }

    @Test
    fun videoFinitoEDoppioniEsclusi() {
        val finished = yt("bbbbbbbbbbb", now, 870_000, 900_000)
        val list = Resume.pick(listOf(finished, yt("ccccccccccc", now - 1), yt("ccccccccccc", now - 5 * hour)))
        assertEquals(listOf("ccccccccccc"), list.map { it.key })
        assertEquals(now - 1, list.single().at)
    }

    @Test
    fun inAdessoSoloVideoAMetaDelleUltime24Ore() {
        assertNull(Resume.forAdesso(listOf(radio(now - hour)), now))
        assertNull(Resume.forAdesso(listOf(yt("aaaaaaaaaaa", now - 25 * hour, 30_000, 900_000)), now))
        assertNull(Resume.forAdesso(listOf(yt("aaaaaaaaaaa", now - hour)), now))
        val half = yt("aaaaaaaaaaa", now - 2 * hour, 750_000, 2_700_000)
        assertEquals(half, Resume.forAdesso(listOf(radio(now - hour), half), now))
    }

    @Test
    fun etichetteELink() {
        assertEquals("12:30 di 45:00", Resume.progressLabel(yt("a", now, 750_000, 2_700_000)))
        assertEquals("1:02:03 di 2:00:00", Resume.progressLabel(yt("a", now, 3_723_000, 7_200_000)))
        assertEquals("", Resume.progressLabel(radio(now)))
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa&t=750s", Resume.youtubeLink(yt("aaaaaaaaaaa", now, 750_000, 2_700_000)))
        assertEquals("https://www.youtube.com/watch?v=aaaaaaaaaaa", Resume.youtubeLink(yt("aaaaaaaaaaa", now)))
    }

    @Test
    fun secondoDiPartenzaDalLink() {
        assertEquals(750, YtResolver.startSeconds("https://www.youtube.com/watch?v=aaaaaaaaaaa&t=750s"))
        assertEquals(90, YtResolver.startSeconds("https://youtu.be/aaaaaaaaaaa?t=90"))
        assertEquals(90, YtResolver.startSeconds("https://www.youtube.com/watch?v=aaaaaaaaaaa&t=1m30s"))
        assertEquals(3723, YtResolver.startSeconds("https://www.youtube.com/watch?v=aaaaaaaaaaa&t=1h2m3s"))
        assertEquals(0, YtResolver.startSeconds("https://www.youtube.com/watch?v=aaaaaaaaaaa"))
    }

    @Test
    fun rigaInAdesso() {
        val at = LocalDateTime.of(2026, 10, 22, 10, 0)
        val item = yt("aaaaaaaaaaa", at.toInstant(ZoneOffset.UTC).toEpochMilli() - hour, 750_000, 2_700_000)
        val lines = Briefing.build(at, null, emptyList(), null, emptyList(), emptyList(), zone = ZoneOffset.UTC, resume = item)
        val line = lines.single { it.kind == BriefKind.MEDIA }
        assertEquals("Riprendi: Video aaaaaaaaaaa · 12:30 di 45:00", line.text)
        assertTrue(lines.none { it.kind == BriefKind.FREE })
    }
}
