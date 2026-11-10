package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtQuality

/**
 * Le impostazioni del video (1.455): un pannello come la coda, con tutto in vista (qualità, velocità, sottotitoli,
 * ripeti) invece del fumetto a pagine di prima. Ogni scelta vale subito, dal punto in cui si è.
 */
internal object VideoSettings {
    /** Aperto dalla rotella sul video; il pannello sta sopra il player (PlayerScreen), anche a schermo intero. */
    var open by mutableStateOf(false)

    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** "1×", "1,5×", "0,75×". */
    fun speedLabel(speed: Float): String =
        (if (speed % 1f == 0f) "${speed.toInt()}" else speed.toString().replace('.', ',')) + "×"

    /** Le qualità dalla più alta, una sola per altezza. */
    fun qualities(list: List<YtQuality>): List<Int> = list.map { it.height }.filter { it > 0 }.distinct().sortedDescending()

    /** Il valore a destra di "Qualità": in automatico dice anche cosa si sta vedendo adesso. */
    fun qualityValue(auto: Boolean, showing: Int, fixed: Int): String =
        if (auto) (if (showing > 0) "Automatica · ${showing}p ora" else "Automatica") else "${fixed}p"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoSettingsPopup() {
    val context = LocalContext.current
    val now by YtPlayer.now.collectAsState()
    val stream = now?.stream
    // Niente video (la coda è passata a una canzone): il pannello non ha senso e si chiude.
    LaunchedEffect(stream == null) { if (stream == null) VideoSettings.open = false }
    DisposableEffect(Unit) { onDispose { VideoSettings.open = false } }
    PlayerSheet(
        open = VideoSettings.open && stream != null,
        onClose = { VideoSettings.open = false },
        title = "Video",
        sideShare = 0.45f,
        sideMax = 420.dp,
    ) { _, _ ->
        if (stream == null) return@PlayerSheet
        // Velocità e ripeti non arrivano come stato dal player: si tiene la scelta qui, letta all'apertura.
        var speed by remember(VideoSettings.open) { mutableStateOf(YtPlayer.speed()) }
        var loop by remember(VideoSettings.open) { mutableStateOf(YtPlayer.loop()) }
        val auto = YtPlayer.isAuto() && stream.dashManifest != null
        val showing = now?.playingHeight?.takeIf { it > 0 } ?: stream.height
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val heights = VideoSettings.qualities(stream.qualities)
            if (heights.isNotEmpty()) {
                SettingBlock("Qualità", VideoSettings.qualityValue(auto, showing, stream.height)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (stream.dashManifest != null) {
                            CanvasChip(auto, { YtPlayer.setQuality(context, 0) }, "Automatica")
                        }
                        heights.forEach { h ->
                            CanvasChip(!auto && h == stream.height, { YtPlayer.setQuality(context, h) }, "${h}p")
                        }
                    }
                }
            }
            SettingBlock("Velocità", if (speed == 1f) "Normale" else VideoSettings.speedLabel(speed)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VideoSettings.speeds.forEach { s ->
                        CanvasTabChip(
                            selected = s == speed,
                            onClick = { speed = s; YtPlayer.setSpeed(context, s) },
                            label = VideoSettings.speedLabel(s),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (stream.subtitles.isNotEmpty()) {
                val on = now?.subtitle
                val current = stream.subtitles.firstOrNull { it.language == on }
                SettingBlock("Sottotitoli", current?.label ?: "Spenti") {
                    // Le lingue possono essere tante: una fila che scorre di lato, la scelta sempre per prima dopo "Spenti".
                    val ordered = stream.subtitles.sortedByDescending { it.language == on }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                        item(key = "off") { CanvasChip(on == null, { YtPlayer.setSubtitle(context, null) }, "Spenti") }
                        items(ordered, key = { it.url }) { sub ->
                            CanvasChip(sub.language == on, { YtPlayer.setSubtitle(context, sub) }, sub.label)
                        }
                    }
                }
            }
            // Riga Canvas: icona, nome con una riga sotto, interruttore a destra.
            val toggle = hapticToggle { v -> loop = v; YtPlayer.setLoop(context, v) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Repeat, TileAccent.NEUTRAL, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ripeti il video", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (loop) "Ricomincia da capo alla fine" else "Alla fine passa al prossimo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = loop, onCheckedChange = toggle)
            }
        }
    }
}

/** Un gruppo del pannello: nome a sinistra, scelta attuale a destra, le opzioni sotto. */
@Composable
private fun SettingBlock(title: String, value: String, options: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        options()
    }
}
