package com.platinum20.dashboard.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.platinum20.dashboard.update.GitHubRelease
import com.platinum20.dashboard.update.UpdateStatus
import com.platinum20.dashboard.update.UpdateUiState
import com.platinum20.dashboard.update.UpdateViewModel
import com.platinum20.dashboard.update.compareVersions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    // Tornando dalle impostazioni di sistema (permesso di installazione) riprendiamo da dove eravamo.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Aggiornamenti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshReleases, enabled = !state.loadingReleases) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Ricarica changelog")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            UpdateBar(
                state = state,
                onUpdate = viewModel::checkAndUpdate,
                onGrantPermission = viewModel::openPermissionSettings,
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = inner.calculateTopPadding() + 4.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(state) }

            item {
                Text(
                    "Changelog",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }

            when {
                state.loadingReleases && state.releases.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.releasesError != null && state.releases.isEmpty() -> item {
                    MessageCard(Icons.Outlined.ErrorOutline, state.releasesError!!)
                }
                state.releases.isEmpty() -> item {
                    MessageCard(Icons.Outlined.NewReleases, "Nessuna versione pubblicata ancora.")
                }
                else -> items(state.releases, key = { it.tag }) { release ->
                    ReleaseCard(release, state.currentVersion)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: UpdateUiState) {
    val status = state.status
    val available = state.available
    val (icon, title, text) = when {
        status is UpdateStatus.Error -> Triple(Icons.Outlined.ErrorOutline, "Qualcosa non va", status.message)
        status is UpdateStatus.NeedsPermission -> Triple(
            Icons.Outlined.Security,
            "Serve un permesso",
            "Consenti a General Dashboard di installare app, poi torna qui: l'installazione parte da sola.",
        )
        status is UpdateStatus.Installing -> Triple(
            Icons.Outlined.CloudDownload,
            "Installazione avviata",
            "Conferma nella finestra di Android per passare alla ${status.release.version}.",
        )
        status is UpdateStatus.Downloading -> Triple(
            Icons.Outlined.CloudDownload,
            "Download in corso",
            "Sto scaricando la versione ${status.release.version}.",
        )
        available != null -> Triple(
            Icons.Outlined.NewReleases,
            "Nuova versione disponibile",
            "La ${available.version} è pronta. Premi il tasto in basso per aggiornare.",
        )
        status is UpdateStatus.UpToDate -> Triple(
            Icons.Outlined.CheckCircle,
            "Sei aggiornato",
            "Hai già l'ultima versione.",
        )
        else -> Triple(
            Icons.Outlined.CheckCircle,
            "Versione installata",
            "Controlla se è uscito qualcosa di nuovo con il tasto in basso.",
        )
    }
    val highlight = available != null || status is UpdateStatus.NeedsPermission
    val isError = status is UpdateStatus.Error

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isError -> MaterialTheme.colorScheme.errorContainer
                highlight -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Installata: ${state.currentVersion}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (status is UpdateStatus.Downloading) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { status.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: GitHubRelease, currentVersion: String) {
    val cmp = compareVersions(release.version, currentVersion)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Versione ${release.version}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                when {
                    cmp == 0 -> Badge("Installata", MaterialTheme.colorScheme.secondaryContainer)
                    cmp > 0 -> Badge("Nuova", MaterialTheme.colorScheme.tertiaryContainer)
                }
            }
            formatDate(release.publishedAt)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            ReleaseNotes(release.notes)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UpdateBar(
    state: UpdateUiState,
    onUpdate: () -> Unit,
    onGrantPermission: () -> Unit,
) {
    val status = state.status
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            when (status) {
                is UpdateStatus.NeedsPermission -> Button(onClick = onGrantPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Concedi il permesso")
                }
                is UpdateStatus.Checking -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Controllo in corso…")
                }
                is UpdateStatus.Downloading -> Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Download ${(status.progress * 100).toInt()}%")
                }
                else -> {
                    val available = state.available
                    Button(onClick = onUpdate, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (available != null) "Aggiorna alla ${available.version}" else "Controlla e aggiorna")
                    }
                }
            }
        }
    }
}

private fun formatDate(iso: String): String? = runCatching {
    OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}.getOrNull()
