package com.platinum20.dashboard.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.mail.GmailAuth
import com.platinum20.dashboard.mail.MailMessage
import com.platinum20.dashboard.mail.MailStore
import com.platinum20.dashboard.net.userMessage
import com.platinum20.dashboard.work.MailWorker
import kotlinx.coroutines.launch
import java.time.Instant

/** Collegamento a Gmail: chiede il consenso a Google e, appena c'è, legge le mail. */
private class GmailConnect(val start: () -> Unit, val busy: Boolean, val problem: String?)

@Composable
private fun rememberGmailConnect(onConnected: () -> Unit): GmailConnect {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        busy = false
        if (GmailAuth.handleConsent(context, result.data)) onConnected() else problem = "Accesso annullato"
    }
    return GmailConnect(
        start = {
            if (!busy) {
                busy = true
                problem = null
                scope.launch {
                    when (val outcome = GmailAuth.authorize(context)) {
                        is GmailAuth.Outcome.Authorized -> { busy = false; onConnected() }
                        is GmailAuth.Outcome.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(outcome.sender).build())
                        is GmailAuth.Outcome.Failed -> {
                            busy = false
                            problem = if (outcome.notConfigured) "Google non riconosce ancora l'app per Gmail: manca la configurazione su Google Cloud"
                            else "Accesso non riuscito: ${outcome.message}"
                        }
                    }
                }
            }
        },
        busy = busy,
        problem = problem,
    )
}

/** In Oggi: le mail importanti non lette, solo mittente e oggetto. Si apre in Canvas, non in Gmail. */
@Composable
fun MailCard(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    MailStore.init(context)
    val mails by MailStore.important.collectAsStateWithLifecycle()
    val needsConsent by GmailAuth.needsConsent.collectAsStateWithLifecycle()
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val connect = rememberGmailConnect(onConnected = { scope.launch { runCatching { MailStore.sync(context) } } })
    if (mails.isEmpty() && !needsConsent) return

    Section(title = if (mails.isEmpty()) "Posta" else "Posta · ${mails.size}") {
        Column {
            if (needsConsent) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Mail, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        connect.problem ?: "Google chiede di ricollegare Gmail",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = !connect.busy, onClick = { haptic(Haptic.TICK); connect.start() }) { Text("Ricollega") }
                }
            }
            mails.forEachIndexed { index, m ->
                if (index > 0 || needsConsent) RowDivider()
                MailRow(m, onClick = { haptic(Haptic.TICK); onOpen(m.id) })
            }
        }
    }
}

@Composable
private fun MailRow(m: MailMessage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.from, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                m.subject,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            relativeTime(Instant.ofEpochMilli(m.at)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Impostazioni > Compila da solo > Gmail: collega, leggi adesso, scollega. */
@Composable
internal fun GmailSettingsGroup() {
    val context = LocalContext.current
    MailStore.init(context)
    val connected by GmailAuth.connected.collectAsStateWithLifecycle()
    val needsConsent by GmailAuth.needsConsent.collectAsStateWithLifecycle()
    val lastSync by MailStore.lastSync.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    fun syncNow() {
        syncing = true
        result = null
        scope.launch {
            result = runCatching { MailStore.sync(context) }.fold(
                onSuccess = { n ->
                    when {
                        GmailAuth.needsConsent.value -> "Google chiede di ricollegare Gmail"
                        n == 0 -> "Niente di nuovo nelle mail"
                        n == 1 -> "Trovata 1 cosa: la vedi in Oggi"
                        else -> "Trovate $n cose: le vedi in Oggi"
                    }
                },
                onFailure = { it.userMessage("Gmail non risponde, riprova") },
            )
            syncing = false
        }
    }
    val connect = rememberGmailConnect(onConnected = {
        MailWorker.schedule(context)
        syncNow()
    })

    SettingsGroup {
        SettingsItem(
            icon = Icons.Outlined.Mail,
            title = if (connected) "Gmail collegato" else "Collega Gmail",
            subtitle = connect.problem ?: when {
                !connected -> "Legge le mail intere, non solo l'anteprima della notifica"
                needsConsent -> "Google chiede di ricollegarlo"
                syncing -> "Leggo le mail…"
                result != null -> result
                lastSync > 0 -> "Letta " + relativeTime(Instant.ofEpochMilli(lastSync)) + " · ogni ora e a ogni mail"
                else -> "Sola lettura · niente lascia il telefono"
            },
            onClick = { if (!connected || needsConsent) connect.start() else if (!syncing) syncNow() },
            trailing = {
                if (!connected || needsConsent) {
                    FilledTonalButton(enabled = !connect.busy, onClick = { connect.start() }) {
                        Text(if (connected) "Ricollega" else "Collega")
                    }
                }
            },
        )
        if (connected && !needsConsent) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.Refresh,
                title = "Leggi adesso",
                subtitle = "Pacchi, spese, scadenze, prenotazioni e rinnovi dalle mail nuove",
                onClick = { if (!syncing) syncNow() },
            )
        }
        if (connected) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.LinkOff,
                title = "Scollega Gmail",
                subtitle = "Canvas smette di leggere le mail; le cose già salvate restano",
                onClick = {
                    scope.launch {
                        MailWorker.cancel(context)
                        GmailAuth.disconnect(context)
                        MailStore.forget()
                        result = null
                    }
                },
            )
        }
    }
}

/** Una mail letta in Canvas: mittente, oggetto e testo, senza aprire Gmail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    MailStore.init(context)
    val mail = remember(id) { MailStore.find(id) }
    // Aperta: esce dalla card di Oggi.
    LaunchedEffect(id) { MailStore.markSeen(id) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = mail?.from ?: "Posta",
                subtitle = mail?.let { relativeTime(Instant.ofEpochMilli(it.at)) },
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
        ) {
            if (mail == null) {
                item {
                    Text(
                        "Questa mail non è più tra le importanti da leggere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }
            item {
                Text(mail.subject, style = MaterialTheme.typography.titleLarge)
                if (mail.fromAddress.isNotEmpty()) {
                    Text(
                        mail.fromAddress,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            // Paragrafo per paragrafo: le mail lunghe scorrono senza comporre tutto il testo in una volta.
            val paragraphs = mail.body.ifEmpty { mail.snippet }.split("\n\n")
            items(paragraphs.size) { i ->
                SelectionContainer {
                    Text(
                        paragraphs[i],
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
        }
    }
}
