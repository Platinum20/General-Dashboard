package com.platinum20.dashboard.search

/** Le regole dei risultati nel + (idea 11): pure, provate nei test. */
object CaptureMatch {
    /** Da quante lettere si cerca: con una o due compare di tutto e la lista salterebbe a ogni tasto. */
    const val FROM = 3

    /** Quanti risultati al più: il riquadro per scrivere resta il protagonista. */
    const val MAX = 3

    fun asks(query: String): Boolean = query.trim().length >= FROM

    /** I [MAX] che valgono di più (a pari merito vince l'ordine delle fonti), solo quelli trovati davvero. */
    fun <T> top(list: List<T>, score: (T) -> Int): List<T> =
        list.filter { score(it) > 0 }.sortedByDescending(score).take(MAX)
}
