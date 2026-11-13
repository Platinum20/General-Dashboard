package com.platinum20.dashboard.search

import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.youtube.YtChannel

/** Un artista di Spotify o un canale di YouTube trovato in Cerca: tocco = la sua pagina nell'app. */
sealed interface PersonHit {
    val key: String
    val title: String
    data class Artist(val item: SpItem) : PersonHit {
        override val key get() = "sp:" + item.id
        override val title get() = item.title
    }
    data class Channel(val channel: YtChannel, val subscribers: Long) : PersonHit {
        override val key get() = "yt:" + channel.id
        override val title get() = channel.title
    }
}

/** Funzioni pure di Cerca per artisti e canali, provate nei test. */
object SearchPeople {
    /**
     * Artisti e canali in un gruppo solo, prima gli artisti (Musica) poi i canali (Video), ognuno
     * nell'ordine della sua fonte: niente alternanza. Ogni fonte conta solo se il suo filtro è acceso.
     */
    fun hits(spotify: List<SpItem>, channels: List<Pair<YtChannel, Long>>, music: Boolean, video: Boolean): List<PersonHit> {
        val artists = if (music) spotify.filter { it.kind == SpKind.ARTIST }.map { PersonHit.Artist(it) } else emptyList()
        val chans = if (video) channels.map { (c, n) -> PersonHit.Channel(c, n) } else emptyList()
        return (artists + chans).distinctBy { it.key }
    }

    /** "1,2 Mln di iscritti", "15.300 iscritti"; solo "Canale" se non si sa. */
    fun channelLine(subscribers: Long): String = when {
        subscribers <= 0 -> "Canale"
        subscribers >= 1_000_000 -> "Canale · " + "%.1f Mln di iscritti".format(java.util.Locale.ITALY, subscribers / 1_000_000.0).replace(",0 ", " ")
        else -> "Canale · " + "%,d iscritti".format(java.util.Locale.ITALY, subscribers)
    }

    /** Apre o richiude una fonte dei risultati. */
    fun toggled(open: Set<String>, key: String): Set<String> = if (key in open) open - key else open + key
}
