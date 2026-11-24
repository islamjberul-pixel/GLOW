package com.example.model

object MacroConstants {
    const val MIN_CPS = 1
    const val MAX_CPS = 30
    const val DEFAULT_CPS = 13

    const val MIN_TRIGGER_SIZE_DP = 48f
    const val MAX_TRIGGER_SIZE_DP = 132f
    const val DEFAULT_TRIGGER_SIZE_DP = 68f

    const val MIN_TAP_SIZE_DP = 40f
    const val MAX_TAP_SIZE_DP = 120f
    const val DEFAULT_TAP_SIZE_DP = 56f

    val CPS_PRESETS = listOf(5, 10, 13, 20)
}

enum class InputEngineMode(val displayName: String) {
    AUTOMATIC("Automatic"),
    ACCESSIBILITY("Accessibility"),
    SHIZUKU("Shizuku");

    companion object {
        fun fromStorage(raw: String?): InputEngineMode {
            return entries.firstOrNull { it.name == raw } ?: AUTOMATIC
        }
    }
}

enum class ActiveBackend(val displayName: String) {
    NONE("Unavailable"),
    ACCESSIBILITY("Accessibility"),
    SHIZUKU("Shizuku")
}

data class ShizukuStatus(
    val isInstalled: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isAuthorized: Boolean = false,
    val isBackendReady: Boolean = false,
    val errorMessage: String? = null
)

data class InstalledGameApp(
    val packageName: String,
    val appName: String
)

data class MacroConfig(
    val isConfigured: Boolean = false,
    val selectedGamePackage: String = "",
    val selectedGameName: String = "",
    // Normalized [0f..1f] coordinates against full physical display bounds
    val triggerNormX: Float = 0.16f,
    val triggerNormY: Float = 0.72f,
    val triggerSizeDp: Float = MacroConstants.DEFAULT_TRIGGER_SIZE_DP,
    val tapNormX: Float = 0.80f,
    val tapNormY: Float = 0.65f,
    val tapSizeDp: Float = MacroConstants.DEFAULT_TAP_SIZE_DP,
    val cps: Int = MacroConstants.DEFAULT_CPS,
    val inputEngineMode: InputEngineMode = InputEngineMode.AUTOMATIC,
    val overlayActive: Boolean = false,
    val hasCompletedPermissionSetup: Boolean = false
) {
    val hasSelectedGame: Boolean
        get() = selectedGamePackage.isNotBlank() && selectedGameName.isNotBlank()
}

data class PermissionStatus(
    val overlayGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val needsNotificationRuntimePermission: Boolean = false
) {
    val allOverlayAndNotifyGranted: Boolean
        get() = overlayGranted && notificationGranted
}

data class MacroEngineState(
    val isHoldingTrigger: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val isShizukuReady: Boolean = false,
    val activeBackend: ActiveBackend = ActiveBackend.NONE,
    val isOverlayServiceRunning: Boolean = false,
    val isLiveEditorOpen: Boolean = false,
    val sessionTapCount: Long = 0L,
    val lastErrorMessage: String? = null
)
