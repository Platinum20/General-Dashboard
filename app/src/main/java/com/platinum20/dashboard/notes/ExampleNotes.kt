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
    /** La guida (2026-11-26) ha il suo segno: arriva anche a chi ha già avuto (o cancellato) gli esempi di prima. */
    private const val GUIDE_KEY = "guide_v1"

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

    /** Gli id delle cinque note della guida (2026-11-26): l'indice e le quattro pagine. */
    data class GuideIds(val hub: Long, val plus: Long, val agenda: Long, val notes: Long, val media: Long)

    /**
     * La guida di Canvas (Antonio, 2026-11-26: "note esempio che spiegano come funzionano alcune sezioni"): un indice
     * fissato con le sotto-note, più una nota per sezione. Ognuna usa pezzi diversi dell'editor, così insieme li mostrano
     * tutti (titoli, liste, rientri, spunte, bacheca, tabelle, colonne, riquadri, citazioni, colori, link, date e codici
     * che si toccano, #guida, foto, copertina, vocale). Solo la bacheca dell'Agenda ha voci aperte: le altre non
     * riempiono "Da fare". [map] e [cover] sono i disegni, [chime] il vocale (vuoti = senza).
     */
    fun guide(ids: GuideIds, now: Long, map: String, cover: String, chime: String): List<Note> {
        val minute = 60_000L
        val hub = buildString {
            appendLine("Guida di Canvas")
            appendLine("Canvas è **una Home sola** con il tasto **+** in basso a destra: niente schede, tutto parte da qui.")
            if (map.isNotEmpty()) appendLine(NoteLines.imageLine(map).replace("🖼 Foto", "🖼 La Home"))
            appendLine("!!🧭 Queste note sono la guida. Tocca #guida per vederle tutte insieme, e cancellale quando non servono più.")
            appendLine("## Le pagine della guida")
            appendLine("[📄 Il tasto +](nota:${ids.plus})")
            appendLine("[📄 Agenda e Da fare](nota:${ids.agenda})")
            appendLine("[📄 Le note, tutto quello che sanno fare](nota:${ids.notes})")
            appendLine("[📄 Musica, video e Cerca](nota:${ids.media})")
            appendLine("## Tre gesti che valgono ovunque")
            appendLine("| Gesto | Dove | Cosa fa |")
            appendLine("| Tira giù | qualsiasi pagina | apre Cerca |")
            appendLine("| Tieni premuto | una riga, una card | il fumetto con le scelte |")
            appendLine("| Indietro | una pagina aperta | torna nella sua card |")
            appendLine("---")
            appendLine("‖ **Rotella** in alto nella Home: le impostazioni ‖ **Card della Home**: Impostazioni › Home, per sceglierle e metterle in ordine ‖")
            appendLine("> Ogni card si apre con un tocco e diventa la sua pagina; Indietro la rimette al suo posto.")
            append("#guida")
        }
        val plus = """
            Il tasto +
            Un tasto solo, in basso a destra: scrivi o parli e Canvas capisce da solo **che cosa è**. Non ti chiede mai il tipo.
            ## I gesti
            1. **Tocca**: si apre il riquadro per scrivere; la freccia salva, la X lo svuota
            2. **Tieni premuto**: parli, e quello che dici finisce nel riquadro (non parte da solo)
            3. Tenendo premuto, scorri **in su** per bloccare la voce e lasciare il dito
            4. Scorri **a sinistra** per annullare
            ## Dove finisce
            Sotto il riquadro ci sono Da fare, Appunti, Calendario e Abitudine: Canvas ne accende una da sola, tu la cambi con un tocco se serve.
            | Scrivi | Diventa |
            | venerdì alle 20 cena da Luca | un evento in Agenda |
            | compleanno di Marta 12 marzo | una data che torna ogni anno |
            | ogni giorno leggere | un'abitudine |
            | comprare le lampadine | una cosa Da fare |
            | un testo su più righe | Appunti |
            | un codice di spedizione | un pacco da seguire |
            !!🔎 Mentre scrivi, sopra il riquadro compaiono le cose che hai già: toccane una per aprirla invece di rifarla.
            ## Anche con una foto
            - Dal + aggiungi una foto: Canvas legge il testo
              - Biglietti, volantini, appunti scritti a mano
              - Se non trova niente da fare, la foto diventa una nota
            Dopo ogni salvataggio, nel fumetto in basso c'è _Annulla_: ~~la paura di sbagliare~~ si rimedia con un tocco.
            Torna alla [Guida di Canvas](nota:${ids.hub})
            #guida
        """.trimIndent()
        val agenda = """
            Agenda e Da fare
            Un calendario solo per tutto: eventi, turni, treni, promemoria e pacchi.
            ## Come si legge
            - La linea a sinistra è il tempo, il segno {b:blu} è **adesso**
            - Sotto OGGI c'è tutto quello di oggi, anche quello già finito
            - Gli eventi con l'ora si leggono come un treno: partenza, durata, arrivo
            - Il mese mostra solo il mese in corso; per quelli prima scorri indietro
            ## Cosa va dove
            | Cosa | Dove la trovi |
            | Ha una data | Agenda |
            | Da fare, senza data | Da fare, in Home |
            | Un'idea, un elenco | Appunti, in Home |
            | Si ripete ogni giorno | in cima a Da fare |
            !!🔥 Le abitudini contano i giorni di fila. Spuntata oggi, resta grigia fino a domani.
            ## Appunti attaccati
            Ogni evento, turno o treno ha il suo blocco **Appunti**: quello che scrivi lì compare nella riga dell'Agenda con 📝 e ti torna nella notifica.
            > Per esempio "portare il caricabatterie" sul treno di domani mattina.
            ## Prova la bacheca
            Le spunte sotto la bacheca diventano schede: tocca una scheda per spostarla in un'altra colonna.
            [bacheca]
            [ ] Scrivi "venerdì alle 20 cena da Luca" nel +
            [-] Tieni premuto un evento dell'Agenda
            [x] Apri la guida
            Torna alla [Guida di Canvas](nota:${ids.hub})
            #guida
        """.trimIndent()
        val notes = buildString {
            appendLine("Le note, tutto quello che sanno fare")
            appendLine("Si scrive come su un foglio: i segni (asterischi, trattini) **non si vedono mai**, resta il testo già formattato.")
            appendLine("## La barra sopra la tastiera")
            appendLine("1. **+** aggiunge i blocchi: tabella, colonne, bacheca, foto, vocale, disegno, sotto-nota")
            appendLine("2. Annulla e rifai")
            appendLine("3. Elenco puntato, numerato e spunte")
            appendLine("4. **Aa** apre la riga dei formati, la tastiera si chiude con l'ultimo tasto")
            appendLine("## I formati")
            appendLine("- **Grassetto**, _corsivo_, ~~barrato~~ ed ==evidenziato==")
            appendLine("- Testo colorato: {r:rosso}, {b:blu}, {v:verde}, {p:viola}; oppure lo sfondo: {A:arancio}, {G:giallo}")
            appendLine("- I titoletti, come \"I formati\" qui sopra")
            appendLine("  - Le righe rientrano fino a quattro livelli")
            appendLine("    - Così")
            appendLine("> La citazione, per una frase da tenere a mente.")
            appendLine("!!💡 Il riquadro: un'emoji davanti e il testo in evidenza.")
            appendLine("---")
            appendLine("## Le righe si spostano")
            appendLine("Tieni la maniglia ⋮⋮ accanto alla riga: su e giù la sposti, di lato la rientri.")
            appendLine("## Le parti che si toccano")
            appendLine("- Una data a parole, come domani alle 9: un tocco e va in Agenda")
            appendLine("- Un codice come wifi: CanvasCasa24 si copia con un tocco")
            appendLine("- Le #etichette raccolgono le note dello stesso tema")
            appendLine("- Con @ colleghi un'altra nota o un evento, come la [Guida di Canvas](nota:${ids.hub})")
            if (chime.isNotEmpty()) {
                appendLine("## Un vocale")
                appendLine("[🎙 Audio 0:03](${NoteAudio.SCHEME}$chime)")
            }
            appendLine("## E poi")
            appendLine("‖ **Altro**, in basso: Ricordamelo sulla riga, il posto, le versioni di prima ‖ **Condividi**: PDF, copia, manda a qualcuno ‖")
            appendLine("[x] Una lista con tutte le spunte fatte non finisce in Da fare")
            appendLine("[x] Una con voci aperte sì, e da lì si spunta senza aprirla")
            append("#guida")
        }
        val media = """
            Musica, video e Cerca
            ## Il player
            Uno solo per tutto: Spotify, YouTube, radio e podcast passano dalla **stessa coda**.
            - La card Riprendi, in Home, riparte da dove eri e dice cosa viene dopo
            - Il mini player si sposta nei quattro angoli
              - Spinto di lato diventa una linguetta sul bordo
              - Trascinato sul + si chiude
            - I titoli di YouTube restano nella lingua originale
            ## Annota un minuto
            Mentre guardi un video, nel fumetto del player c'è **Annota**: nasce una nota con il minuto, e toccandolo il video riparte da lì.
            ## Cerca
            Tira giù qualsiasi pagina e scrivi.
            1. Le parole vanno in qualsiasi ordine, anche con un errore di battitura
            2. Prima le tue cose (note, eventi, playlist), poi il resto
            3. Capisce anche il tempo: domani, venerdì, marzo
            | Scrivi | Trovi |
            | luca cena | la cena da Luca, anche se hai scritto il contrario |
            | venerdì | tutto quello che hai venerdì |
            | spesa latte | la nota Spesa, sulla riga del latte |
            !!↩️ Tornando indietro Cerca ricorda i risultati: non devi riscrivere niente.
            Torna alla [Guida di Canvas](nota:${ids.hub})
            #guida
        """.trimIndent()
        fun note(id: Long, text: String, ago: Int, emoji: String, photos: List<String> = emptyList(), cover: String? = null, pinned: Boolean = false) =
            Note(id, text, now - ago * minute, pinned = pinned, createdAt = now - ago * minute, photos = photos, emoji = emoji, cover = cover)
        return listOf(
            note(ids.hub, hub, 0, "🧭", photos = listOfNotNull(map.ifEmpty { null }), cover = cover.ifEmpty { null }, pinned = true),
            note(ids.plus, plus, 1, "➕"),
            note(ids.agenda, agenda, 2, "📅"),
            note(ids.notes, notes, 3, "📝"),
            note(ids.media, media, 4, "🎧"),
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
        seedGuideOnce(context)
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

    /** La guida, una volta sola: se la cancella non torna. */
    private fun seedGuideOnce(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(GUIDE_KEY, false)) return
        prefs.edit().putBoolean(GUIDE_KEY, true).apply()
        runCatching {
            NotesStore.init(context)
            val base = System.nanoTime() + 100
            val ids = GuideIds(base, base + 1, base + 2, base + 3, base + 4)
            val dir = File(context.filesDir, "note-photos").apply { mkdirs() }
            val map = runCatching { save(homeMap(), File(dir, "guida-home.jpg")) }.getOrDefault("")
            val cover = runCatching { save(guideCover(), File(dir, "guida-copertina.jpg")) }.getOrDefault("")
            val chime = runCatching {
                File(context.filesDir, "note-audio/guida-campanello.wav").apply { parentFile?.mkdirs(); writeBytes(chimeWav()) }.absolutePath
            }.getOrDefault("")
            NotesStore.addExamples(guide(ids, System.currentTimeMillis(), map, cover, chime), emptyList())
        }
    }

    /**
     * Il vocale della guida: tre note che salgono (do, mi, sol) e l'accordo, poco più di tre secondi. Un WAV fatto a mano
     * (MediaPlayer lo suona come i vocali veri); puro, così il test ne controlla la durata.
     */
    fun chimeWav(rate: Int = 22_050): ByteArray {
        val notes = listOf(523.25, 659.25, 783.99)
        val step = (rate * 0.45).toInt()
        val total = (rate * 3.2).toInt()
        val pcm = ShortArray(total)
        for (i in 0 until total) {
            var v = 0.0
            notes.forEachIndexed { n, f ->
                val start = n * step
                if (i >= start) {
                    val t = (i - start).toDouble() / rate
                    v += kotlin.math.sin(2 * Math.PI * f * t) * kotlin.math.exp(-t * 2.2) * 0.28
                }
            }
            pcm[i] = (v.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        val data = total * 2
        val buf = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(data)
        pcm.forEach { buf.putShort(it) }
        return buf.array()
    }

    /** La Home disegnata in piccolo: le card, la rotella in alto e il + in basso a destra, con le frecce che spiegano. */
    private fun homeMap(): Bitmap {
        val w = 1200
        val h = 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(28, 22, 24))
        val phone = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(48, 38, 40) }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 96, 100); style = Paint.Style.STROKE; strokeWidth = 5f }
        val left = 340f
        val right = 860f
        c.drawRoundRect(left, 60f, right, 940f, 56f, 56f, phone)
        c.drawRoundRect(left, 60f, right, 940f, 56f, 56f, rim)
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(74, 58, 62) }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(240, 228, 230); textSize = 34f; isFakeBoldText = true }
        val soft = Paint(ink).apply { color = Color.rgb(190, 170, 174); textSize = 26f; isFakeBoldText = false }
        c.drawText("Buonasera", left + 40f, 150f, ink)
        // La rotella delle impostazioni.
        val gear = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 170, 174); style = Paint.Style.STROKE; strokeWidth = 7f }
        c.drawCircle(right - 70f, 138f, 20f, gear)
        listOf("ADESSO" to 180f, "AGENDA ›" to 220f, "DA FARE ›" to 200f).foldIndexed(190f) { i, top, (name, tall) ->
            c.drawRoundRect(left + 30f, top, right - 30f, top + tall, 32f, 32f, card)
            c.drawText(name, left + 60f, top + 50f, soft)
            if (i == 1) c.drawCircle(left + 70f, top + 110f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 160, 255) })
            top + tall + 24f
        }
        // Il +, rotondo, in basso a destra.
        val blue = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(110, 160, 255) }
        c.drawCircle(right - 80f, 860f, 46f, blue)
        val plusInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(20, 24, 40); strokeWidth = 9f; strokeCap = Paint.Cap.ROUND }
        c.drawLine(right - 100f, 860f, right - 60f, 860f, plusInk)
        c.drawLine(right - 80f, 840f, right - 80f, 880f, plusInk)
        // Le frecce con le spiegazioni, ai lati del telefono.
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 170, 90); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
        val note = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 196, 140); textSize = 36f; isFakeBoldText = true }
        c.drawLine(200f, 90f, 200f, 260f, arrow)
        c.drawLine(200f, 260f, 180f, 230f, arrow)
        c.drawLine(200f, 260f, 220f, 230f, arrow)
        c.drawText("Tira giù", 40f, 320f, note)
        c.drawText("= Cerca", 40f, 364f, note)
        c.drawLine(right - 40f, 138f, 960f, 138f, arrow)
        c.drawText("Impostazioni", 900f, 200f, note)
        c.drawLine(right - 20f, 860f, 960f, 860f, arrow)
        c.drawText("Scrivi", 900f, 800f, note)
        c.drawText("o parla", 900f, 930f, note)
        c.drawText("Tieni premuta", 30f, 560f, note)
        c.drawText("una card", 30f, 604f, note)
        c.drawLine(280f, 580f, left + 60f, 580f, arrow)
        return bmp
    }

    /** La copertina della guida: una bussola su un cielo rosso scuro, i colori del tema che usa. */
    private fun guideCover(): Bitmap {
        val w = 1200
        val h = 600
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.rgb(120, 40, 50), Color.rgb(40, 24, 60), Shader.TileMode.CLAMP)
        })
        val cx = w * 0.5f
        val cy = h * 0.5f
        c.drawCircle(cx, cy, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) })
        c.drawCircle(cx, cy, 190f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 6f })
        val north = Path().apply { moveTo(cx, cy - 160f); lineTo(cx + 34f, cy); lineTo(cx - 34f, cy); close() }
        val south = Path().apply { moveTo(cx, cy + 160f); lineTo(cx + 34f, cy); lineTo(cx - 34f, cy); close() }
        c.drawPath(north, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 120, 110) })
        c.drawPath(south, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 225, 230) })
        c.drawCircle(cx, cy, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 24, 40) })
        return bmp
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
