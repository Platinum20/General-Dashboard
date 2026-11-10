package com.platinum20.dashboard.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.platinum20.dashboard.bank.Pocket
import com.platinum20.dashboard.bank.RevolutPockets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.Routes
import com.platinum20.dashboard.bank.RevolutLink
import com.platinum20.dashboard.bank.RevolutStore
import com.platinum20.dashboard.media.inFrame
import com.platinum20.dashboard.notes.Expenses
import java.time.Instant

/** La riga del saldo per la pagina Spese: null se Revolut non è collegato. */
internal fun revolutLine(linked: Boolean, expired: Boolean, balance: Long?): String? = when {
    !linked -> null
    expired -> "Revolut: accesso scaduto"
    balance != null -> "Saldo Revolut " + Expenses.format(balance)
    else -> "Revolut collegato"
}

/**
 * La card Revolut della Home, come quella della SIM (Antonio): in alto il saldo totale, sotto una
 * riga per tasca (conto e salvadanai). Solo lettura. Tocco = le spese del mese; prima del
 * collegamento, o a sessione scaduta, porta all'accesso.
 */
@Composable
internal fun RevolutCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    RevolutStore.init(context)
    val linked by RevolutStore.linked.collectAsStateWithLifecycle()
    val expired by RevolutStore.expired.collectAsStateWithLifecycle()
    val pockets by RevolutStore.pockets.collectAsStateWithLifecycle()
    val balance by RevolutStore.balance.collectAsStateWithLifecycle()
    val readAt by RevolutStore.readAt.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val scope = rememberCoroutineScope()
    // Vecchio dopo un'ora: la rilettura è ogni 14 minuti, quindi ne sono saltate almeno tre.
    val link = LinkStatus.of(linked, expired, readAt, System.currentTimeMillis(), staleAfter = 4 * RevolutStore.STALE_MS)
    // La rilettura ogni mezz'ora la fa già la Home (spese nel calendario): qui solo si mostra.
    Section(title = "Revolut", modifier = Modifier.sharedItem(SharedKeys.BANK, rounded = true)) {
        // Lo stato vero sopra il dato, con l'azione lì (1.402): "Rientra" apre subito l'accesso.
        StatusLine(
            link,
            action = if (expired) "Rientra" else "Aggiorna",
            onAction = {
                if (expired) inApp?.openRoute?.invoke(Routes.REVOLUT)
                else scope.launch { RevolutLink.refresh(context) }
            },
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = { if (!linked || expired) inApp?.openRoute?.invoke(Routes.REVOLUT) else onOpen() })
                .padding(16.dp),
        ) {
            // Prima che arrivi il wallet c'è almeno il saldo del conto (lettura delle versioni prima).
            val shown = pockets.ifEmpty { balance?.let { listOf(Pocket("main", "Conto", it, main = true)) } ?: emptyList() }
            // Mentre legge: la forma del saldo in grigio, già alla sua altezza (1.357), invece di una frase che poi si allunga.
            if (linked && !expired && shown.isEmpty()) {
                Column(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = "Sto leggendo il conto Revolut" },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Placeholder(84.dp, 10.dp)
                    Placeholder(150.dp, 30.dp)
                    PlaceholderLine(1f, 14.dp)
                    PlaceholderLine(1f, 14.dp)
                }
                return@Column
            }
            if (expired && shown.isEmpty()) return@Column
            if (!linked || shown.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Outlined.AccountBalanceWallet, TileAccent.BLUE, size = 36.dp, iconSize = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        when {
                            !linked -> "Collega Revolut: saldo e tasche qui"
                            else -> "Sto leggendo il conto Revolut…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
                return@Column
            }
            Column(Modifier.alpha(if (link.faded) FADED_ALPHA else 1f)) { RevolutPanel(shown) }
        }
    }
}

/** Saldo totale grande in alto, poi una tasca per riga: nome a sinistra, importo a destra. */
@Composable
private fun RevolutPanel(pockets: List<Pocket>) {
    val soft = LocalContentColor.current.copy(alpha = 0.7f)
    // Quando è stato letto lo dice la riga di stato, solo se il dato è vecchio (1.402).
    Text("SALDO TOTALE", style = MaterialTheme.typography.labelMedium, color = soft)
    Spacer(Modifier.height(4.dp))
    RollingText(Expenses.format(RevolutPockets.total(pockets)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    // Con una tasca sola il totale è già lei: niente riga doppia.
    if (pockets.size > 1) pockets.forEach { p ->
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, style = MaterialTheme.typography.bodyMedium, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            RollingText(Expenses.format(p.cents), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Il collegamento in Impostazioni › Account, come WindTre: qui si entra, si esce e si condividono i dati letti. */
@Composable
internal fun RevolutAccountRows() {
    val context = LocalContext.current
    RevolutStore.init(context)
    val linked by RevolutStore.linked.collectAsStateWithLifecycle()
    val expired by RevolutStore.expired.collectAsStateWithLifecycle()
    val readAt by RevolutStore.readAt.collectAsStateWithLifecycle()
    val balance by RevolutStore.balance.collectAsStateWithLifecycle()
    val inApp = LocalInAppNav.current
    val open = { inApp?.openRoute?.invoke(Routes.REVOLUT) }
    SettingsItem(
        icon = Icons.Outlined.AccountBalanceWallet,
        accent = TileAccent.BLUE,
        title = "Revolut",
        subtitle = when {
            !linked -> "Tocca per accedere: spese e saldo nel calendario"
            expired -> "Accesso scaduto · tocca per rientrare"
            readAt > 0 -> listOfNotNull(balance?.let { Expenses.format(it) }, "letto " + relativeTime(Instant.ofEpochMilli(readAt))).joinToString(" · ")
            else -> "Collegato"
        },
        onClick = if (!linked || expired) ({ open() }) else null,
        trailing = {
            when {
                !linked -> FilledTonalButton(onClick = { open() }) { Text("Accedi") }
                expired -> FilledTonalButton(onClick = { open() }) { Text("Rientra") }
                else -> DangerButton(onClick = { RevolutLink.forget(context) }) { Text("Esci") }
            }
        },
    )
    if (linked) {
        RowDivider()
        SettingsItem(
            icon = Icons.Outlined.Share,
            title = "Salva i dati letti di Revolut",
            subtitle = "Un file da mandarmi se non torna",
            onClick = {
                val where = saveTextToDownloads(context, stampedName("revolut-dati-letti"), RevolutStore.capturesText())
                com.platinum20.dashboard.capture.CaptureFeedback.show(
                    com.platinum20.dashboard.capture.CaptureNotice(
                        com.platinum20.dashboard.capture.CaptureMode.NOTE,
                        if (where != null) "Salvato in " + where.substringAfterLast('/') else "Non sono riuscito a salvarlo",
                        undo = null,
                        icon = Icons.Outlined.Share,
                        fly = false,
                    ),
                )
            },
            trailing = { FilledTonalButton(onClick = { shareTextFile(context, "revolut-dati-letti.txt", RevolutStore.capturesText()) }) { Text("Condividi") } },
        )
    }
}

/**
 * L'accesso sulla pagina ufficiale di Revolut (numero, poi conferma nell'app Revolut sul telefono).
 * Appena arrivano i dati del conto rilegge i movimenti e si chiude da sola.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevolutLoginScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    RevolutStore.init(context)
    val readAt by RevolutStore.readAt.collectAsStateWithLifecycle()
    val startedAt = remember { System.currentTimeMillis() }
    var canGoBack by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf<String?>(null) }
    val webView = remember {
        WebView(context).also { view ->
            RevolutLink.prepare(view, context) { canGoBack = view.canGoBack(); url = it }
            view.loadUrl(RevolutLink.HOME)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }
    // Chiave vera/falsa, non l'ora: la rilettura aggiorna l'ora e non deve ripartire da capo.
    val entered = readAt > startedAt && !RevolutLink.isLoginPage(url)
    LaunchedEffect(entered) {
        if (entered) {
            kotlinx.coroutines.delay(2_000)
            RevolutLink.pull(webView)
            kotlinx.coroutines.delay(5_000)
            android.webkit.CookieManager.getInstance().flush()
            onClose()
        }
    }
    BackHandler(enabled = canGoBack) { webView.goBack() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Accedi a Revolut") },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Chiudi") } },
                    actions = {
                        // Se la home del conto è aperta ma la lettura non parte da sola.
                        TextButton(onClick = { RevolutLink.pull(webView) }) { Text("Leggi") }
                    },
                )
                Text(
                    "Una volta sola: conferma l'accesso nell'app Revolut. Canvas legge solo, non può muovere soldi.",
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
