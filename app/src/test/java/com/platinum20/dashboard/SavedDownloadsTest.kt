package com.platinum20.dashboard

import com.platinum20.dashboard.media.PackFile
import com.platinum20.dashboard.media.PackKind
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.SavedDownloadPlan
import com.platinum20.dashboard.media.SavedQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedDownloadsTest {
    private val video = ResumeItem(ResumeKind.YOUTUBE, "AAAAAAAAAAA", "Motore", "Veritasium", "img", at = 1)
    private val episode = ResumeItem(ResumeKind.PODCAST, "ep1", "Puntata", "Il Post", "", at = 2, link = "https://x.it/a.mp3")
    private val track = ResumeItem(ResumeKind.SPOTIFY, "spotify:track:1", "Brano", "Artista", "", at = 3)

    @Test fun spotifyERadioNonSiScaricano() {
        assertEquals(PackKind.VIDEO, SavedDownloadPlan.kindOf(video))
        assertEquals(PackKind.PODCAST, SavedDownloadPlan.kindOf(episode))
        assertNull(SavedDownloadPlan.kindOf(track))
        assertNull(SavedDownloadPlan.kindOf(episode.copy(link = "")))
    }

    @Test fun mancaSoloQuelloNonScaricato() {
        val have = listOf(PackFile(video, PackKind.VIDEO, "yt-1.mp4", 600))
        assertEquals(listOf(episode), SavedDownloadPlan.missing(listOf(video, episode, track), have))
    }

    @Test fun toltoDaiSalvatiSiButta() {
        val have = listOf(PackFile(video, PackKind.VIDEO, "yt-1.mp4", 600), PackFile(episode, PackKind.PODCAST, "pod-1.audio", 1800))
        assertEquals(listOf("yt-1.mp4"), SavedDownloadPlan.stale(listOf(episode), have).map { it.file })
    }

    @Test fun maiPiuDiDueGiga() {
        assertTrue(SavedDownloadPlan.fits(0, SavedDownloadPlan.estimateBytes(PackKind.VIDEO, 3600)))
        assertFalse(SavedDownloadPlan.fits(SavedDownloadPlan.MAX_BYTES - 10, 100))
    }

    @Test fun rigaInImpostazioni() {
        assertNull(SavedDownloadPlan.readyLine(0, 0))
        assertEquals("1 pronto senza rete · 300 MB", SavedDownloadPlan.readyLine(1, 300L * 1024 * 1024))
        assertEquals("3 pronti senza rete · 1,5 GB", SavedDownloadPlan.readyLine(3, 1536L * 1024 * 1024))
    }

    @Test fun fullHdSeCeESeCiSta() {
        val have = listOf(1080, 720, 480, 360, 240)
        assertEquals(1080, SavedDownloadPlan.pickHeight(have, 1080, 600, 0))
        assertEquals(720, SavedDownloadPlan.pickHeight(have, 720, 600, 0))
        // Solo fino a 720p su YouTube: si prende quella.
        assertEquals(720, SavedDownloadPlan.pickHeight(listOf(720, 360), 1080, 600, 0))
        // Leggera = il file unico a 360p.
        assertNull(SavedDownloadPlan.pickHeight(have, 360, 600, 0))
        // Un'ora e mezza in Full HD non sta accanto a 1 GB già scaricato: si scende.
        assertEquals(720, SavedDownloadPlan.pickHeight(have, 1080, 5400, 1L shl 30))
    }

    @Test fun alzataLaQualitaSiRiscarica() {
        val old = PackFile(video, PackKind.VIDEO, "yt-1.mp4", 600)
        assertEquals(listOf(video), SavedDownloadPlan.missing(listOf(video), listOf(old), 1080))
        assertEquals(emptyList<ResumeItem>(), SavedDownloadPlan.missing(listOf(video), listOf(old.copy(height = 720, asked = 1080)), 1080))
        // Abbassata non si riscarica niente.
        assertEquals(emptyList<ResumeItem>(), SavedDownloadPlan.missing(listOf(video), listOf(old.copy(asked = 1080)), 360))
    }

    @Test fun qualitaDiDefaultFullHd() {
        assertEquals(SavedQuality.FULL_HD, SavedQuality.of(0))
        assertEquals(SavedQuality.HD, SavedQuality.of(720))
        assertTrue(SavedDownloadPlan.estimateBytes(PackKind.VIDEO, 60, 1080) > 4 * SavedDownloadPlan.estimateBytes(PackKind.VIDEO, 60))
    }
}
