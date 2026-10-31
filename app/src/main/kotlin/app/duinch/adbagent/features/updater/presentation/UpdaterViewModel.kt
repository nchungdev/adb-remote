package app.duinch.adbagent.features.updater.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.duinch.adbagent.core.di.AppContainer
import app.duinch.adbagent.features.updater.domain.UpdateStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UpdaterViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        UpdaterUiState(
            currentVersionName = container.appUpdateGateway.installedVersionName,
            currentVersionCode = container.appUpdateGateway.installedVersionCode
        )
    )
    val uiState: StateFlow<UpdaterUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<UpdaterUiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UpdaterUiEffect> = _effects.asSharedFlow()

    init {
        if (container.tunnelConfigRepository.getConfig().hasCloudflareAuth) {
            checkUpdate(userInitiated = false)
        }
    }

    fun onIntent(intent: UpdaterUiIntent) {
        when (intent) {
            UpdaterUiIntent.CheckForUpdate -> checkUpdate(userInitiated = true)
            UpdaterUiIntent.StartUpdate -> startDownloadAndInstall()
            UpdaterUiIntent.InstallDownloadedApk -> installExistingApk()
            UpdaterUiIntent.GrantInstallPermission ->
                _effects.tryEmit(UpdaterUiEffect.OpenInstallUnknownAppsSettings)
        }
    }

    private fun checkUpdate(userInitiated: Boolean) {
        if (_uiState.value.status is UpdateStatus.Checking || _uiState.value.status is UpdateStatus.Downloading) {
            return
        }
        _uiState.update { it.copy(status = UpdateStatus.Checking) }
        viewModelScope.launch {
            val result = container.checkAppUpdate()
            _uiState.update { it.copy(status = result) }
            if (userInitiated) {
                when (result) {
                    is UpdateStatus.UpToDate ->
                        _effects.emit(UpdaterUiEffect.ShowMessage("Bạn đang dùng bản mới nhất (${result.versionName})"))
                    is UpdateStatus.AuthRequired ->
                        _effects.emit(UpdaterUiEffect.RequestCloudflareLogin)
                    is UpdateStatus.Failed ->
                        _effects.emit(UpdaterUiEffect.ShowMessage(result.reason))
                    else -> Unit
                }
            }
        }
    }

    private fun startDownloadAndInstall() {
        val current = _uiState.value.status as? UpdateStatus.Available ?: return
        val release = current.release
        _uiState.update { it.copy(status = UpdateStatus.Downloading(release, 0)) }
        viewModelScope.launch {
            val finalStatus = container.downloadAndInstallUpdate(release) { pct ->
                _uiState.update { it.copy(status = UpdateStatus.Downloading(release, pct)) }
            }
            _uiState.update { it.copy(status = finalStatus) }
            if (finalStatus is UpdateStatus.ReadyToInstall && finalStatus.needsInstallPermission) {
                _effects.emit(UpdaterUiEffect.OpenInstallUnknownAppsSettings)
            } else if (finalStatus is UpdateStatus.Failed) {
                _effects.emit(UpdaterUiEffect.ShowMessage(finalStatus.reason))
            }
        }
    }

    private fun installExistingApk() {
        val ready = _uiState.value.status as? UpdateStatus.ReadyToInstall ?: return
        if (!container.appUpdateGateway.canRequestPackageInstalls()) {
            _effects.tryEmit(UpdaterUiEffect.OpenInstallUnknownAppsSettings)
            return
        }
        container.appUpdateGateway.triggerPackageInstall(ready.localFilePath).onSuccess {
            _uiState.update { it.copy(status = ready.copy(needsInstallPermission = false)) }
        }.onFailure { err ->
            _effects.tryEmit(UpdaterUiEffect.ShowMessage(err.message ?: "Không thể mở bộ cài đặt"))
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UpdaterViewModel(container) as T
                }
            }
    }
}
