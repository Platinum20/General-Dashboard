package com.platinum20.dashboard

import com.platinum20.dashboard.media.PhoneVideo
import com.platinum20.dashboard.media.PhoneVideos
import com.platinum20.dashboard.media.EngineNow
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.VideoEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** La libreria dei video del telefono nella pagina Video. */
class PhoneVideosTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 22)
    private fun sec(day: LocalDate) = day.atTime(18, 30).toEpochSecond(zone)
    private fun video(n: Int, day: LocalDate = today, ms: Long = 60_000) =
        PhoneVideo("content://media/external/video/media/$n", "VID_$n", ms, sec(day))

    @Test fun senzaDoppioniConIRecenti() {
        val phone = listOf(video(1), video(2), video(3))
        val recent = listOf(VideoEntry(uri = phone[1].uri, title = "VID_2"))
        assertEquals(listOf(phone[0], phone[2]).map { it.uri }, PhoneVideos.forLibrary(phone, recent).map { it.uri })
    }

    @Test fun dalPiuNuovoEAlMassimoDodici() {
        val phone = (1..20).map { video(it, today.minusDays(it.toLong())) }.shuffled()
        val shown = PhoneVideos.forLibrary(phone, emptyList())
        assertEquals(PhoneVideos.MAX, shown.size)
        assertEquals((1..12).map { "VID_$it" }, shown.map { it.title })
    }

    @Test fun stessoIndirizzoUnaVolta() {
        val shown = PhoneVideos.forLibrary(listOf(video(1), video(1)), emptyList())
        assertEquals(1, shown.size)
    }

    @Test fun titoloSenzaEstensione() {
        assertEquals("VID_20261021_183012", PhoneVideos.cleanTitle("VID_20261021_183012.mp4"))
        assertEquals("vacanze al mare", PhoneVideos.cleanTitle("vacanze al mare.mov"))
        assertEquals("senza_estensione", PhoneVideos.cleanTitle("senza_estensione"))
        assertEquals("Video", PhoneVideos.cleanTitle(""))
    }

    @Test fun durataEGiorno() {
        assertEquals("3:12 · oggi", PhoneVideos.caption(video(1, today, 192_000), today, zone))
        assertEquals("1:02:03 · ieri", PhoneVideos.caption(video(1, today.minusDays(1), 3_723_000), today, zone))
        assertEquals("0:45 · 12 ott", PhoneVideos.caption(video(1, LocalDate.of(2026, 10, 12), 45_000), today, zone))
        assertEquals("3 mar 2025", PhoneVideos.caption(video(1, LocalDate.of(2025, 3, 3), 0), today, zone))
    }

    @Test fun rifiutoDefinitivoApreLeImpostazioni() {
        // V23: dopo due "Non consentire" la card non deve restare muta.
        assertEquals(PhoneVideos.Invite.ASK, PhoneVideos.invite(deniedOnce = false, rationale = false))
        assertEquals(PhoneVideos.Invite.ASK, PhoneVideos.invite(deniedOnce = true, rationale = true))
        assertEquals(PhoneVideos.Invite.OPEN_SETTINGS, PhoneVideos.invite(deniedOnce = true, rationale = false))
    }

    /** Un video del telefono è una voce del player unico: stessa coda, riparte da dove era. */
    @Test fun videoDelTelefonoNelPlayerUnico() {
        val entry = VideoEntry(uri = "content://media/external/video/media/7", title = "VID_7", positionMs = 40_000, durationMs = 120_000)
        val item = ResumeItem.fromVideo(entry)
        assertEquals(ResumeKind.VIDEO, item.kind)
        assertEquals(entry.uri, item.key)
        assertEquals(40_000L, item.positionMs)
        assertEquals("Video del telefono", item.subtitle)
        assertEquals(true, EngineNow(1, item).hasVideo)
    }
}
