package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActiveBackend
import com.example.model.InputEngineMode
import com.example.model.MacroConfig
import com.example.model.MacroEngineState
import com.example.model.PermissionStatus
import com.example.model.ShizukuStatus
import com.example.ui.UiFeedbackBanner
import com.example.ui.components.FeedbackBannerCard
import com.example.ui.components.GameAppIcon
import com.example.ui.components.GlowFirstLogoMark
import com.example.ui.components.StatusPill
import com.example.ui.theme.GlowBlack
import com.example.ui.theme.GlowBorderSubtle
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.GlowGreenContainer
import com.example.ui.theme.GlowGreenDeep
import com.example.ui.theme.GlowSurface
import com.example.ui.theme.GlowSurfaceCard
import com.example.ui.theme.GlowSurfaceElevated
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TapCoordinateMapper
import java.util.Locale

@Composable
fun HomeScreen(
    macroConfig: MacroConfig,
    permissionStatus: PermissionStatus,
    shizukuStatus: ShizukuStatus,
    engineState: MacroEngineState,
    banner: UiFeedbackBanner?,
    onAddGameClick: () -> Unit,
    onOpenGameClick: () -> Unit,
    onOpenLiveKeymapClick: () -> Unit,
    onSelectInputMode: (InputEngineMode) -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenInfo: () -> Unit,
    onDismissBanner: () -> Unit
) {
    val activeError = banner?.message ?: engineState.lastErrorMessage
    val isBannerError = if (banner != null) banner.isError else engineState.lastErrorMessage != null

    val hasActiveBackend = engineState.activeBackend != ActiveBackend.NONE
    val canRunLive = permissionStatus.overlayGranted && hasActiveBackend

    Scaffold(
        containerColor = GlowBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GlowSurface, GlowBlack, GlowBlack)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlowFirstLogoMark(size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = GlowGreen
                        )
                    }

                    val statusText = when {
                        macroConfig.overlayActive && canRunLive -> "ACTIVE"
                        canRunLive -> "READY"
                        else -> "SETUP"
                    }
                    StatusPill(
                        label = statusText,
                        isPositive = canRunLive,
                        isWarning = !canRunLive && (permissionStatus.overlayGranted || hasActiveBackend)
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenInfo,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_settings_info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "App information and settings",
                            tint = TextSecondary
                        )
                    }
                }

                // Error / Feedback Banner
                if (!activeError.isNullOrBlank()) {
                    FeedbackBannerCard(
                        message = activeError,
                        isError = isBannerError,
                        onDismiss = onDismissBanner
                    )
                }

                // Game Launcher & Target Game Card
                GameTargetCard(
                    macroConfig = macroConfig,
                    onAddGameClick = onAddGameClick,
                    onOpenGameClick = onOpenGameClick
                )

                // Input Engine Mode Selector
                InputEngineCard(
                    selectedMode = macroConfig.inputEngineMode,
                    activeBackend = engineState.activeBackend,
                    accessibilityGranted = permissionStatus.accessibilityGranted,
                    shizukuStatus = shizukuStatus,
                    onSelectInputMode = onSelectInputMode
                )

                // Live Keymap & Floating Trigger Card
                LiveKeymapStatusCard(
                    macroConfig = macroConfig,
                    engineState = engineState,
                    onOpenLiveKeymapClick = onOpenLiveKeymapClick,
                    onToggleOverlay = onToggleOverlay
                )

                // System & Backend Status Summary
                SystemStatusCard(
                    macroConfig = macroConfig,
                    permissionStatus = permissionStatus,
                    shizukuStatus = shizukuStatus,
                    onOpenPermissions = onOpenPermissions
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun GameTargetCard(
    macroConfig: MacroConfig,
    onAddGameClick: () -> Unit,
    onOpenGameClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, GlowGreen.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SportsEsports,
                        contentDescription = null,
                        tint = GlowGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GAME TARGET",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                StatusPill(
                    label = if (macroConfig.hasSelectedGame) "SELECTED" else "NO GAME",
                    isPositive = macroConfig.hasSelectedGame,
                    isWarning = !macroConfig.hasSelectedGame
                )
            }

            if (macroConfig.hasSelectedGame) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(GlowSurfaceCard)
                        .border(1.dp, GlowBorderSubtle, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GameAppIcon(
                        packageName = macroConfig.selectedGamePackage,
                        contentDescription = "${macroConfig.selectedGameName} icon",
                        size = 46.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = macroConfig.selectedGameName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = macroConfig.selectedGamePackage,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Text(
                    text = "Select your game first, then tap OPEN GAME and open KEYMAP from the notification bar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onAddGameClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("add_game_button"),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, GlowGreenDeep.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GlowGreen)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ADD GAME",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = onOpenGameClick,
                    enabled = macroConfig.hasSelectedGame,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("open_game_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowGreen,
                        contentColor = GlowBlack,
                        disabledContainerColor = GlowSurfaceCard,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "OPEN GAME",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun InputEngineCard(
    selectedMode: InputEngineMode,
    activeBackend: ActiveBackend,
    accessibilityGranted: Boolean,
    shizukuStatus: ShizukuStatus,
    onSelectInputMode: (InputEngineMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, GlowBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = if (activeBackend != ActiveBackend.NONE) GlowGreen else StatusWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INPUT ENGINE",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                StatusPill(
                    label = when (activeBackend) {
                        ActiveBackend.SHIZUKU -> "USING SHIZUKU"
                        ActiveBackend.ACCESSIBILITY -> "USING ACCESSIBILITY"
                        ActiveBackend.NONE -> "NO BACKEND"
                    },
                    isPositive = activeBackend != ActiveBackend.NONE
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputEngineMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    val bgColor = if (isSelected) GlowGreenContainer else GlowSurfaceCard
                    val borderColor = if (isSelected) GlowGreen else GlowBorderSubtle
                    val textColor = if (isSelected) GlowGreen else TextSecondary
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(bgColor)
                            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                            .clickable { onSelectInputMode(mode) }
                            .testTag("engine_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            val helperText = when (selectedMode) {
                InputEngineMode.AUTOMATIC -> when {
                    shizukuStatus.isBackendReady -> "Automatic selected Shizuku (ready) — Accessibility stays idle."
                    accessibilityGranted -> "Automatic selected Accessibility (Shizuku not ready)."
                    else -> "Enable Accessibility Service or authorize Shizuku to activate taps."
                }
                InputEngineMode.ACCESSIBILITY -> if (accessibilityGranted) {
                    "Locked to AccessibilityService tap dispatch."
                } else {
                    "AccessibilityService is currently disabled in Android Settings."
                }
                InputEngineMode.SHIZUKU -> if (shizukuStatus.isBackendReady) {
                    "Locked to Shizuku privileged touch injection."
                } else {
                    "Shizuku is not ready. Open Permissions & Shizuku to pair and authorize."
                }
            }
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LiveKeymapStatusCard(
    macroConfig: MacroConfig,
    engineState: MacroEngineState,
    onOpenLiveKeymapClick: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, GlowBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = if (macroConfig.isConfigured) GlowGreen else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE KEYMAP & TRIGGER",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                StatusPill(
                    label = if (macroConfig.isConfigured) "SAVED" else "NOT SET",
                    isPositive = macroConfig.isConfigured,
                    isWarning = !macroConfig.isConfigured
                )
            }

            if (macroConfig.isConfigured) {
                val intervalMs = TapCoordinateMapper.cpsToExactIntervalMs(macroConfig.cps)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(GlowSurfaceCard)
                        .border(1.dp, GlowBorderSubtle, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = GlowGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${macroConfig.cps} CPS (${String.format(Locale.US, "%.1f", intervalMs)} ms)",
                                style = MaterialTheme.typography.labelLarge,
                                color = GlowGreen
                            )
                            Text(
                                text = "Trigger ${macroConfig.triggerSizeDp.toInt()}dp · Tap ${macroConfig.tapSizeDp.toInt()}dp",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    if (engineState.sessionTapCount > 0L) {
                        Text(
                            text = "${engineState.sessionTapCount} taps",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (macroConfig.overlayActive) GlowGreenContainer else GlowSurfaceCard
                        )
                        .border(
                            1.dp,
                            if (macroConfig.overlayActive) GlowGreen.copy(alpha = 0.5f) else GlowBorderSubtle,
                            MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = if (macroConfig.overlayActive) GlowGreen else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Trigger",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = if (macroConfig.overlayActive) {
                                "Only Trigger circle visible · Hold to tap"
                            } else {
                                "Hidden · Enable to show Trigger"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = macroConfig.overlayActive,
                        onCheckedChange = onToggleOverlay,
                        modifier = Modifier.testTag("overlay_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GlowBlack,
                            checkedTrackColor = GlowGreen,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = GlowSurface
                        )
                    )
                }
            } else {
                Text(
                    text = "Launch your game with OPEN GAME, then tap KEYMAP in the notification bar to drag & resize TRIGGER and TAP live over your game.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            OutlinedButton(
                onClick = onOpenLiveKeymapClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("home_live_keymap_button"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, GlowGreenDeep.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlowGreen)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (macroConfig.isConfigured) "EDIT KEYMAP OVER GAME" else "OPEN LIVE KEYMAP",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun SystemStatusCard(
    macroConfig: MacroConfig,
    permissionStatus: PermissionStatus,
    shizukuStatus: ShizukuStatus,
    onOpenPermissions: () -> Unit
) {
    val allHealthy = permissionStatus.overlayGranted &&
        permissionStatus.notificationGranted &&
        (permissionStatus.accessibilityGranted || shizukuStatus.isBackendReady)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, GlowBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (allHealthy) GlowGreen else Color(0xFFFFB300),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SYSTEM & BACKEND STATUS",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Text(
                    text = if (allHealthy) "READY" else "ACTION NEEDED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (allHealthy) GlowGreen else Color(0xFFFFB300),
                    fontWeight = FontWeight.Bold
                )
            }

            StatusRow(
                title = "Display over other apps",
                valueText = if (permissionStatus.overlayGranted) "Granted" else "Not Granted",
                isPositive = permissionStatus.overlayGranted
            )
            StatusRow(
                title = "Accessibility Service",
                valueText = if (permissionStatus.accessibilityGranted) "Enabled" else "Disabled",
                isPositive = permissionStatus.accessibilityGranted
            )
            StatusRow(
                title = "Shizuku Backend",
                valueText = when {
                    shizukuStatus.isBackendReady -> "Ready"
                    shizukuStatus.isServiceRunning -> "Auth Needed"
                    shizukuStatus.isInstalled -> "Stopped"
                    else -> "Not Installed"
                },
                isPositive = shizukuStatus.isBackendReady,
                isWarning = shizukuStatus.isInstalled && !shizukuStatus.isBackendReady
            )
            StatusRow(
                title = "Keymap Profile",
                valueText = if (macroConfig.isConfigured) "${macroConfig.cps} CPS Saved" else "Not Set",
                isPositive = macroConfig.isConfigured,
                isWarning = !macroConfig.isConfigured
            )

            OutlinedButton(
                onClick = onOpenPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("manage_permissions_button"),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, GlowBorderSubtle)
            ) {
                Text(
                    text = "PERMISSIONS & SHIZUKU SETUP",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    title: String,
    valueText: String,
    isPositive: Boolean,
    isWarning: Boolean = false
) {
    val accent = when {
        isPositive -> GlowGreen
        isWarning -> StatusWarning
        else -> StatusError
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isPositive) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}
