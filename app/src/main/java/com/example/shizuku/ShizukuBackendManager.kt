package com.example.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import com.example.model.ShizukuStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuBackendManager(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dispatchThread = HandlerThread("GlowShizukuDispatch").apply { start() }
    private val dispatchHandler = Handler(dispatchThread.looper)

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    @Volatile
    private var inputService: IShizukuInputService? = null

    @Volatile
    private var isBindingUserService = false

    private var onBackendDisconnectedCallback: (() -> Unit)? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext, ShizukuInputUserService::class.java)
        )
            .daemon(false)
            .processNameSuffix("shizuku_input")
            .version(2)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBindingUserService = false
            val stub = service?.let { IShizukuInputService.Stub.asInterface(it) }
            val ready = runCatching { stub?.isInputReady() == true }.getOrDefault(false)
            if (stub != null && ready) {
                inputService = stub
                refreshStatus(errorOverride = null)
            } else {
                inputService = null
                refreshStatus(errorOverride = "Shizuku input service could not initialize InputManager on this device.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBindingUserService = false
            inputService = null
            onBackendDisconnectedCallback?.invoke()
            refreshStatus(errorOverride = "Shizuku input service disconnected.")
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshStatus()
        bindUserServiceIfAuthorized()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        isBindingUserService = false
        inputService = null
        onBackendDisconnectedCallback?.invoke()
        refreshStatus(errorOverride = "Shizuku service stopped.")
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    refreshStatus(errorOverride = null)
                    bindUserServiceIfAuthorized()
                } else {
                    inputService = null
                    onBackendDisconnectedCallback?.invoke()
                    refreshStatus(errorOverride = "Shizuku authorization was denied.")
                }
            }
        }

    fun initialize(onUnexpectedDisconnect: () -> Unit) {
        onBackendDisconnectedCallback = onUnexpectedDisconnect
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshStatus()
        bindUserServiceIfAuthorized()
    }

    fun refreshStatus(errorOverride: String? = _status.value.errorMessage): ShizukuStatus {
        val installed = isShizukuAppInstalled()
        val running = installed && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val authorized = running && !isPreV11Safe() && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        if (!authorized && inputService != null) {
            unbindUserServiceSafely()
            onBackendDisconnectedCallback?.invoke()
        }

        val ready = authorized && runCatching { inputService?.isInputReady() == true }.getOrDefault(false)
        val cleanError = when {
            ready -> null
            !installed -> null
            !running -> errorOverride?.takeIf { it.contains("stopped", ignoreCase = true) }
            !authorized -> errorOverride
            else -> errorOverride
        }

        val updated = ShizukuStatus(
            isInstalled = installed,
            isServiceRunning = running,
            isAuthorized = authorized,
            isBackendReady = ready,
            errorMessage = cleanError
        )
        _status.value = updated

        if (authorized && !ready && !isBindingUserService) {
            bindUserServiceIfAuthorized()
        }
        return updated
    }

    fun requestAuthorization(): Boolean {
        val current = refreshStatus(errorOverride = null)
        if (!current.isInstalled || !current.isServiceRunning || isPreV11Safe()) {
            return false
        }
        if (current.isAuthorized) {
            bindUserServiceIfAuthorized()
            return true
        }
        return runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            true
        }.getOrDefault(false)
    }

    fun openShizukuApp(): Boolean {
        val intent = appContext.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun bindUserServiceIfAuthorized() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running || isPreV11Safe()) return
        val authorized = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (!authorized) return
        if (inputService != null || isBindingUserService) return

        isBindingUserService = true
        runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        }.onFailure { err ->
            isBindingUserService = false
            refreshStatus(errorOverride = err.message ?: "Failed to bind Shizuku input service.")
        }
    }

    fun dispatchTapAsync(
        x: Float,
        y: Float,
        durationMs: Long,
        onComplete: (Boolean) -> Unit
    ) {
        val service = inputService
        if (service == null || !runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            inputService = null
            mainHandler.post {
                refreshStatus(errorOverride = "Shizuku service is not connected.")
                onComplete(false)
            }
            return
        }

        dispatchHandler.post {
            val ok = runCatching {
                service.injectTap(x, y, durationMs)
            }.getOrElse {
                inputService = null
                false
            }
            mainHandler.post {
                if (!ok) {
                    refreshStatus(errorOverride = "Shizuku touch injection failed on this device.")
                }
                onComplete(ok)
            }
        }
    }

    fun unbindUserServiceSafely() {
        isBindingUserService = false
        inputService = null
        runCatching { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) }
    }

    fun isShizukuAppInstalled(): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        }.getOrDefault(false)
    }

    private fun isPreV11Safe(): Boolean {
        return runCatching { Shizuku.isPreV11() }.getOrDefault(false)
    }

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 2001
    }
}
