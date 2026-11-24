package com.example.engine

import com.example.model.ActiveBackend
import com.example.model.InputEngineMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Coordinates the two mutually exclusive tap execution backends:
 * 1) AccessibilityService.dispatchGesture
 * 2) Shizuku privileged UserService (InputManager.injectInputEvent)
 *
 * Only ONE backend is ever active for a given configuration/hold session.
 */
class InputEngineManager {

    private var currentMode: InputEngineMode = InputEngineMode.AUTOMATIC
    private var accessibilityConnected: Boolean = false
    private var shizukuReady: Boolean = false

    private val _activeBackendFlow = MutableStateFlow(ActiveBackend.NONE)
    val activeBackendFlow: StateFlow<ActiveBackend> = _activeBackendFlow.asStateFlow()

    @Synchronized
    fun updateMode(mode: InputEngineMode): ActiveBackend {
        currentMode = mode
        return recompute()
    }

    @Synchronized
    fun updateAccessibilityConnected(connected: Boolean): ActiveBackend {
        accessibilityConnected = connected
        return recompute()
    }

    @Synchronized
    fun updateShizukuReady(ready: Boolean): ActiveBackend {
        shizukuReady = ready
        return recompute()
    }

    @Synchronized
    fun resolveNow(): ActiveBackend = recompute()

    @Synchronized
    private fun recompute(): ActiveBackend {
        val resolved = resolveBackend(
            mode = currentMode,
            accessibilityConnected = accessibilityConnected,
            shizukuReady = shizukuReady
        )
        _activeBackendFlow.value = resolved
        return resolved
    }

    companion object {
        fun resolveBackend(
            mode: InputEngineMode,
            accessibilityConnected: Boolean,
            shizukuReady: Boolean
        ): ActiveBackend {
            return when (mode) {
                InputEngineMode.AUTOMATIC -> when {
                    shizukuReady -> ActiveBackend.SHIZUKU
                    accessibilityConnected -> ActiveBackend.ACCESSIBILITY
                    else -> ActiveBackend.NONE
                }
                InputEngineMode.ACCESSIBILITY -> {
                    if (accessibilityConnected) ActiveBackend.ACCESSIBILITY else ActiveBackend.NONE
                }
                InputEngineMode.SHIZUKU -> {
                    if (shizukuReady) ActiveBackend.SHIZUKU else ActiveBackend.NONE
                }
            }
        }
    }
}
