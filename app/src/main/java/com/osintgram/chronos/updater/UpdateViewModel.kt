package com.osintgram.chronos.updater

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateInfo) : UpdateUiState
    /** Download progress, so the screen can show a real bar instead of a static row. */
    data class Downloading(
        val downloaded: Long = 0L,
        val total: Long = 0L,
    ) : UpdateUiState {
        val percent: Float
            get() = if (total > 0L) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

        val downloadedLabel: String get() = megabytes(downloaded)
        val totalLabel: String get() = megabytes(total)

        private fun megabytes(bytes: Long): String =
            String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
    data class Failed(val message: String) : UpdateUiState
}

private const val MIN_CHECK_GAP_MS = 2_000L

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    var state: UpdateUiState by mutableStateOf(UpdateUiState.Idle)
        private set

    private var lastCheckAt = 0L

    /** Whether Android currently allows Chronos to install packages. */
    fun canInstall(): Boolean = AppUpdater.canInstallPackages(context)

    fun openInstallPermissionSettings() = AppUpdater.openInstallPermissionSettings(context)

    fun check(currentVersion: String) {
        if (state is UpdateUiState.Checking || state is UpdateUiState.Downloading) return
        // A failing check returns in milliseconds, so repeated taps used to fire
        // a request each time — 96 of them in one minute in a real log. Two
        // seconds is short enough to feel instant and long enough to stop that.
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < MIN_CHECK_GAP_MS) {
            com.osintgram.chronos.diagnostics.DiagnosticsLogger.debug(
                "Updater",
                "check skipped, ${now - lastCheckAt} ms since the last one",
            )
            return
        }
        lastCheckAt = now
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
        state = UpdateUiState.Downloading(0L, info.sizeBytes)
        viewModelScope.launch {
            state = AppUpdater.downloadAndInstall(context, info) { done, total ->
                // Called from the IO thread; the state write belongs on main.
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    if (state is UpdateUiState.Downloading) {
                        state = UpdateUiState.Downloading(done, total)
                    }
                }
            }.fold(
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
