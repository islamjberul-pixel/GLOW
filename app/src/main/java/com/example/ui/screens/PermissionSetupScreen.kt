package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PermissionStatus
import com.example.model.ShizukuStatus
import com.example.ui.components.StatusPill
import com.example.ui.theme.GlowBlack
import com.example.ui.theme.GlowBorderSubtle
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.GlowSurface
import com.example.ui.theme.GlowSurfaceCard
import com.example.ui.theme.GlowSurfaceElevated
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.PermissionHelper

@Composable
fun PermissionSetupScreen(
    permissionStatus: PermissionStatus,
    shizukuStatus: ShizukuStatus,
    isFirstLaunchOnboarding: Boolean,
    onRefreshPermissions: () -> Unit,
    onRequestShizukuAuth: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onContinue: () -> Unit,
    onBack: (() -> Unit)?
) {
    val context = LocalContext.current
    var askedNotificationOnce by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onRefreshPermissions()
        if (!granted && askedNotificationOnce) {
            runCatching {
                context.startActivity(PermissionHelper.buildNotificationSettingsIntent(context))
            }
        }
        askedNotificationOnce = true
    }

    val hasAnyBackendReady = permissionStatus.accessibilityGranted || shizukuStatus.isBackendReady
    val coreReady = permissionStatus.overlayGranted && permissionStatus.notificationGranted && hasAnyBackendReady

    Scaffold(
        containerColor = GlowBlack,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(GlowSurface, GlowBlack, GlowBlack))
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null && !isFirstLaunchOnboarding) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("permissions_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PERMISSIONS & SHIZUKU",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Configure overlay, notification, and your preferred tap backend (Accessibility or Shizuku).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // 1. Display over other apps
                PermissionItemCard(
                    icon = Icons.Rounded.Layers,
                    title = "1. Display Over Other Apps",
                    description = "Required for both the Live Keymap Editor over your game and the floating circular Trigger button.",
                    isGranted = permissionStatus.overlayGranted,
                    actionLabel = if (permissionStatus.overlayGranted) "Granted" else "Open Overlay Settings",
                    buttonTestTag = "grant_overlay_button",
                    onActionClick = {
                        runCatching {
                            context.startActivity(PermissionHelper.buildOverlaySettingsIntent(context))
                        }
                    }
                )

                // 2. Accessibility Service
                PermissionItemCard(
                    icon = Icons.Rounded.AccessibilityNew,
                    title = "2. Accessibility Service Backend",
                    description = "Standard no-ADB tap engine. Enable \"GLOW FIRST Tap Engine\" in Android Accessibility Settings to use Accessibility Mode or Automatic fallback.",
                    isGranted = permissionStatus.accessibilityGranted,
                    actionLabel = if (permissionStatus.accessibilityGranted) "Enabled" else "Open Accessibility Settings",
                    buttonTestTag = "grant_accessibility_button",
                    onActionClick = {
                        runCatching {
                            context.startActivity(PermissionHelper.buildAccessibilitySettingsIntent())
                        }
                    }
                )

                // 3. Dedicated Shizuku Setup Card
                ShizukuSetupCard(
                    shizukuStatus = shizukuStatus,
                    onRequestAuthorization = onRequestShizukuAuth,
                    onOpenShizukuApp = onOpenShizukuApp,
                    onRefreshStatus = onRefreshPermissions
                )

                // 4. Notifications
                PermissionItemCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "4. Notifications (Android 13+)"
                    } else {
                        "4. Foreground Service Notification"
                    },
                    description = "Provides the KEYMAP / EDIT KEYMAP and STOP actions while your game is open.",
                    isGranted = permissionStatus.notificationGranted,
                    actionLabel = if (permissionStatus.notificationGranted) "Granted" else "Allow Notifications",
                    buttonTestTag = "grant_notification_button",
                    onActionClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotificationOnce) {
                            askedNotificationOnce = true
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching {
                                context.startActivity(PermissionHelper.buildNotificationSettingsIntent(context))
                            }
                        }
                    }
                )

                // 5. Battery optimization guidance
                PermissionItemCard(
                    icon = Icons.Rounded.BatterySaver,
                    title = "5. Battery Optimization (Nokia C30 / Android 11)",
                    description = "Set GLOW FIRST to Unrestricted / Don't Optimize so Android 11 does not kill the foreground overlay service mid-match.",
                    isGranted = permissionStatus.batteryOptimizationIgnored,
                    positiveLabel = "Unrestricted",
                    negativeLabel = "Standard",
                    isWarningWhenNotGranted = true,
                    actionLabel = "Open Battery Settings",
                    buttonTestTag = "battery_settings_button",
                    onActionClick = {
                        runCatching {
                            context.startActivity(PermissionHelper.buildBatteryOptimizationSettingsIntent())
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("continue_permissions_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowGreen,
                        contentColor = GlowBlack
                    )
                ) {
                    if (coreReady) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (coreReady) "CONTINUE TO GLOW FIRST" else "CONTINUE ANYWAY",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ShizukuSetupCard(
    shizukuStatus: ShizukuStatus,
    onRequestAuthorization: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (shizukuStatus.isBackendReady) GlowGreen.copy(alpha = 0.45f) else GlowBorderSubtle
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = if (shizukuStatus.isBackendReady) GlowGreen else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "3. Shizuku Backend Setup",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                StatusPill(
                    label = when {
                        shizukuStatus.isBackendReady -> "READY"
                        shizukuStatus.isServiceRunning -> "AUTH NEEDED"
                        shizukuStatus.isInstalled -> "STOPPED"
                        else -> "NOT INSTALLED"
                    },
                    isPositive = shizukuStatus.isBackendReady,
                    isWarning = shizukuStatus.isInstalled && !shizukuStatus.isBackendReady
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlowSurfaceCard, MaterialTheme.shapes.medium)
                    .border(1.dp, GlowBorderSubtle, MaterialTheme.shapes.medium)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ShizukuChecklistRow("Shizuku App Installed", shizukuStatus.isInstalled)
                ShizukuChecklistRow("Shizuku Service Running", shizukuStatus.isServiceRunning)
                ShizukuChecklistRow("GLOW FIRST Authorized", shizukuStatus.isAuthorized)
                ShizukuChecklistRow("Shizuku Backend Ready", shizukuStatus.isBackendReady)
            }

            if (!shizukuStatus.errorMessage.isNullOrBlank()) {
                Text(
                    text = shizukuStatus.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError
                )
            }

            Text(
                text = "Android 11 Setup: Open Shizuku -> Pair & start via Wireless Debugging (Developer Options) or ADB (`adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`), then tap Authorize below.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRequestAuthorization,
                    enabled = shizukuStatus.isInstalled && shizukuStatus.isServiceRunning && !shizukuStatus.isAuthorized,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("authorize_shizuku_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowGreen,
                        contentColor = GlowBlack
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (shizukuStatus.isAuthorized) "Authorized" else "Authorize",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = if (shizukuStatus.isInstalled) onOpenShizukuApp else onRefreshStatus,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_shizuku_app_button"),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, GlowBorderSubtle)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (shizukuStatus.isInstalled) "Open Shizuku" else "Re-check",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuChecklistRow(
    label: String,
    ok: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (ok) GlowGreen else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }
        Text(
            text = if (ok) "Yes" else "No",
            style = MaterialTheme.typography.labelSmall,
            color = if (ok) GlowGreen else TextSecondary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    buttonTestTag: String,
    positiveLabel: String = "Granted",
    negativeLabel: String = "Not Granted",
    isWarningWhenNotGranted: Boolean = false,
    onActionClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = GlowSurfaceElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (isGranted) GlowGreen.copy(alpha = 0.35f) else GlowBorderSubtle
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) GlowGreen else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(
                    label = if (isGranted) positiveLabel else negativeLabel,
                    isPositive = isGranted,
                    isWarning = !isGranted && isWarningWhenNotGranted
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            OutlinedButton(
                onClick = onActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(buttonTestTag),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    1.dp,
                    if (isGranted) GlowGreen.copy(alpha = 0.4f) else GlowBorderSubtle
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = if (isGranted) GlowGreen else TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isGranted) GlowGreen else TextPrimary
                )
            }
        }
    }
}
