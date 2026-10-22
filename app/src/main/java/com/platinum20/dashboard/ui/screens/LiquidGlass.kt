package com.platinum20.dashboard.ui.screens

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Vetro liquido (Android 13+): la pagina sotto la barra e il mini player viene sfocata e, vicino ai bordi,
 * piegata come da una lente, con un filo di luce sul bordo. Sotto Android 13 resta il vetro di Haze.
 */
val liquidGlassSupported: Boolean get() = Build.VERSION.SDK_INT >= 33

/** Le pagine registrate in un livello grafico, da cui il vetro legge cosa c'è sotto di sé. */
class Backdrop {
    internal var layer: GraphicsLayer? = null
    internal var origin = Offset.Zero
    /** Cambia a ogni disegno delle pagine, così il vetro si ridisegna con loro. */
    internal var frame by mutableIntStateOf(0)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Le pagine sotto il vetro: le disegna normalmente e le tiene anche nel livello del [Backdrop]. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = composed {
    val layer = rememberGraphicsLayer()
    this
        .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
        .drawWithContent {
            // I livelli grafici esistono solo col disegno accelerato; altrimenti (catture a bitmap, test) si disegna e basta.
            if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                backdrop.layer = layer
                backdrop.frame++
            } else {
                drawContent()
            }
        }
}

/**
 * Disegna dietro al contenuto il vetro liquido: pagina sfocata e rifratta ai bordi, velo [tint] e riflessi.
 * Il chiamante ritaglia già sulla [shape].
 */
@RequiresApi(33)
internal fun Modifier.liquidGlass(backdrop: Backdrop, shape: Shape, tint: Color): Modifier = composed {
    val effectLayer = rememberGraphicsLayer()
    // Se lo shader non si compila (driver strani, test), resta il vetro sfocato senza lente.
    val shader = remember { runCatching { RuntimeShader(LIQUID_GLASS_SHADER) }.getOrNull() }
    var position by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithContent {
            val source = backdrop.layer
            @Suppress("UNUSED_VARIABLE") val frame = backdrop.frame
            if (source != null && size.width > 0f && size.height > 0f && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                // Margine attorno: la lente pesca un po' oltre il bordo e la sfocatura non scurisce i lati.
                val pad = 24.dp.toPx()
                val radius = when (val outline = shape.createOutline(size, layoutDirection, this)) {
                    is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x
                    else -> 0f
                }
                val blur = 8.dp.toPx()
                val blurEffect = RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP)
                val lens = shader?.let {
                    runCatching {
                        it.setFloatUniform("size", size.width, size.height)
                        it.setFloatUniform("pad", pad)
                        it.setFloatUniform("radius", radius.coerceAtMost(minOf(size.width, size.height) / 2f))
                        it.setFloatUniform("band", minOf(18.dp.toPx(), size.height / 2f))
                        it.setFloatUniform("strength", 7.dp.toPx())
                        RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(it, "content"), blurEffect)
                    }.getOrNull()
                }
                effectLayer.renderEffect = (lens ?: blurEffect).asComposeRenderEffect()
                val offset = position - backdrop.origin
                effectLayer.record(IntSize(ceil(size.width + 2 * pad).toInt(), ceil(size.height + 2 * pad).toInt())) {
                    translate(-offset.x + pad, -offset.y + pad) { drawLayer(source) }
                }
                translate(-pad, -pad) { drawLayer(effectLayer) }
            }
            drawRect(tint)
            drawContent()
        }
}

/**
 * Lente sui bordi di un rettangolo arrotondato: dentro una fascia vicina al bordo il punto letto si sposta
 * verso il centro (la pagina si ingrandisce e si curva sul bordo, senza riflettere cosa c'è fuori),
 * poi un filo di luce sul bordo e una leggera lucentezza in alto. Niente aberrazione cromatica: faceva aloni.
 */
private const val LIQUID_GLASS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float radius;
uniform float band;
uniform float strength;

float roundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - pad - hs;
    float d = roundRect(p, hs, radius);
    if (d > 0.0) return content.eval(coord);
    float inside = -d;
    float2 n = float2(
        roundRect(p + float2(1.0, 0.0), hs, radius) - roundRect(p - float2(1.0, 0.0), hs, radius),
        roundRect(p + float2(0.0, 1.0), hs, radius) - roundRect(p - float2(0.0, 1.0), hs, radius)
    );
    float len = length(n);
    n = len > 0.0001 ? n / len : float2(0.0, 0.0);
    // Lente: vicino al bordo si legge un po' più verso il centro, così la pagina sotto si ingrandisce e si curva.
    float t = clamp(1.0 - inside / band, 0.0, 1.0);
    half4 c = content.eval(coord - n * (t * t * strength));
    // Riflesso sottile sul bordo, più chiaro in alto, e una lucentezza morbida nella metà superiore.
    float rim = smoothstep(1.6, 0.0, inside);
    float light = 0.35 + 0.65 * clamp(dot(n, float2(-0.3, -0.95)), 0.0, 1.0);
    float gloss = clamp(1.0 - (coord.y - pad) / (size.y * 0.55), 0.0, 1.0);
    float glow = rim * light * 0.55 + gloss * gloss * 0.10;
    c.rgb = mix(c.rgb, half3(1.0), half(clamp(glow, 0.0, 0.6)));
    return c;
}
"""
