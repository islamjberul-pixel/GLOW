package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.model.MacroConfig
import com.example.model.MacroEngineState
import com.example.model.PermissionStatus
import com.example.model.ShizukuStatus
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.GlowFirstTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name and package configuration match GLOW FIRST`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("GLOW FIRST", context.getString(R.string.app_name))
        assertEquals("com.glowfirst.macro", context.packageName)
    }

    @Test
    fun `home screen displays branding, ADD GAME, OPEN GAME, and backend statuses`() {
        composeTestRule.setContent {
            GlowFirstTheme {
                HomeScreen(
                    macroConfig = MacroConfig(),
                    permissionStatus = PermissionStatus(
                        overlayGranted = false,
                        accessibilityGranted = false,
                        notificationGranted = true
                    ),
                    shizukuStatus = ShizukuStatus(),
                    engineState = MacroEngineState(),
                    banner = null,
                    onAddGameClick = {},
                    onOpenGameClick = {},
                    onOpenLiveKeymapClick = {},
                    onSelectInputMode = {},
                    onToggleOverlay = {},
                    onOpenPermissions = {},
                    onOpenInfo = {},
                    onDismissBanner = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GLOW FIRST").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manage_permissions_button").assertIsDisplayed()
    }
}
