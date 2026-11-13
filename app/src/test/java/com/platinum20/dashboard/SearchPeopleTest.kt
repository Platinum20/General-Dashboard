package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.search.PersonHit
import com.platinum20.dashboard.search.SearchPeople
import org.junit.Assert.assertEquals
import org.junit.Test

/** Artisti e canali in Cerca: un gruppo solo, prima gli artisti, e ogni fonte si apre e si richiude. */
class SearchPeopleTest {
    private fun sp(id: String, kind: SpKind) = SpItem(id, "spotify:$id", kind, id, "", "")
    private val chan = YtChannel("UC1", "Canale uno", "") to 1_200_000L

    @Test fun primaGliArtistiPoiICanali() {
        val hits = SearchPeople.hits(listOf(sp("brano", SpKind.TRACK), sp("artista", SpKind.ARTIST)), listOf(chan), music = true, video = true)
        assertEquals(listOf("sp:artista", "yt:UC1"), hits.map { it.key })
        assertEquals(true, hits.last() is PersonHit.Channel)
    }

    @Test fun ilFiltroVideoTieneSoloICanali() {
        val hits = SearchPeople.hits(listOf(sp("artista", SpKind.ARTIST)), listOf(chan), music = false, video = true)
        assertEquals(listOf("yt:UC1"), hits.map { it.key })
    }

    @Test fun iscrittiInItaliano() {
        assertEquals("Canale · 1,2 Mln di iscritti", SearchPeople.channelLine(1_200_000))
        assertEquals("Canale · 15.300 iscritti", SearchPeople.channelLine(15_300))
        assertEquals("Canale", SearchPeople.channelLine(0))
    }

    @Test fun unaFonteApertaSiRichiude() {
        val open = SearchPeople.toggled(emptySet(), "news")
        assertEquals(setOf("news"), open)
        assertEquals(emptySet<String>(), SearchPeople.toggled(open, "news"))
    }
}
