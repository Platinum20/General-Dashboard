package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.spotify.SpArtistLatest
import com.platinum20.dashboard.media.spotify.SpArtistPage
import com.platinum20.dashboard.media.spotify.SpReleaseGroup
import com.platinum20.dashboard.media.spotify.SpDiscoverRow
import com.platinum20.dashboard.media.spotify.SpHome
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.spotify.SpLibrary
import com.platinum20.dashboard.media.spotify.SpProfile
import com.platinum20.dashboard.media.spotify.SpRelease
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtPlaylist
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.net.DiskCache
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

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
        .put("st", v.startsAt?.epochSecond ?: 0L).put("m", v.membersOnly)

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
        startsAt = o.optLong("st").takeIf { it > 0 }?.let { Instant.ofEpochSecond(it) },
        membersOnly = o.optBoolean("m"),
    )

    private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = (0 until length()).map { map(getJSONObject(it)) }

    private fun <T> List<T>.toArray(map: (T) -> JSONObject) = JSONArray().also { array -> forEach { array.put(map(it)) } }

    fun videos(file: String) = FeedCodec<List<YtVideo>>(file, { list -> list.toArray(::video) }, { it.objects(::video) })

    val playlists = FeedCodec<List<YtPlaylist>>(
        "yt-playlist.json",
        { list -> list.toArray { JSONObject().put("id", it.id).put("t", it.title).put("i", it.thumbnail).put("n", it.count) } },
        { array -> array.objects { YtPlaylist(it.getString("id"), it.optString("t"), it.optString("i"), it.optInt("n")) } },
    )

    val channels = FeedCodec<List<YtChannel>>(
        "yt-canali.json",
        { list -> list.toArray { JSONObject().put("id", it.id).put("t", it.title).put("i", it.thumbnail) } },
        { array -> array.objects { YtChannel(it.getString("id"), it.optString("t"), it.optString("i")) } },
    )

    // --- Spotify ---

    private fun item(i: SpItem) = JSONObject()
        .put("id", i.id).put("u", i.uri).put("k", i.kind.name).put("t", i.title).put("s", i.subtitle).put("i", i.image).put("d", i.durationMs)

    private fun item(o: JSONObject) = SpItem(
        id = o.getString("id"),
        uri = o.optString("u"),
        kind = SpKind.valueOf(o.getString("k")),
        title = o.optString("t"),
        subtitle = o.optString("s"),
        image = o.optString("i"),
        durationMs = o.optLong("d"),
    )

    private fun items(list: List<SpItem>) = list.toArray(::item)

    private fun items(array: JSONArray) = array.objects(::item)

    fun spItems(file: String) = FeedCodec<List<SpItem>>(file, ::items, ::items)

    /** Le nuove uscite degli artisti seguiti, con la data di uscita. */
    val spReleases = FeedCodec<List<SpRelease>>(
        "sp-releases.json",
        { list -> list.toArray { item(it.item).put("d", it.released.toString()) } },
        { array -> array.objects { SpRelease(item(it), LocalDate.parse(it.getString("d"))) } },
    )

    /** Le file di "Scopri" della musica (1.488). */
    val spDiscover = FeedCodec<List<SpDiscoverRow>>(
        "sp-scopri.json",
        { list -> list.toArray { JSONObject().put("t", it.title).put("w", it.why).put("i", items(it.items)) } },
        { array -> array.objects { SpDiscoverRow(it.optString("t"), it.optString("w"), items(it.getJSONArray("i"))) } },
    )

    /** Gli artisti che segui con la data dell'ultima uscita (le storie di Media). */
    val spArtists = FeedCodec<List<SpArtistLatest>>(
        "sp-artists.json",
        { list -> list.toArray { item(it.artist).put("d", it.latest?.toString().orEmpty()) } },
        { array -> array.objects { SpArtistLatest(item(it), it.optString("d").takeIf { d -> d.isNotBlank() }?.let(LocalDate::parse)) } },
    )

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

    /**
     * Le pagine degli artisti aperti di recente, solo testo (le copertine sono nella cache delle immagini, che ha già
     * un tetto): riaprire un artista non chiede niente a Spotify. Ognuna con l'ora in cui è stata letta.
     */
    val spArtistPages = FeedCodec<List<Pair<Long, SpArtistPage>>>(
        "sp-artist-pages.json",
        { list -> list.toArray { (at, p) -> artistPage(p).put("at", at) } },
        { array -> array.objects { it.optLong("at") to artistPage(it) } },
    )

    private fun artistPage(p: SpArtistPage) = JSONObject()
        .put("a", item(p.artist)).put("ph", p.photo).put("f", p.followers)
        .put("g", JSONArray(p.genres)).put("pop", items(p.popular))
        .put("al", items(p.albums)).put("si", items(p.singles)).put("co", items(p.compilations)).put("ap", items(p.appearsOn))
        .put("r", JSONObject().also { o -> p.released.forEach { (id, day) -> o.put(id, day.toString()) } })
        .put("n", JSONObject().also { o -> p.totals.forEach { (g, n) -> o.put(g.name, n) } })
        .put("c", p.complete)

    private fun artistPage(o: JSONObject): SpArtistPage {
        val released = o.optJSONObject("r")
        val totals = o.optJSONObject("n")
        val genres = o.optJSONArray("g")
        return SpArtistPage(
            artist = item(o.getJSONObject("a")),
            photo = o.optString("ph"),
            followers = o.optLong("f"),
            genres = (0 until (genres?.length() ?: 0)).map { genres!!.getString(it) },
            popular = items(o.getJSONArray("pop")),
            albums = items(o.getJSONArray("al")),
            singles = items(o.getJSONArray("si")),
            compilations = items(o.getJSONArray("co")),
            appearsOn = items(o.getJSONArray("ap")),
            released = released?.keys()?.asSequence()?.mapNotNull { id ->
                runCatching { id to LocalDate.parse(released.getString(id)) }.getOrNull()
            }?.toMap().orEmpty(),
            totals = totals?.keys()?.asSequence()?.mapNotNull { k ->
                runCatching { SpReleaseGroup.valueOf(k) to totals.getInt(k) }.getOrNull()
            }?.toMap().orEmpty(),
            complete = o.optBoolean("c"),
        )
    }

    val spLibrary = FeedCodec<SpLibrary>(
        "sp-libreria.json",
        { lib -> JSONArray().put(items(lib.liked)).put(items(lib.albums)).put(items(lib.shows)) },
        { array -> SpLibrary(items(array.getJSONArray(0)), items(array.getJSONArray(1)), items(array.getJSONArray(2))) },
    )
}
