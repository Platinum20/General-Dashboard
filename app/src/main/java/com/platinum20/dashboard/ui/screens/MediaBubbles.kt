package com.platinum20.dashboard.ui.screens

import androidx.compose.material.icons.rounded.PlayArrow
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.ThumbUpOffAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.SavedLink
import com.platinum20.dashboard.capture.SavedLinks
import com.platinum20.dashboard.media.CanvasPlayer
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.notes.NotesStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Un fumetto solo per ogni media (Antonio, 2026-11-23, "Fumetti-Canvas"): lo stesso brano, video, radio o episodio
 * ha le stesse azioni in Media, negli Appunti, nella ricerca e nel player. Prima c'era un fumetto a parte che si
 * chiudeva da solo dopo 6 secondi: ora è il solito [ActionBubble], che resta finché non lo chiudi tu.
 */
private val bubbleScope = MainScope()

/** Che cosa è, per la testata: "BRANO", "VIDEO", "PLAYLIST"… */
internal fun mediaKindLabel(item: ResumeItem): String = when (item.kind) {
    ResumeKind.YOUTUBE, ResumeKind.VIDEO -> "Video"
    ResumeKind.RADIO -> "Radio"
    ResumeKind.PODCAST -> "Episodio"
    ResumeKind.MUSIC -> "Brano"
    ResumeKind.SPOTIFY -> when (item.key.split(":").getOrNull(1)) {
        "playlist" -> "Playlist"
        "album" -> "Album"
        "artist" -> "Artista"
        "show" -> "Podcast"
        "episode" -> "Episodio"
        "track" -> "Brano"
        else -> if (item.key.startsWith("mix:")) "Mix" else "Brano"
    }
}

/** Il fumetto di una cosa salvabile: un media o un articolo. */
internal fun showLinkBubble(context: Context, link: SavedLink, openRoute: ((String) -> Unit)?) {
    when (link) {
        is SavedLink.Media -> showMediaBubble(context, link.item, openRoute)
        is SavedLink.Article -> showArticleBubble(context, link, openRoute)
    }
}

private fun savedNow(context: Context, link: SavedLink): Boolean {
    NotesStore.init(context)
    return SavedLinks.notesWith(NotesStore.data.value.notes, link).isNotEmpty()
}

/** ② per tutto ciò che si salva per dopo: Salva, o Salvato (toccandolo si toglie, con Annulla). */
private fun saveSlot(context: Context, link: SavedLink): BubbleAction {
    val saved = savedNow(context, link)
    return Slot.save(saved) { if (saved) SavedLinks.remove(context, link) else SavedLinks.save(context, link) }
}

/** Notizia o articolo: Leggi · Salva · Condividi. */
internal fun showArticleBubble(context: Context, link: SavedLink.Article, openRoute: ((String) -> Unit)?, read: (() -> Unit)? = null, extra: List<BubbleAction> = emptyList()) {
    ActionBubble.show(
        BubbleHead("Notizia", listOf(link.source, link.title).filter { it.isNotBlank() }.joinToString(" · ")),
        *(extra + listOfNotNull(
            (read ?: openRoute?.let { o -> { SavedLinks.open(context, link, o) } })?.let { Slot.read(it) },
            saveSlot(context, link),
            Slot.share { shareText(context, link.url) },
        )).toTypedArray(),
    )
}

/**
 * Brano, video, radio, episodio, album, playlist: testata, Coda (scelta), le righe della sua pagina, Vai a,
 * Annota/Radio, Aggiungi a una playlist, poi Riproduci (o Apri per le raccolte) · Salva · Condividi · Togli/Elimina.
 * [remove] = il tasto ④ di chi lo mostra (la coda: Togli). [queue] = false dove la coda non ha senso (la coda stessa).
 */
internal fun showMediaBubble(
    context: Context,
    item: ResumeItem,
    openRoute: ((String) -> Unit)?,
    queue: Boolean = true,
    remove: BubbleAction? = null,
    extra: List<BubbleAction> = emptyList(),
) {
    val link = SavedLink.Media(item)
    val kind = mediaKindLabel(item)
    val head = BubbleHead(kind, listOf(item.title, item.subtitle).filter { it.isNotBlank() }.joinToString(" · "))
    val page = SavedLinks.pageRoute(item)
    val playlistId = com.platinum20.dashboard.media.spotify.SpotifyPlaylists.playlistId(item.key)
    val seed = com.platinum20.dashboard.media.spotify.MadeStore.seedOf(item)
    fun build(): List<BubbleAction> = buildList {
        addAll(extra)
        if (queue && SavedLinks.queueable(item)) add(
            choice(
                "Coda",
                BubbleChip("Subito dopo", icon = Icons.Rounded.PlaylistPlay) {
                    CanvasPlayer.addNext(item)
                    CaptureFeedback.tell(context, "Dopo questo: ${item.title}", Icons.Rounded.PlaylistPlay)
                },
                BubbleChip("In fondo", icon = Icons.Rounded.QueueMusic) {
                    CanvasPlayer.add(item)
                    CaptureFeedback.tell(context, "In coda: ${item.title}", Icons.Rounded.QueueMusic)
                },
            ),
        )
        // Una raccolta di Spotify parte da qui senza aprirla.
        if (page != null && item.key.startsWith("spotify:") && kind != "Artista") add(
            choice("Riproduci", BubbleChip("Ora", icon = Icons.Rounded.PlayArrow) { com.platinum20.dashboard.media.spotify.SpotifyPlayer.playLink(context, item.key, item.title, item.subtitle, item.image) }),
        )
        addAll(pageRows(context, item))
        if (playlistId != null) addAll(playlistRows(playlistId, item, openRoute))
        goToCreatorAction(context, item, openRoute)?.let { add(it.copy(group = BubbleGroup.VAI)) }
        // "Annota" (1.606): se è quello che suona, una nota col minuto già messo; il video continua.
        val playing = com.platinum20.dashboard.media.Playback.currentAsItem()
            ?.takeIf { it.kind == item.kind && it.key == item.key && com.platinum20.dashboard.notes.NoteSeek.canAnnotate(it) }
        if (playing != null && openRoute != null) {
            val at = playing.positionMs
            add(
                act(BubbleGroup.CREA, Icons.Rounded.EditNote, "Annota al minuto", "a ${com.platinum20.dashboard.notes.NoteSeek.clock(at)}, toccandolo riparte da lì") {
                    com.platinum20.dashboard.notes.NoteSeek.init(context)
                    com.platinum20.dashboard.notes.NoteSeek.annotate(playing, at)?.let { openRoute(com.platinum20.dashboard.Routes.noteEdit(it)) }
                },
            )
        }
        if (seed != null) {
            add(
                act(BubbleGroup.CREA, Icons.Rounded.Radio, "Radio", "brani simili, poi resta in Musica") {
                    CaptureFeedback.tell(context, "Preparo la radio di ${item.title}", Icons.Rounded.Radio)
                    com.platinum20.dashboard.media.spotify.MadeStore.startRadio(context, seed) { error -> CaptureFeedback.tell(context, error) }
                },
            )
            add(act(BubbleGroup.METTI, Icons.Rounded.PlaylistAdd, "Aggiungi a una playlist", "una delle tue o una nuova") { PlaylistPicker.open(context, seed) })
        }
        if (openRoute != null) add(
            if (page != null) Slot.open { openRoute(page) }
            else Slot.play { openResumeItem(context, item, openRoute = openRoute) },
        )
        add(saveSlot(context, link))
        if (item.kind == ResumeKind.YOUTUBE || item.kind == ResumeKind.VIDEO) {
            add(Slot.share { runCatching { shareText(context, SavedLinks.shareText(link)) } })
        }
        if (remove != null) add(remove)
        else if (playlistId != null) playlistAway(context, playlistId, item)?.let(::add)
    }
    ActionBubble.show(head, *build().toTypedArray())
    // Se una playlist è tua si sa solo dopo averla letta: il fumetto si completa appena arriva la risposta.
    if (playlistId != null && PlaylistManage.infos[playlistId] == null) {
        bubbleScope.launch {
            PlaylistManage.load(playlistId)
            if (ActionBubble.head === head) ActionBubble.show(head, *build().toTypedArray())
        }
    }
}

/** Le righe dei tasti della pagina del media: Mi piace per un video, Preferita e sveglia per una radio, Segui per l'episodio. */
private fun pageRows(context: Context, media: ResumeItem): List<BubbleAction> = when (media.kind) {
    ResumeKind.YOUTUBE -> {
        val liked = com.platinum20.dashboard.media.youtube.YtFavorites.items.value.any { it.id == media.key }
        listOf(
            row(
                BubbleGroup.SEGNA,
                if (liked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbUpOffAlt,
                if (liked) "Ti piace" else "Mi piace",
                if (liked) "toccalo per toglierlo" else "fra i video che ti piacciono",
            ) {
                com.platinum20.dashboard.media.youtube.YtFavorites.toggle(
                    context,
                    com.platinum20.dashboard.media.youtube.YtVideo(media.key, media.title, media.subtitle, "", null, durationSec = (media.durationMs / 1000).toInt()),
                )
            },
        )
    }
    ResumeKind.RADIO -> media.station?.let { station ->
        val fav = com.platinum20.dashboard.media.radio.RadioStore.favorites.value.any { it.id == station.id }
        com.platinum20.dashboard.media.radio.RadioAlarm.init(context)
        val alarm = com.platinum20.dashboard.media.radio.RadioAlarm.settings.value
        val already = alarm.enabled && alarm.station?.id == station.id
        listOf(
            act(BubbleGroup.SEGNA, if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (fav) "Fra le preferite" else "Preferita", if (fav) "toccala per toglierla" else "fra le tue radio") {
                com.platinum20.dashboard.media.radio.RadioStore.toggleFavorite(station)
            },
            // Sveglia con questa radio (1.529): all'ora già scelta, Annulla rimette com'era.
            row(
                BubbleGroup.CAMBIA, Icons.Rounded.Alarm,
                if (already) "Ti sveglia questa radio" else "Svegliami con questa radio",
                (if (alarm.weekdaysOnly) "dal lunedì al venerdì alle " else "ogni giorno alle ") + alarm.timeLabel,
            ) {
                if (!already) {
                    val before = alarm
                    com.platinum20.dashboard.media.radio.RadioAlarm.update(context, alarm.copy(enabled = true, station = station))
                    CaptureFeedback.tell(context, "Sveglia alle ${alarm.timeLabel} con ${station.name}", Icons.Rounded.Alarm) {
                        com.platinum20.dashboard.media.radio.RadioAlarm.update(context, before)
                    }
                }
            },
        )
    }.orEmpty()
    ResumeKind.PODCAST -> media.feed.takeIf { it.isNotBlank() }?.let { feed ->
        val followed = com.platinum20.dashboard.media.podcast.PodcastStore.followed.value
        val podcast = followed.firstOrNull { it.feedUrl == feed }
            ?: com.platinum20.dashboard.media.podcast.Podcast(feed, media.subtitle, "", feed, media.image)
        val following = followed.any { it.feedUrl == feed }
        listOf(
            act(BubbleGroup.SEGNA, Icons.Rounded.AddCircleOutline, "Segui il podcast", if (following) "già lo segui: toccalo per smettere" else "i nuovi episodi in Musica") {
                com.platinum20.dashboard.media.podcast.PodcastStore.toggleFollow(podcast)
            },
        )
    }.orEmpty()
    else -> emptyList()
}

private fun playlistItem(id: String, item: ResumeItem) = com.platinum20.dashboard.media.spotify.SpItem(
    id, item.key, com.platinum20.dashboard.media.spotify.SpKind.PLAYLIST, item.title, item.subtitle, item.image,
)

/** Modifica e Riordina, appena si sa che la playlist è tua (1.512). */
private fun playlistRows(id: String, item: ResumeItem, openRoute: ((String) -> Unit)?): List<BubbleAction> {
    if (PlaylistManage.infos[id]?.second != true) return emptyList()
    val playlist = playlistItem(id, item)
    return listOf(
        act(BubbleGroup.CAMBIA, Icons.Outlined.Edit, "Modifica", "nome, descrizione, privata") { PlaylistManage.edit(id) },
        act(BubbleGroup.CAMBIA, Icons.Outlined.SwapVert, "Riordina e togli brani", "si apre la playlist") {
            PlaylistManage.arranging[id] = true
            openRoute?.invoke(com.platinum20.dashboard.Routes.spotifyItem(playlist.copy(title = PlaylistManage.title(playlist))))
        },
    )
}

/** ④ di una playlist: Elimina se è tua (sparisce anche da Spotify: due tocchi), Togli se la segui soltanto. */
private fun playlistAway(context: Context, id: String, item: ResumeItem): BubbleAction? {
    val playlist = playlistItem(id, item)
    val mine = PlaylistManage.infos[id]?.second == true
    val name = PlaylistManage.title(playlist)
    return when {
        mine -> Slot.delete(confirm = "Tocca ancora: sparisce da Spotify, anche sul PC") { PlaylistManage.delete(context, playlist, name) {} }
        com.platinum20.dashboard.media.spotify.SpotifyStore.inLibrary(id) -> Slot.remove { PlaylistManage.delete(context, playlist, name) {} }
        else -> null
    }
}

/**
 * Chi tiene premuta una cosa chiama [SavedLinks.offer] (o [showLinkBubble]): qui l'offerta diventa il fumetto unico.
 * Disegnato una volta in MainActivity, dentro la navigazione dell'app (serve per Apri e Vai a).
 */
@Composable
internal fun SaveToNotesLayer() {
    val offered = SavedLinks.offered
    val context = LocalContext.current
    val inApp = LocalInAppNav.current
    val haptic = rememberHaptic()
    LaunchedEffect(offered) {
        val link = offered ?: return@LaunchedEffect
        haptic(Haptic.LONG_PRESS)
        SavedLinks.dismiss()
        showLinkBubble(context, link, inApp?.openRoute)
    }
}
