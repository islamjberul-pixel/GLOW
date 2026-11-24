package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GlowFirstApplication
import com.example.R
import com.example.model.ActiveBackend
import com.example.model.InputEngineMode
import com.example.model.InstalledGameApp
import com.example.model.MacroConfig
import com.example.model.MacroEngineState
import com.example.model.PermissionStatus
import com.example.model.ShizukuStatus
import com.example.service.MacroOverlayService
import com.example.util.GameDiscoveryHelper
import com.example.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiFeedbackBanner(
    val message: String,
    val isError: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GlowFirstApplication
    private val repository = app.macroRepository
    private val engine = app.macroEngine
    private val shizukuManager = app.shizukuBackendManager

    val macroConfig: StateFlow<MacroConfig> = repository.macroConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MacroConfig()
        )

    val engineState: StateFlow<MacroEngineState> = engine.state
    val shizukuStatus: StateFlow<ShizukuStatus> = shizukuManager.status

    private val _permissionStatus = MutableStateFlow(PermissionHelper.getPermissionStatus(app))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _installedGames = MutableStateFlow<List<InstalledGameApp>>(emptyList())
    val installedGames: StateFlow<List<InstalledGameApp>> = _installedGames.asStateFlow()

    private val _isLoadingGames = MutableStateFlow(false)
    val isLoadingGames: StateFlow<Boolean> = _isLoadingGames.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<UiFeedbackBanner?>(null)
    val feedbackBanner: StateFlow<UiFeedbackBanner?> = _feedbackBanner.asStateFlow()

    fun refreshAllStatuses() {
        val status = PermissionHelper.getPermissionStatus(app)
        _permissionStatus.value = status
        engine.setAccessibilityConnected(status.accessibilityGranted)
        shizukuManager.refreshStatus()

        if (!status.overlayGranted && macroConfig.value.overlayActive) {
            viewModelScope.launch {
                repository.setOverlayActive(false)
                MacroOverlayService.stop(app)
            }
        }
    }

    fun markPermissionSetupCompleted() {
        viewModelScope.launch {
            repository.setPermissionSetupCompleted(true)
        }
    }

    fun dismissBanner() {
        _feedbackBanner.value = null
        engine.clearError()
    }

    fun showError(message: String) {
        _feedbackBanner.value = UiFeedbackBanner(message = message, isError = true)
    }

    fun loadInstalledGames() {
        if (_isLoadingGames.value) return
        viewModelScope.launch {
            _isLoadingGames.value = true
            _installedGames.value = GameDiscoveryHelper.getLaunchableApps(app)
            _isLoadingGames.value = false
        }
    }

    fun selectGame(game: InstalledGameApp) {
        viewModelScope.launch {
            repository.saveSelectedGame(game.packageName, game.appName)
            _feedbackBanner.value = UiFeedbackBanner(
                message = "Selected ${game.appName}. Tap OPEN GAME to launch with GLOW FIRST.",
                isError = false
            )
        }
    }

    fun setInputEngineMode(mode: InputEngineMode) {
        viewModelScope.launch {
            repository.saveInputEngineMode(mode)
            val resolved = app.inputEngineManager.updateMode(mode)
            if (mode == InputEngineMode.SHIZUKU && resolved != ActiveBackend.SHIZUKU) {
                shizukuManager.refreshStatus()
                shizukuManager.bindUserServiceIfAuthorized()
            }
        }
    }

    fun requestShizukuAuthorization() {
        val status = shizukuManager.refreshStatus()
        if (!status.isInstalled) {
            showError("Shizuku app is not installed on this device.")
            return
        }
        if (!status.isServiceRunning) {
            showError("Shizuku service is not running yet. Start it in the Shizuku app first.")
            return
        }
        val triggered = shizukuManager.requestAuthorization()
        if (!triggered) {
            showError("Could not prompt for Shizuku authorization.")
        }
    }

    fun openShizukuApp() {
        val launched = shizukuManager.openShizukuApp()
        if (!launched) {
            showError("Shizuku app is not installed on this device.")
        }
    }

    fun openSelectedGame() {
        val config = macroConfig.value
        if (!config.hasSelectedGame) {
            showError("Select a game first using ADD GAME.")
            return
        }

        val permissions = PermissionHelper.getPermissionStatus(app)
        _permissionStatus.value = permissions
        if (!permissions.overlayGranted) {
            showError(app.getString(R.string.error_overlay_permission_missing))
            return
        }

        val launched = GameDiscoveryHelper.launchGame(app, config.selectedGamePackage)
        if (!launched) {
            showError(app.getString(R.string.error_game_launch_failed))
            return
        }

        MacroOverlayService.startStandby(app)

        if (engine.state.value.activeBackend == ActiveBackend.NONE) {
            _feedbackBanner.value = UiFeedbackBanner(
                message = "Game launched with KEYMAP notification. Enable Accessibility or Shizuku before tapping.",
                isError = true
            )
        } else {
            _feedbackBanner.value = UiFeedbackBanner(
                message = "Opened ${config.selectedGameName}. Use KEYMAP in the notification bar over your game.",
                isError = false
            )
        }
    }

    fun openLiveKeymapEditorFromApp() {
        val permissions = PermissionHelper.getPermissionStatus(app)
        _permissionStatus.value = permissions
        if (!permissions.overlayGranted) {
            showError(app.getString(R.string.error_overlay_permission_missing))
            return
        }
        val config = macroConfig.value
        if (config.hasSelectedGame) {
            GameDiscoveryHelper.launchGame(app, config.selectedGamePackage)
        }
        MacroOverlayService.openLiveEditor(app)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        val current = macroConfig.value
        if (enabled) {
            if (!current.isConfigured) {
                showError(app.getString(R.string.error_macro_not_configured))
                return
            }
            val permissions = PermissionHelper.getPermissionStatus(app)
            _permissionStatus.value = permissions
            if (!permissions.overlayGranted) {
                showError(app.getString(R.string.error_overlay_permission_missing))
                return
            }
            viewModelScope.launch {
                repository.setOverlayActive(true)
                MacroOverlayService.start(app)
            }
        } else {
            viewModelScope.launch {
                engine.stopHoldTap()
                repository.setOverlayActive(false)
                MacroOverlayService.stop(app)
            }
        }
    }

    fun clearSavedMacro() {
        viewModelScope.launch {
            engine.stopHoldTap()
            MacroOverlayService.stop(app)
            repository.clearMacro()
            _feedbackBanner.value = UiFeedbackBanner(
                message = "Saved game and keymap cleared.",
                isError = false
            )
        }
    }
}
