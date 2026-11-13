package com.platinum20.dashboard.media.spotify

import org.json.JSONObject

/** Le regole pure per creare playlist e metterci brani (1.500, Antonio: "aggiungere i brani alle playlist e creare playlist"). */
object SpotifyPlaylists {
    /** Quante playlist entrano come chip nel fumetto: oltre, il fumetto diventerebbe una pagina. */
    const val MAX_CHIPS = 12

    /** Il nome più lungo che Spotify mostra bene; il resto si taglia. */
    const val MAX_NAME = 100

    /**
     * Da "me/playlists": solo quelle dove si può scrivere, cioè le tue ([myId] = proprietario) e le collaborative.
     * Senza [myId] (profilo non letto) non si indovina: meglio nessuna che una che poi risponde 403.
     */
    fun editable(json: JSONObject?, myId: String): List<SpItem> {
        val items = json?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val p = items.optJSONObject(i) ?: return@mapNotNull null
            val owner = p.optJSONObject("owner")?.optString("id").orEmpty()
            val mine = myId.isNotBlank() && owner == myId
            if (!mine && !p.optBoolean("collaborative")) null else SpotifyApi.parseItem(p)
        }
    }

    /** Il nome scritto: spazi in più tolti, tagliato a [MAX_NAME]; vuoto = non si crea. */
    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME).trim()

    /** Il brano c'è già nei brani letti della playlist ([known] = null se non li abbiamo ancora letti). */
    fun alreadyIn(known: List<SpItem>?, trackUri: String): Boolean = known.orEmpty().any { it.uri == trackUri }

    /** Le chip del fumetto: prima l'ultima dove hai messo un brano, poi l'ordine di Spotify. */
    fun ordered(playlists: List<SpItem>, lastUsed: String?): List<SpItem> {
        val last = playlists.firstOrNull { it.id == lastUsed } ?: return playlists.take(MAX_CHIPS)
        return (listOf(last) + playlists.filter { it.id != lastUsed }).take(MAX_CHIPS)
    }

    /** Quante playlist toccate si ricordano: bastano per la fila Musica. */
    const val MAX_TOUCHED = 50

    /**
     * Una playlist appena creata o riempita conta come ascoltata adesso (Antonio: "una playlist appena creata deve
     * finire all'inizio, anche se non l'ho mai ascoltata"). Tiene le [MAX_TOUCHED] più recenti.
     */
    fun touch(touched: Map<String, Long>, uri: String, at: Long): Map<String, Long> =
        (touched + (uri to at)).entries.sortedByDescending { it.value }.take(MAX_TOUCHED).associate { it.key to it.value }

    // --- Gestire le tue playlist (1.507, Antonio: "poter cancellare le playlist e comunque gestirle") ---

    /** La descrizione più lunga che Spotify accetta. */
    const val MAX_DESCRIPTION = 300

    /** Si può cambiare se è tua o collaborativa; senza il tuo id non si indovina (vedi [editable]). */
    fun canEdit(info: SpPlaylistInfo?, myId: String): Boolean =
        info != null && myId.isNotBlank() && (info.ownerId == myId || info.collaborative)

    /** Da "playlists/<id>": nome, descrizione (Spotify la manda con l'HTML codificato), privata o no, proprietario. */
    fun info(json: JSONObject?): SpPlaylistInfo? {
        json ?: return null
        val id = json.optString("id").ifBlank { return null }
        return SpPlaylistInfo(
            id = id,
            name = json.optString("name"),
            description = plainDescription(json.optString("description")),
            public = json.optBoolean("public", false),
            collaborative = json.optBoolean("collaborative", false),
            ownerId = json.optJSONObject("owner")?.optString("id").orEmpty(),
        )
    }

    /** "Rock &amp; roll &#x27;80" → "Rock & roll '80": senza tag, come la scriveresti tu. */
    fun plainDescription(raw: String): String {
        if (raw == "null") return ""
        var s = raw.replace(Regex("<[^>]*>"), "")
        s = Regex("&#x([0-9a-fA-F]+);").replace(s) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        s = Regex("&#([0-9]+);").replace(s) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&").trim()
    }

    /** La descrizione scritta: a capo e spazi doppi tolti (Spotify non tiene gli a capo), tagliata a [MAX_DESCRIPTION]. */
    fun cleanDescription(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_DESCRIPTION).trim()

    /** Cosa mandare a Spotify per cambiare i dettagli: solo quello che è cambiato; null = niente da fare. */
    fun changes(before: SpPlaylistInfo, name: String, description: String, public: Boolean): JSONObject? {
        val body = JSONObject()
        val n = cleanName(name)
        val d = cleanDescription(description)
        if (n.isNotEmpty() && n != before.name) body.put("name", n)
        if (d != before.description) body.put("description", d)
        if (public != before.public) body.put("public", public)
        return if (body.length() == 0) null else body
    }

    /**
     * Le chiavi delle righe: lo stesso brano può stare due volte nella playlist, e la lista vuole chiavi diverse.
     * La seconda volta diventa "uri#1", la terza "uri#2".
     */
    fun keys(tracks: List<SpItem>): List<String> {
        val seen = HashMap<String, Int>()
        return tracks.map { t ->
            val n = seen.getOrDefault(t.uri, 0)
            seen[t.uri] = n + 1
            if (n == 0) t.uri else "${t.uri}#$n"
        }
    }

    /** La riga in [from] spostata al posto [to], come la vedi mentre la trascini. */
    fun <T> moved(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Lo stesso spostamento detto a Spotify: "metti la riga [from] prima della riga insert_before", contata
     * sulla lista di prima. Scendendo si conta anche la riga stessa, quindi +1.
     */
    fun insertBefore(from: Int, to: Int): Int = if (to > from) to + 1 else to

    /** Togliere un brano lo toglie tutte le volte che c'è (Spotify lavora per brano, non per riga). */
    fun without(tracks: List<SpItem>, uri: String): List<SpItem> = tracks.filterNot { it.uri == uri }

    /** Per "Annulla": il brano torna dove era la prima volta. */
    fun restored(tracks: List<SpItem>, track: SpItem, at: Int): List<SpItem> =
        tracks.toMutableList().apply { add(at.coerceIn(0, size), track) }
}

/** I dettagli di una playlist che si possono cambiare, più il proprietario per sapere se si può. */
data class SpPlaylistInfo(
    val id: String,
    val name: String,
    val description: String,
    val public: Boolean,
    val collaborative: Boolean,
    val ownerId: String,
)
