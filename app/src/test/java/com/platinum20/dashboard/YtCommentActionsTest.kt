package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.YtCommentActions
import com.platinum20.dashboard.media.youtube.player.YtComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YtCommentActionsTest {
    @Test
    fun likeCountFollowsYourTap() {
        assertEquals(11, YtCommentActions.shownLikes(10, likedNow = true, likedWhenRead = false))
        assertEquals(10, YtCommentActions.shownLikes(10, likedNow = true, likedWhenRead = true))
        assertEquals(9, YtCommentActions.shownLikes(10, likedNow = false, likedWhenRead = true))
        assertEquals(0, YtCommentActions.shownLikes(0, likedNow = false, likedWhenRead = true))
    }

    @Test
    fun rememberedKeepsRecentAndRemoves() {
        val set = YtCommentActions.remembered(setOf("a", "b"), "c", true, max = 2)
        assertEquals(listOf("b", "c"), set.toList())
        assertEquals(setOf("c"), YtCommentActions.remembered(set, "b", false, max = 2))
        assertEquals(set, YtCommentActions.remembered(set, "", true, max = 2))
    }

    @Test
    fun replyToReplyQuotesAuthor() {
        assertEquals("@mario ", YtCommentActions.replyPrefill("@mario", toReply = true))
        assertEquals("", YtCommentActions.replyPrefill("@mario", toReply = false))
    }

    @Test
    fun scriptQuotesTextAndPage() {
        val c = YtComment("@a", "x", 0, "", "", 0, id = "Ugx1", source = "tok\"en")
        val js = YtCommentActions.script("vid12345678", c, "reply", "ciao \"tu\"\n")
        assertTrue(js.contains("\"Ugx1\",\"tok\\\"en\",\"reply\",\"ciao \\\"tu\\\"\\n\""))
        assertTrue(YtCommentActions.script("v", null, "post", "t").contains("\"\",null,\"post\""))
    }
}
