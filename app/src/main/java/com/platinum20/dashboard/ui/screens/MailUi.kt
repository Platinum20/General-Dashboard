package com.platinum20.dashboard.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.platinum20.dashboard.autofill.AutoFill
import com.platinum20.dashboard.autofill.AutoFillRules
import com.platinum20.dashboard.autofill.SuggestionState
import com.platinum20.dashboard.mail.GmailAuth
import com.platinum20.dashboard.mail.GmailImap
import com.platinum20.dashboard.mail.GmailPasswordException
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

/**
 * In Oggi, le mail importanti non lette (si aprono in Canvas). Le cose trovate in mail,
 * notifiche e SMS non passano di qui: si salvano da sole in Note.
 */
@Composable
fun FromMailCard(onOpenMail: (String) -> Unit) {
    val context = LocalContext.current
    MailStore.init(context)
    val oauth by GmailAuth.connected.collectAsStateWithLifecycle()
    val imap by GmailImap.linked.collectAsStateWithLifecycle()
    val rejected by GmailImap.rejected.collectAsStateWithLifecycle()
    val gmail = oauth || imap
    val mails by MailStore.important.collectAsStateWithLifecycle()
    val needsConsent by GmailAuth.needsConsent.collectAsStateWithLifecycle()
    var askPassword by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val connect = rememberGmailConnect(onConnected = { scope.launch { runCatching { MailStore.sync(context) } } })
    val mail = if (gmail) mails else emptyList()
    val consent = if (imap) rejected else oauth && needsConsent
    if (askPassword) {
        GmailPasswordDialog(
            onDismiss = { askPassword = false },
            onLinked = {
                askPassword = false
                scope.launch { runCatching { MailStore.sync(context) } }
            },
        )
    }
    if (mail.isEmpty() && !consent) return

    // Al massimo due in Home (1.528, Antonio scelta B); il numero di tutte a destra del titolo.
    CardSection(title = "Dalla posta", trailing = {
        if (mail.isNotEmpty()) Text(
            if (mail.size == 1) "1 importante" else "${mail.size} importanti",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }) {
        Column {
            // La stessa riga di stato di WindTre (1.402), con "Rientra" lì.
            StatusLine(
                LinkStatus(LinkStatus.Kind.EXPIRED, if (imap) "Gmail non accetta più la password" else connect.problem ?: "Google chiede di ricollegare Gmail").takeIf { consent }
                    ?: LinkStatus(LinkStatus.Kind.OK, null),
                action = "Rientra",
                onAction = {
                    haptic(Haptic.TICK)
                    if (imap) askPassword = true else connect.start()
                },
            )
            mail.take(MAX_HOME_MAIL).forEachIndexed { index, m ->
                if (index > 0 || consent) RowDivider()
                MailRow(m, onClick = { haptic(Haptic.TICK); onOpenMail(m.id) })
            }
        }
    }
}

/** Quante mail importanti si vedono nella card in Home. */
internal const val MAX_HOME_MAIL = 2

/** L'iniziale del mittente nel tondo a sinistra della riga (1.528). */
internal fun senderInitial(from: String): String =
    from.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "@"

/** Il tondo con l'iniziale del mittente: la colonna di sinistra della riga Canvas. */
@Composable
private fun SenderDot(from: String) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(senderInitial(from), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MailRow(m: MailMessage, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SenderDot(m.from)
        Spacer(Modifier.width(12.dp))
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

/**
 * Collegamento con la password per app di Google: indirizzo e password, si prova subito l'accesso.
 * Non scade come l'accesso Google con l'app "in prova".
 */
@Composable
internal fun GmailPasswordDialog(onDismiss: () -> Unit, onLinked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    var address by remember { mutableStateOf(GmailImap.address.value) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val ready = '@' in address && password.replace(" ", "").length >= 16 && !busy

    fun link() {
        busy = true
        problem = null
        scope.launch {
            runCatching { GmailImap.link(context, address, password) }.fold(
                onSuccess = {
                    haptic(Haptic.CONFIRM)
                    // Una strada sola: l'accesso Google, se c'era, non serve più.
                    if (GmailAuth.connected.value) GmailAuth.disconnect(context)
                    onLinked()
                },
                onFailure = {
                    problem = if (it is GmailPasswordException) "Gmail non accetta indirizzo e password: controllali"
                    else it.userMessage("Gmail non risponde, riprova")
                },
            )
            busy = false
        }
    }

    CanvasDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Password per app di Google") },
        text = {
            Column {
                Text(
                    "Account Google › Sicurezza › Password per le app (serve la verifica in due passaggi). " +
                        "Sola lettura: Canvas non segna, non sposta e non invia niente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Indirizzo Gmail") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = canvasFieldColors(),
                    modifier = Modifier.fillMaxWidth().autoFocus(address.isEmpty()),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password per app (16 lettere)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ready) link() }),
                    colors = canvasFieldColors(),
                    modifier = Modifier.fillMaxWidth().autoFocus(address.isNotEmpty()),
                )
                problem?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            ConfirmButton(enabled = ready, onClick = { link() }) { Text(if (busy) "Provo…" else "Collega") }
        },
        dismissButton = {
            DismissButton(enabled = !busy, onClick = onDismiss) { Text("Annulla") }
        },
    )
}

/** Impostazioni > Account > Posta: collega con la password per app, leggi adesso, scollega (righe dentro un SettingsBlock). */
@Composable
internal fun GmailAccountRows() {
    val context = LocalContext.current
    MailStore.init(context)
    val oauth by GmailAuth.connected.collectAsStateWithLifecycle()
    val needsConsent by GmailAuth.needsConsent.collectAsStateWithLifecycle()
    val imap by GmailImap.linked.collectAsStateWithLifecycle()
    val rejected by GmailImap.rejected.collectAsStateWithLifecycle()
    val address by GmailImap.address.collectAsStateWithLifecycle()
    val lastSync by MailStore.lastSync.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    val connected = imap || oauth
    // Con la password per app l'accesso Google non conta più.
    val broken = if (imap) rejected else needsConsent

    fun syncNow() {
        syncing = true
        result = null
        scope.launch {
            result = runCatching { MailStore.sync(context) }.fold(
                onSuccess = { n ->
                    when {
                        imap && GmailImap.rejected.value -> "Gmail non accetta più la password"
                        !imap && GmailAuth.needsConsent.value -> "Google chiede di ricollegare Gmail"
                        n == 0 -> "Niente di nuovo nelle mail"
                        n == 1 -> "Trovata 1 cosa: la vedi nel calendario"
                        else -> "Trovate $n cose: le vedi nel calendario"
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
    if (askPassword) {
        GmailPasswordDialog(
            onDismiss = { askPassword = false },
            onLinked = {
                askPassword = false
                MailWorker.schedule(context)
                syncNow()
            },
        )
    }
    fun fix() {
        if (imap || !oauth) askPassword = true else connect.start()
    }

    run {
        SettingsItem(
            icon = Icons.Outlined.Mail,
            title = if (connected) "Gmail collegato" else "Collega Gmail",
            subtitle = connect.problem ?: when {
                !connected -> "Con la password per app: legge le mail intere"
                imap && rejected -> "Gmail non accetta più la password: inseriscila di nuovo"
                !imap && needsConsent -> "Google chiede di ricollegarlo"
                syncing -> "Leggo le mail…"
                result != null -> result
                lastSync > 0 -> "Letta " + relativeTime(Instant.ofEpochMilli(lastSync)) + " · ogni ora e a ogni mail"
                imap -> address
                else -> "Sola lettura · niente lascia il telefono"
            },
            onClick = { if (!connected || broken) fix() else if (!syncing) syncNow() },
            trailing = {
                if (!connected || broken) {
                    FilledTonalButton(enabled = !connect.busy, onClick = { fix() }) {
                        Text(if (connected) "Ricollega" else "Collega")
                    }
                }
            },
        )
        if (oauth && !imap) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.Key,
                title = "Usa la password per app",
                subtitle = "Mai più da ricollegare",
                onClick = { askPassword = true },
            )
        }
        if (connected && !broken) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.Refresh,
                title = "Leggi adesso",
                subtitle = "Pacchi e prenotazioni",
                onClick = { if (!syncing) syncNow() },
            )
        }
        if (connected) {
            RowDivider()
            SettingsItem(
                icon = Icons.Outlined.LinkOff,
                title = "Scollega Gmail",
                subtitle = "Smette di leggere le mail",
                onClick = {
                    scope.launch {
                        MailWorker.cancel(context)
                        GmailImap.unlink(context)
                        if (GmailAuth.connected.value) GmailAuth.disconnect(context)
                        MailStore.forget()
                        result = null
                    }
                },
            )
        }
    }
}

/**
 * Una mail letta in Canvas (1.528, Antonio scelta B), sulla pagina di dettaglio: l'oggetto come titolo, il
 * mittente sotto, il testo. In basso "Letta" la toglie dalla Home; "Altro" copia il testo.
 */
@Composable
fun MailScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    MailStore.init(context)
    val mail = remember(id) { MailStore.find(id) }
    val when_ = mail?.let { java.time.Instant.ofEpochMilli(it.at).atZone(java.time.ZoneId.systemDefault()) }
    DetailPage(
        onBack = onBack,
        onEdit = null,
        onDelete = null,
        meta = when_?.let { mailMoment(it.toLocalDateTime(), java.time.LocalDateTime.now()) } ?: "Posta",
        primary = mail?.let {
            DetailAction(Icons.Outlined.DoneAll, "Letta") {
                MailStore.markSeen(id)
                onBack()
            }
        },
        more = listOfNotNull(
            mail?.let { m ->
                BubbleAction(Icons.Outlined.ContentCopy, "Copia il testo") {
                    Clip.copy(context, "Mail", m.body.ifEmpty { m.snippet }, "Testo copiato")
                }
            },
        ),
    ) {
        if (mail == null) {
            DetailMissing(Icons.Outlined.MailOutline, "Questa mail non è più tra le importanti da leggere.")
            return@DetailPage
        }
        Text(
            mail.subject.ifBlank { "(senza oggetto)" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            SenderDot(mail.from)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(mail.from, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (mail.fromAddress.isNotEmpty()) {
                    Text(mail.fromAddress, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        SelectionContainer {
            Text(
                mail.body.ifEmpty { mail.snippet },
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** "Oggi 10:40", "Ieri 18:02", "Martedì 10:40", "3 novembre": quando è arrivata, sopra il titolo. Pura. */
internal fun mailMoment(at: java.time.LocalDateTime, now: java.time.LocalDateTime): String {
    val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())
    val time = at.format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))
    return when {
        days == 0L -> "Oggi $time"
        days == 1L -> "Ieri $time"
        days in 2..6 -> at.format(java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.ITALIAN)).replaceFirstChar { it.uppercase() } + " " + time
        else -> at.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.ITALIAN))
    }
}
