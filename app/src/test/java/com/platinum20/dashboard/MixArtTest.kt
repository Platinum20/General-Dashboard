package com.platinum20.dashboard

import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpMix
import com.platinum20.dashboard.ui.screens.MixArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.497: le copertine dei mix, colore fisso per mix, mosaico dei brani e nome senza "Mix di". */
class MixArtTest {
    private fun track(i: Int, image: String) = SpItem("t$i", "spotify:track:t$i", SpKind.TRACK, "B$i", "A", image)
    private fun mix(title: String, badge: String = "Mix", images: List<String> = emptyList()) =
        SpMix("id-$title", title, "A, B", "cover", images.mapIndexed { i, im -> track(i, im) }, badge)

    @Test fun stessoMixStessoColore() {
        assertEquals(MixArt.color(mix("Daily Mix 1", "Rock")), MixArt.color(mix("Daily Mix 1", "Rock")))
        assertTrue(MixArt.color(mix("Release Radar")) in MixArt.PALETTE)
    }

    @Test fun mosaicoSenzaDoppioni() {
        assertEquals(listOf("a", "b", "c", "d"), MixArt.images(mix("X", images = listOf("a", "a", "", "b", "c", "d", "e"))))
    }

    @Test fun nomeSenzaEtichetta() {
        assertEquals("Daft Punk", MixArt.name(mix("Mix di Daft Punk")))
        assertEquals("Release Radar", MixArt.name(mix("Release Radar")))
    }
}
