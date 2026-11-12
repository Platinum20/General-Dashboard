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
}
