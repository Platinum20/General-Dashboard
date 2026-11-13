package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.platinum20.dashboard.media.youtube.player.YtPlayer
import com.platinum20.dashboard.media.youtube.player.YtQuality
import com.platinum20.dashboard.media.youtube.player.YtSubtitle

/**
 * Le impostazioni del video (1.513, Antonio ha scelto la proposta A): un fumetto di vetro con quattro righe Canvas
 * (Qualità, Velocità, Sottotitoli, Ripeti). Una riga alla volta si apre e mostra le sue scelte su UNA fila che scorre
 * di lato (niente tasti su due righe). In verticale sta in basso e il video resta in vista; in orizzontale sta a destra.
 */
internal object VideoSettings {
    /** Aperto dalla rotella sul video; il fumetto è disegnato fuori dal NavHost (MainActivity), così il vetro sfoca il video. */
    var open by mutableStateOf(false)

    enum class Group { QUALITY, SPEED, SUBTITLES }

    /** La riga aperta quando si riapre il fumetto: l'ultima che si è cambiata. */
    var last by mutableStateOf(Group.QUALITY)

    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

    /** "1×", "1,5×", "0,75×". */
    fun speedLabel(speed: Float): String =
        (if (speed % 1f == 0f) "${speed.toInt()}" else speed.toString().replace('.', ',')) + "×"

    /** Sulla scelta la velocità normale si chiama così, non "1×". */
    fun speedChip(speed: Float): String = if (speed == 1f) "Normale" else speedLabel(speed)

    /** Le qualità dalla più alta, una sola per altezza. */
    fun qualities(list: List<YtQuality>): List<Int> = list.map { it.height }.filter { it > 0 }.distinct().sortedDescending()

    /** Le più usate per prime (si vedono senza scorrere), poi le altre dalla più alta. */
    val common = listOf(1080, 720, 480, 360)
    fun qualityRow(heights: List<Int>): List<Int> =
        common.filter { it in heights } + heights.filter { it !in common }.sortedDescending()

    /** Sottotitoli: prima la lingua originale, poi italiano, poi gli altri (i normali prima degli automatici). */
    fun subtitleRow(subs: List<YtSubtitle>): List<YtSubtitle> =
        subs.sortedWith(compareBy<YtSubtitle>({ !it.original }, { !it.language.startsWith("it") }, { it.auto }))

    /** Il valore a destra di "Qualità": in automatico dice anche cosa si sta vedendo adesso. */
    fun qualityValue(auto: Boolean, showing: Int, fixed: Int): String =
        if (auto) (if (showing > 0) "Automatica · ${showing}p ora" else "Automatica") else "${fixed}p"
}

@Composable
internal fun VideoSettingsLayer() {
    val context = LocalContext.current
    val now by YtPlayer.now.collectAsState()
    val stream = now?.stream
    // Niente video (la coda è passata a una canzone): il fumetto non ha senso e si chiude.
    LaunchedEffect(stream == null) { if (stream == null) VideoSettings.open = false }
    val visible = VideoSettings.open && stream != null
    val close = { VideoSettings.open = false }
    if (visible) BackHandler(onBack = close)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideways = QueueSheet.sideways(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val maxH = maxHeight
        AnimatedVisibility(visible, enter = fadeIn(Motion.settle()), exit = fadeOut(tween(Motion.EXIT_MS)), modifier = Modifier.matchParentSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .pointerInput(Unit) { detectTapGestures { close() } },
            )
        }
        val swipe = rememberSwipeAwayState()
        AnimatedVisibility(
            visible,
            enter = fadeIn(Motion.settle()) + scaleIn(Motion.lively(), initialScale = 0.92f) +
                if (sideways) slideInHorizontally(Motion.lively()) { it / 3 } else slideInVertically(Motion.lively()) { it / 3 },
            exit = fadeOut(tween(Motion.EXIT_MS - 20)) +
                if (sideways) slideOutHorizontally(tween(Motion.EXIT_MS, easing = Motion.Accelerate)) { it / 3 }
                else slideOutVertically(tween(Motion.EXIT_MS, easing = Motion.Accelerate)) { it / 3 },
            modifier = Modifier
                .align(if (sideways) Alignment.CenterEnd else Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(if (sideways) WindowInsetsSides.End + WindowInsetsSides.Vertical else WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(if (sideways) PaddingValues(end = 16.dp, top = 12.dp, bottom = 12.dp) else PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                SwipeAwayMark(swipe)
                val shape = RoundedCornerShape(26.dp)
                Surface(
                    shape = shape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = cardBorder(),
                    modifier = Modifier
                        .then(if (sideways) Modifier.width(380.dp) else Modifier.widthIn(max = 480.dp).fillMaxWidth())
                        .heightIn(max = maxH - 24.dp)
                        .swipeAway(swipe) { if (visible) close() }
                        .glass(shape)
                        // I tocchi sul fumetto restano al fumetto, non chiudono.
                        .pointerInput(Unit) { detectTapGestures { } },
                ) {
                    if (stream != null) SettingsRows(context, now, stream)
                }
            }
        }
    }
}

@Composable
private fun SettingsRows(
    context: android.content.Context,
    now: com.platinum20.dashboard.media.youtube.player.YtNow?,
    stream: com.platinum20.dashboard.media.youtube.player.YtStream,
) {
    // Velocità e ripeti non arrivano come stato dal player: si tiene la scelta qui, letta all'apertura.
    var speed by remember(VideoSettings.open) { mutableStateOf(YtPlayer.speed()) }
    var loop by remember(VideoSettings.open) { mutableStateOf(YtPlayer.loop()) }
    var openGroup by remember(VideoSettings.open) { mutableStateOf<VideoSettings.Group?>(VideoSettings.last) }
    val auto = YtPlayer.isAuto() && stream.dashManifest != null
    val showing = now?.playingHeight?.takeIf { it > 0 } ?: stream.height
    val toggleGroup = { g: VideoSettings.Group -> openGroup = if (openGroup == g) null else g }
    val chose = { g: VideoSettings.Group -> VideoSettings.last = g }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
        val heights = VideoSettings.qualityRow(VideoSettings.qualities(stream.qualities))
        if (heights.isNotEmpty()) {
            val labels = (if (stream.dashManifest != null) listOf("Automatica") else emptyList()) + heights.map { "${it}p" }
            val selected = if (auto) 0 else labels.indexOf("${stream.height}p")
            Group(Icons.Outlined.HighQuality, "Qualità", VideoSettings.qualityValue(auto, showing, stream.height), openGroup == VideoSettings.Group.QUALITY, { toggleGroup(VideoSettings.Group.QUALITY) }) {
                ChoiceRow(labels, selected) { i ->
                    chose(VideoSettings.Group.QUALITY)
                    YtPlayer.setQuality(context, if (labels[i] == "Automatica") 0 else heights[i - (labels.size - heights.size)])
                }
            }
        }
        Group(Icons.Outlined.Speed, "Velocità", VideoSettings.speedChip(speed), openGroup == VideoSettings.Group.SPEED, { toggleGroup(VideoSettings.Group.SPEED) }) {
            ChoiceRow(VideoSettings.speeds.map(VideoSettings::speedChip), VideoSettings.speeds.indexOf(speed)) { i ->
                chose(VideoSettings.Group.SPEED)
                speed = VideoSettings.speeds[i]
                YtPlayer.setSpeed(context, speed)
            }
        }
        if (stream.subtitles.isNotEmpty()) {
            val on = now?.subtitle
            val subs = VideoSettings.subtitleRow(stream.subtitles)
            val current = subs.firstOrNull { it.language == on }
            val labels = listOf("Spenti") + subs.map { if (it.original) "${it.label} · originale" else it.label }
            val selected = if (current == null) 0 else subs.indexOf(current) + 1
            Group(Icons.Outlined.ClosedCaption, "Sottotitoli", current?.label ?: "Spenti", openGroup == VideoSettings.Group.SUBTITLES, { toggleGroup(VideoSettings.Group.SUBTITLES) }) {
                ChoiceRow(labels, selected) { i ->
                    chose(VideoSettings.Group.SUBTITLES)
                    YtPlayer.setSubtitle(context, if (i == 0) null else subs[i - 1])
                }
            }
        }
        // Riga Canvas: icona, nome con una riga sotto, interruttore a destra.
        val toggle = hapticToggle { v -> loop = v; YtPlayer.setLoop(context, v) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.Repeat, TileAccent.NEUTRAL, size = 38.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            RowTexts("Ripeti il video", if (loop) "Ricomincia da capo alla fine" else "Alla fine passa al prossimo", Modifier.weight(1f))
            Switch(checked = loop, onCheckedChange = toggle)
        }
    }
}

/** Una riga che si apre: icona, nome con la scelta attuale sotto, freccia; aperta, le scelte su una fila sotto. */
@Composable
private fun Group(icon: ImageVector, title: String, value: String, expanded: Boolean, onToggle: () -> Unit, choices: @Composable () -> Unit) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.settle(), label = "freccia")
    val tint by animateFloatAsState(if (expanded) 0.08f else 0f, Motion.settle(), label = "aperta")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = tint)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, TileAccent.NEUTRAL, size = 38.dp, iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            RowTexts(title, value, Modifier.weight(1f))
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Chiudi" else "Apri",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).rotate(turn),
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically(Motion.settle()) + fadeIn(Motion.settle()), exit = shrinkVertically(Motion.settle()) + fadeOut(Motion.settle())) {
            Box(Modifier.padding(bottom = 10.dp)) { choices() }
        }
    }
}

@Composable
private fun RowTexts(title: String, line: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Le scelte di un gruppo su UNA fila (Antonio: mai su due righe): scorre di lato, il bordo destro sfuma se ce ne sono altre. */
@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    // Si apre con la scelta attuale in vista.
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 1).coerceAtLeast(0))
    val fade = state.canScrollForward
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (fade) {
                    drawRect(
                        Brush.horizontalGradient(0.85f to Color.Black, 1f to Color.Transparent, startX = 0f, endX = size.width),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
    ) {
        itemsIndexed(labels, key = { i, l -> "$i-$l" }) { i, label ->
            CanvasChip(i == selected, { onPick(i) }, label)
        }
    }
}
