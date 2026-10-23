package com.platinum20.dashboard.widget

/** Le righe di testo del widget, separate da RemoteViews per poterle provare. */
object WidgetRows {
    /** Sotto la temperatura: "Sereno · 22°/10° · pioggia 40%" (la pioggia solo dal 30%). */
    fun weatherLine(description: String, max: String?, min: String?, rainChance: Int?): String =
        description +
            (if (max != null && min != null) " · $max/$min" else "") +
            (rainChance?.takeIf { it >= 30 }?.let { " · pioggia $it%" } ?: "")

    /** Quante notizie stanno nel widget: tre se non c'è altro, due se sopra ci sono le righe di Adesso o i promemoria. */
    fun newsCount(briefLines: Int, hasTasks: Boolean): Int = if (briefLines == 0 && !hasTasks) 3 else 2
}
