package com.platinum20.dashboard.capture

import android.content.Context
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Le copertine dei salvati di Spotify che non l'avevano (2026-11-21, Antonio: "serve una copertina per le robe
 * salvate"): l'oEmbed pubblico di Spotify la dà senza accesso; si scrive nella nota, così resta anche senza rete.
 */
object SavedCovers {
    suspend fun fill(context: Context) {
        NotesStore.init(context)
        val missing = NotesStore.data.value.notes.mapNotNull { n ->
            val item = (SavedLinks.find(n.text) as? SavedLink.Media)?.item ?: return@mapNotNull null
            if (item.image.isNotBlank() || item.kind != ResumeKind.SPOTIFY) return@mapNotNull null
            SavedLinks.spotifyPage(item.key)?.let { n.id to it }
        }
        for ((id, page) in missing) {
            val image = runCatching { thumbnail(page) }.getOrNull() ?: continue
            val note = NotesStore.data.value.notes.firstOrNull { it.id == id } ?: continue
            NotesStore.saveNote(id, SavedLinks.withImage(note.text, image))
        }
    }

    private suspend fun thumbnail(page: String): String? = withContext(Dispatchers.IO) {
        val c = URL("https://open.spotify.com/oembed?url=" + URLEncoder.encode(page, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        try {
            if (c.responseCode != 200) return@withContext null
            JSONObject(c.inputStream.bufferedReader().readText()).optString("thumbnail_url").takeIf { it.startsWith("http") }
        } finally {
            c.disconnect()
        }
    }
}
