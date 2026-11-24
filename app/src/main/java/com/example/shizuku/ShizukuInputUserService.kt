package com.example.shizuku

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.Method
import kotlin.system.exitProcess

/**
 * Privileged Shizuku UserService running inside Shizuku's server process (UID 2000 shell / UID 0 root).
 *
 * Uses cached InputManager.injectInputEvent reflection to avoid spawning slow `sh` / `input tap`
 * subprocesses per tap, keeping CPU/RAM usage minimal on Nokia C30 (Android 11).
 */
class ShizukuInputUserService : IShizukuInputService.Stub() {

    private val inputManager: Any?
    private val injectInputEventMethod: Method?

    init {
        var resolvedManager: Any? = null
        var resolvedMethod: Method? = null
        try {
            val imClass = InputManager::class.java
            val getInstance = imClass.getMethod("getInstance")
            resolvedManager = getInstance.invoke(null)
            resolvedMethod = imClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            resolvedMethod.isAccessible = true
        } catch (_: Throwable) {
            resolvedManager = null
            resolvedMethod = null
        }
        inputManager = resolvedManager
        injectInputEventMethod = resolvedMethod
    }

    override fun isInputReady(): Boolean {
        return inputManager != null && injectInputEventMethod != null
    }

    @SuppressLint("Recycle")
    override fun injectTap(x: Float, y: Float, durationMs: Long): Boolean {
        val manager = inputManager ?: return false
        val method = injectInputEventMethod ?: return false

        val safeDuration = durationMs.coerceIn(8L, 35L)
        val downTime = SystemClock.uptimeMillis()

        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            1.0f,
            1.0f,
            0,
            1.0f,
            1.0f,
            0,
            0
        ).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }

        return try {
            val downOk = (method.invoke(manager, downEvent, INJECT_MODE_ASYNC) as? Boolean) ?: true
            if (!downOk) return false

            SystemClock.sleep(safeDuration)

            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime,
                upTime,
                MotionEvent.ACTION_UP,
                x,
                y,
                1.0f,
                1.0f,
                0,
                1.0f,
                1.0f,
                0,
                0
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                (method.invoke(manager, upEvent, INJECT_MODE_ASYNC) as? Boolean) ?: true
            } finally {
                upEvent.recycle()
            }
        } catch (_: Throwable) {
            false
        } finally {
            downEvent.recycle()
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    companion object {
        // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC = 0
        private const val INJECT_MODE_ASYNC = 0
    }
}
