package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.spotify.SpArtistLatest
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
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
) {
    enum class Kind { CHANNEL, ARTIST }

    val key: String get() = "${kind.name}-$id"
}

/**
 * Le storie: canali e artisti insieme, dal più recente a pubblicare. Un artista ha solo il giorno dell'uscita:
 * conta come mezzanotte, così è "nuovo" solo se è uscito in un giorno dopo l'ultima visita.
 */
object MediaStories {
    const val MAX = 40

    fun build(
        channels: List<YtChannel>,
        recent: List<YtVideo>,
        artists: List<SpArtistLatest>,
        since: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        max: Int = MAX,
    ): List<MediaStory> {
        val newest = recent.filter { it.channelId.isNotBlank() }.groupBy { it.channelId }
        val fromChannels = channels.distinctBy { it.id }.map { c ->
            val videos = newest[c.id].orEmpty()
            val latest = videos.mapNotNull { it.published }.maxOrNull()
            MediaStory(
                MediaStory.Kind.CHANNEL, c.id, c.title,
                c.thumbnail.ifBlank { videos.firstOrNull { it.channelAvatar.isNotBlank() }?.channelAvatar.orEmpty() },
                latest, MediaFresh.isNew(latest, since),
            )
        }
        val fromArtists = artists.distinctBy { it.artist.id }.map { a ->
            val latest = a.latest?.atStartOfDay(zone)?.toInstant()
            MediaStory(MediaStory.Kind.ARTIST, a.artist.id, a.artist.title, a.artist.image, latest, MediaFresh.isNew(latest, since))
        }
        return (fromChannels + fromArtists)
            .sortedWith(compareByDescending<MediaStory> { it.latest ?: Instant.MIN }.thenBy { it.title.lowercase() })
            .take(max)
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

    /** Sotto un episodio nel feed: "Il Post · 38 min · 3 h fa" (una riga sola). */
    fun episodeLine(episode: Episode, now: Long): String {
        val minutes = (episode.durationSec / 60).takeIf { it > 0 }?.let { "$it min" }
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
        return listOfNotNull(episode.podcastTitle.takeIf { it.isNotBlank() }, minutes, ago).joinToString(" · ")
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
