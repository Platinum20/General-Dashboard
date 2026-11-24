package com.platinum20.dashboard.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import java.io.File

/**
 * Le note di esempio (Antonio, 2026-11-24): con l'aggiornamento arrivano una volta sola quattro note e due cose da fare
 * vere, così le card della Home e l'editor si vedono pieni. Usano un po' tutto quello che l'editor sa fare (liste,
 * spunte, rientri, tabella, riquadro, citazione, colonne, bacheca, colori, link, sotto-nota, #tag, foto, copertina).
 * Se le cancella non tornano: il segno "fatto" sta nelle preferenze. Il testo è puro, si prova senza telefono.
 */
object ExampleNotes {
    private const val PREFS = "notes_examples"
    private const val KEY = "seeded_v1"

    /** Gli id delle quattro note: servono per i link tra una nota e l'altra. */
    data class Ids(val gifts: Long, val shopping: Long, val trip: Long, val board: Long)

    /** Le quattro note, dalla più recente; [photo] e [cover] sono i file disegnati (vuoti = senza). */
    fun notes(ids: Ids, now: Long, photo: String, cover: String): List<Note> {
        val minute = 60_000L
        val gifts = """
            Idee regali
            ## Natale
            - Mamma
              - Sciarpa di lana grigia
              - Libro di ricette, ==quello con la copertina rossa==
            - Papà: cuffie per la palestra
            - Giulia
              [x] Chiedere a Marco la sua taglia
              [ ] Felpa con il cappuccio
            ## Le taglie
            | Chi | Maglia | Scarpe |
            | Mamma | M | 38 |
            | Papà | L | 43 |
            | Giulia | S | 37 |
            !!🎄 Ordinare tutto **entro il 15 dicembre**, così arriva in tempo
            Per la cena della vigilia c'è la [Spesa](nota:${ids.shopping})
            #regali #natale
        """.trimIndent()
        val shopping = """
            Spesa
            [ ] Latte
            [ ] Pane integrale
            [x] Uova
            [ ] Pomodori
              [ ] Basilico
            [ ] Caffè
            [x] Detersivo per i piatti
            !!🥛 Il latte {r:senza lattosio}, per Giulia
        """.trimIndent()
        val trip = """
            Weekend a Napoli
            Partenza **sabato alle 8:30** da Roma Termini, ritorno _domenica sera_.
            > Vedi Napoli e poi muori (ma prima una pizza)
            ## Programma
            1. Lungomare e Castel dell'Ovo
            2. Pizza da Sorbillo
            3. ~~Capri~~ troppo poco tempo, la prossima volta
            ---
            ‖ **Sabato**: centro storico e Spaccanapoli ‖ **Domenica**: Vomero e funicolare ‖
            ## Da sistemare
            [bacheca]
            [ ] Prenotare il B&B
            [-] Biglietti del treno
            [x] Chiedere le ferie
            Il B&B costa meno se si prenota {V:entro venerdì}.
            [🔗 Trenitalia](https://www.trenitalia.com)
            #viaggi
        """.trimIndent()
        val board = buildString {
            appendLine("Lavagna della riunione")
            if (photo.isNotEmpty()) appendLine(NoteLines.imageLine(photo).replace("🖼 Foto", "🖼 Lavagna"))
            appendLine("La foto della lavagna di lunedì, con i punti da ricordare.")
            appendLine("- Turno nuovo del {b:venerdì}, dalle 14 alle 22")
            appendLine("- Inventario del magazzino entro fine mese")
            appendLine("- Ferie di Natale da segnare **entro il 1° dicembre**")
            appendLine("Collegata al viaggio:")
            append("[📄 Weekend a Napoli](nota:${ids.trip})")
        }
        fun note(id: Long, text: String, ago: Int, emoji: String, photos: List<String> = emptyList(), cover: String? = null) =
            Note(id, text, now - ago * minute, createdAt = now - ago * minute, photos = photos, emoji = emoji, cover = cover)
        return listOf(
            note(ids.shopping, shopping, 1, "🛒"),
            note(ids.gifts, gifts, 2, "🎁"),
            note(ids.trip, trip, 3, "🧳", cover = cover.ifEmpty { null }),
            note(ids.board, board, 4, "📌", photos = listOfNotNull(photo.ifEmpty { null })),
        )
    }

    /** Le due cose da fare senza data (quelle da spuntare in "Da fare"). */
    fun todos(firstId: Long, now: Long): List<TaskItem> = listOf(
        TaskItem(firstId, "Chiamare il meccanico per il tagliando", createdAt = now),
        TaskItem(firstId + 1, "Restituire il libro a Luca", createdAt = now - 60_000L),
    )

    /** Una volta sola: alla prima apertura dopo l'aggiornamento. */
    fun seedOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY, false)) return
        prefs.edit().putBoolean(KEY, true).apply()
        runCatching {
            NotesStore.init(context)
            val base = System.nanoTime()
            val ids = Ids(base, base + 1, base + 2, base + 3)
            val dir = File(context.filesDir, "note-photos").apply { mkdirs() }
            val photo = runCatching { save(whiteboard(), File(dir, "esempio-lavagna.jpg")) }.getOrDefault("")
            val cover = runCatching { save(sunset(), File(dir, "esempio-napoli.jpg")) }.getOrDefault("")
            val now = System.currentTimeMillis()
            NotesStore.addExamples(notes(ids, now, photo, cover), todos(base + 10, now))
        }
    }

    private fun save(bmp: Bitmap, file: File): String {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bmp.recycle()
        return file.absolutePath
    }

    /** Una lavagna bianca con tre punti scritti a pennarello. */
    private fun whiteboard(): Bitmap {
        val w = 1200
        val h = 900
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(96, 96, 100))
        val board = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(246, 247, 244) }
        c.drawRect(40f, 40f, w - 40f, h - 40f, board)
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 76, 168); textSize = 64f; isFakeBoldText = true
        }
        val red = Paint(blue).apply { color = Color.rgb(196, 40, 40) }
        val ink = Paint(blue).apply { color = Color.rgb(40, 40, 44); textSize = 52f; isFakeBoldText = false }
        c.drawText("RIUNIONE LUNEDÌ", 110f, 170f, blue)
        val line = Paint(blue).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
        c.drawLine(110f, 195f, 640f, 205f, line)
        listOf("Turno venerdì 14–22", "Inventario fine mese", "Ferie Natale entro 1/12").forEachIndexed { i, t ->
            val y = 330f + i * 130f
            c.drawCircle(130f, y - 18f, 10f, ink)
            c.drawText(t, 170f, y, ink)
        }
        // Un cerchio rosso intorno all'ultima riga, come fatto a mano.
        val circle = Paint(red).apply { style = Paint.Style.STROKE; strokeWidth = 7f }
        c.drawOval(90f, 520f, 820f, 640f, circle)
        c.drawText("!", 860f, 610f, red)
        val arrow = Path().apply { moveTo(900f, 300f); lineTo(1060f, 420f); moveTo(1060f, 420f); lineTo(1010f, 420f); moveTo(1060f, 420f); lineTo(1050f, 372f) }
        c.drawPath(arrow, line)
        return bmp
    }

    /** La copertina del viaggio: un tramonto sul golfo con il Vesuvio. */
    private fun sunset(): Bitmap {
        val w = 1200
        val h = 600
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val sky = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.65f, Color.rgb(255, 150, 90), Color.rgb(255, 214, 150), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w.toFloat(), h * 0.65f, sky)
        c.drawCircle(w * 0.7f, h * 0.55f, 70f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 240, 200) })
        val mountain = Path().apply {
            moveTo(0f, h * 0.65f); lineTo(w * 0.18f, h * 0.38f); lineTo(w * 0.26f, h * 0.36f); lineTo(w * 0.32f, h * 0.42f)
            lineTo(w * 0.55f, h * 0.65f); close()
        }
        c.drawPath(mountain, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 80, 110) })
        val sea = Paint().apply {
            shader = LinearGradient(0f, h * 0.65f, 0f, h.toFloat(), Color.rgb(70, 110, 170), Color.rgb(30, 60, 110), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.65f, w.toFloat(), h.toFloat(), sea)
        return bmp
    }
}
