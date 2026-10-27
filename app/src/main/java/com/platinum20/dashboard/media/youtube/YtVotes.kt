package com.platinum20.dashboard.media.youtube

import org.json.JSONObject

/** Mi piace e Non mi piace di un video secondo Return YouTube Dislike (stima pubblica). */
data class YtVotes(val likes: Long, val dislikes: Long) {
    /** Percentuale di Mi piace sul totale dei voti, null se non c'è nessun voto. */
    fun ratio(): Int? {
        val total = likes.coerceAtLeast(0) + dislikes.coerceAtLeast(0)
        if (total <= 0) return null
        return Math.round(likes.coerceAtLeast(0) * 100.0 / total).toInt()
    }

    companion object {
        /** Risposta di returnyoutubedislikeapi.com/votes: i campi mancanti valgono -1. */
        fun parse(json: String): YtVotes {
            val o = JSONObject(json)
            return YtVotes(o.optLong("likes", -1), o.optLong("dislikes", -1))
        }
    }
}
