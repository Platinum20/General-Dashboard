package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.ui.graphics.vector.ImageVector
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeKind
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.notes.NotesStore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import kotlinx.coroutines.delay

/** Quanto resta il fumetto "Salva nelle note" se non lo si tocca. */
private const val OFFER_MS = 6_000L

/**
 * Tenendo premuta una cosa (un brano, un video, una radio, un articolo) sale dal basso il fumetto
 * di vetro con "Salva nelle note" (e, per i media, "Aggiungi alla coda"). Toccandolo la nota è fatta e il pallino vola alle Note.
 * Portandolo di lato (come il mini player) si annulla.
 * Chi vuole offrirlo chiama solo [SavedLinks.offer] nel suo onLongClick.
 */
@Composable
internal fun BoxScope.SaveToNotesLayer() {
    val offered = SavedLinks.offered
    val context = LocalContext.current
    val haptic = rememberHaptic()
    NotesStore.init(context)
    val notes by NotesStore.data.collectAsStateWithLifecycle()
    // L'ultima offerta resta disegnata mentre il fumetto si chiude.
    var last by remember { mutableStateOf<SavedLink?>(null) }
    // Portando il fumetto di lato si annulla, con lo stesso gesto che chiude il mini player.
    val swipe = rememberSwipeAwayState()
    LaunchedEffect(offered) {
        if (offered == null) return@LaunchedEffect
        last = offered
        swipe.reset()
        haptic(Haptic.LONG_PRESS)
        delay(OFFER_MS)
        if (SavedLinks.offered === offered) SavedLinks.dismiss()
    }
    BottomBubble(visible = offered != null, swipe = swipe, onDismiss = { SavedLinks.dismiss() }) {
        val link = last ?: return@BottomBubble
        val media = (link as? SavedLink.Media)?.item?.takeIf { it.kind != ResumeKind.VIDEO }
        // Video, canzoni, radio ed episodi si possono anche mettere in coda, dopo quello che suona.
        if (media != null) {
            BubbleRow(icon = Icons.Rounded.QueueMusic, title = "Aggiungi alla coda", detail = media.title) {
                haptic(Haptic.CONFIRM)
                SavedLinks.dismiss()
                CanvasPlayer.add(media)
                CaptureFeedback.tell(context, "In coda: ${media.title}", Icons.Rounded.QueueMusic)
            }
            RowDivider()
            // Un brano di Spotify fa partire la sua radio: simili a lui, poi resta nella fila Musica (1.491).
            com.platinum20.dashboard.media.spotify.MadeStore.seedOf(media)?.let { seed ->
                BubbleRow(icon = Icons.Rounded.Radio, title = "Radio", detail = "Brani simili a ${media.title}") {
                    haptic(Haptic.CONFIRM)
                    SavedLinks.dismiss()
                    CaptureFeedback.tell(context, "Preparo la radio di ${media.title}", Icons.Rounded.Radio)
                    com.platinum20.dashboard.media.spotify.MadeStore.startRadio(context, seed) { error ->
                        CaptureFeedback.tell(context, error)
                    }
                }
                RowDivider()
                // Nelle tue playlist, o in una nuova (1.500): il fumetto dopo mostra le playlist come chip.
                BubbleRow(icon = Icons.Rounded.PlaylistAdd, title = "Aggiungi a una playlist", detail = "Una delle tue o una nuova") {
                    haptic(Haptic.CONFIRM)
                    SavedLinks.dismiss()
                    PlaylistPicker.open(context, seed)
                }
                RowDivider()
            }
        }
        // Le azioni della sua pagina, senza aprirla (Antonio, 2026-11-13): Mi piace e Condividi per un video,
        // i preferiti per una radio, Segui per il podcast dell'episodio.
        if (media != null) MediaPageRows(media) { SavedLinks.dismiss() }
        // Se la cosa è già nelle note, lo stesso posto la toglie: mai due copie.
        val saved = remember(notes.notes, link) { SavedLinks.notesWith(notes.notes, link).isNotEmpty() }
        BubbleRow(
            icon = if (saved) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
            title = SavedLinks.saveLabel(link, saved),
            detail = listOf(link.title, link.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
        ) {
            haptic(Haptic.CONFIRM)
            if (saved) SavedLinks.remove(context, link) else SavedLinks.save(context, link)
        }
    }
}

/**
 * Indietro chiude prima il fumetto, poi la pagina dietro. Il gestore entra in composizione solo
 * quando il fumetto si apre: così è l'ultimo registrato e vince su quelli delle schermate
 * (che col semplice `BackHandler(visible)` venivano dopo e chiudevano la pagina al suo posto).
 */
@Composable
internal fun BubbleBack(open: Boolean, onBack: () -> Unit) {
    if (open) BackHandler(onBack = onBack)
}

/**
 * Il fumetto di vetro in fondo al centro, comune a tutti i pop-up di Canvas: velo leggero dietro
 * (toccandolo si chiude), indietro lo chiude, e portandolo di lato si chiude come il mini player.
 * Va disegnato fuori dal NavHost, se no il vetro non ha niente dietro da sfocare.
 */
@Composable
internal fun BoxScope.BottomBubble(
    visible: Boolean,
    swipe: SwipeAwayState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BubbleBack(visible, onDismiss)
    // Le prime volte che si vede un fumetto, una riga piccola dice come si chiude (poi mai più).
    val context = LocalContext.current
    val hint = remember(visible) { visible && GestureHints.stillShow(context, GestureHints.SWIPE_BUBBLE) }
    LaunchedEffect(visible) { if (visible) GestureHints.mark(context, GestureHints.SWIPE_BUBBLE) }
    AnimatedVisibility(visible, enter = fadeIn(Motion.settle()), exit = fadeOut(tween(Motion.EXIT_MS)), modifier = Modifier.matchParentSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible,
        enter = fadeIn(Motion.settle()) + scaleIn(Motion.lively(), initialScale = 0.92f) + slideInVertically(Motion.lively()) { it / 3 },
        exit = fadeOut(tween(Motion.EXIT_MS - 20)) + slideOutVertically(tween(Motion.EXIT_MS, easing = Motion.Accelerate)) { it / 3 },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SwipeAwayMark(swipe)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.swipeAway(swipe) { if (visible) onDismiss() },
            ) {
                // Un solo blocco di vetro con le righe dentro (1.402): prima ogni azione era una pillola a sé.
                val shape = RoundedCornerShape(22.dp)
                Surface(
                    shape = shape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = cardBorder(),
                    modifier = Modifier.fillMaxWidth().glass(shape),
                ) {
                    Column(Modifier.padding(vertical = 4.dp), content = content)
                }
                if (hint) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        border = cardBorder(),
                        modifier = Modifier.align(Alignment.CenterHorizontally).glass(CircleShape),
                    ) {
                        Text(
                            "Portalo di lato per chiuderlo",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Una riga del fumetto: icona in inchiostro, titolo e una riga di dettaglio (1.402: niente pallini colorati).
 * [danger] = rosso, per le azioni che tolgono qualcosa. Le righe stanno nel blocco di [BottomBubble].
 */
@Composable
internal fun BubbleRow(icon: ImageVector, title: String, detail: String = "", danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Le righe del fumetto che fanno quello che fanno i tasti della pagina del media. [done] chiude il fumetto. */
@Composable
private fun MediaPageRows(media: ResumeItem, done: () -> Unit) {
    val context = LocalContext.current
    val haptic = rememberHaptic()
    when (media.kind) {
        ResumeKind.YOUTUBE -> {
            val liked by com.platinum20.dashboard.media.youtube.YtFavorites.items.collectAsStateWithLifecycle()
            val isLiked = liked.any { it.id == media.key }
            BubbleRow(
                icon = if (isLiked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                title = if (isLiked) "Togli Mi piace" else "Mi piace",
            ) {
                haptic(Haptic.CONFIRM)
                done()
                com.platinum20.dashboard.media.youtube.YtFavorites.toggle(
                    context,
                    com.platinum20.dashboard.media.youtube.YtVideo(
                        media.key, media.title, media.subtitle, "", null, durationSec = (media.durationMs / 1000).toInt(),
                    ),
                )
            }
            RowDivider()
            BubbleRow(icon = Icons.Rounded.Share, title = "Condividi", detail = "Il link del video") {
                done()
                runCatching { shareText(context, "https://youtu.be/${media.key}") }
            }
            RowDivider()
        }
        ResumeKind.RADIO -> media.station?.let { station ->
            val favorites by com.platinum20.dashboard.media.radio.RadioStore.favorites.collectAsStateWithLifecycle()
            val fav = favorites.any { it.id == station.id }
            BubbleRow(
                icon = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                title = if (fav) "Togli dai preferiti" else "Aggiungi ai preferiti",
                detail = station.name,
            ) {
                haptic(Haptic.CONFIRM)
                done()
                com.platinum20.dashboard.media.radio.RadioStore.toggleFavorite(station)
            }
            RowDivider()
        }
        ResumeKind.PODCAST -> media.feed.takeIf { it.isNotBlank() }?.let { feed ->
            val followed by com.platinum20.dashboard.media.podcast.PodcastStore.followed.collectAsStateWithLifecycle()
            val podcast = followed.firstOrNull { it.feedUrl == feed }
                ?: com.platinum20.dashboard.media.podcast.Podcast(feed, media.subtitle, "", feed, media.image)
            val following = followed.any { it.feedUrl == feed }
            BubbleRow(
                icon = if (following) Icons.Rounded.RemoveCircleOutline else Icons.Rounded.AddCircleOutline,
                title = if (following) "Non seguire più" else "Segui il podcast",
                detail = media.subtitle,
            ) {
                haptic(Haptic.CONFIRM)
                done()
                com.platinum20.dashboard.media.podcast.PodcastStore.toggleFollow(podcast)
            }
            RowDivider()
        }
        else -> Unit
    }
}
