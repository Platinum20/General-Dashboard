package com.platinum20.dashboard.notes

import android.net.Uri
import java.time.LocalDate

/** Un pacco in arrivo: cosa è, il codice di tracciamento e quando dovrebbe arrivare. */
data class Parcel(
    val id: Long,
    val title: String,
    val code: String = "",
    val expected: LocalDate? = null,
    val deliveredOn: LocalDate? = null,
) {
    val carrier: Carrier get() = Carrier.detect(code)
    val delivered: Boolean get() = deliveredOn != null

    /** Pagina di tracciamento del corriere; se il corriere non si riconosce, un tracker universale. */
    fun trackingUrl(): String? {
        if (code.isBlank()) return null
        return carrier.url.replace("{code}", Uri.encode(code))
    }
}

enum class Carrier(val label: String, val url: String) {
    AMAZON("Amazon", "https://www.amazon.it/gp/css/order-history"),
    UPS("UPS", "https://www.ups.com/track?loc=it_IT&tracknum={code}"),
    DHL("DHL", "https://www.dhl.com/it-it/home/tracking.html?tracking-id={code}&submit=1"),
    POSTE("Poste", "https://www.poste.it/cerca/index.html#/risultati-spedizioni/{code}"),
    GLS("GLS", "https://gls-group.com/IT/it/servizi-online/ricerca-spedizioni/?match={code}"),
    BRT("BRT", "https://vas.brt.it/vas/sped_det_show.hsm?Nspediz={code}"),
    OTHER("Corriere", "https://parcelsapp.com/it/tracking/{code}");

    companion object {
        /** Riconosce il corriere dalla forma del codice, quando è inconfondibile. */
        fun detect(code: String): Carrier {
            val c = code.trim().uppercase()
            return when {
                c.isEmpty() -> OTHER
                c.startsWith("TBA") || c.startsWith("AMZ") -> AMAZON
                c.startsWith("1Z") && c.length == 18 -> UPS
                Regex("[A-Z]{2}\\d{9}IT").matches(c) -> POSTE
                Regex("JJD\\d+|JVGL\\d+").matches(c) -> DHL
                else -> OTHER
            }
        }

        /** Il primo codice di tracciamento in un testo (es. una mail condivisa), se c'è. */
        fun findCode(text: String): String? {
            val patterns = listOf(
                "TBA\\d{9,12}", "1Z[0-9A-Z]{16}", "[A-Z]{2}\\d{9}IT", "JJD\\d{10,}", "JVGL\\d{8,}",
            )
            val upper = text.uppercase()
            return patterns.firstNotNullOfOrNull { Regex("(?<![0-9A-Z])$it(?![0-9A-Z])").find(upper)?.value }
        }
    }
}
