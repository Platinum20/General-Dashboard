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
import com.platinum20.dashboard.media.youtube.player.YtResolver
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

    /**
     * Qualunque cosa di Spotify (1.490, Antonio: "qualunque cosa di media si possa salvare"): un brano si riapre fermo,
     * un album, una playlist, un artista o un podcast nella sua pagina.
     */
    fun spotify(item: SpItem): SavedLink? = item.takeIf { it.uri.isNotBlank() }?.let {
        SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, it.uri, it.title, it.subtitle, it.image, durationMs = it.durationMs, at = System.currentTimeMillis()))
    }

    /** Un mix fatto da Canvas: si riapre nella sua pagina con Riproduci. */
    fun mix(id: String, title: String, subtitle: String, image: String): SavedLink =
        SavedLink.Media(ResumeItem(ResumeKind.SPOTIFY, MIX_PREFIX + id, title, subtitle, image, at = System.currentTimeMillis()))

    private const val MIX_PREFIX = "mix:"

    /**
     * Dove si apre una cosa di Spotify salvata che non è un brano: la pagina del mix, dell'album, della playlist,
     * dell'artista o del podcast; null per i brani e gli episodi, che si aprono fermi nel player.
     */
    fun pageRoute(item: ResumeItem): String? {
        if (item.kind != ResumeKind.SPOTIFY) return null
        if (item.key.startsWith(MIX_PREFIX)) return Routes.mix(item.key.removePrefix(MIX_PREFIX))
        val parts = item.key.split(":")
        if (parts.size != 3 || parts[0] != "spotify") return null
        val kind = when (parts[1]) {
            "album" -> SpKind.ALBUM
            "playlist" -> SpKind.PLAYLIST
            "artist" -> SpKind.ARTIST
            "show" -> SpKind.SHOW
            else -> return null
        }
        return Routes.spotifyItem(SpItem(parts[2], item.key, kind, item.title, item.subtitle, item.image))
    }

    /**
     * Una cosa salvata che entra nella coda così com'è: video di YouTube, brani, episodi, radio. Album, playlist,
     * mix e artisti si aprono nella loro pagina (con Riproduci); i video del telefono restano fuori come nel fumetto.
     */
    fun queueable(item: ResumeItem): Boolean = item.kind != ResumeKind.VIDEO && pageRoute(item) == null

    fun youtube(video: YtVideo): SavedLink = SavedLink.Media(
        ResumeItem(
            ResumeKind.YOUTUBE, video.id, video.title, video.channel, "https://i.ytimg.com/vi/${video.id}/hqdefault.jpg",
            at = System.currentTimeMillis(),
        ),
    )

    fun radio(station: RadioStation): SavedLink = SavedLink.Media(
        ResumeItem(ResumeKind.RADIO, station.id, station.name, "Radio", station.logo, at = System.currentTimeMillis(), station = station),
    )

    /**
     * I video di YouTube che hai nelle note, dal più recente: quelli salvati col fumetto e i link
     * youtube.com/youtu.be incollati o condivisi. È la lista "Più tardi" della pagina Video.
     */
    fun youtubeVideos(notes: List<Note>): List<YtVideo> = notes
        .sortedByDescending { it.createdAt }
        .mapNotNull { note ->
            val saved = (find(note.text) as? SavedLink.Media)?.item?.takeIf { it.kind == ResumeKind.YOUTUBE }
            if (saved != null) return@mapNotNull YtVideo(saved.key, saved.title, saved.subtitle, "", null)
            val url = Regex("""https?://\S*(?:youtube\.com|youtu\.be)/\S+""").find(note.text)?.value ?: return@mapNotNull null
            val id = YtResolver.videoId(url) ?: return@mapNotNull null
            val title = note.title.takeUnless { it.contains(url) || it.startsWith("http") } ?: "Video di YouTube"
            YtVideo(id, title, "", "", null)
        }
        .distinctBy { it.id }

    /** Una cosa da guardare o ascoltare "per dopo", con la nota che la tiene. */
    data class Later(val note: Note, val item: ResumeItem)

    /** Un articolo salvato da leggere, con la nota che lo tiene. */
    data class ToRead(val note: Note, val article: SavedLink.Article)

    private val youtubeUrl = Regex("""https?://\S*(?:youtube\.com|youtu\.be)/\S+""")

    /**
     * Un link di YouTube incollato da solo (al massimo con una riga di titolo): è un video da guardare, non una
     * nota. Un testo lungo che cita un video resta una nota nel calendario.
     */
    internal fun pastedYoutube(note: Note): ResumeItem? {
        if (note.text.lineSequence().count { it.isNotBlank() } > 2) return null
        val url = youtubeUrl.find(note.text)?.value ?: return null
        val id = YtResolver.videoId(url) ?: return null
        val title = note.title.takeUnless { it.contains(url) || it.startsWith("http") } ?: "Video di YouTube"
        return ResumeItem(ResumeKind.YOUTUBE, id, title, "", "https://i.ytimg.com/vi/$id/hqdefault.jpg", at = note.createdAt)
    }

    /**
     * Video, brani, radio e podcast salvati per dopo e i link di YouTube incollati, dal più recente: "Salvati" in Media
     * (1.490, Antonio: ognuno nella sua sezione). Restano note (sincronia con YouTube, Zaino del treno).
     */
    fun later(notes: List<Note>): List<Later> = notes
        .sortedByDescending { it.createdAt }
        .mapNotNull { note -> ((find(note.text) as? SavedLink.Media)?.item ?: pastedYoutube(note))?.let { Later(note, it) } }
        .distinctBy { "${it.item.kind}:${it.item.key}" }

    /** Le cose salvate per dopo da vedere (video) o da ascoltare (il resto): Media le divide nelle sue due file (1.490). */
    fun isVideo(item: ResumeItem): Boolean = item.kind == ResumeKind.YOUTUBE || item.kind == ResumeKind.VIDEO

    /** Gli articoli salvati da leggere, dal più recente: "Salvati" in cima a News (1.490). */
    fun toRead(notes: List<Note>): List<ToRead> = notes
        .sortedByDescending { it.createdAt }
        .mapNotNull { note -> (find(note.text) as? SavedLink.Article)?.let { ToRead(note, it) } }
        .distinctBy { identity(it.article) }

    /** Le note salvate per dopo: stanno in Media o in News, non nella card Per dopo né nel calendario. */
    fun livesElsewhere(note: Note): Boolean = find(note.text) != null || pastedYoutube(note) != null

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
                "Tolto: ${link.title}",
                undo = { removed.forEach(NotesStore::restoreNote) },
                icon = Icons.Rounded.BookmarkRemove,
            ),
        )
    }

    /** Le parole del tasto: "Leggi dopo" per gli articoli, "Salva per più tardi" per il resto. */
    fun saveLabel(link: SavedLink, saved: Boolean): String = when {
        saved -> "Togli dai salvati"
        link is SavedLink.Article -> "Leggi dopo"
        else -> "Salva per più tardi"
    }

    /** Toglie una cosa per dopo (la sua nota), con "Annulla" e senza pallino. */
    fun forget(note: Note, message: String) {
        NotesStore.deleteNote(note.id)
        CaptureFeedback.show(
            CaptureNotice(CaptureMode.NOTE, message, undo = { NotesStore.restoreNote(note) }, icon = Icons.Rounded.BookmarkRemove, fly = false),
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
                "Salvato per dopo: ${link.title}",
                undo = { NotesStore.deleteNote(id) },
                icon = Icons.Rounded.BookmarkAdded,
                // Il pallino vola dove la cosa sta (1.490): News per gli articoli, Media per il resto.
                target = if (link is SavedLink.Article) CaptureTargets.NEWS_TAB else CaptureTargets.MEDIA_TAB,
            ),
        )
    }

    /** Riapre la cosa salvata: i media fermi, in attesa del play. */
    fun open(context: Context, link: SavedLink, openRoute: (String) -> Unit) {
        when (link) {
            is SavedLink.Media -> pageRoute(link.item)?.let(openRoute)
                ?: openResumeItem(context, link.item, autoplay = false, openRoute = openRoute)
            is SavedLink.Article -> openRoute(Routes.reader(link.url, link.source, link.title))
        }
    }
}
