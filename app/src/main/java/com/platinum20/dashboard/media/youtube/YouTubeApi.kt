package com.platinum20.dashboard.media.youtube

import android.util.Xml
import com.platinum20.dashboard.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

data class YtVideo(
    val id: String,
    val title: String,
    val channel: String,
    val channelId: String,
    val published: Instant?,
    val isShort: Boolean = false,
) {
    val thumbnail: String get() = "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    val watchUrl: String get() = "https://m.youtube.com/watch?v=$id"
}

data class YtPlaylist(val id: String, val title: String, val thumbnail: String, val count: Int)

data class YtChannel(val id: String, val title: String, val thumbnail: String)

class YouTubeAuthException : IOException("Accesso a YouTube scaduto")

/** API ufficiale di YouTube (Data API v3) più i feed RSS pubblici dei canali, che non consumano quota. */
object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3/"

    private suspend fun get(token: String, path: String, params: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val query = params.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            val conn = (URL("$BASE$path?$query").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (val code = conn.responseCode) {
                    in 200..299 -> JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    401 -> throw YouTubeAuthException()
                    else -> {
                        val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                        val reason = runCatching {
                            JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).getString("reason")
                        }.getOrNull()
                        throw IOException(
                            when (reason) {
                                "quotaExceeded" -> "Quota giornaliera di YouTube esaurita, riprova domani"
                                "accessNotConfigured" -> "YouTube Data API non abilitata nel progetto Google Cloud"
                                else -> "YouTube non risponde ($code), riprova più tardi"
                            }
                        )
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

    /** Scorre le pagine di una lista fino a [limit] elementi. */
    private suspend fun paged(
        token: String,
        path: String,
        params: Map<String, String>,
        limit: Int,
    ): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var pageToken: String? = null
        do {
            val page = get(token, path, params + ("maxResults" to "50") + (pageToken?.let { mapOf("pageToken" to it) } ?: emptyMap()))
            val items = page.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) out += items.getJSONObject(i)
            pageToken = page.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null && out.size < limit)
        return out.take(limit)
    }

    suspend fun subscriptions(token: String): List<YtChannel> =
        paged(token, "subscriptions", mapOf("part" to "snippet", "mine" to "true", "order" to "relevance"), limit = 200)
            .mapNotNull { item ->
                val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                val id = snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty()
                if (id.isBlank()) null else YtChannel(id, snippet.optString("title"), snippet.thumbnail())
            }

    suspend fun playlists(token: String): List<YtPlaylist> =
        paged(token, "playlists", mapOf("part" to "snippet,contentDetails", "mine" to "true"), limit = 100)
            .map { item ->
                val snippet = item.optJSONObject("snippet") ?: JSONObject()
                YtPlaylist(
                    id = item.optString("id"),
                    title = snippet.optString("title"),
                    thumbnail = snippet.thumbnail(),
                    count = item.optJSONObject("contentDetails")?.optInt("itemCount") ?: 0,
                )
            }

    suspend fun playlistItems(token: String, playlistId: String): List<YtVideo> =
        paged(token, "playlistItems", mapOf("part" to "snippet", "playlistId" to playlistId), limit = 200)
            .mapNotNull { item ->
                val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
                val id = snippet.optJSONObject("resourceId")?.optString("videoId").orEmpty()
                // I video privati o eliminati restano in playlist senza titolo utile.
                if (id.isBlank() || snippet.optString("title") in setOf("Private video", "Deleted video")) null
                else YtVideo(
                    id = id,
                    title = snippet.optString("title"),
                    channel = snippet.optString("videoOwnerChannelTitle"),
                    channelId = snippet.optString("videoOwnerChannelId"),
                    published = snippet.optString("publishedAt").toInstant(),
                )
            }

    suspend fun liked(token: String): List<YtVideo> =
        paged(token, "videos", mapOf("part" to "snippet", "myRating" to "like"), limit = 100)
            .map { item -> item.optJSONObject("snippet").toVideo(item.optString("id")) }

    suspend fun search(token: String, query: String): List<YtVideo> =
        get(token, "search", mapOf("part" to "snippet", "q" to query, "type" to "video", "maxResults" to "30", "regionCode" to "IT"))
            .optJSONArray("items")
            ?.let { items ->
                (0 until items.length()).mapNotNull { i ->
                    val item = items.getJSONObject(i)
                    val id = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    if (id.isBlank()) null else item.optJSONObject("snippet").toVideo(id)
                }
            }
            .orEmpty()

    /** Ultimi video dei canali seguiti, dai feed RSS pubblici (gratis, senza quota). */
    suspend fun uploads(channels: List<YtChannel>): List<YtVideo> = coroutineScope {
        val gate = Semaphore(8)
        channels.map { channel ->
            async(Dispatchers.IO) {
                gate.withPermit { runCatching { channelFeed(channel.id) }.getOrDefault(emptyList()) }
            }
        }.awaitAll().flatten()
            .distinctBy { it.id }
            .sortedByDescending { it.published }
    }

    private fun channelFeed(channelId: String): List<YtVideo> {
        val conn = (URL("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "GeneralDashboard/${BuildConfig.VERSION_NAME} (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            return conn.inputStream.use { parseFeed(it) }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseFeed(input: InputStream): List<YtVideo> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val out = mutableListOf<YtVideo>()
        var inEntry = false
        var id = ""
        var title = ""
        var channel = ""
        var channelId = ""
        var published = ""
        var link = ""
        var inAuthor = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> {
                        inEntry = true
                        id = ""; title = ""; channel = ""; channelId = ""; published = ""; link = ""
                    }
                    "author" -> inAuthor = true
                    "link" -> if (inEntry) link = parser.getAttributeValue(null, "href").orEmpty()
                    "yt:videoId" -> if (inEntry) id = parser.nextText()
                    "yt:channelId" -> if (inEntry) channelId = parser.nextText()
                    "title" -> if (inEntry) title = parser.nextText()
                    "name" -> if (inEntry && inAuthor) channel = parser.nextText()
                    "published" -> if (inEntry) published = parser.nextText()
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "author" -> inAuthor = false
                    "entry" -> {
                        inEntry = false
                        if (id.isNotBlank()) {
                            out += YtVideo(id, title, channel, channelId, published.toInstant(), isShort = "/shorts/" in link)
                        }
                    }
                }
            }
        }
        return out
    }

    private fun JSONObject.thumbnail(): String {
        val thumbs = optJSONObject("thumbnails") ?: return ""
        return listOf("medium", "high", "default").firstNotNullOfOrNull { thumbs.optJSONObject(it)?.optString("url") }.orEmpty()
    }

    private fun JSONObject?.toVideo(id: String): YtVideo {
        val snippet = this ?: JSONObject()
        return YtVideo(
            id = id,
            title = snippet.optString("title").unescapeHtml(),
            channel = snippet.optString("channelTitle"),
            channelId = snippet.optString("channelId"),
            published = snippet.optString("publishedAt").toInstant(),
        )
    }

    private fun String.toInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    // La ricerca restituisce i titoli con entità HTML (&#39;, &quot;, &amp;).
    private fun String.unescapeHtml(): String =
        replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
