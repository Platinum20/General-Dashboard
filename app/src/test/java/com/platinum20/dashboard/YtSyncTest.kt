package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.youtube.SyncPlan
import com.platinum20.dashboard.media.youtube.YtHistory
import com.platinum20.dashboard.media.youtube.YtSync
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtWatched
import com.platinum20.dashboard.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class YtSyncTest {
    private fun video(id: String, percent: Int = 0, seconds: Int = 0) =
        YtVideo(id, "Titolo $id", "Canale", "", null, durationSec = seconds, watchedPercent = percent)

    @Test
    fun firstSyncOnlyAddsBothWays() {
        val plan = SyncPlan.of(remote = setOf("a", "b"), local = setOf("b", "c"), synced = emptySet())
        assertEquals(setOf("c"), plan.addRemote)
        assertEquals(setOf("a"), plan.addLocal)
        assertTrue(plan.removeRemote.isEmpty() && plan.removeLocal.isEmpty())
        assertEquals(setOf("a", "b", "c"), plan.synced(setOf("a", "b"), setOf("b", "c"), emptySet(), remoteComplete = true))
    }

    @Test
    fun removalOnOneSideRemovesOnTheOther() {
        // "a" tolto da Canvas, "b" tolto da YouTube (dal PC), "d" nuovo dal PC, "e" nuovo da Canvas.
        val synced = setOf("a", "b", "c")
        val plan = SyncPlan.of(remote = setOf("a", "c", "d"), local = setOf("b", "c", "e"), synced = synced)
        assertEquals(setOf("a"), plan.removeRemote)
        assertEquals(setOf("b"), plan.removeLocal)
        assertEquals(setOf("d"), plan.addLocal)
        assertEquals(setOf("e"), plan.addRemote)
        assertEquals(setOf("c", "d", "e"), plan.synced(setOf("a", "c", "d"), setOf("b", "c", "e"), synced, remoteComplete = true))
    }

    @Test
    fun incompleteRemoteNeverRemovesFromCanvas() {
        val plan = SyncPlan.of(remote = setOf("new"), local = setOf("old"), synced = setOf("old"), remoteComplete = false)
        assertTrue(plan.removeLocal.isEmpty())
        assertTrue(plan.addRemote.isEmpty())
        // "old" è oltre le pagine lette: resta sincronizzato.
        assertTrue("old" in plan.synced(setOf("new"), setOf("old"), setOf("old"), remoteComplete = false))
    }

    @Test
    fun likesRemovedHereAreNotPulledBack() {
        // Mi piace tolto in Canvas mentre YouTube non l'ha ancora saputo.
        val plan = SyncPlan.of(remote = setOf("x", "y"), local = setOf("y"), synced = setOf("y"), pushRemovals = false, ignore = setOf("x"))
        assertTrue(plan.addLocal.isEmpty())
        assertTrue(plan.removeRemote.isEmpty())
        // Uscito dalla lista locale senza un tocco (tetto dei 500): non torna e non si toglie su YouTube.
        val lost = SyncPlan.of(remote = setOf("z"), local = emptySet(), synced = setOf("z"), pushRemovals = false)
        assertTrue(lost.addLocal.isEmpty() && lost.removeRemote.isEmpty())
        assertTrue("z" in lost.synced(setOf("z"), emptySet(), setOf("z"), remoteComplete = true))
    }

    @Test
    fun keptNotesStaySyncedSoTheyAreNotPushedAgain() {
        val plan = SyncPlan.of(remote = emptySet(), local = setOf("n"), synced = setOf("n"))
        assertEquals(setOf("n"), plan.removeLocal)
        assertEquals(setOf("n"), plan.synced(emptySet(), setOf("n"), setOf("n"), remoteComplete = true, kept = setOf("n")))
        val again = SyncPlan.of(remote = emptySet(), local = setOf("n"), synced = setOf("n"))
        assertTrue(again.addRemote.isEmpty())
    }

    @Test
    fun onlyCanvasNotesAndBareLinksAreRemovable() {
        val saved = Note(1, SavedLinks.noteText(SavedLinks.youtube(video("dQw4w9WgXcQ"))))
        val bare = Note(2, "https://youtu.be/dQw4w9WgXcQ")
        val written = Note(3, "Da vedere con Marco https://youtu.be/dQw4w9WgXcQ")
        val other = Note(4, "https://youtu.be/aaaaaaaaaaa")
        val notes = listOf(saved, bare, written, other)
        assertEquals(setOf("dQw4w9WgXcQ", "aaaaaaaaaaa"), YtSync.laterIds(notes))
        assertEquals(listOf(1L, 2L), YtSync.removableNotes(notes, "dQw4w9WgXcQ").map { it.id })
    }

    @Test
    fun newLikesFromYouTubeGoOnTop() {
        val merged = YtSync.mergeLikes(listOf(video("a"), video("b")), listOf(video("n"), video("a")), add = setOf("n"), remove = setOf("b"))
        assertEquals(listOf("n", "a"), merged.map { it.id })
    }

    @Test
    fun historyFromYouTubeAddsAndAdvancesProgress() {
        val local = mapOf("a" to YtWatched("a", "A", "", "", 10_000, 100_000, 5L))
        val merged = YtHistory.mergedWith(local, listOf(video("a", percent = 50), video("b", percent = 100, seconds = 60)), now = 1_000_000L)
        assertEquals(50_000L, merged.getValue("a").positionMs)
        assertEquals(5L, merged.getValue("a").watchedAt)
        assertEquals(1f, merged.getValue("b").progress)
        // Dove eri più avanti in Canvas non torna indietro.
        val back = YtHistory.mergedWith(merged, listOf(video("a", percent = 10)), now = 2_000_000L)
        assertEquals(50_000L, back.getValue("a").positionMs)
    }

    @Test
    fun halfWatchedOnPcGoesToContinue() {
        val local = listOf(ResumeItem(ResumeKind.YOUTUBE, "a", "A", positionMs = 90_000, durationMs = 100_000, at = 7L))
        val remote = listOf(
            video("n", percent = 40, seconds = 600), video("done", percent = 100, seconds = 60),
            video("a", percent = 50, seconds = 100), video("x", percent = 0, seconds = 60),
        )
        val items = YtSync.continueItems(local, remote, now = 10_000L)
        assertEquals(listOf("n"), items.map { it.key })
        assertEquals(240_000L, items.single().positionMs)
        assertEquals(10_000L, items.single().at)
        // Più avanti sul PC: si aggiorna il punto, non quando l'hai guardato.
        val further = YtSync.continueItems(local, listOf(video("a", percent = 93, seconds = 100)), now = 10_000L).single()
        assertEquals(93_000L, further.positionMs)
        assertEquals(7L, further.at)
    }

    @Test
    fun canvasPositionReachesYouTubeWithoutFlooding() {
        val min = 60_000L
        assertEquals(null, YtSync.nextPush(null, 10_000, 600_000, playing = true, now = 0))
        assertEquals("40:1000", YtSync.nextPush(null, 40_000, 600_000, playing = true, now = 1_000))
        // Mentre guardi: non prima di 2 minuti e di un minuto di video in più.
        assertEquals(null, YtSync.nextPush("40:1000", 100_000, 600_000, playing = true, now = 1_000 + min))
        assertEquals("160:121000", YtSync.nextPush("40:1000", 160_000, 600_000, playing = true, now = 121_000))
        // In pausa (o uscendo) si manda subito il punto.
        assertEquals("55:2000", YtSync.nextPush("40:1000", 55_000, 600_000, playing = false, now = 2_000))
        // Finito: una volta sola.
        assertEquals("d", YtSync.nextPush("160:121000", 590_000, 600_000, playing = true, now = 200_000))
        assertEquals(null, YtSync.nextPush("d", 300_000, 600_000, playing = false, now = 300_000))
    }
}
