package com.platinum20.dashboard.trips

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.platinum20.dashboard.R

/**
 * La linea del viaggio della notifica, disegnata da noi (proposta A, 2026-11-20): la ProgressStyle di Android 16
 * su One UI fa i pallini quadrati e spezza la linea. Linea piena fin dove sei, un pallino tondo per fermata
 * (pieni quelli passati), il treno in un cerchio bianco. [steps] dice dove, in minuti.
 */
object TrackArt {
    private const val GREY = 0xFF6B6B6B.toInt()

    fun draw(context: Context, steps: TrackSteps, accent: Int, arrived: Boolean): Bitmap {
        val dm = context.resources.displayMetrics
        fun dp(v: Float) = v * dm.density
        // Larga quanto lo schermo meno i margini della notifica: l'ImageView la rimpicciolisce se serve, senza stirarla.
        val w = (dm.widthPixels - dp(96f)).toInt().coerceAtLeast(dp(160f).toInt())
        val h = dp(26f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cy = h / 2f
        val r = dp(12f)
        val x0 = r
        val x1 = w - r
        fun x(minute: Int) = x0 + (x1 - x0) * (minute.toFloat() / steps.max.coerceAtLeast(1))
        val xp = x(steps.progress)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = dp(4f); strokeCap = Paint.Cap.ROUND }
        line.color = GREY
        c.drawLine(x0, cy, x1, cy, line)
        line.color = accent
        if (xp > x0) c.drawLine(x0, cy, xp, cy, line)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        val dots = listOf(0) + steps.points + steps.max
        for (m in dots) {
            dot.color = if (m <= steps.progress || (arrived && m == steps.max)) accent else GREY
            c.drawCircle(x(m), cy, dp(5f), dot)
        }
        if (!arrived) {
            // Il treno: cerchio bianco con un filo grigio (si vede anche sul tema chiaro) e l'icona nel colore della linea.
            dot.color = 0xFFFFFFFF.toInt()
            c.drawCircle(xp, cy, r, dot)
            dot.style = Paint.Style.STROKE
            dot.strokeWidth = dp(1f)
            dot.color = 0x33000000
            c.drawCircle(xp, cy, r - dp(0.5f), dot)
            ContextCompat.getDrawable(context, R.drawable.ic_train_tracker)?.mutate()?.let { icon ->
                icon.setTint(accent)
                val s = dp(8f).toInt()
                icon.setBounds((xp - s).toInt(), (cy - s).toInt(), (xp + s).toInt(), (cy + s).toInt())
                icon.draw(c)
            }
        }
        return bmp
    }
}
