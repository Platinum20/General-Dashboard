package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.podcast.Episode

/**
 * Tocco normale più "tieni premuto per salvare nelle note", per le righe e le tessere dei media.
 * Se [link] dà null (es. un album di Spotify) il tenere premuto non fa nulla.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.tapOrSave(link: () -> SavedLink?, onClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = { link()?.let(SavedLinks::offer) })

/** Un episodio di podcast da salvare: si riapre dal punto in cui era rimasto. */
internal fun episodeLink(episode: Episode, positionMs: Long = 0): SavedLink = SavedLink.Media(
    ResumeItem(
        ResumeKind.PODCAST, episode.id, episode.title, episode.podcastTitle, episode.image,
        positionMs = positionMs, durationMs = episode.durationSec * 1000, at = System.currentTimeMillis(),
        link = episode.audioUrl, feed = episode.feedUrl,
    ),
)
