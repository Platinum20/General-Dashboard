package com.platinum20.dashboard

import com.platinum20.dashboard.media.youtube.player.YtText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

/** Data del video relativa e link veri nella descrizione (non "Discord: discord"). */
class YtTextTest {
    private val now = OffsetDateTime.parse("2026-10-27T18:13:00Z").toInstant().toEpochMilli()

    @Test fun dataIsoDiventaRelativa() {
        assertEquals("29 minuti fa", YtText.age("2026-10-27T10:43:59-07:00", now))
        assertEquals("7 ore fa", YtText.age("2026-10-27T10:43:59Z", now))
    }

    @Test fun etaVarie() {
        assertEquals("1 ora fa", YtText.age("2026-10-27T17:00:00Z", now))
        assertEquals("5 minuti fa", YtText.age("2026-10-27T18:08:00Z", now))
        assertEquals("2 giorni fa", YtText.age("2026-10-25T12:00:00Z", now))
        assertEquals("3 mesi fa", YtText.age("2026-07-20", now))
        assertEquals("1 anno fa", YtText.age("2025-09-01", now))
    }

    @Test fun testoGiaLeggibileResta() = assertEquals("2 giorni fa", YtText.age("2 giorni fa", now))

    @Test fun linkEsternoMostraIndirizzo() {
        val html = "Discord: <a href=\"https://www.youtube.com/redirect?event=video_description&amp;q=https%3A%2F%2Fdiscord.gg%2Fflow\">discord</a>"
        assertEquals("Discord: discord.gg/flow", YtText.descriptionHtml(html))
        assertEquals("patreon.com/x", YtText.descriptionHtml("<a href=\"https://www.patreon.com/x/\">patreon</a>"))
    }

    @Test fun hashtagEMinutaggiRestano() {
        val html = "<a href=\"https://www.youtube.com/hashtag/flowstate\">#flowstate</a> <a href=\"/watch?v=a&amp;t=60s\">1:00</a>"
        assertEquals(html, YtText.descriptionHtml(html))
    }

    @Test fun linkToccabili() {
        val text = "Discord: discord.gg/flow. Video: https://youtu.be/abcdefghijk e basta Part 1."
        val found = YtText.links(text).map { text.substring(it) }
        assertEquals(listOf("discord.gg/flow", "https://youtu.be/abcdefghijk"), found)
    }
}
