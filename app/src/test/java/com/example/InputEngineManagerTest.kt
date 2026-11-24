package com.example

import com.example.engine.InputEngineManager
import com.example.model.ActiveBackend
import com.example.model.InputEngineMode
import org.junit.Assert.assertEquals
import org.junit.Test

class InputEngineManagerTest {

    @Test
    fun `automatic mode prefers Shizuku when ready and falls back to Accessibility`() {
        assertEquals(
            ActiveBackend.SHIZUKU,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.AUTOMATIC,
                accessibilityConnected = true,
                shizukuReady = true
            )
        )
        assertEquals(
            ActiveBackend.ACCESSIBILITY,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.AUTOMATIC,
                accessibilityConnected = true,
                shizukuReady = false
            )
        )
        assertEquals(
            ActiveBackend.NONE,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.AUTOMATIC,
                accessibilityConnected = false,
                shizukuReady = false
            )
        )
    }

    @Test
    fun `accessibility mode strictly enforces Accessibility only`() {
        assertEquals(
            ActiveBackend.NONE,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.ACCESSIBILITY,
                accessibilityConnected = false,
                shizukuReady = true
            )
        )
        assertEquals(
            ActiveBackend.ACCESSIBILITY,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.ACCESSIBILITY,
                accessibilityConnected = true,
                shizukuReady = true
            )
        )
    }

    @Test
    fun `shizuku mode strictly enforces Shizuku only`() {
        assertEquals(
            ActiveBackend.NONE,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.SHIZUKU,
                accessibilityConnected = true,
                shizukuReady = false
            )
        )
        assertEquals(
            ActiveBackend.SHIZUKU,
            InputEngineManager.resolveBackend(
                mode = InputEngineMode.SHIZUKU,
                accessibilityConnected = true,
                shizukuReady = true
            )
        )
    }

    @Test
    fun `manager state flow updates immediately when active backend disconnects`() {
        val manager = InputEngineManager()
        manager.updateMode(InputEngineMode.AUTOMATIC)
        manager.updateAccessibilityConnected(false)
        manager.updateShizukuReady(true)
        assertEquals(ActiveBackend.SHIZUKU, manager.activeBackendFlow.value)

        manager.updateShizukuReady(false)
        assertEquals(ActiveBackend.NONE, manager.activeBackendFlow.value)
    }
}
