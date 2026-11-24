package com.example.util

import com.example.model.MacroConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PointPx(
    val x: Float,
    val y: Float
)

data class OverlayPositionPx(
    val x: Int,
    val y: Int
)

sealed class CpsValidationResult {
    data class Valid(val cps: Int) : CpsValidationResult()
    data class Invalid(val reason: String) : CpsValidationResult()
}

object TapCoordinateMapper {

    /**
     * Converts normalized [0f..1f] coordinates into physical display pixel coordinates,
     * clamped safely inside the active display dimensions across rotations and aspect ratios.
     */
    fun mapNormalizedPointToDisplay(
        normX: Float,
        normY: Float,
        displayWidth: Int,
        displayHeight: Int
    ): PointPx {
        val safeW = displayWidth.coerceAtLeast(1).toFloat()
        val safeH = displayHeight.coerceAtLeast(1).toFloat()
        return PointPx(
            x = (normX.coerceIn(0f, 1f) * safeW).coerceIn(1f, max(1f, safeW - 1f)),
            y = (normY.coerceIn(0f, 1f) * safeH).coerceIn(1f, max(1f, safeH - 1f))
        )
    }

    /**
     * Converts a WindowManager overlay top-left coordinate (xPx, yPx) and diameter into normalized
     * [0f..1f] center coordinates relative to the full physical display.
     */
    fun mapOverlayTopLeftToNormalizedCenter(
        xPx: Int,
        yPx: Int,
        overlaySizePx: Int,
        displayWidth: Int,
        displayHeight: Int
    ): PointPx {
        val safeW = displayWidth.coerceAtLeast(1).toFloat()
        val safeH = displayHeight.coerceAtLeast(1).toFloat()
        val centerX = xPx + overlaySizePx / 2f
        val centerY = yPx + overlaySizePx / 2f
        return PointPx(
            x = (centerX / safeW).coerceIn(0f, 1f),
            y = (centerY / safeH).coerceIn(0f, 1f)
        )
    }

    /**
     * Converts normalized center coordinates (0f..1f) into WindowManager top-left
     * (x, y) pixel coordinates, clamped so the circular overlay stays fully inside the display.
     */
    fun mapTriggerToOverlayParams(
        triggerNormX: Float,
        triggerNormY: Float,
        overlaySizePx: Int,
        displayWidth: Int,
        displayHeight: Int
    ): OverlayPositionPx {
        val half = overlaySizePx / 2f
        val maxX = max(0, displayWidth - overlaySizePx)
        val maxY = max(0, displayHeight - overlaySizePx)
        val rawX = (triggerNormX.coerceIn(0f, 1f) * displayWidth - half).roundToInt()
        val rawY = (triggerNormY.coerceIn(0f, 1f) * displayHeight - half).roundToInt()
        return OverlayPositionPx(
            x = rawX.coerceIn(0, maxX),
            y = rawY.coerceIn(0, maxY)
        )
    }

    fun validateCps(input: String): CpsValidationResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return CpsValidationResult.Invalid(
                "Enter a CPS value between ${MacroConstants.MIN_CPS} and ${MacroConstants.MAX_CPS}."
            )
        }
        val parsed = trimmed.toIntOrNull()
            ?: return CpsValidationResult.Invalid("CPS must be a whole number.")
        if (parsed !in MacroConstants.MIN_CPS..MacroConstants.MAX_CPS) {
            return CpsValidationResult.Invalid(
                "CPS must be between ${MacroConstants.MIN_CPS} and ${MacroConstants.MAX_CPS}."
            )
        }
        return CpsValidationResult.Valid(parsed)
    }

    /**
     * Exact average interval in milliseconds for a given CPS (e.g., 13 CPS -> 76.923 ms).
     */
    fun cpsToExactIntervalMs(cps: Int): Double {
        val safeCps = cps.coerceIn(MacroConstants.MIN_CPS, MacroConstants.MAX_CPS)
        return 1000.0 / safeCps.toDouble()
    }

    /**
     * Safe stroke duration for tap injection so high CPS (up to 30 CPS = 33.3ms period)
     * never overlaps the next tap while still registering reliably as a tap event.
     */
    fun cpsToTapStrokeDurationMs(cps: Int): Long {
        val intervalMs = cpsToExactIntervalMs(cps).toLong()
        return min(22L, max(10L, intervalMs / 2L))
    }
}
