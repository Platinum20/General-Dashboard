package com.platinum20.dashboard.ui.screens

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.shifts.CreatorsLink
import com.platinum20.dashboard.shifts.Shift
import com.platinum20.dashboard.shifts.ShiftStore
import java.time.Instant

/** "3 turni in arrivo": quanti ne restano da oggi in poi, per la riga in Impostazioni. */
internal fun shiftsLine(shifts: List<Shift>, now: Long = System.currentTimeMillis()): String {
    val next = shifts.count { it.end > now }
    return when (next) {
        0 -> "Nessun turno in arrivo"
        1 -> "1 turno in arrivo"
        else -> "$next turni in arrivo"
    }
}

/** Il collegamento in Impostazioni › Account: i turni di Creators Pro nel calendario. */
@Composable
internal fun ShiftAccountRows() {
    val context = LocalContext.current
    ShiftStore.init(context)
    val linked by ShiftStore.linked.collectAsStateWithLifecycle()
    val expired by ShiftStore.expired.collectAsStateWithLifecycle()
    val readAt by ShiftStore.readAt.collectAsStateWithLifecycle()
    val shifts by ShiftStore.shifts.collectAsStateWithLifecycle()
    val hasData by ShiftStore.hasData.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val open = { inApp?.openRoute?.invoke(Routes.SHIFTS) }
    SettingsItem(
        icon = Icons.Outlined.Work,
        accent = TileAccent.BLUE,
        title = "Turni Creators Pro",
        subtitle = when {
            !linked -> "Tocca per accedere: i tuoi turni nel calendario"
            expired -> "Accesso scaduto · tocca per rientrare"
            readAt > 0 -> shiftsLine(shifts) + " · letto " + relativeTime(Instant.ofEpochMilli(readAt))
            else -> "Collegato"
        },
        onClick = if (!linked || expired) ({ open() }) else null,
        trailing = {
            when {
                !linked -> FilledTonalButton(onClick = { open() }) { Text("Accedi") }
                expired -> FilledTonalButton(onClick = { open() }) { Text("Rientra") }
                else -> DangerButton(onClick = { CreatorsLink.forget() }) { Text("Esci") }
            }
        },
    )
    // Anche se nessun turno è stato riconosciuto: è proprio allora che serve mandare i dati (Antonio, 1.378).
    if (linked || hasData) {
        RowDivider()
        CaptureFileRow("Salva i dati letti dei turni", "turni-dati-letti") { ShiftStore.capturesText() }
    }
}

/**
 * L'accesso a Creators Pro, una volta sola. Dopo l'accesso Antonio apre la Timeline: appena la
 * pagina riceve i turni la schermata si chiude da sola, e da lì in poi Canvas la rilegge di nascosto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsLoginScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    ShiftStore.init(context)
    val readAt by ShiftStore.readAt.collectAsStateWithLifecycle()
    val shifts by ShiftStore.shifts.collectAsStateWithLifecycle()
    val startedAt = remember { System.currentTimeMillis() }
    var canGoBack by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    // La finestra dell'accesso con Google, sopra la pagina finché Google non la chiude.
    var popup by remember { mutableStateOf<WebView?>(null) }
    val popups = remember {
        object : CreatorsLink.PopupHost {
            override fun open(view: WebView) { popup = view }
            override fun close(view: WebView) {
                if (popup == view) popup = null
                view.destroy()
            }
        }
    }
    val webView = remember {
        WebView(context).also { view ->
            CreatorsLink.prepare(view, context, popups) { canGoBack = view.canGoBack(); url = it }
            view.loadUrl(ShiftStore.timelineUrl ?: CreatorsLink.HOME)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            android.webkit.CookieManager.getInstance().flush()
            popup?.destroy()
            webView.stopLoading()
            webView.destroy()
        }
    }
    // Turni letti sulla timeline: ancora un attimo per le altre risposte, poi si chiude.
    val done = readAt > startedAt && url?.contains("timeline", ignoreCase = true) == true
    LaunchedEffect(done) {
        if (done) {
            // Poi i turni di tutte le creator, non solo quella aperta.
            kotlinx.coroutines.delay(1_500)
            CreatorsLink.pull(webView)
            kotlinx.coroutines.delay(6_000)
            onClose()
        }
    }
    BackHandler(enabled = canGoBack && popup == null) { webView.goBack() }
    // Indietro sulla finestra di Google: torna indietro lì, o la chiude.
    BackHandler(enabled = popup != null) {
        val p = popup ?: return@BackHandler
        if (p.canGoBack()) p.goBack() else popups.close(p)
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Accedi a Creators Pro") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") } },
                    actions = {
                        // Con la timeline aperta: prende anche il testo della pagina e manda tutto, per sistemare la lettura.
                        IconButton(onClick = {
                            CreatorsLink.readText(webView) { shareTextFile(context, "turni-dati-letti.txt", ShiftStore.capturesText()) }
                        }) { Icon(Icons.Outlined.Share, contentDescription = "Manda i dati letti") }
                        if (readAt > startedAt) TextButton(onClick = onClose) { Text("Fatto") }
                    },
                )
                Text(
                    if (readAt > startedAt) "Letto: " + shiftsLine(shifts).lowercase()
                    else "Una volta sola: entra e apri la Timeline. Canvas legge solo i turni di Antonio Nesca.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        },
    ) { inner ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(inner)) {
            AndroidView(factory = { ctx -> webView.inFrame(ctx) }, modifier = Modifier.fillMaxSize())
            popup?.let { p ->
                androidx.compose.runtime.key(p) {
                    AndroidView(factory = { ctx -> p.inFrame(ctx) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
