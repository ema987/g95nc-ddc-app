package com.g95nc.controller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SettingsModalUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testSaveConfiguration_passesEditedPathAndToggledCheckbox() {
        var saved: AppConfig? = null
        composeRule.setContent {
            AppTheme {
                SettingsModal(
                    config = AppConfig(ddcutilPath = "ddcutil", betterdisplaycliPath = "betterdisplaycli", enableSafetyCountdown = true),
                    onSave = { saved = it },
                    platform = Platform.LINUX
                )
            }
        }

        composeRule.onNodeWithTag("ddcutilPathField").performTextClearance()
        composeRule.onNodeWithTag("ddcutilPathField").performTextInput("/usr/local/bin/ddcutil")

        composeRule.onAllNodes(isToggleable()).onFirst().performClick()

        composeRule.onNodeWithText("Save Configuration").performClick()

        assertEquals("/usr/local/bin/ddcutil", saved?.ddcutilPath)
        assertEquals("betterdisplaycli", saved?.betterdisplaycliPath)
        assertEquals(false, saved?.enableSafetyCountdown)
    }

    @Test
    fun testTestButton_showsSuccessIconWhenToolIsReachable() {
        val runner = FakeCommandRunner { "ddcutil 2.1.0" }
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(ddcutilPath = "ddcutil"), onSave = {}, commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("ddcutil path is reachable").assertIsDisplayed()
    }

    @Test
    fun testTestButton_showsErrorIconWhenToolIsNotReachable() {
        val runner = FakeCommandRunner { throw RuntimeException("not found") }
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(ddcutilPath = "/bad/path"), onSave = {}, commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("ddcutil path is not reachable").assertIsDisplayed()
    }

    @Test
    fun testEditingPath_clearsThePreviousTestResult() {
        val runner = FakeCommandRunner { "ddcutil 2.1.0" }
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(ddcutilPath = "ddcutil"), onSave = {}, commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("ddcutil path is reachable").assertIsDisplayed()

        composeRule.onNodeWithTag("ddcutilPathField").performTextInput("2")

        composeRule.onAllNodesWithContentDescription("ddcutil path is reachable").assertCountEquals(0)
    }

    @Test
    fun testLinux_showsOnlyDdcutilField() {
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(), onSave = {}, platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithTag("ddcutilPathField").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("betterdisplaycliPathField")).assertCountEquals(0)
    }

    @Test
    fun testMac_showsOnlyBetterDisplayCliField() {
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(), onSave = {}, platform = Platform.MAC)
            }
        }

        composeRule.onNodeWithTag("betterdisplaycliPathField").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("ddcutilPathField")).assertCountEquals(0)
    }

    @Test
    fun testWindows_showsNeitherPathFieldAndShowsAMessageInstead() {
        composeRule.setContent {
            AppTheme {
                SettingsModal(config = AppConfig(), onSave = {}, platform = Platform.WINDOWS)
            }
        }

        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("ddcutilPathField")).assertCountEquals(0)
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("betterdisplaycliPathField")).assertCountEquals(0)
        composeRule.onNodeWithText("No additional configuration is needed on this platform.").assertIsDisplayed()
    }
}
