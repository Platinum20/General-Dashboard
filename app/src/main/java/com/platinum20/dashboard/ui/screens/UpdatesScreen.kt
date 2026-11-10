package com.platinum20.dashboard.ui.screens

import com.platinum20.dashboard.update.UpdateText
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.platinum20.dashboard.update.UpdateUiState
import com.platinum20.dashboard.update.UpdateViewModel
import com.platinum20.dashboard.update.compareVersions
import com.platinum20.dashboard.work.UpdateCheckWorker
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current

    // Sei già qui: l'avviso della nuova versione non serve più.
    LaunchedEffect(Unit) { UpdateCheckWorker.dismiss(context) }

    // Tornando dalle impostazioni di sistema (permesso di installazione) riprendiamo da dove eravamo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 16.dp + miniPlayerRoom(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(state, onUpdate = viewModel::checkAndUpdate, onGrantPermission = viewModel::openPermissionSettings) }

            item {
                SectionHeader("Novità", Modifier.padding(top = 8.dp))
            }

            when {
                state.loadingReleases && state.releases.isEmpty() -> item {
                    PlaceholderList(rows = 3, thumb = false, label = "Leggo le versioni")
                }
                state.releasesError != null && state.releases.isEmpty() -> item {
                    MessageCard(Icons.Outlined.ErrorOutline, state.releasesError!!)
                }
                state.releases.isEmpty() -> item {
                    MessageCard(Icons.Outlined.NewReleases, "Nessuna versione pubblicata ancora.")
                }
                // Una riga per versione, le note si aprono a richiesta.
                else -> item {
                    CanvasCard(modifier = Modifier.fillMaxWidth()) {
                        state.releases.forEachIndexed { i, release ->
                            if (i > 0) RowDivider()
                            ReleaseRow(release, state.currentVersion, startOpen = i == 0 && state.available != null)
                        }
                    }
                }
            }
        }
    }
}

/** Lo stato vero: Canvas controlla da sola; il pulsante piccolo resta per chi ha fretta (punto 52). */
@Composable
private fun StatusCard(state: UpdateUiState, onUpdate: () -> Unit, onGrantPermission: () -> Unit) {
    val status = state.status
    val available = state.available
    val auto by AppSettings.updateNotify.collectAsState()
    val title = when {
        status is UpdateStatus.Error -> "Qualcosa non va"
        status is UpdateStatus.NeedsPermission -> "Serve un permesso"
        status is UpdateStatus.Installing -> "Installazione avviata"
        status is UpdateStatus.Downloading -> "Scarico la ${status.release.version}"
        available != null -> "C'è la ${available.version}"
        else -> "Canvas ${state.currentVersion} · aggiornata"
    }
    val text = when {
        status is UpdateStatus.Error -> status.message
        status is UpdateStatus.NeedsPermission -> "Consenti a Canvas di installare app, poi torna qui: l'installazione parte da sola."
        status is UpdateStatus.Installing -> "Canvas si chiude e passa alla ${status.release.version}; la prima volta Android chiede conferma."
        status is UpdateStatus.Downloading -> "Installata: ${state.currentVersion}"
        available != null -> "Installata: ${state.currentVersion}"
        else -> UpdateText.checked(state.checkedAt, auto, UpdateCheckWorker.CHECK_MINUTES)
    }
    val isError = status is UpdateStatus.Error
    CanvasCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (status is UpdateStatus.Downloading) {
                LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            Spacer(Modifier.height(6.dp))
            when {
                status is UpdateStatus.NeedsPermission -> CanvasButton("Concedi il permesso", onClick = onGrantPermission, primary = true)
                status is UpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingPulse()
                    Spacer(Modifier.width(12.dp))
                    Text("Controllo in corso…", style = MaterialTheme.typography.labelLarge)
                }
                status is UpdateStatus.Downloading || status is UpdateStatus.Installing -> Unit
                available != null -> CanvasButton("Aggiorna alla ${available.version}", onClick = onUpdate, icon = Icons.Outlined.CloudDownload, primary = true)
                else -> CanvasButton("Controlla adesso", onClick = onUpdate)
            }
        }
    }
}

/** "1.401  Calendario compatto · ieri": un tocco apre le note della versione. */
@Composable
private fun ReleaseRow(release: GitHubRelease, currentVersion: String, startOpen: Boolean) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(release.tag) { androidx.compose.runtime.mutableStateOf(startOpen) }
    val cmp = compareVersions(release.version, currentVersion)
    Column(
        Modifier
            .fillMaxWidth()
            .bouncyPress(0.98f)
            .clickable { open = !open }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                release.version.removeSuffix(".0"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
            )
            Text(
                UpdateText.headline(release),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = if (open) 3 else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    cmp == 0 -> "installata"
                    cmp > 0 -> "nuova"
                    else -> UpdateText.whenLabel(release.publishedAt).orEmpty()
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.padding(start = 52.dp, top = 8.dp)) { ReleaseNotes(UpdateText.body(release)) }
        }
    }
}

/** Rende in modo semplice il Markdown delle note: titoli, elenchi puntati e testo. */
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
                line.startsWith("#") -> Text(
                    line.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(line.drop(2).replace("**", ""), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(line.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = color) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MessageCard(icon: ImageVector, text: String) {
    CanvasCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun formatDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrNull()
