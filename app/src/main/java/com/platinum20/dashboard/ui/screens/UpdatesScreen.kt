package com.platinum20.dashboard.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.update.GitHubRelease
import com.platinum20.dashboard.update.UpdateStatus
import com.platinum20.dashboard.update.UpdateText
import com.platinum20.dashboard.update.UpdateUiState
import com.platinum20.dashboard.update.UpdateViewModel
import com.platinum20.dashboard.update.compareVersions
import com.platinum20.dashboard.work.UpdateCheckWorker
import java.time.Instant

/**
 * Aggiornamenti (1.510, Antonio ha scelto la bozza C): in alto il riquadro con lo stato e l'unico tasto,
 * sotto le versioni su una linea come nel Calendario, con il segno INSTALLATA tra le nuove e quelle che hai.
 * Le note sono quelle del CHANGELOG, parola per parola: cambia solo l'impaginazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Sei già qui: l'avviso della nuova versione non serve più.
    LaunchedEffect(Unit) { UpdateCheckWorker.dismiss(context) }

    // Tornando dalle impostazioni di sistema (permesso di installazione) riprendiamo da dove eravamo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    UpdatesContent(state, onBack, viewModel::checkAndUpdate, viewModel::openPermissionSettings)
}

/** La pagina senza ViewModel, così il giro fotografico la disegna con versioni finte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatesContent(
    state: UpdateUiState,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopLevelBar(
                title = "Aggiornamenti",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        val versions = state.releases.map { it.version }
        val marker = UpdateText.installedIndex(versions, state.currentVersion)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 24.dp + miniPlayerRoom(),
            ),
        ) {
            item(key = "hero") {
                StatusHero(
                    state = state,
                    onUpdate = onUpdate,
                    onGrantPermission = onGrantPermission,
                )
            }
            item { Spacer(Modifier.height(12.dp)) }

            when {
                state.loadingReleases && state.releases.isEmpty() -> item {
                    PlaceholderList(rows = 3, thumb = false, label = "Leggo le versioni")
                }
                state.releasesError != null && state.releases.isEmpty() -> item {
                    Text(
                        state.releasesError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                state.releases.isEmpty() -> item {
                    Text(
                        "Nessuna versione pubblicata ancora.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
                else -> itemsIndexed(state.releases, key = { _, r -> r.tag }) { i, release ->
                    Column {
                        if (i == marker) NewsVisitMarker("Installata")
                        VersionRow(
                            release = release,
                            newer = compareVersions(release.version, state.currentVersion) > 0,
                            last = i == state.releases.lastIndex,
                        )
                    }
                }
            }
            if (state.releases.isNotEmpty() && marker == state.releases.size) item { NewsVisitMarker("Installata") }
        }
    }
}

/** Il riquadro in alto: da dove a dove, quando ho controllato, e l'unico tasto della pagina. */
@Composable
private fun StatusHero(state: UpdateUiState, onUpdate: () -> Unit, onGrantPermission: () -> Unit) {
    val status = state.status
    val available = state.available
    val auto by AppSettings.updateNotify.collectAsState()
    val installed = UpdateText.short(state.currentVersion)
    val checked = state.checkedAt.takeIf { it > 0 }?.let { "controllato " + relativeTime(Instant.ofEpochMilli(it)) }
    val autoLine = if (auto) "si aggiorna da sola" else "aggiornamento automatico spento"

    val top: String
    val title: String
    val line: String?
    when {
        status is UpdateStatus.Error -> {
            top = "Installata $installed · $autoLine"
            title = "Qualcosa non va"
            line = status.message
        }
        status is UpdateStatus.NeedsPermission -> {
            top = "Installata $installed"
            title = "Serve un permesso"
            line = "Consenti a Canvas di installare app, poi torna qui: l'installazione parte da sola."
        }
        status is UpdateStatus.Installing -> {
            top = "Installata $installed"
            title = "Passo alla ${UpdateText.short(status.release.version)}"
            line = "Canvas si chiude e si riapre aggiornata."
        }
        status is UpdateStatus.Downloading -> {
            top = "Installata $installed"
            title = "Scarico la ${UpdateText.short(status.release.version)}"
            line = "${(status.progress * 100).toInt()}%"
        }
        available != null -> {
            val n = UpdateText.newCount(state.releases.map { it.version }, state.currentVersion)
            top = "Installata $installed · $autoLine"
            title = "Pronta la ${UpdateText.short(available.version)}"
            line = listOfNotNull(if (n == 1) "1 versione nuova" else "$n versioni nuove", checked).joinToString(" · ")
        }
        else -> {
            top = listOfNotNull(checked?.replaceFirstChar { it.uppercase() }, autoLine).joinToString(" · ")
            title = "Sei alla $installed"
            line = when {
                status is UpdateStatus.Checking || state.loadingReleases -> "Controllo se c'è una versione nuova"
                state.releasesError != null -> state.releasesError
                else -> "È l'ultima versione"
            }
        }
    }

    CanvasCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (status is UpdateStatus.Error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(top, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.alpha(0.75f))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
            line?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp).alpha(0.75f))
            }
            if (status is UpdateStatus.Downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
            }
            // Il tasto c'è solo quando serve: già aggiornato, il controllo si fa da solo aprendo la pagina.
            val button: Pair<String, () -> Unit>? = when {
                status is UpdateStatus.NeedsPermission -> "Concedi il permesso" to onGrantPermission
                status is UpdateStatus.Error -> "Riprova" to onUpdate
                status is UpdateStatus.Downloading || status is UpdateStatus.Installing || status is UpdateStatus.Checking -> null
                available != null -> "Aggiorna ora" to onUpdate
                state.releasesError != null -> "Riprova" to onUpdate
                else -> null
            }
            button?.let { (text, action) ->
                Spacer(Modifier.height(14.dp))
                CanvasButton(text = text, onClick = action, primary = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Una versione sulla linea: pallino colorato se nuova, inchiostro se già tua; quelle vecchie attenuate. */
@Composable
private fun VersionRow(release: GitHubRelease, newer: Boolean, last: Boolean) {
    val rail = MaterialTheme.colorScheme.outlineVariant
    val dot = if (newer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 5.dp.toPx()
                val y = 19.dp.toPx()
                drawLine(rail, Offset(x, 0f), Offset(x, if (last) y else size.height), 2.dp.toPx())
                drawCircle(dot, radius = 5.dp.toPx(), center = Offset(x, y))
            }
            .padding(start = 24.dp, top = 10.dp, bottom = 12.dp)
            .alpha(if (newer) 1f else 0.6f),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UpdateText.short(release.version),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                UpdateText.releaseWhen(release.publishedAt)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            ReleaseNotes(release.notes)
        }
    }
}

/** Rende in modo semplice il Markdown delle note: titoli, grassetto, elenchi puntati e testo. */
@Composable
private fun ReleaseNotes(notes: String) {
    val lines = notes.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        Text(
            "Nessuna nota per questa versione.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { raw ->
            val line = raw.trimStart()
            when {
                // Le versioni uscite insieme a questa ("## 1.506.0"): il loro numero, corto come gli altri.
                line.startsWith("#") -> Text(
                    UpdateText.short(line.trimStart('#').trim()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.startsWith("**") -> Text(
                    line.replace("**", ""),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(line.drop(2).replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(line.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
