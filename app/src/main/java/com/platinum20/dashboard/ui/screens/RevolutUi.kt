package com.platinum20.dashboard.ui.screens

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
    expired -> "Revolut: rifai l'accesso in Impostazioni › Account"
    balance != null -> "Saldo Revolut " + Expenses.format(balance)
    else -> "Revolut collegato"
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
                else -> TextButton(onClick = { RevolutLink.forget() }) { Text("Esci") }
            }
        },
    )
    if (linked) {
        RowDivider()
        SettingsItem(
            icon = Icons.Outlined.Share,
            title = "Salva i dati letti di Revolut",
            subtitle = "Un file in Download, da allegare se qualcosa non torna",
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
            trailing = { TextButton(onClick = { shareTextFile(context, "revolut-dati-letti.txt", RevolutStore.capturesText()) }) { Text("Condividi") } },
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
