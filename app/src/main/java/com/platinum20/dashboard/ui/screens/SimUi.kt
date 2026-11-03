package com.platinum20.dashboard.ui.screens

import android.content.Intent
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.FilledTonalButton
import com.platinum20.dashboard.Routes
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

/** Il numero di una soglia: "141", "7,5", "50"; null se illimitata. */
internal fun meterValue(a: Allowance): String = when {
    a.left >= 10 || a.left == Math.floor(a.left) -> String.format(Locale.ITALIAN, "%.0f", a.left)
    else -> String.format(Locale.ITALIAN, "%.1f", a.left)
}

/** Come si legge una soglia piccola: "illimitati", "50 di 50", "7,5 di 7,5 GB". */
internal fun meterText(a: Allowance, unit: String): String = when {
    a.unlimited -> "illimitati"
    a.total != null -> (meterValue(a) + " di " + meterValue(Allowance(a.total)) + " " + unit).trim()
    else -> (meterValue(a) + " " + unit).trim()
}

/** "Rinnovo 8/11 · tra 5 giorni". */
internal fun renewalLine(day: LocalDate, today: LocalDate = LocalDate.now()): String =
    "Rinnovo " + day.format(WindTreParse.shortDate) + " · " + renewalLabel(day, today)

/**
 * La card della Home: tutto quello che c'è da sapere della SIM in un blocco solo, uguale (più grande)
 * nella pagina. Prima del collegamento dice solo come collegarsi.
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
                .padding(16.dp),
        ) {
            val s = status
            if (s == null || !linked) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            SimPanel(s)
        }
    }
}

/**
 * Il blocco della SIM: in alto l'offerta e quando è stato letto, i giga in grande con la barra, sotto
 * minuti, SMS e roaming UE in colonne con una barra sottile, in fondo credito e rinnovo.
 * Il colore del testo viene da fuori: nella card normale e nel blocco colorato della pagina.
 */
@Composable
private fun SimPanel(s: SimStatus, big: Boolean = false) {
    val soft = LocalContentColor.current.copy(alpha = 0.7f)
    val barColor = if (big) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val track = barColor.copy(alpha = 0.18f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            (s.offer ?: "WindTre").uppercase(Locale.ITALIAN),
            style = MaterialTheme.typography.labelMedium,
            color = soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(relativeTime(Instant.ofEpochMilli(s.at)), style = MaterialTheme.typography.labelSmall, color = soft)
    }
    Spacer(Modifier.height(if (big) 8.dp else 4.dp))
    val data = s.data
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            when {
                data == null -> "– GB"
                data.unlimited -> "Giga illimitati"
                else -> gbLabel(data.left)
            },
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        data?.total?.takeIf { !data.unlimited }?.let {
            Text(
                " di " + gbLabel(it),
                style = MaterialTheme.typography.bodyMedium,
                color = soft,
                modifier = Modifier.padding(bottom = if (big) 6.dp else 4.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    SimBar(if (data?.unlimited == true) 1f else data?.fraction ?: 0f, barColor, track, height = if (big) 8.dp else 6.dp)
    val small = listOfNotNull(
        s.minutes?.let { Triple("Minuti", it, "") },
        s.sms?.let { Triple("SMS", it, "") },
        s.roaming?.let { Triple("Roaming UE", it, "GB") },
    )
    if (small.isNotEmpty()) {
        Spacer(Modifier.height(if (big) 20.dp else 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            small.forEach { (name, a, unit) ->
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.labelSmall, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        meterText(a, unit),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    SimBar(if (a.unlimited) 1f else a.fraction ?: 0f, barColor, track, height = 3.dp)
                }
            }
        }
    }
    if (s.credit != null || s.renewal != null) {
        Spacer(Modifier.height(if (big) 20.dp else 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            s.credit?.let {
                Text("Credito ", style = MaterialTheme.typography.bodySmall, color = soft)
                Text(euroLabel(it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            s.renewal?.let {
                Text(renewalLine(it), style = MaterialTheme.typography.bodySmall, color = soft, maxLines = 1)
            }
        }
    }
}

/** Una barra orizzontale arrotondata: quanto resta. Sotto il 10% diventa rossa. */
@Composable
private fun SimBar(fraction: Float, color: Color, track: Color, height: Dp) {
    val fill = if (fraction < 0.1f) MaterialTheme.colorScheme.error else color
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(fill),
        )
    }
}

/**
 * La pagina della SIM (dalla card): tutto nel blocco colorato e "Aggiorna ora". Accesso, condivisione
 * dei dati letti e scollegamento stanno in Impostazioni › Account (Antonio). Senza collegamento si apre
 * direttamente l'accesso sulla pagina ufficiale di WindTre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimScreen(onBack: () -> Unit, bottomPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val refreshing by SimStore.refreshing.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf(!SimStore.linked.value) }
    val scope = rememberCoroutineScope()

    if (login) {
        WindTreLogin(onClose = { login = false; if (!SimStore.linked.value) onBack() })
        return
    }
    Scaffold(topBar = { TopLevelBar(title = "SIM", subtitle = "WindTre", onBack = onBack) }) { inner ->
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = inner.calculateTopPadding() + 8.dp)
                .padding(bottom = maxOf(inner.calculateBottomPadding(), bottomPadding.calculateBottomPadding()) + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status
            // Il blocco colorato della pagina: tutto qui dentro.
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
                        SimPanel(s, big = true)
                    }
                }
            }
            Section(title = "Dati") {
                SettingsItem(
                    icon = Icons.Outlined.Refresh,
                    title = if (refreshing) "Aggiorno…" else "Aggiorna ora",
                    subtitle = "Collegamento e accesso in Impostazioni › Account",
                    onClick = {
                        if (!refreshing) scope.launch {
                            val ok = WindTreLink.refresh(context)
                            if (!ok) CaptureFeedback.tell(context, "WindTre non ha risposto: rifai l'accesso da Impostazioni", Icons.Outlined.SimCard)
                        }
                    },
                )
            }
        }
    }
}

/**
 * WindTre in Impostazioni › Account: stato del collegamento, accesso (apre la pagina SIM, che parte
 * dall'accesso), "Condividi i dati letti" per correggere la lettura, "Esci".
 */
@Composable
internal fun WindTreAccountRows() {
    val context = LocalContext.current
    SimStore.init(context)
    val linked by SimStore.linked.collectAsStateWithLifecycle()
    val status by SimStore.status.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    SettingsItem(
        icon = Icons.Outlined.SimCard,
        accent = TileAccent.BLUE,
        title = "WindTre",
        subtitle = when {
            !linked -> "Tocca per accedere: giga, minuti e credito in Home"
            status != null -> "Collegato · letto " + relativeTime(Instant.ofEpochMilli(status!!.at))
            else -> "Collegato"
        },
        onClick = if (!linked) ({ inApp?.openRoute?.invoke(Routes.SIM) }) else null,
        trailing = {
            if (linked) TextButton(onClick = { WindTreLink.forget() }) { Text("Esci") }
            else FilledTonalButton(onClick = { inApp?.openRoute?.invoke(Routes.SIM) }) { Text("Accedi") }
        },
    )
    if (linked) {
        RowDivider()
        // Se un numero esce sbagliato, quello che WindTre ha mandato serve a correggere la lettura.
        SettingsItem(
            icon = Icons.Outlined.Share,
            title = "Condividi i dati letti di WindTre",
            subtitle = "Per correggere la lettura se qualcosa non torna",
            onClick = {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SimStore.capturesText())
                context.startActivity(Intent.createChooser(share, null))
            },
        )
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
