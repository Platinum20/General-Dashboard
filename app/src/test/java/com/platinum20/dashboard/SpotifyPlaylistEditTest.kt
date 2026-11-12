package com.platinum20.dashboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpotifyPlaylists
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 1.500 (Antonio): brani nelle playlist e playlist nuove; si propongono solo quelle dove Spotify fa scrivere. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SpotifyPlaylistEditTest {
    private fun playlist(id: String, owner: String, collaborative: Boolean = false) = JSONObject()
        .put("id", id).put("uri", "spotify:playlist:$id").put("type", "playlist").put("name", "P $id")
        .put("collaborative", collaborative)
        .put("owner", JSONObject().put("id", owner).put("display_name", owner))

    private fun item(id: String, kind: SpKind = SpKind.PLAYLIST) = SpItem(id, "spotify:${kind.name.lowercase()}:$id", kind, "T $id", "", "")

    @Test
    fun soloLeTueELeCollaborative() {
        val json = JSONObject().put(
            "items",
            org.json.JSONArray().put(playlist("a", "antonio")).put(playlist("b", "spotify")).put(playlist("c", "luca", collaborative = true)),
        )
        assertEquals(listOf("a", "c"), SpotifyPlaylists.editable(json, "antonio").map { it.id })
        // Senza profilo non si indovina il proprietario: restano solo le collaborative.
        assertEquals(listOf("c"), SpotifyPlaylists.editable(json, "").map { it.id })
        assertEquals(emptyList<SpItem>(), SpotifyPlaylists.editable(null, "antonio"))
    }

    @Test
    fun nomePulito() {
        assertEquals("Estate 2026", SpotifyPlaylists.cleanName("  Estate   2026 "))
        assertEquals("", SpotifyPlaylists.cleanName("   "))
        assertEquals(SpotifyPlaylists.MAX_NAME, SpotifyPlaylists.cleanName("x".repeat(300)).length)
    }

    @Test
    fun giaDentro() {
        val tracks = listOf(item("t1", SpKind.TRACK))
        assertTrue(SpotifyPlaylists.alreadyIn(tracks, "spotify:track:t1"))
        assertFalse(SpotifyPlaylists.alreadyIn(tracks, "spotify:track:t2"))
        assertFalse(SpotifyPlaylists.alreadyIn(null, "spotify:track:t1"))
    }

    @Test
    fun primaLUltimaUsata() {
        val all = (1..20).map { item("p$it") }
        assertEquals(listOf("p5", "p1", "p2"), SpotifyPlaylists.ordered(all, "p5").take(3).map { it.id })
        assertEquals(SpotifyPlaylists.MAX_CHIPS, SpotifyPlaylists.ordered(all, null).size)
        assertEquals("p1", SpotifyPlaylists.ordered(all, "sparita").first().id)
    }
}
