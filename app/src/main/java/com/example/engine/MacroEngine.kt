package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.R
import com.example.model.ActiveBackend
import com.example.model.InputEngineMode
import com.example.model.MacroConfig
import com.example.model.MacroEngineState
import com.example.model.ShizukuStatus
import com.example.service.MacroAccessibilityService
import com.example.shizuku.ShizukuBackendManager
import com.example.util.TapCoordinateMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

class MacroEngine(
    private val appContext: Context,
    private val inputEngineManager: InputEngineManager,
    private val shizukuBackendManager: ShizukuBackendManager
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(MacroEngineState())
    val state: StateFlow<MacroEngineState> = _state.asStateFlow()

    @Volatile
    private var latestConfig: MacroConfig = MacroConfig()

    private var isHolding = false
    private var isDispatchInFlight = false
    private var holdSessionToken = 0L
    private var nextTapDeadlineNanos = 0L
    private var consecutiveFailures = 0
    private var lockedHoldBackend: ActiveBackend = ActiveBackend.NONE

    private val fireRunnable = Runnable {
        dispatchNextTapIfActive(holdSessionToken)
    }

    fun updateConfig(config: MacroConfig) {
        latestConfig = config
        val active = inputEngineManager.updateMode(config.inputEngineMode)
        _state.update { it.copy(activeBackend = active) }
        if (!config.isConfigured || !config.overlayActive) {
            stopHoldTap()
        }
    }

    fun setAccessibilityConnected(connected: Boolean) {
        val active = inputEngineManager.updateAccessibilityConnected(connected)
        _state.update {
            it.copy(
                isAccessibilityConnected = connected,
                activeBackend = active,
                lastErrorMessage = if (connected && it.lastErrorMessage == appContext.getString(R.string.error_accessibility_disabled)) {
                    null
                } else {
                    it.lastErrorMessage
                }
            )
        }
        if (!connected && lockedHoldBackend == ActiveBackend.ACCESSIBILITY) {
            stopHoldTap()
        }
    }

    fun setShizukuStatus(status: ShizukuStatus) {
        val active = inputEngineManager.updateShizukuReady(status.isBackendReady)
        _state.update {
            it.copy(
                isShizukuReady = status.isBackendReady,
                activeBackend = active,
                lastErrorMessage = if (status.isBackendReady && it.lastErrorMessage == appContext.getString(R.string.error_shizuku_not_ready)) {
                    null
                } else {
                    it.lastErrorMessage
                }
            )
        }
        if (!status.isBackendReady && lockedHoldBackend == ActiveBackend.SHIZUKU) {
            stopHoldTap()
        }
    }

    fun onBackendDisconnected(backend: ActiveBackend) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { onBackendDisconnected(backend) }
            return
        }
        when (backend) {
            ActiveBackend.ACCESSIBILITY -> inputEngineManager.updateAccessibilityConnected(false)
            ActiveBackend.SHIZUKU -> inputEngineManager.updateShizukuReady(false)
            ActiveBackend.NONE -> Unit
        }
        val active = inputEngineManager.resolveNow()
        val wasHoldingThisBackend = isHolding && lockedHoldBackend == backend
        if (wasHoldingThisBackend) {
            stopHoldTap()
        }
        _state.update {
            it.copy(
                isAccessibilityConnected = if (backend == ActiveBackend.ACCESSIBILITY) false else it.isAccessibilityConnected,
                isShizukuReady = if (backend == ActiveBackend.SHIZUKU) false else it.isShizukuReady,
                activeBackend = active,
                lastErrorMessage = if (wasHoldingThisBackend) {
                    when (backend) {
                        ActiveBackend.ACCESSIBILITY -> appContext.getString(R.string.error_accessibility_disabled)
                        ActiveBackend.SHIZUKU -> appContext.getString(R.string.error_shizuku_not_ready)
                        ActiveBackend.NONE -> null
                    }
                } else {
                    it.lastErrorMessage
                }
            )
        }
    }

    fun setOverlayServiceRunning(running: Boolean) {
        _state.update { it.copy(isOverlayServiceRunning = running) }
        if (!running) {
            stopHoldTap()
        }
    }

    fun setLiveEditorOpen(open: Boolean) {
        _state.update { it.copy(isLiveEditorOpen = open) }
        if (open) {
            stopHoldTap()
        }
    }

    fun clearError() {
        _state.update { it.copy(lastErrorMessage = null) }
    }

    fun startHoldTap() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { startHoldTap() }
            return
        }

        if (_state.value.isLiveEditorOpen) {
            return
        }

        val config = latestConfig
        if (!config.isConfigured) {
            _state.update {
                it.copy(
                    isHoldingTrigger = false,
                    lastErrorMessage = appContext.getString(R.string.error_macro_not_configured)
                )
            }
            return
        }

        // Sync live connection flags before resolving
        inputEngineManager.updateAccessibilityConnected(
            MacroAccessibilityService.instance != null && MacroAccessibilityService.isConnectedFlow.value
        )
        inputEngineManager.updateShizukuReady(shizukuBackendManager.status.value.isBackendReady)

        val chosenBackend = inputEngineManager.resolveNow()
        if (chosenBackend == ActiveBackend.NONE) {
            val errorMsg = when (config.inputEngineMode) {
                InputEngineMode.ACCESSIBILITY -> appContext.getString(R.string.error_accessibility_disabled)
                InputEngineMode.SHIZUKU -> appContext.getString(R.string.error_shizuku_not_ready)
                InputEngineMode.AUTOMATIC -> appContext.getString(R.string.error_no_backend_ready)
            }
            _state.update {
                it.copy(
                    isHoldingTrigger = false,
                    activeBackend = ActiveBackend.NONE,
                    lastErrorMessage = errorMsg
                )
            }
            return
        }

        if (isHolding) return

        holdSessionToken++
        isHolding = true
        isDispatchInFlight = false
        consecutiveFailures = 0
        lockedHoldBackend = chosenBackend
        nextTapDeadlineNanos = System.nanoTime()

        _state.update {
            it.copy(
                isHoldingTrigger = true,
                activeBackend = chosenBackend,
                lastErrorMessage = null
            )
        }

        dispatchNextTapIfActive(holdSessionToken)
    }

    fun stopHoldTap() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stopHoldTap() }
            return
        }
        holdSessionToken++
        isHolding = false
        isDispatchInFlight = false
        consecutiveFailures = 0
        lockedHoldBackend = ActiveBackend.NONE
        mainHandler.removeCallbacks(fireRunnable)
        _state.update { it.copy(isHoldingTrigger = false) }
    }

    private fun dispatchNextTapIfActive(token: Long) {
        if (!isHolding || token != holdSessionToken || isDispatchInFlight) return

        val config = latestConfig
        if (!config.isConfigured || _state.value.isLiveEditorOpen) {
            stopHoldTap()
            return
        }

        val (displayW, displayH) = readRealDisplaySize()
        val tapPoint = TapCoordinateMapper.mapNormalizedPointToDisplay(
            normX = config.tapNormX,
            normY = config.tapNormY,
            displayWidth = displayW,
            displayHeight = displayH
        )

        val cps = config.cps
        val intervalNanos = (TapCoordinateMapper.cpsToExactIntervalMs(cps) * 1_000_000.0).toLong()
        val strokeDurationMs = TapCoordinateMapper.cpsToTapStrokeDurationMs(cps)

        val nowNanos = System.nanoTime()
        if (nextTapDeadlineNanos < nowNanos) {
            nextTapDeadlineNanos = nowNanos + intervalNanos
        } else {
            nextTapDeadlineNanos += intervalNanos
        }

        when (lockedHoldBackend) {
            ActiveBackend.SHIZUKU -> dispatchViaShizuku(
                token = token,
                x = tapPoint.x,
                y = tapPoint.y,
                durationMs = strokeDurationMs,
                intervalNanos = intervalNanos
            )

            ActiveBackend.ACCESSIBILITY -> dispatchViaAccessibility(
                token = token,
                x = tapPoint.x,
                y = tapPoint.y,
                durationMs = strokeDurationMs,
                intervalNanos = intervalNanos
            )

            ActiveBackend.NONE -> stopHoldTap()
        }
    }

    private fun dispatchViaShizuku(
        token: Long,
        x: Float,
        y: Float,
        durationMs: Long,
        intervalNanos: Long
    ) {
        if (!shizukuBackendManager.status.value.isBackendReady) {
            onBackendDisconnected(ActiveBackend.SHIZUKU)
            return
        }

        isDispatchInFlight = true
        shizukuBackendManager.dispatchTapAsync(x, y, durationMs) { ok ->
            if (token != holdSessionToken || !isHolding) {
                isDispatchInFlight = false
                return@dispatchTapAsync
            }
            isDispatchInFlight = false
            if (ok) {
                consecutiveFailures = 0
                _state.update { it.copy(sessionTapCount = it.sessionTapCount + 1L) }
                scheduleNextDispatch()
            } else {
                consecutiveFailures++
                if (!shizukuBackendManager.status.value.isBackendReady || consecutiveFailures >= 3) {
                    stopHoldTap()
                    _state.update {
                        it.copy(
                            isShizukuReady = shizukuBackendManager.status.value.isBackendReady,
                            activeBackend = inputEngineManager.resolveNow(),
                            lastErrorMessage = appContext.getString(R.string.error_shizuku_not_ready)
                        )
                    }
                } else {
                    val retryDelayMs = max(20L, intervalNanos / 1_000_000L)
                    mainHandler.removeCallbacks(fireRunnable)
                    mainHandler.postDelayed(fireRunnable, retryDelayMs)
                }
            }
        }
    }

    private fun dispatchViaAccessibility(
        token: Long,
        x: Float,
        y: Float,
        durationMs: Long,
        intervalNanos: Long
    ) {
        val service = MacroAccessibilityService.instance
        if (service == null || !MacroAccessibilityService.isConnectedFlow.value) {
            onBackendDisconnected(ActiveBackend.ACCESSIBILITY)
            return
        }

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        isDispatchInFlight = true
        val accepted = try {
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (token != holdSessionToken || !isHolding) {
                            isDispatchInFlight = false
                            return
                        }
                        isDispatchInFlight = false
                        consecutiveFailures = 0
                        _state.update { it.copy(sessionTapCount = it.sessionTapCount + 1L) }
                        scheduleNextDispatch()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (token != holdSessionToken || !isHolding) {
                            isDispatchInFlight = false
                            return
                        }
                        isDispatchInFlight = false
                        val fallbackDelayMs = max(20L, intervalNanos / 1_000_000L)
                        mainHandler.removeCallbacks(fireRunnable)
                        mainHandler.postDelayed(fireRunnable, fallbackDelayMs)
                    }
                },
                mainHandler
            )
        } catch (_: Throwable) {
            isDispatchInFlight = false
            false
        }

        if (!accepted) {
            isDispatchInFlight = false
            consecutiveFailures++
            if (consecutiveFailures >= 3) {
                stopHoldTap()
                _state.update {
                    it.copy(lastErrorMessage = appContext.getString(R.string.error_gesture_dispatch_failed))
                }
            } else {
                val retryDelayMs = max(25L, intervalNanos / 1_000_000L)
                mainHandler.removeCallbacks(fireRunnable)
                mainHandler.postDelayed(fireRunnable, retryDelayMs)
            }
        }
    }

    private fun scheduleNextDispatch() {
        val delayMs = ((nextTapDeadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
        mainHandler.removeCallbacks(fireRunnable)
        mainHandler.postDelayed(fireRunnable, delayMs)
    }

    private fun readRealDisplaySize(): Pair<Int, Int> {
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }
}
