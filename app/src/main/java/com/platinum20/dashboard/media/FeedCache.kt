package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.spotify.SpHome
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpLibrary
import com.platinum20.dashboard.media.spotify.SpProfile
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.net.DiskCache
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Come salvare su file un tipo di feed: nome del file e conversione in testo e ritorno. */
class FeedCodec<T>(val file: String, val write: (T) -> JSONArray, val read: (JSONArray) -> T)

/**
 * Gli ultimi feed di YouTube e Spotify salvati su file, per mostrarli subito all'apertura
 * mentre arrivano quelli nuovi. Si cancellano quando si scollega l'account.
 */
object FeedCache {
    /** Oltre una settimana i feed salvati non servono più. */
    private const val MAX_AGE_MS = 7 * 24 * 3_600_000L

    suspend fun <T> restore(codec: FeedCodec<T>): Loadable<T>? {
        val text = DiskCache.read(codec.file) ?: return null
        return runCatching {
            val root = JSONObject(text)
            val savedAt = root.optLong("savedAt")
            if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) return null
            Loadable(data = codec.read(root.getJSONArray("data")), loadedAt = savedAt)
        }.getOrNull()
    }

    suspend fun <T> save(codec: FeedCodec<T>, data: T, savedAt: Long) {
        val text = runCatching { encode(codec, data, savedAt) }.getOrNull() ?: return
        DiskCache.write(codec.file, text)
    }

    internal fun <T> encode(codec: FeedCodec<T>, data: T, savedAt: Long): String =
        JSONObject().put("savedAt", savedAt).put("data", codec.write(data)).toString()

    internal fun <T> decode(codec: FeedCodec<T>, text: String): T = codec.read(JSONObject(text).getJSONArray("data"))

    fun clear(prefix: String) = DiskCache.delete(prefix)

    // --- YouTube ---

    private fun video(v: YtVideo) = JSONObject()
        .put("id", v.id).put("t", v.title).put("c", v.channel).put("ci", v.channelId)
        .put("p", v.published?.toEpochMilli() ?: 0L).put("s", v.isShort).put("d", v.durationSec)
        .put("l", v.live).put("u", v.upcoming).put("a", v.channelAvatar)
        .put("v", v.views).put("ut", v.uploadedText).put("w", v.watchedPercent)

    private fun video(o: JSONObject) = YtVideo(
        id = o.getString("id"),
        title = o.optString("t"),
        channel = o.optString("c"),
        channelId = o.optString("ci"),
        published = o.optLong("p").takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
        isShort = o.optBoolean("s"),
        durationSec = o.optInt("d"),
        live = o.optBoolean("l"),
        upcoming = o.optBoolean("u"),
        channelAvatar = o.optString("a"),
        views = o.optLong("v", -1),
        uploadedText = o.optString("ut"),
        watchedPercent = o.optInt("w"),
    )

    private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = (0 until length()).map { map(getJSONObject(it)) }

    private fun <T> List<T>.toArray(map: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(map(it)) } }

    fun videos(file: String) = FeedCodec<List<YtVideo>>(file, { list -> list.toArray(::video) }, { it.objects(::video) })

    val playlists = FeedCodec<List<YtPlaylist>>(
        "yt-playlist.json",
        { list -> list.toArray { JSONObject().put("id", it.id).put("t", it.title).put("i", it.thumbnail).put("n", it.count) } },
        { array -> array.objects { YtPlaylist(it.getString("id"), it.optString("t"), it.optString("i"), it.optInt("n")) } },
    )

    // --- Spotify ---

    private fun item(i: SpItem) = JSONObject()
        .put("id", i.id).put("u", i.uri).put("k", i.kind.name).put("t", i.title).put("s", i.subtitle).put("i", i.image)

    private fun item(o: JSONObject) = SpItem(
        id = o.getString("id"),
        uri = o.optString("u"),
        kind = SpKind.valueOf(o.getString("k")),
        title = o.optString("t"),
        subtitle = o.optString("s"),
        image = o.optString("i"),
    )

    private fun items(list: List<SpItem>) = list.toArray(::item)

    private fun items(array: JSONArray) = array.objects(::item)

    fun spItems(file: String) = FeedCodec<List<SpItem>>(file, ::items, ::items)

    /** "Per te": quattro parti, salvate come elenchi uno dopo l'altro. */
    val spHome = FeedCodec<SpHome>(
        "sp-home.json",
        { home ->
            JSONArray()
                .put(JSONObject().put("name", home.profile.name).put("premium", home.profile.premium))
                .put(items(home.recent)).put(items(home.topTracks)).put(items(home.topArtists))
        },
        { array ->
            val profile = array.getJSONObject(0)
            SpHome(
                profile = SpProfile(profile.optString("name"), profile.optBoolean("premium")),
                recent = items(array.getJSONArray(1)),
                topTracks = items(array.getJSONArray(2)),
                topArtists = items(array.getJSONArray(3)),
            )
        },
    )

    val spLibrary = FeedCodec<SpLibrary>(
        "sp-libreria.json",
        { lib -> JSONArray().put(items(lib.liked)).put(items(lib.albums)).put(items(lib.shows)) },
        { array -> SpLibrary(items(array.getJSONArray(0)), items(array.getJSONArray(1)), items(array.getJSONArray(2))) },
    )
}
