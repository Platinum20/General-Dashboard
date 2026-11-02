package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Euro
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.sim.Allowance
import com.platinum20.dashboard.sim.SimStatus
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.sim.WindTreLink
import com.platinum20.dashboard.sim.WindTreParse
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "42 GB", "3,5 GB", "800 MB": come si legge un residuo di giga. */
internal fun gbLabel(gb: Double): String = when {
    gb >= 10 -> String.format(Locale.ITALIAN, "%.0f GB", gb)
    gb >= 1 -> String.format(Locale.ITALIAN, "%.1f GB", gb)
    else -> String.format(Locale.ITALIAN, "%.0f MB", gb * 1024)
}

internal fun euroLabel(v: Double): String = String.format(Locale.ITALIAN, "%.2f €", v)

/** "oggi", "domani", "tra 6 giorni". */
internal fun renewalLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String =
    when (val days = ChronoUnit.DAYS.between(today, day)) {
        0L -> "oggi"
        1L -> "domani"
        else -> "tra $days giorni"
    }

private fun Allowance.label(unit: String): String = when {
    unlimited -> "illimitati"
    total != null -> "${left.toInt()} di ${total.toInt()} $unit"
    else -> "${left.toInt()} $unit"
}

/** Sotto gli anelli: rinnovo, credito, quando è stato letto. */
internal fun SimStatus.footer(today: LocalDate = LocalDate.now()): String = buildList {
    renewal?.let { add("Rinnovo " + it.format(WindTreParse.shortDate) + " (" + renewalLabel(it, today) + ")") }
    credit?.let { add("Credito " + euroLabel(it)) }
}.joinToString(" · ")

/** Il numero dentro l'anello: "141", "7,5", "∞". */
internal fun ringValue(a: Allowance): String = when {
    a.unlimited -> "∞"
    a.left >= 10 || a.left == Math.floor(a.left) -> String.format(Locale.ITALIAN, "%.0f", a.left)
    else -> String.format(Locale.ITALIAN, "%.1f", a.left)
}

/** La riga sotto il nome: "su 200 GB", "illimitati", "su 50". */
internal fun ringCaption(a: Allowance, unit: String): String = when {
    a.unlimited -> "illimitati"
    a.total != null -> ("su " + (if (unit == "GB") ringValue(Allowance(a.total)) else a.total.toInt().toString()) + " " + unit).trim()
    else -> unit
}

/**
 * La card della Home, compatta: un anello per giga, minuti, SMS e roaming UE, sotto rinnovo e credito.
 * Prima del collegamento dice solo come collegarsi.
 */
@Composable
internal fun SimCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val linked by SimStore.linked.collectAsStateWithLifecycle()
    // Si aggiorna da sola quando la Home è aperta e il dato ha più di un'ora.
    AutoRefresh(everyMs = SimStore.STALE_MS) { if (linked && SimStore.isStale()) WindTreLink.refresh(context) }
    Section(title = "SIM") {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            val s = status
            if (s == null || !linked) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.SimCard, TileAccent.BLUE, size = 36.dp, iconSize = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (linked) "Sto leggendo i dati di WindTre…" else "Collega WindTre: giga, minuti e credito qui",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
                return@Column
            }
            SimRings(s)
            val footer = s.footer()
            Row(Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    footer,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    relativeTime(Instant.ofEpochMilli(s.at)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Gli anelli in fila, uno per soglia che WindTre ha dato. */
@Composable
private fun SimRings(s: SimStatus) {
    val rings = listOfNotNull(
        (s.data ?: Allowance(0.0)).let { Triple("Giga", it, "GB") },
        s.minutes?.let { Triple("Minuti", it, "min") },
        s.sms?.let { Triple("SMS", it, "") },
        s.roaming?.let { Triple("UE", it, "GB") },
    )
    Row(Modifier.fillMaxWidth()) {
        rings.forEach { (name, a, unit) ->
            SimRing(name, a, unit, known = name != "Giga" || s.data != null, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SimRing(name: String, a: Allowance, unit: String, known: Boolean, modifier: Modifier = Modifier) {
    val f = if (a.unlimited) 1f else a.fraction
    val low = f != null && f < 0.1f && !a.unlimited
    val color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { f ?: 0f },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 5.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
            Text(
                if (known) ringValue(a) else "–",
                style = if (a.unlimited) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        if (known) Text(
            ringCaption(a, unit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** I giga: numero grande, totale, barra. */
@Composable
private fun SimDataBlock(s: SimStatus, big: Boolean = false) {
    val data = s.data
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            when {
                data == null -> "Giga"
                data.unlimited -> "Giga illimitati"
                else -> gbLabel(data.left)
            },
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        )
        data?.total?.takeIf { !data.unlimited }?.let {
            Text(
                " di " + gbLabel(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            relativeTime(Instant.ofEpochMilli(s.at)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp),
        )
    }
    data?.fraction?.let { f ->
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { f },
            color = if (f < 0.1f) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * La pagina della SIM (dalla card): il dettaglio, l'aggiornamento e il collegamento. L'accesso si fa
 * sulla pagina ufficiale di WindTre, una volta; dopo Canvas legge i dati da solo, senza mostrarla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimScreen(onBack: () -> Unit, bottomPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val linked by SimStore.linked.collectAsStateWithLifecycle()
    val refreshing by SimStore.refreshing.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf(!SimStore.linked.value) }
    val scope = rememberCoroutineScope()

    if (login) {
        WindTreLogin(onClose = { login = false; if (!SimStore.linked.value) onBack() })
        return
    }
    Scaffold(topBar = { TopLevelBar(title = "SIM", subtitle = status?.offer ?: "WindTre", onBack = onBack) }) { inner ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp)
                .padding(bottom = maxOf(inner.calculateBottomPadding(), bottomPadding.calculateBottomPadding()) + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status
            // Il blocco colorato della pagina: i giga.
            CanvasCard(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    if (s == null) {
                        Text(if (refreshing) "Sto leggendo i dati…" else "Ancora nessun dato da WindTre", style = MaterialTheme.typography.titleMedium)
                    } else {
                        SimDataBlock(s, big = true)
                    }
                }
            }
            if (s != null) {
                val rows = listOfNotNull(
                    s.minutes?.let { Icons.Outlined.Call to "Minuti " + it.label("min") },
                    s.sms?.let { Icons.Outlined.Sms to ("SMS " + it.label("")).trim() },
                    s.roaming?.let { Icons.Outlined.Public to "Roaming UE " + (if (it.unlimited) "illimitati" else gbLabel(it.left) + (it.total?.let { t -> " di " + gbLabel(t) } ?: "")) },
                    s.credit?.let { Icons.Outlined.Euro to "Credito " + euroLabel(it) },
                    s.renewal?.let { Icons.Outlined.CalendarMonth to "Si rinnova " + renewalLabel(it) + " (" + it.format(WindTreParse.shortDate) + ")" },
                )
                if (rows.isNotEmpty()) Section(title = "Offerta") {
                    rows.forEachIndexed { i, (icon, text) ->
                        if (i > 0) RowDivider()
                        DetailRow(icon, text)
                    }
                }
            }
            Section(title = "Collegamento") {
                SettingsItem(
                    icon = Icons.Outlined.Refresh,
                    title = if (refreshing) "Aggiorno…" else "Aggiorna ora",
                    subtitle = s?.let { "Letto " + relativeTime(Instant.ofEpochMilli(it.at)) },
                    onClick = {
                        if (!refreshing) scope.launch {
                            val ok = WindTreLink.refresh(context)
                            if (!ok) CaptureFeedback.tell(context, "WindTre non ha risposto: forse va rifatto l'accesso", Icons.Outlined.SimCard)
                        }
                    },
                )
                RowDivider()
                SettingsItem(icon = Icons.AutoMirrored.Outlined.Login, title = "Accedi di nuovo", onClick = { login = true })
                RowDivider()
                // Se un numero esce sbagliato, quello che WindTre ha mandato serve a correggere la lettura.
                SettingsItem(
                    icon = Icons.Outlined.Share,
                    title = "Condividi i dati letti",
                    subtitle = "Per correggere la lettura se qualcosa non torna",
                    onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SimStore.capturesText())
                        context.startActivity(Intent.createChooser(share, null))
                    },
                )
                RowDivider()
                SettingsItem(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Scollega WindTre",
                    accent = TileAccent.ORANGE,
                    onClick = {
                        WindTreLink.forget()
                        onBack()
                    },
                )
            }
            if (!linked) DetailNote("Non collegato: tocca Accedi di nuovo.")
        }
    }
}

/** L'accesso, una volta sola, sulla pagina ufficiale: appena arrivano i dati si chiude da sola. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindTreLogin(onClose: () -> Unit) {
    val context = LocalContext.current
    val status by SimStore.status.collectAsStateWithLifecycle()
    val startedAt = remember { System.currentTimeMillis() }
    var canGoBack by remember { mutableStateOf(false) }
    val webView = remember {
        WebView(context).also { view ->
            WindTreLink.prepare(view, context) { canGoBack = view.canGoBack() }
            SimStore.beginSession()
            view.loadUrl(WindTreLink.HOME)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    // Arrivati i contatori l'accesso è fatto: si legge anche il testo e si torna alla pagina SIM.
    LaunchedEffect(status?.at) {
        if ((status?.at ?: 0) > startedAt) {
            kotlinx.coroutines.delay(4_000)
            WindTreLink.readText(webView)
            kotlinx.coroutines.delay(800)
            android.webkit.CookieManager.getInstance().flush()
            onClose()
        }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Accedi a WindTre") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") } },
                    actions = {
                        TextButton(onClick = {
                            WindTreLink.readText(webView)
                            android.webkit.CookieManager.getInstance().flush()
                            SimStore.setLinked(true)
                            onClose()
                        }) { Text("Fatto") }
                    },
                )
                Text(
                    "Una volta sola: poi Canvas legge giga e credito da solo. La password va solo a WindTre.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        },
    ) { inner ->
        AndroidView(factory = { ctx -> webView.inFrame(ctx) }, modifier = Modifier.fillMaxSize().padding(inner))
    }
}
