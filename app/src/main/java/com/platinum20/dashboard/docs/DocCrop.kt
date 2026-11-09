package com.platinum20.dashboard.docs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Raddrizza e ritaglia la foto di un documento (1.358, Antonio: "ritagli e ruoti correttamente").
 * Il verso viene dal testo letto sul telefono: le righe stampate dicono di quanto è storta la foto,
 * anche se è girata di lato o sottosopra. Raddrizzata, la tessera ha i bordi dritti: sono le linee
 * più nette attorno al testo, con la forma di una tessera vera. Se i bordi non si trovano la foto
 * resta intera, solo girata nel verso giusto.
 */
object DocCrop {
    /** Un tratto di testo letto: la direzione della riga e quanto conta (i caratteri). */
    data class Seg(val dx: Float, val dy: Float, val weight: Int)

    /** Un rettangolo in pixel, [right] e [bottom] compresi. */
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right - left + 1
        val height get() = bottom - top + 1
    }

    /** Le proporzioni di una tessera (85,6 × 54 mm) e quelle ammesse per una foto un po' storta. */
    const val CARD = 1.586f
    val CARD_RANGE = 1.3f..1.85f

    /** Patente, carta d'identità, tessere: formato tessera. Libretto e assicurazione sono fogli. */
    fun aspect(kind: DocKind): Pair<Float, ClosedFloatingPointRange<Float>> = when (kind) {
        DocKind.PASSPORT -> 1.42f to 1.25f..1.75f
        DocKind.VEHICLE, DocKind.INSURANCE, DocKind.OTHER -> 1.41f to 0.6f..1.85f
        else -> CARD to CARD_RANGE
    }

    /**
     * Pura: di quanti gradi (in senso orario, y verso il basso) è girato il testo. Prima il quarto di
     * giro che vince fra le righe (di lato, sottosopra), poi lo scarto fine: la mediana pesata, così
     * una scritta storta sulla foto della tessera non sposta tutto. Null senza testo.
     */
    fun textAngle(segs: List<Seg>): Float? {
        val lines = segs.filter { it.weight > 0 && (it.dx != 0f || it.dy != 0f) }
            .map { Math.toDegrees(atan2(it.dy.toDouble(), it.dx.toDouble())).toFloat() to it.weight }
        if (lines.isEmpty()) return null
        fun quarter(a: Float) = ((a / 90f).roundToInt() % 4 + 4) % 4
        val q = lines.groupBy { quarter(it.first) }.maxBy { (_, l) -> l.sumOf { it.second } }.key
        val rest = lines.filter { quarter(it.first) == q }.map { (a, w) ->
            var r = a - q * 90f
            while (r > 180f) r -= 360f
            while (r <= -180f) r += 360f
            r to w
        }.sortedBy { it.first }
        val half = rest.sumOf { it.second } / 2.0
        var acc = 0
        val fine = rest.first { acc += it.second; acc >= half }.first
        val angle = q * 90f + fine
        return if (angle > 180f) angle - 360f else angle
    }

    /**
     * Pura: i bordi della tessera in un'immagine in grigi ([gray] 0..255, riga per riga) già dritta.
     * Per ogni lato si cercano le linee più nette fuori dal testo ([text]); fra tutte le combinazioni
     * vince quella più netta con le proporzioni più vicine a [ideal]. Null se non c'è un bordo vero
     * su almeno tre lati: meglio la foto intera che un ritaglio sbagliato.
     */
    fun edges(
        gray: IntArray,
        w: Int,
        h: Int,
        text: Box,
        ideal: Float = CARD,
        range: ClosedFloatingPointRange<Float> = CARD_RANGE,
    ): Box? {
        if (w < 16 || h < 16) return null
        val t = Box(text.left.coerceIn(1, w - 2), text.top.coerceIn(1, h - 2), text.right.coerceIn(1, w - 2), text.bottom.coerceIn(1, h - 2))
        if (t.right <= t.left || t.bottom <= t.top) return null
        fun g(x: Int, y: Int) = gray[y * w + x]
        // Bordi verticali (sinistra, destra): quanto cambia la luce da una colonna all'altra, sulle righe del testo.
        val cols = FloatArray(w)
        for (x in 1 until w - 1) {
            var s = 0
            for (y in t.top..t.bottom) s += abs(g(x + 1, y) - g(x - 1, y))
            cols[x] = s.toFloat() / t.height
        }
        val rows = FloatArray(h)
        for (y in 1 until h - 1) {
            var s = 0
            for (x in t.left..t.right) s += abs(g(x, y + 1) - g(x, y - 1))
            rows[y] = s.toFloat() / t.width
        }
        val colFloor = floor(cols)
        val rowFloor = floor(rows)
        val lefts = peaks(cols, 1, t.left - 1, colFloor) + Cand(0, 0f)
        val rights = peaks(cols, t.right + 1, w - 2, colFloor) + Cand(w - 1, 0f)
        val tops = peaks(rows, 1, t.top - 1, rowFloor) + Cand(0, 0f)
        val bottoms = peaks(rows, t.bottom + 1, h - 2, rowFloor) + Cand(h - 1, 0f)
        var best: Box? = null
        var bestScore = 0f
        for (l in lefts) for (r in rights) for (tp in tops) for (b in bottoms) {
            if (listOf(l, r, tp, b).count { it.strength > 0f } < 3) continue
            val bw = r.at - l.at
            val bh = b.at - tp.at
            if (bw <= 0 || bh <= 0) continue
            val a = bw.toFloat() / bh
            if (a !in range) continue
            // Una tessera vera occupa una parte buona della foto.
            if (bw * bh < w * h * 0.12f) continue
            val fit = 1f - minOf(1f, abs(a - ideal) / 0.5f)
            val score = (l.strength / colFloor + r.strength / colFloor + tp.strength / rowFloor + b.strength / rowFloor) * (0.3f + 0.7f * fit)
            if (score > bestScore) {
                bestScore = score
                best = Box(l.at, tp.at, r.at, b.at)
            }
        }
        return best
    }

    private data class Cand(val at: Int, val strength: Float)

    /** Il "rumore" di fondo di un profilo: un bordo vero deve stare ben sopra. */
    private fun floor(p: FloatArray): Float {
        val sorted = p.sorted()
        return maxOf(sorted[sorted.size / 2], 1f)
    }

    /** I massimi locali più forti di [p] fra [from] e [to], almeno tre volte il fondo. */
    private fun peaks(p: FloatArray, from: Int, to: Int, floor: Float): List<Cand> {
        if (to < from) return emptyList()
        val out = mutableListOf<Cand>()
        for (i in from..to) {
            val v = p[i]
            if (v < floor * 3f) continue
            val lo = maxOf(from, i - 3)
            val hi = minOf(to, i + 3)
            if ((lo..hi).all { j -> j == i || p[j] < v || (p[j] == v && j > i) }) out += Cand(i, v)
        }
        return out.sortedByDescending { it.strength }.take(4)
    }

    /** La foto già girata secondo l'EXIF, raddrizzata e ritagliata; la stessa se non c'è niente da fare. */
    suspend fun straighten(src: Bitmap, kind: DocKind): Bitmap {
        val lines = read(src)
        val angle = textAngle(lines.map { (pts, chars) -> Seg(pts[2] - pts[0], pts[3] - pts[1], chars) }) ?: return src
        val (ideal, range) = aspect(kind)
        // Girata del tutto (anche di poco), con il testo trasportato nella foto nuova.
        val matrix = Matrix().apply { setRotate(-angle) }
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()).also(matrix::mapRect)
        matrix.postTranslate(-bounds.left, -bounds.top)
        val turned = rotate(src, matrix, bounds.width().roundToInt(), bounds.height().roundToInt())
        val textPts = lines.flatMap { (pts, _) -> pts.toList() }.toFloatArray().also(matrix::mapPoints)
        val xs = textPts.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = textPts.filterIndexed { i, _ -> i % 2 == 1 }
        // I bordi si cercano su una copia piccola: bastano 600 px e si fa in un attimo.
        val scale = minOf(1f, 600f / maxOf(turned.width, turned.height))
        val small = Bitmap.createScaledBitmap(turned, maxOf(1, (turned.width * scale).roundToInt()), maxOf(1, (turned.height * scale).roundToInt()), true)
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val gray = IntArray(px.size) { i -> val c = px[i]; (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 }
        val text = Box(
            (xs.min() * scale).toInt(), (ys.min() * scale).toInt(),
            (xs.max() * scale).toInt(), (ys.max() * scale).toInt(),
        )
        val box = edges(gray, small.width, small.height, text, ideal, range)
        if (small !== turned) small.recycle()
        if (box == null) {
            // Senza bordi sicuri: solo il quarto di giro, così non compaiono angoli neri.
            if (turned !== src) turned.recycle()
            val quarter = (angle / 90f).roundToInt() * 90f
            if (quarter % 360f == 0f) return src
            val m = Matrix().apply { setRotate(-quarter) }
            val b = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()).also(m::mapRect)
            m.postTranslate(-b.left, -b.top)
            return rotate(src, m, b.width().roundToInt(), b.height().roundToInt())
        }
        // Un filo dentro il bordo, così non resta una striscia del tavolo.
        val inset = 0.006f * maxOf(box.width, box.height)
        val l = ((box.left + inset) / scale).roundToInt().coerceIn(0, turned.width - 1)
        val t = ((box.top + inset) / scale).roundToInt().coerceIn(0, turned.height - 1)
        val r = ((box.right - inset) / scale).roundToInt().coerceIn(l + 1, turned.width)
        val b = ((box.bottom - inset) / scale).roundToInt().coerceIn(t + 1, turned.height)
        val out = Bitmap.createBitmap(turned, l, t, r - l, b - t)
        if (out !== turned && turned !== src) turned.recycle()
        return out
    }

    /** Un quarto di giro in senso orario, per il tasto "Ruota". */
    fun turnRight(src: Bitmap): Bitmap {
        val m = Matrix().apply { setRotate(90f); postTranslate(src.height.toFloat(), 0f) }
        return rotate(src, m, src.height, src.width)
    }

    /** Girata su fondo bianco: gli angoli rimasti vuoti non diventano neri nel JPEG. */
    private fun rotate(src: Bitmap, matrix: Matrix, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /** Le righe lette: i quattro angoli (in senso orario dall'alto a sinistra del testo) e i caratteri. */
    private suspend fun read(bitmap: Bitmap): List<Pair<FloatArray, Int>> = runCatching {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val c = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
                val chars = line.text.count { it.isLetterOrDigit() }
                if (chars < 3) return@mapNotNull null
                floatArrayOf(
                    c[0].x.toFloat(), c[0].y.toFloat(), c[1].x.toFloat(), c[1].y.toFloat(),
                    c[2].x.toFloat(), c[2].y.toFloat(), c[3].x.toFloat(), c[3].y.toFloat(),
                ) to chars
            }
        } finally {
            recognizer.close()
        }
    }.getOrDefault(emptyList())
}
