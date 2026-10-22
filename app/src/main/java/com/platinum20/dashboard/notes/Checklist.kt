package com.platinum20.dashboard.notes

/**
 * Liste di spunta dentro le note libere. Restano testo semplice ("[ ] latte", "[x] pane",
 * anche "- uova" conta come voce da fare), così le note vecchie e quelle condivise si leggono ovunque.
 * Funzioni pure: si provano senza telefono.
 */
object Checklist {
    sealed interface Line {
        /** Posizione della riga nel testo intero (la prima, il titolo, è 0). */
        val index: Int
    }
    data class Item(override val index: Int, val text: String, val done: Boolean) : Line
    data class Plain(override val index: Int, val text: String) : Line

    private val CHECKED = Regex("""^\s*\[[xX]]\s?(.*)$""")
    private val UNCHECKED = Regex("""^\s*(?:\[ ?]|[-*•])\s(.*)$""")

    fun parseLine(index: Int, line: String): Line {
        CHECKED.matchEntire(line)?.let { return Item(index, it.groupValues[1].trim(), true) }
        UNCHECKED.matchEntire(line)?.let { return Item(index, it.groupValues[1].trim(), false) }
        return Plain(index, line)
    }

    /** Le righe dopo il titolo. */
    fun lines(text: String): List<Line> =
        text.lines().mapIndexed(::parseLine).drop(1)

    fun hasItems(text: String): Boolean = lines(text).any { it is Item }

    /** "3 di 7": voci fatte e voci totali, null se la nota non è una lista. */
    fun progress(text: String): Pair<Int, Int>? {
        val items = lines(text).filterIsInstance<Item>()
        if (items.isEmpty()) return null
        return items.count { it.done } to items.size
    }

    fun progressLabel(text: String): String? = progress(text)?.let { (done, total) ->
        if (done == total) "Tutto fatto · $total di $total" else "$done di $total fatti"
    }

    /** Spunta o toglie la spunta alla riga [index]; le altre righe restano identiche. */
    fun toggle(text: String, index: Int): String {
        val all = text.lines().toMutableList()
        val item = all.getOrNull(index)?.let { parseLine(index, it) } as? Item ?: return text
        all[index] = format(item.text, !item.done)
        return all.joinToString("\n")
    }

    /** Aggiunge una voce da fare in fondo. */
    fun add(text: String, item: String): String {
        val clean = item.trim()
        if (clean.isEmpty()) return text
        val base = text.trimEnd('\n', ' ')
        // Una lista senza titolo: la nuova voce non deve diventare il titolo.
        return if (base.isEmpty()) "Lista\n${format(clean, false)}" else "$base\n${format(clean, false)}"
    }

    /** Il pulsante "Lista": ogni riga scritta dopo il titolo diventa una voce da spuntare. */
    fun toList(text: String): String {
        val all = text.lines()
        if (all.size < 2) return if (text.isBlank()) "Lista\n" else text
        return (listOf(all.first()) + all.drop(1).mapIndexed { i, line ->
            when (val parsed = parseLine(i + 1, line)) {
                is Item -> format(parsed.text, parsed.done)
                is Plain -> if (line.isBlank()) line else format(line.trim(), false)
            }
        }).joinToString("\n")
    }

    /** Il testo da mostrare nella lista delle note, con i segni al posto delle parentesi. */
    fun preview(body: String): String = body.lines().joinToString("\n") { line ->
        when (val parsed = parseLine(1, line)) {
            is Item -> (if (parsed.done) "✓ " else "○ ") + parsed.text
            is Plain -> line
        }
    }

    private fun format(text: String, done: Boolean) = (if (done) "[x] " else "[ ] ") + text
}
