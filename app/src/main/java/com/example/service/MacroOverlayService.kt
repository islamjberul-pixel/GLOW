package com.example.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.GlowFirstApplication
import com.example.MainActivity
import com.example.R
import com.example.model.ActiveBackend
import com.example.model.MacroConfig
import com.example.util.TapCoordinateMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MacroOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configJob: Job? = null
    private var engineStateJob: Job? = null

    private var windowManager: WindowManager? = null
    private var liveEditorController: LiveEditorOverlayController? = null
    private var triggerView: TriggerCircleView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var latestConfig: MacroConfig = MacroConfig()
    private var currentBackend: ActiveBackend = ActiveBackend.NONE
    private var standbyMode: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        liveEditorController = LiveEditorOverlayController(this, wm)

        val app = applicationContext as GlowFirstApplication
        app.macroEngine.setOverlayServiceRunning(true)

        startForegroundSafely(latestConfig, currentBackend)

        configJob = serviceScope.launch {
            app.macroRepository.macroConfigFlow.collectLatest { config ->
                latestConfig = config
                app.macroEngine.updateConfig(config)

                if (!Settings.canDrawOverlays(this@MacroOverlayService)) {
                    stopSelfAndDisableOverlay()
                    return@collectLatest
                }

                if (liveEditorController?.isEditorOpen != true) {
                    if (config.isConfigured && config.overlayActive) {
                        ensureOrUpdateTriggerOverlay(config)
                    } else {
                        removeTriggerView()
                        if (!standbyMode) {
                            stopSelfAndDisableOverlay()
                            return@collectLatest
                        }
                    }
                }
                updateNotification(config, currentBackend)
            }
        }

        engineStateJob = serviceScope.launch {
            app.macroEngine.state.collectLatest { engineState ->
                triggerView?.syncHoldVisual(engineState.isHoldingTrigger)
                if (currentBackend != engineState.activeBackend) {
                    currentBackend = engineState.activeBackend
                    updateNotification(latestConfig, currentBackend)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                stopSelfAndDisableOverlay()
                return START_NOT_STICKY
            }

            ACTION_OPEN_LIVE_EDITOR -> {
                standbyMode = true
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        getString(R.string.error_overlay_permission_missing),
                        Toast.LENGTH_SHORT
                    ).show()
                    return START_NOT_STICKY
                }
                collapseShadePreS()
                startForegroundSafely(latestConfig, currentBackend)
                openLiveKeymapEditor()
                return START_STICKY
            }

            ACTION_START_STANDBY -> {
                standbyMode = true
                if (!Settings.canDrawOverlays(this)) {
                    stopSelfAndDisableOverlay()
                    return START_NOT_STICKY
                }
                startForegroundSafely(latestConfig, currentBackend)
                if (latestConfig.isConfigured && latestConfig.overlayActive && liveEditorController?.isEditorOpen != true) {
                    ensureOrUpdateTriggerOverlay(latestConfig)
                }
                return START_STICKY
            }

            else -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelfAndDisableOverlay()
                    return START_NOT_STICKY
                }
                startForegroundSafely(latestConfig, currentBackend)
                if (latestConfig.isConfigured && latestConfig.overlayActive && liveEditorController?.isEditorOpen != true) {
                    ensureOrUpdateTriggerOverlay(latestConfig)
                }
                return START_STICKY
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (liveEditorController?.isEditorOpen == true) {
            liveEditorController?.onDisplayChanged()
        } else if (latestConfig.isConfigured && latestConfig.overlayActive) {
            ensureOrUpdateTriggerOverlay(latestConfig)
        }
    }

    override fun onDestroy() {
        val app = applicationContext as GlowFirstApplication
        app.macroEngine.stopHoldTap()
        app.macroEngine.setLiveEditorOpen(false)
        app.macroEngine.setOverlayServiceRunning(false)
        liveEditorController?.dismiss()
        removeTriggerView()
        configJob?.cancel()
        engineStateJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun openLiveKeymapEditor() {
        val controller = liveEditorController ?: return
        val app = applicationContext as GlowFirstApplication
        app.macroEngine.stopHoldTap()
        app.macroEngine.setLiveEditorOpen(true)
        removeTriggerView()

        controller.show(
            config = latestConfig,
            onSave = { tX, tY, tSize, tapX, tapY, tapSize, cps ->
                app.macroEngine.setLiveEditorOpen(false)
                serviceScope.launch {
                    app.macroRepository.saveLiveKeymapConfiguration(
                        triggerNormX = tX,
                        triggerNormY = tY,
                        triggerSizeDp = tSize,
                        tapNormX = tapX,
                        tapNormY = tapY,
                        tapSizeDp = tapSize,
                        cps = cps,
                        overlayActive = true
                    )
                    Toast.makeText(
                        this@MacroOverlayService,
                        "Keymap saved ($cps CPS). Hold Trigger to tap.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onCancel = {
                app.macroEngine.setLiveEditorOpen(false)
                if (latestConfig.isConfigured && latestConfig.overlayActive) {
                    ensureOrUpdateTriggerOverlay(latestConfig)
                }
            }
        )
    }

    private fun stopSelfAndDisableOverlay() {
        val app = applicationContext as GlowFirstApplication
        app.macroEngine.stopHoldTap()
        app.macroEngine.setLiveEditorOpen(false)
        liveEditorController?.dismiss()
        removeTriggerView()
        standbyMode = false
        serviceScope.launch {
            app.macroRepository.setOverlayActive(false)
            stopSelf()
        }
    }

    private fun ensureOrUpdateTriggerOverlay(config: MacroConfig) {
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val sizePx = (config.triggerSizeDp * density).roundToInt().coerceAtLeast(48)
        val (displayW, displayH) = readRealDisplaySize(wm)
        val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
            triggerNormX = config.triggerNormX,
            triggerNormY = config.triggerNormY,
            overlaySizePx = sizePx,
            displayWidth = displayW,
            displayHeight = displayH
        )

        val existingView = triggerView
        val existingParams = overlayParams
        if (existingView == null || existingParams == null) {
            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = pos.x
                y = pos.y
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val app = applicationContext as GlowFirstApplication
            val view = TriggerCircleView(
                context = this,
                onHoldStart = { app.macroEngine.startHoldTap() },
                onHoldStop = { app.macroEngine.stopHoldTap() }
            )

            try {
                wm.addView(view, params)
                triggerView = view
                overlayParams = params
            } catch (_: Throwable) {
                stopSelfAndDisableOverlay()
            }
        } else {
            val changed = existingParams.width != sizePx ||
                existingParams.height != sizePx ||
                existingParams.x != pos.x ||
                existingParams.y != pos.y
            if (changed) {
                existingParams.width = sizePx
                existingParams.height = sizePx
                existingParams.x = pos.x
                existingParams.y = pos.y
                runCatching { wm.updateViewLayout(existingView, existingParams) }
            }
        }
    }

    private fun removeTriggerView() {
        val view = triggerView ?: return
        val wm = windowManager ?: return
        runCatching { wm.removeViewImmediate(view) }
        triggerView = null
        overlayParams = null
    }

    private fun collapseShadePreS() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            runCatching { sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) }
        }
    }

    private fun readRealDisplaySize(wm: WindowManager): Pair<Int, Int> {
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

    private fun startForegroundSafely(config: MacroConfig, backend: ActiveBackend) {
        val notification = buildNotification(config, backend)
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun updateNotification(config: MacroConfig, backend: ActiveBackend) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(config, backend))
    }

    private fun buildNotification(config: MacroConfig, backend: ActiveBackend): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val keymapIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MacroOverlayService::class.java).setAction(ACTION_OPEN_LIVE_EDITOR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MacroOverlayService::class.java).setAction(ACTION_STOP_OVERLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val gameTitle = config.selectedGameName.ifBlank { getString(R.string.app_name) }
        val title = if (config.isConfigured && config.overlayActive) {
            getString(R.string.notification_overlay_title, gameTitle)
        } else {
            getString(R.string.notification_ready_title, gameTitle)
        }

        val text = if (config.isConfigured && config.overlayActive) {
            getString(R.string.notification_overlay_text, config.cps, backend.displayName)
        } else {
            getString(R.string.notification_ready_text)
        }

        val keymapActionLabel = if (config.isConfigured) {
            getString(R.string.action_edit_keymap)
        } else {
            getString(R.string.action_keymap)
        }

        return NotificationCompat.Builder(this, GlowFirstApplication.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_glow)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, keymapActionLabel, keymapIntent)
            .addAction(0, getString(R.string.action_stop_overlay), stopIntent)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_STANDBY = "com.example.action.START_STANDBY"
        const val ACTION_OPEN_LIVE_EDITOR = "com.example.action.OPEN_LIVE_EDITOR"
        const val ACTION_STOP_OVERLAY = "com.example.action.STOP_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, MacroOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun startStandby(context: Context) {
            val intent = Intent(context, MacroOverlayService::class.java)
                .setAction(ACTION_START_STANDBY)
            ContextCompat.startForegroundService(context, intent)
        }

        fun openLiveEditor(context: Context) {
            val intent = Intent(context, MacroOverlayService::class.java)
                .setAction(ACTION_OPEN_LIVE_EDITOR)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MacroOverlayService::class.java)
                .setAction(ACTION_STOP_OVERLAY)
            runCatching { context.startService(intent) }
            runCatching { context.stopService(Intent(context, MacroOverlayService::class.java)) }
        }
    }
}
