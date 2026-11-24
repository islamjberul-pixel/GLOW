package com.example

import com.example.util.CpsValidationResult
import com.example.util.TapCoordinateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapCoordinateMapperTest {

    @Test
    fun `overlay top-left round-trips cleanly through normalized center across Nokia C30 display`() {
        val displayW = 1600
        val displayH = 720
        val overlayPx = 136

        val norm = TapCoordinateMapper.mapOverlayTopLeftToNormalizedCenter(
            xPx = 200,
            yPx = 300,
            overlaySizePx = overlayPx,
            displayWidth = displayW,
            displayHeight = displayH
        )
        // Center is (268, 368)
        assertEquals(268f / 1600f, norm.x, 0.001f)
        assertEquals(368f / 720f, norm.y, 0.001f)

        val restored = TapCoordinateMapper.mapTriggerToOverlayParams(
            triggerNormX = norm.x,
            triggerNormY = norm.y,
            overlaySizePx = overlayPx,
            displayWidth = displayW,
            displayHeight = displayH
        )
        assertEquals(200, restored.x)
        assertEquals(300, restored.y)
    }

    @Test
    fun `mapNormalizedPointToDisplay maps and clamps tap coordinates to display pixels`() {
        val pt = TapCoordinateMapper.mapNormalizedPointToDisplay(
            normX = 0.75f,
            normY = 0.50f,
            displayWidth = 1600,
            displayHeight = 720
        )
        assertEquals(1200f, pt.x, 0.5f)
        assertEquals(360f, pt.y, 0.5f)

        val clamped = TapCoordinateMapper.mapNormalizedPointToDisplay(
            normX = 2.5f,
            normY = -1.0f,
            displayWidth = 1600,
            displayHeight = 720
        )
        assertEquals(1599f, clamped.x, 0.5f)
        assertEquals(1f, clamped.y, 0.5f)
    }

    @Test
    fun `mapTriggerToOverlayParams clamps trigger safely inside display bounds`() {
        val pos = TapCoordinateMapper.mapTriggerToOverlayParams(
            triggerNormX = 1.0f,
            triggerNormY = 1.0f,
            overlaySizePx = 120,
            displayWidth = 1600,
            displayHeight = 720
        )
        assertEquals(1480, pos.x)
        assertEquals(600, pos.y)
    }

    @Test
    fun `cps validation enforces 1 to 30 range`() {
        assertTrue(TapCoordinateMapper.validateCps("1") is CpsValidationResult.Valid)
        assertTrue(TapCoordinateMapper.validateCps("13") is CpsValidationResult.Valid)
        assertTrue(TapCoordinateMapper.validateCps("30") is CpsValidationResult.Valid)
        assertTrue(TapCoordinateMapper.validateCps("0") is CpsValidationResult.Invalid)
        assertTrue(TapCoordinateMapper.validateCps("31") is CpsValidationResult.Invalid)
        assertTrue(TapCoordinateMapper.validateCps("") is CpsValidationResult.Invalid)
        assertTrue(TapCoordinateMapper.validateCps("abc") is CpsValidationResult.Invalid)
    }

    @Test
    fun `13 cps calculates ~76_9ms interval and non-overlapping stroke duration`() {
        val intervalMs = TapCoordinateMapper.cpsToExactIntervalMs(13)
        assertEquals(76.923, intervalMs, 0.01)

        val stroke30 = TapCoordinateMapper.cpsToTapStrokeDurationMs(30)
        assertTrue(stroke30 in 10L..16L)
    }
}
