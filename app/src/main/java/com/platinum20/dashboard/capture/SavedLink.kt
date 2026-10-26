package com.platinum20.dashboard.capture

import android.content.Context
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.media.ResumeItem
import com.platinum20.dashboard.media.ResumeKind
import com.platinum20.dashboard.media.radio.RadioStation
import com.platinum20.dashboard.media.spotify.SpItem
import com.platinum20.dashboard.media.spotify.SpKind
import com.platinum20.dashboard.media.youtube.YtVideo
import com.platinum20.dashboard.notes.Note
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.ui.screens.openResumeItem
import org.json.JSONObject

/** Una cosa dell'app salvata in una nota: un brano, un video, una radio, un podcast o un articolo. */
sealed interface SavedLink {
    val title: String
    val subtitle: String
    /** L'anteprima nelle note: copertina, miniatura del video o foto dell'articolo; vuota se non c'è. */
    val image: String

    data class Media(val item: ResumeItem) : SavedLink {
        override val title get() = item.title
        override val subtitle get() = item.subtitle
        override val image get() = item.image
    }

    data class Article(
        val url: String,
        val source: String,
        override val title: String,
        override val image: String = "",
    ) : SavedLink {
        override val subtitle get() = source
    }
}

/**
 * "Salva nelle note": tenendo premuta una cosa compare il fumetto per salvarla. La nota ha il titolo
 * in prima riga e, in fondo, un indirizzo canvas://open che la riapre: toccando la nota il brano o
 * il video si apre fermo in attesa del play, l'articolo nel lettore.
 */
object SavedLinks {
    private const val PREFIX = "canvas://open"

    /** La cosa offerta in questo momento, mostrata sopra la barra; null se il fumetto è chiuso. */
    var offered by mutableStateOf<SavedLink?>(null)
        private set

    fun offer(link: SavedLink?) {
        offered = link
    }

    fun dismiss() {
        offered = null
    }

    fun encode(link: SavedLink): String = when (link) {
        // Una canzone o un video salvati si riascoltano da capo; un podcast dal punto lasciato.
        is SavedLink.Media -> Uri.Builder().scheme("canvas").authority("open")
            .appendQueryParameter("kind", "media")
            .appendQueryParameter("item", link.item.let { if (it.kind == ResumeKind.PODCAST) it else it.copy(positionMs = 0) }.toJson().toString())
            .build().toString()
        is SavedLink.Article -> Uri.Builder().scheme("canvas").authority("open")
            .appendQueryParameter("kind", "article")
            .appendQueryParameter("url", link.url)
            .appendQueryParameter("source", link.source)
            .appendQueryParameter("title", link.title)
            .apply { if (link.image.isNotBlank()) appendQueryParameter("image", link.image) }
            .build().toString()
    }

    /** La cosa salvata nel testo di una nota, se c'è. */
    fun find(text: String): SavedLink? {
        val line = text.lineSequence().map { it.trim() }.lastOrNull { it.startsWith(PREFIX) } ?: return null
        return runCatching {
            val uri = Uri.parse(line)
            when (uri.getQueryParameter("kind")) {
                "media" -> SavedLink.Media(ResumeItem.fromJson(JSONObject(uri.getQueryParameter("item")!!)))
                "article" -> SavedLink.Article(
                    url = uri.getQueryParameter("url")!!,
                    source = uri.getQueryParameter("source").orEmpty(),
                    title = uri.getQueryParameter("title").orEmpty(),
                    image = uri.getQueryParameter("image").orEmpty(),
                )
                else -> null
            }
        }.getOrNull()
    }

    /** Un brano di Spotify (album e playlist non si riaprono fermi: solo i brani). */
    fun spotify(item: SpItem): SavedLink? = item.takeIf { it.kind == SpKind.TRACK }?.let {
        SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image, at = System.currentTimeMillis()))
    }

    fun youtube(video: YtVideo): SavedLink = SavedLink.Media(
        ResumeItem(
            ResumeKind.YOUTUBE, video.id, video.title, video.channel, "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
            at = System.currentTimeMillis(),
        ),
    )

    fun radio(station: RadioStation): SavedLink = SavedLink.Media(
        ResumeItem(ResumeKind.RADIO, station.id, station.name, "Radio", station.logo, at = System.currentTimeMillis(), station = station),
    )

    /** Le righe di una nota senza l'indirizzo che la riapre (per le anteprime). */
    fun withoutLink(text: String): String =
        text.lineSequence().filterNot { it.trim().startsWith(PREFIX) }.joinToString("\n").trim()

    fun noteText(link: SavedLink): String = buildString {
        appendLine(link.title.ifBlank { "Salvato da Canvas" })
        if (link.subtitle.isNotBlank()) appendLine(link.subtitle)
        append(encode(link))
    }

    /** Chi è la cosa salvata: l'indirizzo dell'articolo, o il tipo e la chiave del media (non il punto a cui si era). */
    fun identity(link: SavedLink): String = when (link) {
        is SavedLink.Media -> "media:${link.item.kind}:${link.item.key}"
        is SavedLink.Article -> "article:${link.url.trim().trimEnd('/')}"
    }

    /** Le note che contengono già questa cosa: se ce n'è una, il tasto diventa "Rimuovi dalle note". */
    fun notesWith(notes: List<Note>, link: SavedLink): List<Note> {
        val id = identity(link)
        return notes.filter { note -> find(note.text)?.let(::identity) == id }
    }

    /** Il testo da condividere: titolo e un indirizzo che si apre anche fuori da Canvas, quando c'è. */
    fun shareText(link: SavedLink): String {
        val url = when (link) {
            is SavedLink.Article -> link.url
            is SavedLink.Media -> when (link.item.kind) {
                ResumeKind.YOUTUBE -> "https://youtu.be/${link.item.key}"
                ResumeKind.MUSIC -> "https://music.youtube.com/watch?v=${link.item.key}"
                ResumeKind.SPOTIFY -> link.item.key.removePrefix("spotify:track:")
                    .takeIf { it != link.item.key }?.let { "https://open.spotify.com/track/$it" }.orEmpty()
                else -> ""
            }
        }
        return listOf(link.title, link.subtitle.takeIf { link is SavedLink.Media }.orEmpty(), url)
            .filter { it.isNotBlank() }.joinToString("\n")
    }

    /** Toglie dalle note ogni copia della cosa, con "Annulla". */
    fun remove(context: Context, link: SavedLink) {
        offered = null
        NotesStore.init(context)
        val removed = notesWith(NotesStore.data.value.notes, link)
        if (removed.isEmpty()) return
        removed.forEach { NotesStore.deleteNote(it.id) }
        CaptureFeedback.show(
            CaptureNotice(
                CaptureMode.NOTE,
                "Tolto dalle note: ${link.title}",
                undo = { removed.forEach(NotesStore::restoreNote) },
                icon = Icons.Rounded.BookmarkRemove,
            ),
        )
    }

    /** Crea la nota e fa volare il pallino alle Note, con "Annulla". Se c'è già, non la ripete. */
    fun save(context: Context, link: SavedLink) {
        offered = null
        NotesStore.init(context)
        if (notesWith(NotesStore.data.value.notes, link).isNotEmpty()) return
        val id = NotesStore.saveNote(null, noteText(link)) ?: return
        CaptureFeedback.show(
            CaptureNotice(
                CaptureMode.NOTE,
                "Salvato nelle note: ${link.title}",
                undo = { NotesStore.deleteNote(id) },
                icon = Icons.Rounded.BookmarkAdded,
            ),
        )
    }

    /** Riapre la cosa salvata: i media fermi, in attesa del play. */
    fun open(context: Context, link: SavedLink, openRoute: (String) -> Unit) {
        when (link) {
            is SavedLink.Media -> openResumeItem(context, link.item, autoplay = false, openRoute = openRoute)
            is SavedLink.Article -> openRoute(Routes.reader(link.url, link.source, link.title))
        }
    }
}
