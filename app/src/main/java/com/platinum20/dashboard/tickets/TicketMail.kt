package com.platinum20.dashboard.tickets

import com.platinum20.dashboard.calendar.Trips
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Da un evento alla mail col biglietto, e dalla mail al QR: logica pura, per i test.
 * Gli eventi che Google crea dalle mail (treni, concerti, voli) non portano il codice da mostrare al controllo:
 * il QR sta nella mail, dentro il PDF allegato o come immagine.
 */
object TicketMail {
    private val day = DateTimeFormatter.ofPattern("yyyy/MM/dd")

    /** L'evento è nato da una mail (Google lo scrive nelle note). */
    fun fromMail(description: String): Boolean =
        description.contains("creato a partire da un'email", ignoreCase = true) ||
            description.contains("creato a partire da un’email", ignoreCase = true) ||
            description.contains("created from an email", ignoreCase = true)

    /**
     * La ricerca Gmail della mail col biglietto, null se l'evento non ha un biglietto da cercare.
     * Il PNR basta da solo; senza, numero del treno o destinazione con i mittenti dei treni; per gli altri eventi
     * nati da una mail, il titolo. Sempre nei mesi prima del giorno dell'evento.
     */
    fun query(title: String, description: String, begin: Long, zone: ZoneId): String? {
        val date = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
        val window = " " + window(date)
        val details = Trips.details(title, description).toMap()
        details["PNR"]?.let { return "\"$it\"$window" }
        val destination = Trips.destination(title)
        if (destination != null) {
            val number = details["Treno"]?.let { Regex("\\d+").find(it)?.value }
            val what = number ?: "\"${destination.replace("\"", "")}\""
            return "$what $SENDERS$window"
        }
        if (!fromMail(description)) return null
        val clean = title.replace("\"", "").trim().take(60)
        if (clean.isBlank()) return null
        return "\"$clean\"$window"
    }

    /** Otto mesi prima (i biglietti si comprano presto), fino al giorno dopo l'evento. */
    fun window(date: LocalDate): String = "after:${date.minusDays(240).format(day)} before:${date.plusDays(2).format(day)}"

    /** Chi manda i biglietti dei treni, nella sintassi Gmail "uno o l'altro". */
    const val SENDERS = "{trenitalia italo italotreno trenord frecciarossa biglietto}"

    private val img = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val codeWords = Regex("""qr|barcode|aztec|codice|ticket|biglietto""", RegexOption.IGNORE_CASE)

    /** Le immagini remote dell'HTML che sembrano il codice del biglietto (alcuni mittenti non lo allegano). */
    fun codeImageUrls(html: String): List<String> = img.findAll(html)
        .map { it.groupValues[1].replace("&amp;", "&") }
        .filter { it.startsWith("https://") && codeWords.containsMatchIn(it) }
        .distinct()
        .take(6)
        .toList()

    /**
     * Il riquadro da ritagliare attorno a un codice trovato nella pagina: un bordo bianco del 10% (serve ai
     * lettori ai tornelli), dentro l'immagine. [left, top, right, bottom].
     */
    fun crop(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): IntArray {
        val pad = (maxOf(right - left, bottom - top) * 0.10f).toInt()
        return intArrayOf(
            (left - pad).coerceAtLeast(0),
            (top - pad).coerceAtLeast(0),
            (right + pad).coerceAtMost(width),
            (bottom + pad).coerceAtMost(height),
        )
    }
}
