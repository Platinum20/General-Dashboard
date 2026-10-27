package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.SbAction
import com.platinum20.dashboard.media.youtube.SbCategory
import com.platinum20.dashboard.media.youtube.SbSegment
import com.platinum20.dashboard.media.youtube.SbType
import com.platinum20.dashboard.media.youtube.SponsorBlock
import com.platinum20.dashboard.media.youtube.YtVotes
import com.platinum20.dashboard.ui.screens.savedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SponsorBlockTest {
    private val defaults = SbCategory.entries.associateWith { it.default }

    private fun seg(uuid: String, category: SbCategory, start: Long, end: Long, type: SbType = SbType.SKIP) =
        SbSegment(uuid, category, type, start, end)

    @Test
    fun parseKeepsOnlyTheVideoAndKnownCategories() {
        val json = """
            [{"videoID":"abc","segments":[
              {"category":"selfpromo","actionType":"skip","segment":[100.5,110],"UUID":"b"},
              {"category":"sponsor","actionType":"skip","segment":[10,20.25],"UUID":"a"},
              {"category":"poi_highlight","actionType":"poi","segment":[300,300],"UUID":"c"},
              {"category":"exclusive_access","actionType":"full","segment":[0,0],"UUID":"d"},
              {"category":"chapter","actionType":"chapter","segment":[1,2],"UUID":"e"}]},
             {"videoID":"other","segments":[{"category":"sponsor","actionType":"skip","segment":[1,2],"UUID":"x"}]}]
        """.trimIndent()
        val segments = SponsorBlock.parse(json, "abc")
        assertEquals(listOf("d", "a", "b", "c"), segments.map { it.uuid })
        assertEquals(10_000L, segments[1].startMs)
        assertEquals(20_250L, segments[1].endMs)
        assertEquals(SbType.POI, segments[3].type)
    }

    @Test
    fun requestUsesOnlyTheHashPrefix() {
        val prefix = SponsorBlock.hashPrefix("dQw4w9WgXcQ")
        assertEquals(4, prefix.length)
        val url = SponsorBlock.url("dQw4w9WgXcQ")
        assertTrue(url.startsWith("https://sponsor.ajay.app/api/skipSegments/$prefix?"))
        assertFalse(url.contains("dQw4w9WgXcQ"))
        assertTrue(url.contains("poi_highlight"))
    }

    @Test
    fun autoSkipJumpsOverTouchingSegments() {
        val segments = listOf(
            seg("a", SbCategory.SPONSOR, 10_000, 20_000),
            seg("b", SbCategory.SPONSOR, 20_500, 30_000),
        )
        val step = SponsorBlock.step(segments, defaults, 12_000)
        assertEquals(30_000L, step.skipTo)
        assertEquals("a", step.skipped?.uuid)
        // Prima e dopo: niente.
        assertNull(SponsorBlock.step(segments, defaults, 5_000).skipTo)
        assertNull(SponsorBlock.step(segments, defaults, 29_800).skipTo)
    }

    @Test
    fun undoneSegmentIsNotSkippedAgain() {
        val segments = listOf(seg("a", SbCategory.SPONSOR, 10_000, 20_000))
        assertNull(SponsorBlock.step(segments, defaults, 12_000, done = setOf("a")).skipTo)
    }

    @Test
    fun eachChoiceIsRespected() {
        val segments = listOf(seg("a", SbCategory.INTRO, 0, 8_000))
        val manual = SponsorBlock.step(segments, defaults + (SbCategory.INTRO to SbAction.MANUAL), 1_000)
        assertNull(manual.skipTo)
        assertEquals("a", manual.offer?.uuid)
        val auto = SponsorBlock.step(segments, defaults + (SbCategory.INTRO to SbAction.AUTO), 1_000)
        assertEquals(8_000L, auto.skipTo)
        val show = SponsorBlock.step(segments, defaults + (SbCategory.INTRO to SbAction.SHOW), 1_000)
        assertNull(show.skipTo)
        assertNull(show.offer)
        assertEquals(1, SponsorBlock.marks(segments, defaults + (SbCategory.INTRO to SbAction.SHOW)).size)
        assertTrue(SponsorBlock.marks(segments, defaults + (SbCategory.INTRO to SbAction.OFF)).isEmpty())
    }

    @Test
    fun muteSegmentsMuteInsteadOfSkipping() {
        val segments = listOf(seg("m", SbCategory.SPONSOR, 5_000, 9_000, SbType.MUTE))
        val step = SponsorBlock.step(segments, defaults, 6_000)
        assertNull(step.skipTo)
        assertTrue(step.mute)
        assertFalse(SponsorBlock.step(segments, defaults, 9_500).mute)
    }

    @Test
    fun highlightIsOfferedAtTheStartOrReachedAutomatically() {
        val segments = listOf(seg("h", SbCategory.HIGHLIGHT, 60_000, 60_000, SbType.POI))
        assertEquals("h", SponsorBlock.step(segments, defaults, 2_000).offer?.uuid)
        assertNull(SponsorBlock.step(segments, defaults, 30_000).offer)
        val auto = SponsorBlock.step(segments, defaults + (SbCategory.HIGHLIGHT to SbAction.AUTO), 500)
        assertEquals(60_000L, auto.skipTo)
        assertNull(SponsorBlock.step(segments, defaults + (SbCategory.HIGHLIGHT to SbAction.AUTO), 500, setOf("h")).skipTo)
    }

    @Test
    fun wholeVideoLabelOnlyWhenShown() {
        val segments = listOf(seg("f", SbCategory.EXCLUSIVE_ACCESS, 0, 0, SbType.FULL))
        assertEquals(SbCategory.EXCLUSIVE_ACCESS, SponsorBlock.label(segments, defaults))
        assertNull(SponsorBlock.label(segments, defaults + (SbCategory.EXCLUSIVE_ACCESS to SbAction.OFF)))
        assertTrue(SponsorBlock.marks(segments, defaults).isEmpty())
        // Il video intero si può solo mostrare o ignorare.
        assertEquals(listOf(SbAction.SHOW, SbAction.OFF), SbCategory.EXCLUSIVE_ACCESS.actions)
    }

    @Test
    fun votesAndSavedTime() {
        val votes = YtVotes.parse("""{"id":"x","likes":900,"dislikes":100,"rating":4.6}""")
        assertEquals(100L, votes.dislikes)
        assertEquals(90, votes.ratio())
        assertNull(YtVotes(0, 0).ratio())
        assertEquals(-1L, YtVotes.parse("{}").likes)
        assertEquals("45 s", savedLabel(45_000))
        assertEquals("12 min", savedLabel(12 * 60_000L))
        assertEquals("2 h 5 min", savedLabel(125 * 60_000L))
    }
}
