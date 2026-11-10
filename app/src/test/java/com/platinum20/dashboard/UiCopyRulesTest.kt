package com.platinum20.dashboard

import com.platinum20.dashboard.ui.screens.SettingsSummary
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le regole del testo (revisione 1.402) controllate sul codice delle schermate: i sottotitoli stanno in una
 * riga senza puntini, e ogni icona che si tocca ha un nome per TalkBack.
 */
class UiCopyRulesTest {
    private val screens: List<File> = listOf("src/main/java/com/platinum20/dashboard/ui/screens", "app/src/main/java/com/platinum20/dashboard/ui/screens")
        .map(::File).first { it.isDirectory }
        .listFiles { f -> f.extension == "kt" }!!.toList()

    @Test fun iSottotitoliStannoInUnaRiga() {
        val literal = Regex("""\b(subtitle|detail)\s*=\s*"((?:[^"\\]|\\.)*)"""")
        val long = screens.flatMap { file ->
            literal.findAll(file.readText()).map { it.groupValues[2] }
                .filter { "\${" !in it && it.length > SettingsSummary.MAX }
                .map { "${file.name}: $it" }.toList()
        }
        assertTrue("Sottotitoli oltre ${SettingsSummary.MAX} caratteri:\n" + long.joinToString("\n"), long.isEmpty())
    }

    @Test fun ogniIconaToccabileHaUnNome() {
        val missing = screens.flatMap { file ->
            val text = file.readText()
            Regex("""IconButton\(""").findAll(text).mapNotNull { call ->
                val after = text.substring(call.range.last, minOf(text.length, call.range.last + 400))
                val icon = after.indexOf("Icon(").takeIf { it >= 0 } ?: return@mapNotNull null
                val line = after.substring(icon).lineSequence().first()
                if ("contentDescription = null" in line) "${file.name}:${text.substring(0, call.range.first).count { it == '\n' } + 1}" else null
            }.toList()
        }
        assertTrue("Icone toccabili senza nome:\n" + missing.joinToString("\n"), missing.isEmpty())
    }
}
