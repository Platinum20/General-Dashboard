package com.platinum20.dashboard.ui.screens

import androidx.compose.ui.draw.alpha
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
import com.platinum20.dashboard.sim.GigaVerdict
import com.platinum20.dashboard.sim.SimUsage
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Il blocco della SIM: in alto l'offerta e quando è stato letto, poi un dato per riga.
 * Il roaming UE solo nella pagina (big).
 * Il colore del testo viene da fuori: nella card normale e nel blocco colorato della pagina.
 */
@Composable
internal fun SimPanel(s: SimStatus, big: Boolean = false, faded: Boolean = false) {
    val soft = LocalContentColor.current.copy(alpha = 0.7f)
    val barColor = if (big) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
    val track = barColor.copy(alpha = 0.18f)
    // Nella card della Home il titolo "SIM" basta (1.450, Antonio): niente riga dell'offerta sopra.
    if (big) Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            (s.offer ?: "WindTre").uppercase(Locale.ITALIAN),
            style = MaterialTheme.typography.labelMedium,
            color = soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Nella card l'ora la dice la riga di stato, solo quando il dato è vecchio (1.402).
        if (big) Text(relativeTime(Instant.ofEpochMilli(s.at)), style = MaterialTheme.typography.labelSmall, color = soft)
    }
    // Un dato per riga (Antonio): nome a sinistra, valore a destra, barra sottile sotto le soglie.
    // Il roaming UE solo nella pagina, non nella card della Home.
    val rows = simRows(s, big)
    rows.forEachIndexed { i, r ->
        if (big || i > 0) Spacer(Modifier.height(if (big) 14.dp else 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.weight(1f))
            // Il dato vecchio resta, sbiadito (1.402): c'è, ma non è di adesso.
            RollingText(
                r.value,
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = if (faded) Modifier.alpha(FADED_ALPHA) else Modifier,
            )
        }
        r.meter?.let { a ->
            Spacer(Modifier.height(6.dp))
            SimBar(if (a.unlimited) 1f else a.fraction ?: 0f, barColor, track, height = if (big) 5.dp else 4.dp)
        }
    }
    // In fondo, sulla stessa riga: credito a sinistra, rinnovo a destra (Antonio).
    if (s.credit != null || s.renewal != null) {
        Spacer(Modifier.height(if (big) 18.dp else 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            s.credit?.let {
                Text("Credito ", style = MaterialTheme.typography.bodyMedium, color = soft)
                Text(euroLabel(it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.weight(1f))
            s.renewal?.let {
                Text(renewalLine(it), style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 1)
            }
        }
    }
}

/** Una riga del blocco SIM: nome, valore e, per giga/minuti/SMS, quanto resta. */
internal data class SimRow(val name: String, val value: String, val meter: Allowance?)

/**
 * Le righe con la linea: giga sempre, minuti e SMS quando WindTre li dà; il roaming UE solo nella pagina.
 * Pura, per i test.
 */
internal fun simRows(s: SimStatus, big: Boolean = false): List<SimRow> = buildList {
    val data = s.data
    add(SimRow("Giga", data?.let { if (it.unlimited) "illimitati" else gbLabel(it.left) + (it.total?.let { t -> " di " + gbLabel(t) } ?: "") } ?: "–", data))
    s.minutes?.let { add(SimRow("Minuti", meterText(it, ""), it)) }
    s.sms?.let { add(SimRow("SMS", meterText(it, ""), it)) }
    if (big) s.roaming?.let { add(SimRow("Roaming UE", meterText(it, "GB"), it)) }
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
 * La pagina della SIM (dalla card): tutto nel blocco colorato, si aggiorna tirando giù. Accesso, condivisione
 * dei dati letti e scollegamento stanno in Impostazioni › Account (Antonio). Senza collegamento si apre
 * direttamente l'accesso sulla pagina ufficiale di WindTre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimScreen(onBack: () -> Unit, bottomPadding: PaddingValues = PaddingValues()) {
    val context = LocalContext.current
    SimStore.init(context)
    val status by SimStore.status.collectAsStateWithLifecycle()
    val history by SimStore.history.collectAsStateWithLifecycle()
    val refreshing by SimStore.refreshing.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf(!SimStore.linked.value) }
    val scope = rememberCoroutineScope()

    if (login) {
        WindTreLogin(onClose = { login = false; if (!SimStore.linked.value) onBack() })
        return
    }
    Scaffold(modifier = Modifier.sharedItem(SharedKeys.SIM, rounded = true), topBar = { TopLevelBar(
        title = "SIM",
        // Quando è stato letto, sotto il titolo (1.357): si rilegge da solo, la frase "Tira giù" non serve più.
        subtitle = status?.let { "WindTre · letto " + relativeTime(Instant.ofEpochMilli(it.at)) } ?: "WindTre",
        onBack = onBack,
    ) }) { inner ->
        // Si aggiorna tirando giù la pagina, niente tasto (Antonio).
        PullToRefreshBox(
            isRefreshing = refreshing,
            state = rememberHapticPullState(refreshing),
            onRefresh = {
                if (!refreshing) scope.launch {
                    val ok = WindTreLink.refresh(context)
                    if (!ok) CaptureFeedback.tell(context, "WindTre non ha risposto: rifai l'accesso da Impostazioni", Icons.Outlined.SimCard)
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = inner.calculateTopPadding()),
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .padding(bottom = maxOf(inner.calculateBottomPadding(), bottomPadding.calculateBottomPadding()) + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val s = status
            // Il blocco colorato della pagina: solo i giga (punto 46). Quando è stato letto sta una volta sola, sotto il titolo.
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
                        SimGigaHero(s, SimUsage.verdict(s, history, LocalDate.now()))
                    }
                }
            }
            // Il resto in righe, come Impostazioni: niente barre per ciò che è illimitato.
            if (s != null) {
                val rows = simPageRows(s)
                if (rows.isNotEmpty()) CanvasCard {
                    Column {
                        rows.forEachIndexed { i, (name, value) ->
                            if (i > 0) RowDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                RollingText(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

/** L'eroe della pagina SIM: l'offerta, i giga grandi e la barra di quanto resta. */
@Composable
private fun SimGigaHero(s: SimStatus, verdict: GigaVerdict?) {
    val soft = LocalContentColor.current.copy(alpha = 0.7f)
    val barColor = MaterialTheme.colorScheme.onPrimaryContainer
    Text(
        (s.offer ?: "WindTre").uppercase(Locale.ITALIAN),
        style = MaterialTheme.typography.labelMedium,
        color = soft,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(6.dp))
    val data = s.data
    RollingText(
        data?.let { if (it.unlimited) "Giga illimitati" else gbLabel(it.left) } ?: "–",
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.SemiBold,
    )
    data?.total?.takeIf { !data.unlimited }?.let {
        Text("rimasti di " + gbLabel(it), style = MaterialTheme.typography.bodyMedium, color = soft)
    }
    if (data != null && !data.unlimited) {
        Spacer(Modifier.height(12.dp))
        SimBar(data.fraction ?: 0f, barColor, barColor.copy(alpha = 0.18f), height = 6.dp)
    }
    // La domanda della pagina (punto 47): bastano fino al rinnovo? Sotto la barra, col perché in una riga.
    verdict?.let { v ->
        val (answer, why) = gigaVerdictText(v)
        Spacer(Modifier.height(14.dp))
        Text(answer, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(why, style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** La risposta e il suo perché: "Ti bastano" / "1,2 GB al giorno · ne avanzano 15 GB". */
internal fun gigaVerdictText(v: GigaVerdict): Pair<String, String> = when (v) {
    GigaVerdict.Learning -> "Ti dico presto se bastano" to "Sto imparando quanto usi al giorno"
    is GigaVerdict.Enough -> "Ti bastano fino al rinnovo" to
        (gbLabel(v.perDay) + " al giorno · " + if (v.spare >= 0.05) "ne avanzano " + gbLabel(v.spare) else "giusti giusti")
    is GigaVerdict.NotEnough -> "Non ti bastano" to
        (gbLabel(v.perDay) + " al giorno · finiscono verso il " + v.runsOut.format(WindTreParse.shortDate))
}

/** Le righe sotto l'eroe della pagina SIM: minuti, SMS, roaming, credito, rinnovo (solo quelle note). */
internal fun simPageRows(s: SimStatus, today: LocalDate = LocalDate.now()): List<Pair<String, String>> = buildList {
    s.minutes?.let { add("Minuti" to meterText(it, "")) }
    s.sms?.let { add("SMS" to meterText(it, "")) }
    s.roaming?.let { add("Roaming UE" to meterText(it, "GB")) }
    s.credit?.let { add("Credito" to euroLabel(it)) }
    s.renewal?.let { add("Rinnovo" to it.format(WindTreParse.shortDate) + " · " + renewalLabel(it, today)) }
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
            if (linked) DangerButton(onClick = { WindTreLink.forget(context) }) { Text("Esci") }
            else FilledTonalButton(onClick = { inApp?.openRoute?.invoke(Routes.SIM) }) { Text("Accedi") }
        },
    )
    if (linked) {
        RowDivider()
        // Se un numero esce sbagliato, quello che WindTre ha mandato serve a correggere la lettura.
        CaptureFileRow("Salva i dati letti di WindTre", "windtre-dati-letti") { SimStore.capturesText() }
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
