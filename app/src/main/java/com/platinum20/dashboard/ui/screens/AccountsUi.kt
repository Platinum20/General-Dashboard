package com.platinum20.dashboard.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.platinum20.dashboard.capture.CaptureFeedback
import com.platinum20.dashboard.capture.CaptureMode
import com.platinum20.dashboard.capture.CaptureNotice
import com.platinum20.dashboard.mail.GmailAuth
import com.platinum20.dashboard.mail.GmailImap
import com.platinum20.dashboard.media.Accounts
import com.platinum20.dashboard.media.MediaService
import com.platinum20.dashboard.media.spotify.SpotifyAuth
import com.platinum20.dashboard.media.spotify.SpotifyStore
import com.platinum20.dashboard.media.spotify.SpotifyWebSession
import com.platinum20.dashboard.media.youtube.YouTubeAuth
import com.platinum20.dashboard.media.youtube.YouTubeStore
import com.platinum20.dashboard.media.youtube.YtSync
import com.platinum20.dashboard.shifts.ShiftStore
import com.platinum20.dashboard.sim.SimStore
import kotlinx.coroutines.launch

/** Lo stato vero di tutti gli account, per la riga "Account" e per la sua pagina. */
@Composable
internal fun rememberAccountTiles(): List<AccountTile> {
    val context = LocalContext.current
    remember {
        YouTubeAuth.init(context); SpotifyAuth.init(context)
        SimStore.init(context); ShiftStore.init(context); GmailImap.init(context); GmailAuth.init(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { Accounts.refresh() }
    val loggedIn by Accounts.loggedIn.collectAsState()
    val youTubeOfficial by YouTubeAuth.connected.collectAsState()
    val spotifyOfficial by SpotifyAuth.connected.collectAsState()
    val simLinked by SimStore.linked.collectAsState()
    val simStatus by SimStore.status.collectAsState()
    val shiftsLinked by ShiftStore.linked.collectAsState()
    val shiftsExpired by ShiftStore.expired.collectAsState()
    val gmailImap by GmailImap.linked.collectAsState()
    val gmailApi by GmailAuth.connected.collectAsState()
    val gmailRejected by GmailImap.rejected.collectAsState()
    val gmailConsent by GmailAuth.needsConsent.collectAsState()
    return AccountTiles.build(
        spotify = spotifyOfficial || MediaService.SPOTIFY in loggedIn,
        youtube = youTubeOfficial || MediaService.YOUTUBE in loggedIn,
        gmail = gmailImap || gmailApi,
        gmailBroken = (gmailImap && gmailRejected) || (!gmailImap && gmailApi && gmailConsent),
        sim = simLinked,
        simStale = simStatus != null && SimStore.isStale(within = 3 * SimStore.STALE_MS),
        shifts = shiftsLinked,
        shiftsExpired = shiftsExpired,
    )
}

/**
 * La pagina Account (1.576, Antonio giro 4): una riga per collegamento, prima quelli da sistemare col tasto
 * arancione. Tocco o tocco lungo = il fumetto con tutto quello che si fa su quell'account.
 */
@Composable
internal fun AccountsPage(onLogin: (MediaService) -> Unit) {
    val tiles = rememberAccountTiles()
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AccountTiles.groups(tiles).forEach { (title, group) ->
            SettingsBlock(title) {
                group.forEachIndexed { index, tile ->
                    if (index > 0) RowDivider()
                    when (tile.kind) {
                        AccountKind.WINDTRE -> WindTreAccountRow(tile)
                        AccountKind.CREATORS -> ShiftAccountRow(tile)
                        AccountKind.GMAIL -> GmailAccountRow(tile)
                        AccountKind.SPOTIFY -> MediaAccountRow(MediaService.SPOTIFY, tile, onLogin)
                        AccountKind.YOUTUBE -> MediaAccountRow(MediaService.YOUTUBE, tile, onLogin)
                    }
                }
            }
        }
    }
}

/** A destra della riga: il tasto arancione se c'è da sistemare, quello tonale se è da collegare, niente se va. */
@Composable
internal fun AccountTrailing(tile: AccountTile, onFix: () -> Unit) {
    when (tile.state) {
        AccountState.ATTENTION -> CanvasButton(tile.line, onClick = onFix, level = ButtonLevel.ATTENTION)
        AccountState.OFF -> CanvasButton(tile.line, onClick = onFix)
        AccountState.OK -> Unit
    }
}

/** "Salva i dati letti" e "Condividi" nel fumetto (WindTre, turni): per correggere la lettura quando un numero non torna. */
internal fun captureActions(context: Context, file: String, text: () -> String): Array<BubbleAction> = arrayOf(
    BubbleAction(Icons.Outlined.SaveAlt, "Salva i dati letti", detail = "Un file in Download da allegare") {
        val where = saveTextToDownloads(context, stampedName(file), text())
        CaptureFeedback.show(
            CaptureNotice(
                CaptureMode.NOTE,
                if (where != null) "Salvato in " + where.substringAfterLast('/') else "Non sono riuscito a salvarlo",
                undo = null,
                icon = Icons.Outlined.Share,
                fly = false,
            ),
        )
    },
    BubbleAction(Icons.Outlined.Share, "Condividi i dati letti", "un file di testo con quello che Canvas legge") { shareTextFile(context, "$file.txt", text()) },
)

/** Spotify o YouTube: un solo accesso per servizio, serve sia al player sia al feed nell'app. */
@Composable
private fun MediaAccountRow(service: MediaService, tile: AccountTile, onLogin: (MediaService) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loggedIn by Accounts.loggedIn.collectAsState()
    val official by when (service) {
        MediaService.YOUTUBE -> YouTubeAuth.connected
        MediaService.SPOTIFY -> SpotifyAuth.connected
    }.collectAsState()
    val youTubeSync by YtSync.status.collectAsState()
    val spotifyClientId by SpotifyAuth.clientId.collectAsState()
    val connectOfficial = rememberOfficialConnect(service)
    var editKey by remember { mutableStateOf(false) }
    val connected = tile.state != AccountState.OFF

    fun logout() {
        Accounts.logout(service)
        when (service) {
            MediaService.YOUTUBE -> {
                scope.launch { YouTubeAuth.disconnect(context) }
                YouTubeStore.clear()
            }
            MediaService.SPOTIFY -> {
                SpotifyAuth.disconnect()
                SpotifyWebSession.clear()
                SpotifyStore.clear()
            }
        }
    }

    val actions = {
        ActionBubble.show(
            *listOfNotNull(
                if (!connected) BubbleAction(Icons.AutoMirrored.Outlined.Login, "Accedi", detail = service.label) { onLogin(service) } else null,
                // Il collegamento ufficiale si rifà sempre da qui, anche dopo «Esci».
                if (!official) BubbleAction(Icons.Outlined.Link, "Collegamento ufficiale", detail = "Feed e player") { connectOfficial() } else null,
                if (service == MediaService.SPOTIFY) {
                    BubbleAction(
                        Icons.Outlined.Key, "Chiave di Spotify",
                        detail = if (spotifyClientId == SpotifyAuth.DEFAULT_CLIENT_ID) "Predefinita" else "La tua",
                    ) { editKey = true }
                } else null,
                if (connected) BubbleAction(Icons.Outlined.LinkOff, "Esci", "Canvas dimentica l'accesso", danger = true) { logout() } else null,
            ).toTypedArray(),
        )
    }
    SettingsItem(
        icon = when (service) {
            MediaService.YOUTUBE -> Icons.Outlined.OndemandVideo
            MediaService.SPOTIFY -> Icons.Outlined.GraphicEq
        },
        accent = when (service) {
            MediaService.YOUTUBE -> TileAccent.ORANGE
            MediaService.SPOTIFY -> TileAccent.GREEN
        },
        title = service.label,
        subtitle = when {
            // Con l'accesso fatto nell'app, YouTube si tiene uguale al PC: qui si vede com'è andata.
            service == MediaService.YOUTUBE && service in loggedIn && youTubeSync != null -> youTubeSync!!
            official -> "Ufficiale · feed e player"
            connected -> "Collegato · feed e player"
            else -> "Feed e player nell'app"
        },
        onClick = actions,
        onLongClick = actions,
        showChevron = connected,
        trailing = { AccountTrailing(tile) { onLogin(service) } },
    )
    if (editKey) SpotifyKeyDialog { editKey = false }
}

/** La chiave di Spotify: il Client ID dell'app creata su developer.spotify.com, vuoto = quella di Canvas. */
@Composable
private fun SpotifyKeyDialog(onDismiss: () -> Unit) {
    val spotifyClientId by SpotifyAuth.clientId.collectAsState()
    var value by remember { mutableStateOf(spotifyClientId) }
    val valid = SpotifyAuth.normalizeClientId(value)
    CanvasDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chiave di Spotify") },
        text = {
            Column {
                Text(
                    "Il Client ID dell'app creata su developer.spotify.com. Vuoto = quella predefinita di Canvas. " +
                        "Se la cambi, ricollega Spotify.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    isError = valid == null,
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            ConfirmButton(enabled = valid != null, onClick = { valid?.let(SpotifyAuth::setClientId); onDismiss() }) { Text("Salva") }
        },
        dismissButton = {
            DismissButton(onClick = { SpotifyAuth.setClientId(SpotifyAuth.DEFAULT_CLIENT_ID); onDismiss() }) { Text("Ripristina") }
        },
    )
}
