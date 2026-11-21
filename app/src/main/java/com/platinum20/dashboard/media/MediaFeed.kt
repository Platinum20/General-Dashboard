package com.platinum20.dashboard.media

import com.platinum20.dashboard.media.podcast.Episode
import com.platinum20.dashboard.media.spotify.SpArtistLatest
import com.platinum20.dashboard.media.spotify.SpListened
import com.platinum20.dashboard.media.youtube.YtChannel
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.media.youtube.YtWatched
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId

/** Quando ha pubblicato: le righe e le tessere di Media (le storie in cima sono state tolte nella 1.490). */
object MediaStories {
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

    /** Sotto un episodio nel feed: "Il Post · 3 h fa" (la durata è sulla copertina, 1.485). */
    fun episodeLine(episode: Episode, now: Long): String =
        listOfNotNull(episode.podcastTitle.takeIf { it.isNotBlank() }, episodeAgo(episode, now)).joinToString(" · ")

    /** Quando è uscito un episodio: "adesso", "3 h fa", "ieri", "4 giorni fa", "2 settimane fa"; null se non si sa. */
    fun episodeAgo(episode: Episode, now: Long): String? = episode.publishedAt.takeIf { it > 0 }?.let { at ->
        val hours = (now - at) / 3_600_000L
        when {
            hours < 1 -> "adesso"
            hours < 24 -> "$hours h fa"
            hours < 48 -> "ieri"
            hours < 24 * 14 -> "${hours / 24} giorni fa"
            else -> "${hours / 24 / 7} settimane fa"
        }
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
    /** Nella fila di Media, dopo ULTIMA VISITA, solo le prime vecchie: il resto è in "Tutte le novità" (1.486). */
    const val ROW_OLDER = 8
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

/** Spotify e YouTube nella stessa lista (1.485, Antonio: "un unico menu che mostra entrambe le cose"). */
object MediaMerge {
    /**
     * Spotify e YouTube insieme nell'ordine in cui li hai ascoltati o guardati (1.488, Antonio: "non mischiati a turno"):
     * prima ciò che ha un'ora [at], dal più recente; poi il resto nell'ordine in cui arriva.
     */
    fun <T> byRecency(items: List<T>, at: (T) -> Long?): List<T> =
        items.sortedByDescending { at(it) ?: Long.MIN_VALUE }

    /** Le ore dell'ultimo ascolto per chiave: uri Spotify (brano e album/playlist da cui veniva) o id del video. */
    fun heardAt(history: List<ResumeItem>, watched: Map<String, Long>, played: List<Pair<String, Long>>): Map<String, Long> {
        val out = HashMap<String, Long>()
        fun put(key: String, at: Long) {
            if (key.isNotBlank() && at > (out[key] ?: Long.MIN_VALUE)) out[key] = at
        }
        history.forEach {
            put(it.key, it.at)
            put(it.link, it.at)
        }
        watched.forEach { (id, at) -> put(id, at) }
        played.forEach { (uri, at) -> put(uri, at) }
        return out
    }

}

/**
 * Le Novità che Media ha appena costruito, per la pagina "Tutte le novità" (1.486): la si apre da Media, quindi sono
 * sempre quelle che hai appena visto nella fila.
 */
object MediaFeedNow {
    var rows by androidx.compose.runtime.mutableStateOf(MediaFeedRows(emptyList(), emptyList()))
    /** L'inizio della visita e gli episodi già ascoltati: la pagina ricostruisce tutte le novità con gli stessi. */
    var since by androidx.compose.runtime.mutableStateOf(0L)
    var listened by androidx.compose.runtime.mutableStateOf(emptySet<String>())
}
