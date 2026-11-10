package com.platinum20.dashboard.notes

/** Una riga letta dalla foto, con la sua posizione verticale (pixel della foto). */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int)

/** Il testo letto da una foto, rimesso in righe. Puro: la foto e il riconoscimento stanno fuori. */
object OcrRows {
    /**
     * Mette insieme le righe che stanno alla stessa altezza (il riconoscimento separa spesso le colonne),
     * da sinistra a destra, dall'alto in basso.
     */
    fun rows(lines: List<OcrLine>): List<String> {
        val out = mutableListOf<MutableList<OcrLine>>()
        lines.sortedBy { it.top }.forEach { line ->
            val center = (line.top + line.bottom) / 2
            val row = out.lastOrNull()?.takeIf { r -> r.any { center in it.top..it.bottom } }
            if (row != null) row += line else out += mutableListOf(line)
        }
        return out.map { r -> r.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
