package com.g95nc.controller

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
}
