package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.spotify.SpArtistLatest
import com.platinum20.dashboard.media.spotify.SpListened
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtWatched
import java.time.Instant
import java.time.ZoneId

/** Una storia in cima a Media (1.481, Antonio: "tipo storie di Instagram"): un canale YouTube o un artista Spotify. */
data class MediaStory(
    val kind: Kind,
    val id: String,
    val title: String,
    val image: String,
    /** Quando ha pubblicato l'ultima volta, se si sa. */
    val latest: Instant?,
    /** Ha pubblicato dopo l'ultima visita: anello blu. */
    val fresh: Boolean,
    /** Quando l'hai guardato o ascoltato l'ultima volta; null = lo segui ma non l'hai visto di recente. */
    val seen: Instant? = null,
) {
    enum class Kind { CHANNEL, ARTIST }

    val key: String get() = "${kind.name}-$id"
}

/**
 * Le storie (1.484, Antonio: "chi ho guardato per ultimo", "quello che ho ascoltato più di recente come primo"):
 * i canali dei video che hai visto e gli artisti che hai ascoltato, dal più recente. Dopo, se c'è posto, i canali e gli
 * artisti che segui, da chi ha pubblicato per ultimo. L'anello blu resta "ha pubblicato dopo l'ultima visita"; un
 * artista ha solo il giorno dell'uscita e conta come mezzanotte.
 */
object MediaStories {
    const val MAX = 40

    fun build(
        watched: List<YtWatched>,
        listened: List<SpListened>,
        channels: List<YtChannel>,
        recent: List<YtVideo>,
        artists: List<SpArtistLatest>,
        since: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        max: Int = MAX,
    ): List<MediaStory> {
        val videos = recent.filter { it.channelId.isNotBlank() }
        val byChannel = videos.groupBy { it.channelId }
        val channelById = channels.associateBy { it.id }
        // I vecchi salvataggi della cronologia non hanno l'id del canale: lo si ritrova dal nome.
        val idByName = (channels.map { it.title to it.id } + videos.map { it.channel to it.channelId })
            .filter { it.first.isNotBlank() }.associate { it.first.lowercase() to it.second }
        val releaseById = artists.associateBy { it.artist.id }

        fun channelStory(id: String, title: String, avatar: String, seen: Instant?): MediaStory {
            val posts = byChannel[id].orEmpty()
            val latest = posts.mapNotNull { it.published }.maxOrNull()
            val image = avatar.ifBlank { channelById[id]?.thumbnail.orEmpty() }
                .ifBlank { posts.firstOrNull { it.channelAvatar.isNotBlank() }?.channelAvatar.orEmpty() }
            return MediaStory(MediaStory.Kind.CHANNEL, id, title.ifBlank { channelById[id]?.title.orEmpty() }, imageUrl(image), latest, MediaFresh.isNew(latest, since), seen)
        }
        fun artistStory(artist: com.platinum20.dashboard.media.spotify.SpItem, seen: Instant?): MediaStory {
            val followed = releaseById[artist.id]
            val latest = followed?.latest?.atStartOfDay(zone)?.toInstant()
            val image = artist.image.ifBlank { followed?.artist?.image.orEmpty() }
            return MediaStory(MediaStory.Kind.ARTIST, artist.id, artist.title, imageUrl(image), latest, MediaFresh.isNew(latest, since), seen)
        }

        val fromWatched = watched.sortedByDescending { it.watchedAt }.mapNotNull { w ->
            val id = w.channelId.ifBlank { idByName[w.channel.lowercase()].orEmpty() }
            if (id.isBlank()) null else channelStory(id, w.channel, w.channelAvatar, Instant.ofEpochMilli(w.watchedAt))
        }.distinctBy { it.id }
        val fromListened = listened.map { artistStory(it.artist, it.at) }.distinctBy { it.id }
        val seen = (fromWatched + fromListened).sortedByDescending { it.seen }
        val taken = seen.map { it.key }.toSet()
        val others = (channels.distinctBy { it.id }.map { channelStory(it.id, it.title, it.thumbnail, null) } +
            artists.distinctBy { it.artist.id }.map { artistStory(it.artist, null) })
            .filter { it.key !in taken }
            .sortedWith(compareByDescending<MediaStory> { it.latest ?: Instant.MIN }.thenBy { it.title.lowercase() })
        return (seen + others).take(max)
    }

    /** Le foto dei canali a volte arrivano senza "https:" davanti ("//yt3.ggpht.com/…"): così non si caricano. */
    fun imageUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        else -> url
    }

    /** Sotto la storia, quanto fa: "ora", "5 h", "ieri", "3 g", "2 sett", "4 mesi"; vuoto se non si sa. */
    fun age(latest: Instant?, now: Long): String {
        latest ?: return ""
        val minutes = (now - latest.toEpochMilli()) / 60_000L
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 60 -> "ora"
            hours < 24 -> "$hours h"
            days < 2 -> "ieri"
            days < 7 -> "$days g"
            days < 35 -> "${days / 7} sett"
            days < 365 -> "${days / 30} mesi"
            else -> "${days / 365} a"
        }
    }

    /** Sotto un episodio nel feed: "Il Post · 3 h fa" (la durata è sulla copertina, 1.484). */
    fun episodeLine(episode: Episode, now: Long): String {
        val ago = episode.publishedAt.takeIf { it > 0 }?.let { at ->
            val hours = (now - at) / 3_600_000L
            when {
                hours < 1 -> "adesso"
                hours < 24 -> "$hours h fa"
                hours < 48 -> "ieri"
                hours < 24 * 14 -> "${hours / 24} giorni fa"
                else -> "${hours / 24 / 7} settimane fa"
            }
        }
        return listOfNotNull(episode.podcastTitle.takeIf { it.isNotBlank() }, ago).joinToString(" · ")
    }
}

/** Una voce del feed di Media: un video (dei canali che segui o consigliato) o un episodio. */
sealed interface FeedEntry {
    val key: String

    data class Video(val video: YtVideo, val recommended: Boolean = false) : FeedEntry {
        override val key: String get() = "v-${video.id}"
    }

    data class Podcast(val episode: Episode) : FeedEntry {
        override val key: String get() = "e-${episode.id}"
    }
}

/** Il feed diviso dal segno ULTIMA VISITA: sopra quello uscito dopo, sotto il resto (sbiadito). */
data class MediaFeedRows(val fresh: List<FeedEntry>, val older: List<FeedEntry>)

/**
 * Il feed unico di Media (1.481, Antonio: "un unico feed, senza tre menu"): video dei canali che segui ed episodi dei
 * podcast insieme, dal più nuovo. Sotto ULTIMA VISITA ogni [every] voci entra un video consigliato; il feed finisce
 * dopo [maxOlder] voci vecchie, così le righe in fondo (radio, Mi piace, Cronologia, I tuoi podcast) si raggiungono.
 */
object MediaFeed {
    const val MAX_OLDER = 30
    const val EVERY = 3

    fun build(
        subscriptions: List<YtVideo>,
        episodes: List<Episode>,
        recommended: List<YtVideo>,
        since: Long,
        listened: Set<String> = emptySet(),
        maxOlder: Int = MAX_OLDER,
        every: Int = EVERY,
    ): MediaFeedRows {
        val videos = subscriptions.distinctBy { it.id }.map { it to (it.published?.toEpochMilli() ?: 0L) }
        val pods = episodes.distinctBy { it.id }.map { it to it.publishedAt }
        val all = (videos.map { (v, at) -> Triple<FeedEntry, Long, Boolean>(FeedEntry.Video(v), at, MediaFresh.isNew(v.published, since)) } +
            pods.map { (e, at) -> Triple<FeedEntry, Long, Boolean>(FeedEntry.Podcast(e), at, since > 0 && at > since && e.id !in listened) })
            .sortedByDescending { it.second }
        val fresh = all.filter { it.third }.map { it.first }
        val old = all.filterNot { it.third }.map { it.first }.take(maxOlder)
        val known = subscriptions.map { it.id }.toSet()
        val tips = recommended.filter { it.id !in known }.distinctBy { it.id }.map { FeedEntry.Video(it, recommended = true) }
        return MediaFeedRows(fresh, interleave(old, tips, every))
    }

    /** Un consigliato dopo ogni [every] voci; senza voci vecchie, qualche consigliato da solo. */
    internal fun interleave(old: List<FeedEntry>, tips: List<FeedEntry>, every: Int): List<FeedEntry> {
        if (old.isEmpty()) return tips.take(every * 2)
        val out = ArrayList<FeedEntry>(old.size + old.size / every)
        var t = 0
        old.forEachIndexed { i, entry ->
            out += entry
            if ((i + 1) % every == 0 && t < tips.size) out += tips[t++]
        }
        return out
    }
}

/** Spotify e YouTube nella stessa lista (1.484, Antonio: "un unico menu che mostra entrambe le cose"). */
object MediaMerge {
    /**
     * Due liste già dalla più recente, senza date da confrontare (i Mi piace): una voce per parte, a turno, finché una
     * finisce; poi il resto dell'altra. Così le più recenti di tutte e due stanno in cima.
     */
    fun <T> alternate(a: List<T>, b: List<T>): List<T> {
        val out = ArrayList<T>(a.size + b.size)
        for (i in 0 until maxOf(a.size, b.size)) {
            if (i < a.size) out += a[i]
            if (i < b.size) out += b[i]
        }
        return out
    }

}
