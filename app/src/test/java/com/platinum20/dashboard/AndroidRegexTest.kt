package com.platinum20.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Sul telefono le espressioni regolari le legge ICU, più severo di Java: una graffa non preceduta da "\" fa
 * chiudere l'app all'avvio (1.590, il colore `{r:testo}`). I test girano su Java e non se ne accorgerebbero:
 * qui si controlla il codice, ogni graffa deve essere un quantificatore ({2}, {1,3}), \p{…}, un \${…} di Kotlin o scritta "\{" "\}".
 */
class AndroidRegexTest {
    @Test
    fun everyBraceInARegexIsEscaped() {
        val literal = Regex("Regex\\(\"\"\"(.*?)\"\"\"")
        val ok = Regex("""\\p\{\w+}|\$\{[^}]*}|\\.|\{\d+(,\d*)?}""")
        // Anche le stringhe normali Regex("…"), lette come le vede il programma (\\ → \); quelle col dollaro di Kotlin si saltano.
        val quoted = Regex("Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"")
        val bad = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val src = f.readText()
            (literal.findAll(src).map { it.groupValues[1] } +
                quoted.findAll(src).map { it.groupValues[1] }.filter { '$' !in it }.map { it.replace("\\\\", "\\").replace("\\\"", "\"") })
                .filter { p -> p.replace(ok, "").any { it == '{' || it == '}' } }
                .map { "${f.name}: $it" }
        }.toList()
        assertEquals(emptyList<String>(), bad)
    }
}
