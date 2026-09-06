package com.g95nc.controller

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
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
            AppTheme(themeMode = ThemeMode.DARK) {
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
    fun testRecoveryExhausted_showsTheUnderlyingControllerErrorWhenOneWasRecorded() {
        LastControllerError.record(RuntimeException("ddcutil: no such file or directory"))
        try {
            setDashboard(status = null, recoveryExhausted = true)

            composeRule.onNodeWithText("ddcutil: no such file or directory").assertIsDisplayed()
        } finally {
            LastControllerError.clear()
        }
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
        composeRule.onNodeWithTag("paneCell_PANE_1").assertIsNotEnabled()
        composeRule.onNodeWithText("Save Current as Preset").assertIsNotEnabled()
        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Delete preset").assertIsNotEnabled()
    }

    @Test
    fun testNotBusy_controlsAreEnabled() {
        setDashboard(isBusy = false)

        composeRule.onNodeWithContentDescription("Refresh").assertIsEnabled()
        composeRule.onNodeWithTag("paneCell_PANE_1").assertIsEnabled()
        composeRule.onNodeWithText("Save Current as Preset").assertIsEnabled()
    }

    @Test
    fun testClickingAPaneCell_opensSourcePickerAndInvokesSetMainSourceWhenLayoutIsOff() {
        var executedAction: (suspend (MonitorStatus?) -> Unit)? = null
        setDashboard(
            status = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1)),
            onExecuteAction = { _, action -> executedAction = action }
        )

        composeRule.onNodeWithTag("paneCell_PANE_1").performClick()
        composeRule.onNodeWithText(SourceCode.DP.description).performClick()

        assertTrue(executedAction != null)
    }

    @Test
    fun testClickingAPaneCell_callsSetSourceOnTheControllerWhenLayoutIsSplit() {
        val controller = FakeMonitorController()
        setDashboard(
            status = status(Layout.PBP_2_50_50, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2)),
            controller = controller,
            onExecuteAction = { _, action -> runBlocking { action(null) } }
        )

        composeRule.onNodeWithTag("paneCell_PANE_2").performClick()
        composeRule.onNodeWithText(SourceCode.DP.description).performClick()

        assertEquals(FakeMonitorController.Call.SetSource(PaneChannel.PANE_2, SourceCode.DP), controller.calls.single())
    }

    @Test
    fun testPaneCell_hasNoAudioOptionWhenLayoutHasOnlyOnePane() {
        setDashboard(status = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1), audio = 0))

        composeRule.onNodeWithTag("paneCell_PANE_1").performClick()

        composeRule.onAllNodes(hasTestTag("setAudioSource_PANE_1")).assertCountEquals(0)
    }

    @Test
    fun testPaneCell_currentAudioPane_hasNoSetAudioSourceOption() {
        setDashboard(
            status = status(
                Layout.PBP_2_50_50,
                mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
                audio = PaneChannel.PANE_1.channel
            )
        )

        // The current audio pane shouldn't offer to re-set itself as the audio source.
        composeRule.onNodeWithTag("paneCell_PANE_1").performClick()
        composeRule.onAllNodes(hasTestTag("setAudioSource_PANE_1")).assertCountEquals(0)
    }

    @Test
    fun testPaneCell_offersSetAsAudioSourceAndCallsSetAudioSourceOnSelection() {
        val controller = FakeMonitorController()
        setDashboard(
            status = status(
                Layout.PBP_2_50_50,
                mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
                audio = PaneChannel.PANE_1.channel
            ),
            controller = controller,
            onExecuteAction = { _, action -> runBlocking { action(null) } }
        )

        composeRule.onNodeWithTag("paneCell_PANE_2").performClick()
        composeRule.onNodeWithTag("setAudioSource_PANE_2").performClick()

        assertEquals(FakeMonitorController.Call.SetAudioSource(PaneChannel.PANE_2.channel), controller.calls.single())
    }

    @Test
    fun testPaneCell_showsAudioIconOnlyOnTheCurrentAudioPane() {
        setDashboard(
            status = status(
                Layout.PBP_2_50_50,
                mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
                audio = PaneChannel.PANE_2.channel
            )
        )

        // Exactly one such icon exists across the whole diagram - onNodeWithContentDescription
        // itself fails if there were zero or more than one, so this also confirms PANE_1 has none.
        composeRule.onNodeWithContentDescription("Audio source pane").assertIsDisplayed()
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
    fun testActivePresetBadge_showsOnlyOnThePresetMatchingCurrentStatus() {
        val matching = Preset(id = "p1", name = "Matches", layout = Layout.OFF, paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1))
        val other = Preset(id = "p2", name = "Different", layout = Layout.OFF, paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.DP))
        setDashboard(
            status = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1)),
            config = AppConfig(presets = listOf(matching, other))
        )

        composeRule.onAllNodesWithText("Active").assertCountEquals(1)
    }

    @Test
    fun testActivePresetBadge_absentWhenNoPresetMatchesCurrentStatus() {
        val preset = Preset(id = "p1", name = "NotActive", layout = Layout.OFF, paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.DP))
        setDashboard(
            status = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1)),
            config = AppConfig(presets = listOf(preset))
        )

        composeRule.onAllNodesWithText("Active").assertCountEquals(0)
    }

    @Test
    fun testPresetAudioChip_showsRoutedPaneWhenLayoutHasMoreThanOnePane() {
        val preset = Preset(
            id = "p1", name = "A", layout = Layout.PBP_2_50_50,
            paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
            audioSource = PaneChannel.PANE_2.channel
        )
        setDashboard(config = AppConfig(presets = listOf(preset)))

        composeRule.onNodeWithText("Audio: Right/Sub").assertIsDisplayed()
    }

    @Test
    fun testPresetAudioChip_absentWhenPresetHasNoAudioSourceRecorded() {
        val preset = Preset(
            id = "p1", name = "A", layout = Layout.PBP_2_50_50,
            paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
            audioSource = null
        )
        setDashboard(config = AppConfig(presets = listOf(preset)))

        composeRule.onAllNodesWithText("Audio:", substring = true).assertCountEquals(0)
    }

    @Test
    fun testPresetAudioChip_absentWhenLayoutHasOnlyOnePane() {
        val preset = Preset(
            id = "p1", name = "A", layout = Layout.OFF,
            paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1),
            audioSource = 0
        )
        setDashboard(config = AppConfig(presets = listOf(preset)))

        composeRule.onAllNodesWithText("Audio:", substring = true).assertCountEquals(0)
    }

    @Test
    fun testPresetReorder_moveDownSwapsWithTheNextPreset() {
        val a = Preset(id = "1", name = "A", layout = Layout.OFF, paneSources = emptyMap())
        val b = Preset(id = "2", name = "B", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(a, b)), onConfigChange = { reported = it })

        composeRule.onAllNodesWithContentDescription("Move preset down")[0].performScrollTo().performClick()

        assertEquals(listOf("2", "1"), reported?.presets?.map { it.id })
    }

    @Test
    fun testPresetReorder_moveUpSwapsWithThePreviousPreset() {
        val a = Preset(id = "1", name = "A", layout = Layout.OFF, paneSources = emptyMap())
        val b = Preset(id = "2", name = "B", layout = Layout.OFF, paneSources = emptyMap())
        var reported: AppConfig? = null
        setDashboard(config = AppConfig(presets = listOf(a, b)), onConfigChange = { reported = it })

        composeRule.onAllNodesWithContentDescription("Move preset up")[1].performScrollTo().performClick()

        assertEquals(listOf("2", "1"), reported?.presets?.map { it.id })
    }

    @Test
    fun testPresetReorder_boundaryButtonsAreDisabled() {
        val a = Preset(id = "1", name = "A", layout = Layout.OFF, paneSources = emptyMap())
        val b = Preset(id = "2", name = "B", layout = Layout.OFF, paneSources = emptyMap())
        setDashboard(config = AppConfig(presets = listOf(a, b)))

        composeRule.onAllNodesWithContentDescription("Move preset up")[0].performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Move preset down")[1].performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithContentDescription("Move preset down")[0].performScrollTo().assertIsEnabled()
        composeRule.onAllNodesWithContentDescription("Move preset up")[1].performScrollTo().assertIsEnabled()
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
