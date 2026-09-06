package com.g95nc.controller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(
                    config = AppConfig(ddcutilPath = "ddcutil", betterdisplaycliPath = "betterdisplaycli", enableSafetyCountdown = true),
                    onSave = { saved = it },
                    controller = FakeMonitorController(),
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
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(ddcutilPath = "ddcutil"), onSave = {}, controller = FakeMonitorController(), commandRunner = runner, platform = Platform.LINUX)
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
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(ddcutilPath = "/bad/path"), onSave = {}, controller = FakeMonitorController(), commandRunner = runner, platform = Platform.LINUX)
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
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(ddcutilPath = "ddcutil"), onSave = {}, controller = FakeMonitorController(), commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("ddcutil path is reachable").assertIsDisplayed()

        composeRule.onNodeWithTag("ddcutilPathField").performTextInput("2")

        composeRule.onAllNodesWithContentDescription("ddcutil path is reachable").assertCountEquals(0)
    }

    @Test
    fun testFailedTest_disablesSaveUntilThePathIsFixed() {
        val runner = FakeCommandRunner { throw RuntimeException("not found") }
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(ddcutilPath = "/bad/path"), onSave = {}, controller = FakeMonitorController(), commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithText("Save Configuration").assertIsEnabled()

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save Configuration").assertIsNotEnabled()
        composeRule.onNodeWithText("Fix the path above before saving").assertIsDisplayed()
    }

    @Test
    fun testSuccessfulTest_keepsSaveEnabled() {
        val runner = FakeCommandRunner { "ddcutil 2.1.0" }
        var saved: AppConfig? = null
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(ddcutilPath = "ddcutil"), onSave = { saved = it }, controller = FakeMonitorController(), commandRunner = runner, platform = Platform.LINUX)
            }
        }

        composeRule.onAllNodesWithText("Test")[0].performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Save Configuration").assertIsEnabled().performClick()

        assertEquals("ddcutil", saved?.ddcutilPath)
    }

    @Test
    fun testLinux_showsOnlyDdcutilField() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = FakeMonitorController(), platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithTag("ddcutilPathField").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("betterdisplaycliPathField")).assertCountEquals(0)
    }

    @Test
    fun testMac_showsOnlyBetterDisplayCliField() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = FakeMonitorController(), platform = Platform.MAC)
            }
        }

        composeRule.onNodeWithTag("betterdisplaycliPathField").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("ddcutilPathField")).assertCountEquals(0)
    }

    @Test
    fun testWindows_showsNeitherPathFieldAndShowsAMessageInstead() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = FakeMonitorController(), platform = Platform.WINDOWS)
            }
        }

        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("ddcutilPathField")).assertCountEquals(0)
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("betterdisplaycliPathField")).assertCountEquals(0)
        composeRule.onNodeWithText("No additional configuration is needed on this platform.").assertIsDisplayed()
    }

    @Test
    fun testThemeModeDefaultsToTheSavedConfigValue() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(themeMode = ThemeMode.LIGHT), onSave = {}, controller = FakeMonitorController(), platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithTag("themeModeButton").assertIsDisplayed()
        composeRule.onNodeWithText("Light").assertIsDisplayed()
    }

    @Test
    fun testSelectingATheme_andSaving_passesItThrough() {
        var saved: AppConfig? = null
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(themeMode = ThemeMode.SYSTEM), onSave = { saved = it }, controller = FakeMonitorController(), platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithTag("themeModeButton").performClick()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.onNodeWithText("Save Configuration").performClick()

        assertEquals(ThemeMode.DARK, saved?.themeMode)
    }

    @Test
    fun testDebugInfo_isCollapsedByDefault() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = FakeMonitorController(), platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithText("Show Debug Info").assertIsDisplayed()
        composeRule.onAllNodes(androidx.compose.ui.test.hasTestTag("readRawVcpButton")).assertCountEquals(0)
    }

    @Test
    fun testDebugInfo_expandingShowsRawVcpValuesOnceRead() {
        val controller = FakeMonitorController(rawVcpValues = mapOf("E2" to 0x39, "E8" to 0x01))
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.DARK) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = controller, platform = Platform.LINUX)
            }
        }

        composeRule.onNodeWithText("Show Debug Info").performClick()
        composeRule.onNodeWithTag("readRawVcpButton").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("debugRawVcpValues").assertIsDisplayed()
        composeRule.onNodeWithText("E2 (Layout): 0x39").assertIsDisplayed()
        composeRule.onNodeWithText("E8 (Audio source): 0x01").assertIsDisplayed()
        composeRule.onNodeWithText("ED (Unknown): read failed").assertIsDisplayed()
    }
}
