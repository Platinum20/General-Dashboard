package com.platinum20.dashboard.media.youtube

/**
 * Le dirette (1.541, Antonio): nel player una barra sulla finestra che YouTube tiene indietro, con "In diretta"
 * che riporta al bordo live; in Media "In diretta" per quelle in corso e "Diretta terminata" per quelle finite.
 */
object YtLive {
    /** Entro questo ritardo dal bordo live si è "in diretta" (HLS ha qualche segmento di margine). */
    const val EDGE_MS = 20_000L

    /** [offsetMs] = quanto si è indietro rispetto al live; negativo = non si sa, e si considera in diretta. */
    fun atEdge(offsetMs: Long): Boolean = offsetMs < 0 || offsetMs <= EDGE_MS

    /** "-1:23" / "-1:02:03": quanto si è indietro rispetto alla diretta. */
    fun behind(offsetMs: Long): String {
        val s = (offsetMs.coerceAtLeast(0) / 1000)
        val h = s / 3600
        return "-" + if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private val endedText = Regex(
        "(?i)^(streamed|trasmess[oa])|\\b(live streaming|in streaming|in diretta)\\b.*(\\bfa|ago)\$",
    )

    /** La data scritta da YouTube dice che era una diretta: "Streamed 2 hours ago", "Trasmesso in live streaming 3 giorni fa". */
    fun ended(uploadedText: String): Boolean = uploadedText.isNotBlank() && endedText.containsMatchIn(uploadedText.trim())

    const val LIVE_BADGE = "● IN DIRETTA"
    const val ENDED_BADGE = "Diretta terminata"

    /** Il segno sulla copertina di una diretta finita: con la durata se si sa. */
    fun endedBadge(duration: String?): String = if (duration.isNullOrBlank()) ENDED_BADGE else "$ENDED_BADGE · $duration"
}
