package com.g95nc.controller

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class OnboardingWizardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testCheckAgainAndOpenSettingsButtons_invokeTheirCallbacks() {
        var checkedAgain = false
        var settingsOpened = false
        composeRule.setContent {
            AppTheme {
                OnboardingWizard(onOpenSettings = { settingsOpened = true }, onCheckAgain = { checkedAgain = true })
            }
        }

        composeRule.onNodeWithText("Check Again").performClick()
        assertTrue(checkedAgain)

        composeRule.onNodeWithText("Open Settings").performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun testMac_mentionsBetterDisplayCli() {
        composeRule.setContent {
            AppTheme { OnboardingWizard(onOpenSettings = {}, onCheckAgain = {}, platform = Platform.MAC) }
        }
        composeRule.onNodeWithText("This app requires 'betterdisplaycli' on macOS.").assertIsDisplayed()
    }

    @Test
    fun testLinux_mentionsDdcutil() {
        composeRule.setContent {
            AppTheme { OnboardingWizard(onOpenSettings = {}, onCheckAgain = {}, platform = Platform.LINUX) }
        }
        composeRule.onNodeWithText("This app requires 'ddcutil' on Linux.").assertIsDisplayed()
    }

    @Test
    fun testWindows_mentionsNativeToolsFailure() {
        composeRule.setContent {
            AppTheme { OnboardingWizard(onOpenSettings = {}, onCheckAgain = {}, platform = Platform.WINDOWS) }
        }
        composeRule.onNodeWithText("Windows native tools failed to load.").assertIsDisplayed()
    }
}
