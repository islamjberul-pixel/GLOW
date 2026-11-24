package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.InputEngineMode
import com.example.model.MacroConfig
import com.example.model.MacroConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.macroDataStore: DataStore<Preferences> by preferencesDataStore(name = "glow_first_macro_v2")

class MacroRepository(private val context: Context) {

    private object Keys {
        val IS_CONFIGURED = booleanPreferencesKey("is_configured")
        val SELECTED_GAME_PACKAGE = stringPreferencesKey("selected_game_package")
        val SELECTED_GAME_NAME = stringPreferencesKey("selected_game_name")
        val TRIGGER_NORM_X = floatPreferencesKey("trigger_norm_x")
        val TRIGGER_NORM_Y = floatPreferencesKey("trigger_norm_y")
        val TRIGGER_SIZE_DP = floatPreferencesKey("trigger_size_dp")
        val TAP_NORM_X = floatPreferencesKey("tap_norm_x")
        val TAP_NORM_Y = floatPreferencesKey("tap_norm_y")
        val TAP_SIZE_DP = floatPreferencesKey("tap_size_dp")
        val CPS = intPreferencesKey("cps")
        val INPUT_ENGINE_MODE = stringPreferencesKey("input_engine_mode")
        val OVERLAY_ACTIVE = booleanPreferencesKey("overlay_active")
        val HAS_COMPLETED_PERMISSION_SETUP = booleanPreferencesKey("has_completed_permission_setup")
    }

    val macroConfigFlow: Flow<MacroConfig> = context.macroDataStore.data.map { prefs ->
        MacroConfig(
            isConfigured = prefs[Keys.IS_CONFIGURED] ?: false,
            selectedGamePackage = prefs[Keys.SELECTED_GAME_PACKAGE] ?: "",
            selectedGameName = prefs[Keys.SELECTED_GAME_NAME] ?: "",
            triggerNormX = (prefs[Keys.TRIGGER_NORM_X] ?: 0.16f).coerceIn(0f, 1f),
            triggerNormY = (prefs[Keys.TRIGGER_NORM_Y] ?: 0.72f).coerceIn(0f, 1f),
            triggerSizeDp = (prefs[Keys.TRIGGER_SIZE_DP] ?: MacroConstants.DEFAULT_TRIGGER_SIZE_DP)
                .coerceIn(MacroConstants.MIN_TRIGGER_SIZE_DP, MacroConstants.MAX_TRIGGER_SIZE_DP),
            tapNormX = (prefs[Keys.TAP_NORM_X] ?: 0.80f).coerceIn(0f, 1f),
            tapNormY = (prefs[Keys.TAP_NORM_Y] ?: 0.65f).coerceIn(0f, 1f),
            tapSizeDp = (prefs[Keys.TAP_SIZE_DP] ?: MacroConstants.DEFAULT_TAP_SIZE_DP)
                .coerceIn(MacroConstants.MIN_TAP_SIZE_DP, MacroConstants.MAX_TAP_SIZE_DP),
            cps = (prefs[Keys.CPS] ?: MacroConstants.DEFAULT_CPS)
                .coerceIn(MacroConstants.MIN_CPS, MacroConstants.MAX_CPS),
            inputEngineMode = InputEngineMode.fromStorage(prefs[Keys.INPUT_ENGINE_MODE]),
            overlayActive = prefs[Keys.OVERLAY_ACTIVE] ?: false,
            hasCompletedPermissionSetup = prefs[Keys.HAS_COMPLETED_PERMISSION_SETUP] ?: false
        )
    }

    suspend fun saveSelectedGame(packageName: String, appName: String) {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.SELECTED_GAME_PACKAGE] = packageName
            prefs[Keys.SELECTED_GAME_NAME] = appName
        }
    }

    suspend fun saveInputEngineMode(mode: InputEngineMode) {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.INPUT_ENGINE_MODE] = mode.name
        }
    }

    suspend fun saveLiveKeymapConfiguration(
        triggerNormX: Float,
        triggerNormY: Float,
        triggerSizeDp: Float,
        tapNormX: Float,
        tapNormY: Float,
        tapSizeDp: Float,
        cps: Int,
        overlayActive: Boolean
    ) {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.IS_CONFIGURED] = true
            prefs[Keys.TRIGGER_NORM_X] = triggerNormX.coerceIn(0f, 1f)
            prefs[Keys.TRIGGER_NORM_Y] = triggerNormY.coerceIn(0f, 1f)
            prefs[Keys.TRIGGER_SIZE_DP] = triggerSizeDp.coerceIn(
                MacroConstants.MIN_TRIGGER_SIZE_DP,
                MacroConstants.MAX_TRIGGER_SIZE_DP
            )
            prefs[Keys.TAP_NORM_X] = tapNormX.coerceIn(0f, 1f)
            prefs[Keys.TAP_NORM_Y] = tapNormY.coerceIn(0f, 1f)
            prefs[Keys.TAP_SIZE_DP] = tapSizeDp.coerceIn(
                MacroConstants.MIN_TAP_SIZE_DP,
                MacroConstants.MAX_TAP_SIZE_DP
            )
            prefs[Keys.CPS] = cps.coerceIn(MacroConstants.MIN_CPS, MacroConstants.MAX_CPS)
            prefs[Keys.OVERLAY_ACTIVE] = overlayActive
        }
    }

    suspend fun setOverlayActive(active: Boolean) {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.OVERLAY_ACTIVE] = active
        }
    }

    suspend fun setPermissionSetupCompleted(completed: Boolean) {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.HAS_COMPLETED_PERMISSION_SETUP] = completed
        }
    }

    suspend fun clearMacro() {
        context.macroDataStore.edit { prefs ->
            prefs[Keys.IS_CONFIGURED] = false
            prefs[Keys.SELECTED_GAME_PACKAGE] = ""
            prefs[Keys.SELECTED_GAME_NAME] = ""
            prefs[Keys.OVERLAY_ACTIVE] = false
        }
    }
}
