package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpPlaylistInfo
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 1.502 (Antonio): "poter cancellare le playlist e comunque gestirle": dettagli, permessi, spostare e togliere brani. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SpotifyPlaylistManageTest {
    private fun track(id: String) = SpItem(id, "spotify:track:$id", SpKind.TRACK, "T $id", "", "")
    private val base = SpPlaylistInfo("p", "Estate", "Da spiaggia", public = false, collaborative = false, ownerId = "antonio")

    @Test
    fun leggeIDettagliESciogliLHtml() {
        val json = JSONObject().put("id", "p").put("name", "Estate").put("description", "Rock &amp; roll &#x27;80 <a href=\"x\">qui</a>")
            .put("public", true).put("owner", JSONObject().put("id", "antonio"))
        val info = SpotifyPlaylists.info(json)!!
        assertEquals("Rock & roll '80 qui", info.description)
        assertTrue(info.public)
        assertEquals("antonio", info.ownerId)
        assertEquals("", SpotifyPlaylists.plainDescription("null"))
        assertNull(SpotifyPlaylists.info(JSONObject()))
    }

    @Test
    fun siCambiaSoloSeTuaOCollaborativa() {
        assertTrue(SpotifyPlaylists.canEdit(base, "antonio"))
        assertFalse(SpotifyPlaylists.canEdit(base.copy(ownerId = "spotify"), "antonio"))
        assertTrue(SpotifyPlaylists.canEdit(base.copy(ownerId = "luca", collaborative = true), "antonio"))
        assertFalse(SpotifyPlaylists.canEdit(base, ""))
        assertFalse(SpotifyPlaylists.canEdit(null, "antonio"))
    }

    @Test
    fun mandaSoloQuelloCheCambia() {
        assertNull(SpotifyPlaylists.changes(base, " Estate ", "Da  spiaggia", public = false))
        val body = SpotifyPlaylists.changes(base, "Estate 2026", "Da spiaggia", public = true)!!
        assertEquals("Estate 2026", body.getString("name"))
        assertTrue(body.getBoolean("public"))
        assertFalse(body.has("description"))
        // Nome vuoto: si tiene quello di prima, non si manda.
        assertEquals("", SpotifyPlaylists.changes(base, "  ", "", public = false)!!.getString("description"))
    }

    @Test
    fun chiaviDiverseAncheConLoStessoBranoDueVolte() {
        val keys = SpotifyPlaylists.keys(listOf(track("a"), track("b"), track("a")))
        assertEquals(listOf("spotify:track:a", "spotify:track:b", "spotify:track:a#1"), keys)
    }

    @Test
    fun spostareComeSpotify() {
        val list = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), SpotifyPlaylists.moved(list, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), SpotifyPlaylists.moved(list, 3, 0))
        assertEquals(list, SpotifyPlaylists.moved(list, 1, 9))
        // Giù da 0 a 2: Spotify vuole "prima della riga 3" contata sulla lista di prima.
        assertEquals(3, SpotifyPlaylists.insertBefore(0, 2))
        assertEquals(0, SpotifyPlaylists.insertBefore(3, 0))
    }

    @Test
    fun togliereERimettere() {
        val list = listOf(track("a"), track("b"), track("a"), track("c"))
        val without = SpotifyPlaylists.without(list, "spotify:track:a")
        assertEquals(listOf("b", "c"), without.map { it.id })
        assertEquals(listOf("a", "b", "c"), SpotifyPlaylists.restored(without, track("a"), 0).map { it.id })
        assertEquals(listOf("b", "c", "a"), SpotifyPlaylists.restored(without, track("a"), 9).map { it.id })
    }
}
