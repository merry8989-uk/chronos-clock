package com.osintgram.chronos.updater

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    data object Downloading : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    var state: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    /** Whether Android currently allows Chronos to install packages. */
    fun canInstall(): Boolean = AppUpdater.canInstallPackages(context)

    fun openInstallPermissionSettings() = AppUpdater.openInstallPermissionSettings(context)

    fun check(currentVersion: String) {
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) return
        state = UpdateUiState.Checking
        viewModelScope.launch {
            state = when (val result = UpdateChecker.check(currentVersion)) {
                is UpdateResult.Found -> UpdateUiState.Available(result.info)
                UpdateResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateResult.Failed -> UpdateUiState.Failed(result.reason)
            }
        }
    }

    fun install(info: UpdateInfo) {
        if (state is UpdateUiState.Downloading) return
        state = UpdateUiState.Downloading
        viewModelScope.launch {
            state = AppUpdater.downloadAndInstall(context, info).fold(
                onSuccess = { UpdateUiState.Idle },
                onFailure = { error ->
                    UpdateUiState.Failed(
                        when (error) {
                            is InstallPermissionRequired ->
                                "Chronos is not allowed to install apps yet. Tap \"Allow app installs\", then update again."
                            is DownloadFailed -> error.message ?: "Update failed"
                            else -> error.message ?: "Update failed"
                        },
                    )
                },
            )
        }
    }

    fun reset() {
        state = UpdateUiState.Idle
    }
}
