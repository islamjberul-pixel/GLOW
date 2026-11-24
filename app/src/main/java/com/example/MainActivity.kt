package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainViewModel
import com.example.ui.components.AppInfoDialog
import com.example.ui.screens.GamePickerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PermissionSetupScreen
import com.example.ui.theme.GlowFirstTheme

private object Routes {
    const val HOME = "home"
    const val GAME_PICKER = "game_picker"
    const val PERMISSIONS = "permissions"
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GlowFirstTheme {
                GlowFirstAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun GlowFirstAppRoot(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current

    val macroConfig by viewModel.macroConfig.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val engineState by viewModel.engineState.collectAsStateWithLifecycle()
    val installedGames by viewModel.installedGames.collectAsStateWithLifecycle()
    val isLoadingGames by viewModel.isLoadingGames.collectAsStateWithLifecycle()
    val feedbackBanner by viewModel.feedbackBanner.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }
    var checkedFirstLaunch by remember { mutableStateOf(false) }

    // Automatically re-check system permissions & Shizuku authorization when returning to app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAllStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val coreReady = permissionStatus.overlayGranted &&
        permissionStatus.notificationGranted &&
        (permissionStatus.accessibilityGranted || shizukuStatus.isBackendReady)

    // First-launch setup gate
    LaunchedEffect(macroConfig.hasCompletedPermissionSetup, coreReady) {
        if (!checkedFirstLaunch) {
            checkedFirstLaunch = true
            if (!macroConfig.hasCompletedPermissionSetup && !coreReady) {
                navController.navigate(Routes.PERMISSIONS) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                macroConfig = macroConfig,
                permissionStatus = permissionStatus,
                shizukuStatus = shizukuStatus,
                engineState = engineState,
                banner = feedbackBanner,
                onAddGameClick = {
                    viewModel.loadInstalledGames()
                    navController.navigate(Routes.GAME_PICKER) {
                        launchSingleTop = true
                    }
                },
                onOpenGameClick = { viewModel.openSelectedGame() },
                onOpenLiveKeymapClick = { viewModel.openLiveKeymapEditorFromApp() },
                onSelectInputMode = { mode -> viewModel.setInputEngineMode(mode) },
                onToggleOverlay = { enabled -> viewModel.setOverlayEnabled(enabled) },
                onOpenPermissions = {
                    navController.navigate(Routes.PERMISSIONS) {
                        launchSingleTop = true
                    }
                },
                onOpenInfo = { showInfoDialog = true },
                onDismissBanner = { viewModel.dismissBanner() }
            )
        }

        composable(Routes.GAME_PICKER) {
            GamePickerScreen(
                installedGames = installedGames,
                isLoading = isLoadingGames,
                selectedPackageName = macroConfig.selectedGamePackage,
                onRefreshGames = { viewModel.loadInstalledGames() },
                onSelectGame = { game ->
                    viewModel.selectGame(game)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionSetupScreen(
                permissionStatus = permissionStatus,
                shizukuStatus = shizukuStatus,
                isFirstLaunchOnboarding = !macroConfig.hasCompletedPermissionSetup,
                onRefreshPermissions = { viewModel.refreshAllStatuses() },
                onRequestShizukuAuth = { viewModel.requestShizukuAuthorization() },
                onOpenShizukuApp = { viewModel.openShizukuApp() },
                onContinue = {
                    viewModel.markPermissionSetupCompleted()
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }

    if (showInfoDialog) {
        AppInfoDialog(
            hasSavedMacroOrGame = macroConfig.isConfigured || macroConfig.hasSelectedGame,
            onOpenPermissions = {
                navController.navigate(Routes.PERMISSIONS) {
                    launchSingleTop = true
                }
            },
            onClearMacro = { viewModel.clearSavedMacro() },
            onDismiss = { showInfoDialog = false }
        )
    }
}
