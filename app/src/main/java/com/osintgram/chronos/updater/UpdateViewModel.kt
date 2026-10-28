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

    fun check(currentVersion: String) {
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) return
        state = UpdateUiState.Checking
        viewModelScope.launch {
            val info = UpdateChecker.check(currentVersion)
            state = if (info == null) UpdateUiState.UpToDate else UpdateUiState.Available(info)
        }
    }

    fun install(info: UpdateInfo) {
        if (state is UpdateUiState.Downloading) return
        state = UpdateUiState.Downloading
        viewModelScope.launch {
            val result = AppUpdater.downloadAndInstall(context, info)
            state = result.fold(
                onSuccess = { UpdateUiState.Idle },
                onFailure = { UpdateUiState.Failed(it.message ?: "Update failed") },
            )
        }
    }

    fun reset() {
        state = UpdateUiState.Idle
    }
}
