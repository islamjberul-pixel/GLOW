package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.MacroRepository
import com.example.engine.InputEngineManager
import com.example.engine.MacroEngine
import com.example.model.ActiveBackend
import com.example.shizuku.ShizukuBackendManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GlowFirstApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val macroRepository: MacroRepository by lazy { MacroRepository(this) }
    val shizukuBackendManager: ShizukuBackendManager by lazy { ShizukuBackendManager(this) }
    val inputEngineManager: InputEngineManager by lazy { InputEngineManager() }
    val macroEngine: MacroEngine by lazy {
        MacroEngine(
            appContext = this,
            inputEngineManager = inputEngineManager,
            shizukuBackendManager = shizukuBackendManager
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        shizukuBackendManager.initialize {
            macroEngine.onBackendDisconnected(ActiveBackend.SHIZUKU)
        }

        appScope.launch {
            shizukuBackendManager.status.collectLatest { shizukuStatus ->
                macroEngine.setShizukuStatus(shizukuStatus)
            }
        }

        appScope.launch {
            macroRepository.macroConfigFlow.collectLatest { config ->
                macroEngine.updateConfig(config)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_OVERLAY,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_OVERLAY = "glow_first_overlay_channel"
    }
}
