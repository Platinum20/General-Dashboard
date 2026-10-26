package com.platinum20.dashboard.media.youtube

import android.content.Context
import com.platinum20.dashboard.media.Loadable
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtResolver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La musica di YouTube dentro Musica, accanto a Spotify: ricerca dei brani di YouTube Music e i
 * "Mi piace" musicali. Si ascolta in solo audio nel player dell'app (niente video da scaricare).
 */
object YtMusic {
    /** La playlist dei brani a cui hai messo "Mi piace" su YouTube Music. */
    const val LIKED = "LM"

    private val scope = MainScope()
    private val _search = MutableStateFlow(Loadable<List<YtVideo>>())
    val search: StateFlow<Loadable<List<YtVideo>>> = _search.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) return
        _search.update { it.copy(loading = true, error = null) }
        scope.launch {
            _search.value = runCatching { YtResolver.searchMusic(query.trim()) }
                .fold({ Loadable(data = it, loadedAt = System.currentTimeMillis()) }, { Loadable(error = "YouTube Music non risponde, riprova") })
        }
    }

    fun clearSearch() {
        _search.value = Loadable()
    }

    /** Ascolta [video] in solo audio; i brani dopo di lui in [list] seguono da soli. */
    fun play(context: Context, video: YtVideo, list: List<YtVideo>) {
        val from = list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        // Vanno nella coda unica: si vedono, si spostano e si mischiano con le altre fonti.
        com.platinum20.dashboard.media.CanvasPlayer.playList(context, list.map { it.asQueueItem() }, from)
    }

    fun YtVideo.asQueueItem() = com.platinum20.dashboard.media.ResumeItem(
        com.platinum20.dashboard.media.ResumeKind.MUSIC, id, title, channel.removeSuffix(" - Topic"), thumbnail,
        durationMs = durationSec * 1000L, at = System.currentTimeMillis(),
    )
}
