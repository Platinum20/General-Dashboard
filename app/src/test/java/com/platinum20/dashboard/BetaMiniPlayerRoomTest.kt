package com.platinum20.dashboard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.286: nelle pagine senza barra delle sezioni la lista finisce sopra il mini player.
 * Se una di queste pagine viene riscritta e perde `miniPlayerRoom()`, l'ultima riga torna sotto il player.
 */
class BetaMiniPlayerRoomTest {

    private val screens = File("src/main/java/com/platinum20/dashboard/ui/screens")
        .let { if (it.isDirectory) it else File("app/src/main/java/com/platinum20/dashboard/ui/screens") }

    @Test
    fun paginaSenzaBarraLasciaSpazioAlMiniPlayer() {
        val pages = mapOf(
            "SettingsScreen.kt" to 2, "WeatherScreen.kt" to 1, "RecapScreen.kt" to 1, "SearchScreen.kt" to 1,
            "UpdatesScreen.kt" to 1, "CustomizeScreen.kt" to 1, "MailUi.kt" to 1, "AutoFillUi.kt" to 1,
            "PlacePickerScreen.kt" to 1,
        )
        pages.forEach { (name, min) ->
            val uses = Regex("""miniPlayerRoom\(\)""").findAll(File(screens, name).readText()).count()
            assertTrue("$name: il fondo della lista non lascia spazio al mini player", uses >= min)
        }
    }
}
