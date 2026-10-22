package com.platinum20.dashboard.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.platinum20.dashboard.BuildConfig
import com.platinum20.dashboard.net.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Downloading(val release: GitHubRelease, val progress: Float) : UpdateStatus
    data class NeedsPermission(val release: GitHubRelease, val apk: File) : UpdateStatus
    data class Installing(val release: GitHubRelease) : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME,
    val releases: List<GitHubRelease> = emptyList(),
    val loadingReleases: Boolean = false,
    val releasesError: String? = null,
    val status: UpdateStatus = UpdateStatus.Idle,
) {
    /** La release più recente, se è più nuova di quella installata. */
    val available: GitHubRelease?
        get() = releases.firstOrNull()
            ?.takeIf { compareVersions(it.version, currentVersion) > 0 && it.apkUrl != null }

    val busy: Boolean
        get() = status is UpdateStatus.Checking || status is UpdateStatus.Downloading
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = UpdateRepository(app)
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        refreshReleases()
    }

    fun refreshReleases() {
        viewModelScope.launch {
            _state.update { it.copy(loadingReleases = true, releasesError = null) }
            runCatching { repository.fetchReleases() }
                .onSuccess { list -> _state.update { it.copy(releases = list, loadingReleases = false) } }
                .onFailure { e ->
                    _state.update { it.copy(loadingReleases = false, releasesError = e.userMessage("Errore di rete.")) }
                }
        }
    }

    /** Il tasto: controlla, e se c'è una versione nuova la scarica e la installa. */
    fun checkAndUpdate() {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(status = UpdateStatus.Checking) }
            val releases = runCatching { repository.fetchReleases() }.getOrElse { e ->
                _state.update { it.copy(status = UpdateStatus.Error(e.userMessage("Errore di rete."))) }
                return@launch
            }
            _state.update { it.copy(releases = releases, releasesError = null) }

            val target = _state.value.available
            if (target == null) {
                _state.update { it.copy(status = UpdateStatus.UpToDate) }
                return@launch
            }

            _state.update { it.copy(status = UpdateStatus.Downloading(target, 0f)) }
            val apk = runCatching {
                repository.downloadApk(target) { p ->
                    _state.update { it.copy(status = UpdateStatus.Downloading(target, p)) }
                }
            }.getOrElse { e ->
                _state.update { it.copy(status = UpdateStatus.Error(e.userMessage("Download non riuscito."))) }
                return@launch
            }
            install(target, apk)
        }
    }

    /** Richiamato quando l'utente torna nell'app, ad esempio dopo aver concesso il permesso. */
    fun onResume() {
        val s = _state.value.status
        if (s is UpdateStatus.NeedsPermission && ApkInstaller.canInstall(getApplication())) {
            install(s.release, s.apk)
        }
    }

    fun openPermissionSettings() = ApkInstaller.openInstallPermissionSettings(getApplication())

    private fun install(release: GitHubRelease, apk: File) {
        val ctx = getApplication<Application>()
        if (!ApkInstaller.canInstall(ctx)) {
            _state.update { it.copy(status = UpdateStatus.NeedsPermission(release, apk)) }
            return
        }
        runCatching { ApkInstaller.install(ctx, apk) }
            .onSuccess { _state.update { it.copy(status = UpdateStatus.Installing(release)) } }
            .onFailure { e ->
                _state.update { it.copy(status = UpdateStatus.Error(e.message ?: "Impossibile avviare l'installazione.")) }
            }
    }
}
