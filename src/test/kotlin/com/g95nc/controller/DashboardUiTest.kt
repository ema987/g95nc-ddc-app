package com.g95nc.controller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These exercise Dashboard purely through its explicit params (config/onConfigChange etc.),
 * never the real ConfigManager singleton - no test here can ever touch a real file on disk.
 */
class DashboardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun status(
        layout: Layout,
        panes: Map<PaneChannel, SourceCode> = emptyMap(),
        audio: Int = 0
    ) = MonitorStatus(layout = layout, layoutStatusRaw = 0, paneSources = panes, audioSource = audio, mainInputSource = 0)

    private fun setDashboard(
        status: MonitorStatus? = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1)),
        controller: MonitorController = FakeMonitorController(),
        pendingChange: PendingChange? = null,
        recoveryExhausted: Boolean = false,
        isBusy: Boolean = false,
        config: AppConfig = AppConfig(),
        onConfigChange: (AppConfig) -> Unit = {},
        onExecuteAction: (Boolean, suspend (MonitorStatus?) -> Unit) -> Unit = { _, _ -> },
        onRefresh: () -> Unit = {},
        onRevertChange: () -> Unit = {},
        onConfirmChange: () -> Unit = {}
    ) {
        composeRule.setContent {
            AppTheme {
                Dashboard(
                    status = status,
                    controller = controller,
                    pendingChange = pendingChange,
                    recoveryExhausted = recoveryExhausted,
                    isBusy = isBusy,
                    config = config,
                    onConfigChange = onConfigChange,
                    onSettingsClick = {},
                    onConfirmChange = onConfirmChange,
                    onRevertChange = onRevertChange,
                    onExecuteAction = onExecuteAction,
                    onRefresh = onRefresh
                )
            }
        }
    }

    @Test
    fun testRecoveryExhausted_showsGiveUpMessageAndRetryButtonCallsOnRefresh() {
        var refreshClicked = false
        setDashboard(status = null, recoveryExhausted = true, onRefresh = { refreshClicked = true })

        composeRule.onNodeWithText("Couldn't reach the monitor after several attempts.").assertIsDisplayed()
        composeRule.onNodeWithText("Try Again").performClick()

        assertTrue(refreshClicked)
    }

    @Test
    fun testStillPolling_showsLoadingMessageInsteadOfGiveUpMessage() {
        setDashboard(status = null, recoveryExhausted = false)

        composeRule.onNodeWithText("Loading status or waiting for the monitor to respond...").assertIsDisplayed()
    }

    @Test
    fun testBusy_disablesControlsThatWouldStartAnotherAction() {
        val preset = Preset(id = "p1", name = "A Preset", layout = Layout.OFF, paneSources = emptyMap())
        setDashboard(isBusy = true, config = AppConfig(presets = listOf(preset)))

        composeRule.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        composeRule.onNodeWithTag("sourceSelector_PANE_1").assertIsNotEnabled()
        composeRule.onNodeWithText("Save Current as Preset").assertIsNotEnabled()
        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Delete preset").assertIsNotEnabled()
    }

    @Test
    fun testNotBusy_controlsAreEnabled() {
        setDashboard(isBusy = false)

        composeRule.onNodeWithContentDescription("Refresh").assertIsEnabled()
        composeRule.onNodeWithTag("sourceSelector_PANE_1").assertIsEnabled()
        composeRule.onNodeWithText("Save Current as Preset").assertIsEnabled()
    }

    @Test
    fun testSafeModeCheckbox_reflectsConfigAndReportsToggleWithoutTouchingConfigManager() {
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(enableSafetyCountdown = false), onConfigChange = { reported = it })

        composeRule.onNodeWithText("Safe Mode").assertIsDisplayed()
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()

        assertEquals(true, reported?.enableSafetyCountdown)
    }

    @Test
    fun testNoPresets_showsEmptyStateInsteadOfBlankSpace() {
        setDashboard(config = AppConfig(presets = emptyList()))

        composeRule.onNodeWithText("No presets yet - save your current layout above to create one.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPresetList_showsNameLayoutAndPerPaneSources() {
        val preset = Preset(
            id = "p1",
            name = "Streaming",
            layout = Layout.PBP_2_50_50,
            paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.DP)
        )
        setDashboard(config = AppConfig(presets = listOf(preset)))

        composeRule.onNodeWithText("Streaming").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(Layout.PBP_2_50_50.description).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Left: HDMI 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Right/Sub: DisplayPort").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("No presets yet - save your current layout above to create one.").assertCountEquals(0)
    }

    @Test
    fun testPresetDelete_showsConfirmationBeforeReportingConfigWithoutThatPreset() {
        val preset = Preset(id = "p1", name = "ToDelete", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(preset)), onConfigChange = { reported = it })

        composeRule.onNodeWithContentDescription("Delete preset").performScrollTo().performClick()

        // Clicking the icon must not delete anything yet - only confirming the dialog does.
        composeRule.onNodeWithText("Delete preset?").assertIsDisplayed()
        assertNull(reported)

        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(emptyList(), reported?.presets)
    }

    @Test
    fun testPresetDelete_cancelLeavesThePresetInPlace() {
        val preset = Preset(id = "p1", name = "KeepMe", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(preset)), onConfigChange = { reported = it })

        composeRule.onNodeWithContentDescription("Delete preset").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertNull(reported)
        composeRule.onNodeWithText("KeepMe").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPresetRename_confirmReportsConfigWithNewName() {
        val preset = Preset(id = "p1", name = "Old Name", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(preset)), onConfigChange = { reported = it })

        composeRule.onNodeWithContentDescription("Rename preset").performScrollTo().performClick()
        composeRule.onNodeWithTag("renamePresetField").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("renamePresetField").performTextInput("New Name")
        composeRule.onNodeWithContentDescription("Confirm rename").performScrollTo().performClick()

        assertEquals("New Name", reported?.presets?.single()?.name)
    }

    @Test
    fun testPresetRename_cancelReportsNothingAndKeepsOriginalName() {
        val preset = Preset(id = "p1", name = "Old Name", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(preset)), onConfigChange = { reported = it })

        composeRule.onNodeWithContentDescription("Rename preset").performScrollTo().performClick()
        composeRule.onNodeWithTag("renamePresetField").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("renamePresetField").performTextInput("Should Not Save")
        composeRule.onNodeWithContentDescription("Cancel rename").performScrollTo().performClick()

        assertNull(reported)
        composeRule.onNodeWithText("Old Name").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun testPresetApply_invokesOnExecuteActionWithSafeModeFromConfig() {
        val preset = Preset(id = "p1", name = "Apply Me", layout = Layout.OFF, paneSources = emptyMap())
        var receivedSafeMode: Boolean? = null
        setDashboard(
            config = AppConfig(presets = listOf(preset), enableSafetyCountdown = true),
            onExecuteAction = { safeMode, _ -> receivedSafeMode = safeMode }
        )

        composeRule.onNodeWithText("Apply").performScrollTo().performClick()

        assertEquals(true, receivedSafeMode)
    }

    @Test
    fun testSaveCurrentAsPreset_appendsPresetBuiltFromCurrentStatus() {
        var reported: AppConfig? = null
        setDashboard(
            status = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.DP), audio = 3),
            config = AppConfig(),
            onConfigChange = { reported = it }
        )

        composeRule.onNodeWithText("Save Current as Preset").performScrollTo().performClick()
        composeRule.onNodeWithTag("newPresetNameField").performScrollTo().performTextInput("My Preset")
        composeRule.onNodeWithText("Save").performScrollTo().performClick()

        val saved = reported?.presets?.singleOrNull()
        assertEquals("My Preset", saved?.name)
        assertEquals(Layout.OFF, saved?.layout)
        assertEquals(SourceCode.DP, saved?.paneSources?.get(PaneChannel.PANE_1))
        assertEquals(3, saved?.audioSource)
    }

    @Test
    fun testPendingChangeCard_showsCountdownAndRevertConfirmCallbacksFire() {
        var reverted = false
        var confirmed = false
        val change = PendingChange(status(Layout.OFF), countdown = 7)
        setDashboard(pendingChange = change, onRevertChange = { reverted = true }, onConfirmChange = { confirmed = true })

        composeRule.onNodeWithText("Keep these display settings?\nReverting in 7s...").assertIsDisplayed()

        composeRule.onNodeWithText("Revert").performClick()
        assertTrue(reverted)

        composeRule.onNodeWithText("Confirm").performClick()
        assertTrue(confirmed)
    }
}
