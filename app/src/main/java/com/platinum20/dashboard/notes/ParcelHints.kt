package com.platinum20.dashboard.notes

/** Un modo per aggiungere pacchi, mostrato sotto una lista corta così la scheda non resta mezza vuota. */
enum class ParcelHint(val title: String, val subtitle: String) {
    PASTE("Incolla un codice di tracciamento", "Lo prendo dagli appunti e riconosco il corriere"),
    AUTOFILL("Prendili dalle notifiche", "Compila da solo aggiunge i pacchi dalle mail e dagli SMS di spedizione"),
    SHARE("Condividi la mail di spedizione", "Da Gmail: Condividi › Salva in Canvas, e il codice si compila da solo"),
}

object ParcelHints {
    /** Da quanti pacchi in arrivo in su i suggerimenti spariscono. */
    const val UNTIL = 3

    fun suggest(incoming: Int, autoFillOn: Boolean): List<ParcelHint> {
        if (incoming >= UNTIL) return emptyList()
        return ParcelHint.entries.filter { it != ParcelHint.AUTOFILL || !autoFillOn }
    }

    /**
     * Il codice da mettere nel dialogo partendo dagli appunti: un codice riconosciuto dentro un testo
     * più lungo (una mail copiata), oppure gli appunti stessi se hanno la forma di un codice; altrimenti vuoto.
     */
    fun codeFrom(clip: String?): String {
        val text = clip?.trim().orEmpty()
        if (text.isEmpty()) return ""
        Carrier.findCode(text)?.let { return it }
        val compact = text.replace(" ", "").uppercase()
        return if (Regex("[A-Z0-9]{8,34}").matches(compact) && compact.any { it.isDigit() }) compact else ""
    }
}
