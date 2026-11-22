package com.platinum20.dashboard.notes

/**
 * La tabella semplice (1.588): righe "| Nome | Scarpe | Maglia |" una sotto l'altra, la prima fa da intestazione.
 * Nel testo restano così (si copiano ovunque); l'editor le nasconde e ci disegna sopra le caselle, in cui si scrive
 * direttamente come su Notion (1.590).
 */
object NoteTable {
    private val rowRx = Regex("""^\|.*\|\s*$""")

    /** Una nuova tabella (1.590): due per due, vuota; si comincia a scrivere nella prima casella. */
    val NEW: List<List<String>> = listOf(listOf("", ""), listOf("", ""))

    fun isRow(line: String): Boolean = rowRx.matches(line)

    /** Le caselle di una riga, senza gli spazi intorno. */
    fun cells(line: String): List<String> = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /** La riga di testo per [cells] ("|" dentro una casella diventa "/"). */
    fun row(cells: List<String>): String = cells.joinToString(" | ", "| ", " |") { it.replace('|', '/').replace('\n', ' ').trim() }

    /** Le righe della tabella che contiene la riga [index] (righe di tabella una dopo l'altra), o null. */
    fun blockAt(text: String, index: Int): IntRange? {
        val lines = NoteLines.parse(text)
        if (lines.getOrNull(index)?.kind != NoteLines.Kind.TABLE) return null
        var a = index
        while (a > 0 && lines[a - 1].kind == NoteLines.Kind.TABLE) a--
        var b = index
        while (b < lines.size - 1 && lines[b + 1].kind == NoteLines.Kind.TABLE) b++
        return a..b
    }

    /** Le caselle di tutta la tabella, righe pareggiate allo stesso numero di colonne. */
    fun grid(text: String, block: IntRange): List<List<String>> {
        val lines = NoteLines.parse(text)
        val rows = block.map { cells(text.substring(lines[it].start, lines[it].end)) }
        val cols = rows.maxOf { it.size }
        return rows.map { it + List(cols - it.size) { "" } }
    }

    /** Il testo con la tabella delle righe [block] sostituita da [grid]; una griglia vuota toglie la tabella. */
    fun replace(text: String, block: IntRange, grid: List<List<String>>): String {
        val all = text.split('\n')
        val rows = grid.filter { it.isNotEmpty() }.map { row(it) }
        return (all.subList(0, block.first) + rows + all.subList(block.last + 1, all.size)).joinToString("\n")
    }

    /** Una tabella nuova sotto la riga di [offset] (o al suo posto, se è vuota); restituisce testo e riga della tabella. */
    fun insert(text: String, offset: Int): Pair<String, Int> {
        val l = NoteLines.lineAt(text, offset.coerceIn(0, text.length))
        val rows = NEW.joinToString("\n") { row(it) }
        return if (text.substring(l.start, l.end).isBlank()) {
            (text.substring(0, l.start) + rows + text.substring(l.end)) to l.index
        } else {
            (text.substring(0, l.end) + "\n" + rows + text.substring(l.end)) to l.index + 1
        }
    }

    /** La casella [r],[c] diventa [value] (senza "|" e a capo, che romperebbero la riga). */
    fun setCell(grid: List<List<String>>, r: Int, c: Int, value: String): List<List<String>> {
        val v = value.replace('|', '/').replace('\n', ' ')
        return grid.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, x -> if (j == c) v else x } else row }
    }

    /** Una riga vuota al posto [at] (0 = in cima). */
    fun addRow(grid: List<List<String>>, at: Int): List<List<String>> {
        val cols = grid.maxOfOrNull { it.size } ?: 1
        return grid.toMutableList().apply { add(at.coerceIn(0, size), List(cols) { "" }) }
    }

    /** Una colonna vuota al posto [at] (0 = a sinistra) in ogni riga. */
    fun addColumn(grid: List<List<String>>, at: Int): List<List<String>> =
        grid.map { row -> row.toMutableList().apply { add(at.coerceIn(0, size), "") } }

    /** Senza la riga [at]; senza righe la tabella se ne va. */
    fun dropRow(grid: List<List<String>>, at: Int): List<List<String>> = grid.filterIndexed { i, _ -> i != at }

    /** Senza la colonna [at]; senza colonne la tabella se ne va. */
    fun dropColumn(grid: List<List<String>>, at: Int): List<List<String>> =
        grid.map { row -> row.filterIndexed { j, _ -> j != at } }.let { g -> if (g.all { it.isEmpty() }) emptyList() else g }
}
