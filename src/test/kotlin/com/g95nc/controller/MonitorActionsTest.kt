package com.g95nc.controller

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MonitorActionsTest {

    private fun status(
        layout: Layout,
        panes: Map<PaneChannel, SourceCode> = emptyMap(),
        audio: Int = 0
    ) = MonitorStatus(layout = layout, layoutStatusRaw = 0, paneSources = panes, audioSource = audio, mainInputSource = 0)

    @Test
    fun testApplyTarget_noopWhenTargetAlreadyMatchesCurrentStatus() = runTest {
        val fake = FakeMonitorController()
        val current = status(Layout.PBP_2_50_50, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2), audio = 1)

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = Layout.PBP_2_50_50,
            targetPaneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2),
            targetAudioSource = 1
        )

        assertEquals(emptyList<FakeMonitorController.Call>(), fake.calls)
    }

    @Test
    fun testApplyTarget_changesLayoutThenSourcesInOrder() = runTest {
        val fake = FakeMonitorController()
        val current = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1))

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = Layout.PBP_2_50_50,
            targetPaneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI2, PaneChannel.PANE_2 to SourceCode.DP)
        )

        assertEquals(
            listOf(
                FakeMonitorController.Call.SetLayout(Layout.PBP_2_50_50),
                FakeMonitorController.Call.SetSource(PaneChannel.PANE_1, SourceCode.HDMI2),
                FakeMonitorController.Call.SetSource(PaneChannel.PANE_2, SourceCode.DP)
            ),
            fake.calls
        )
    }

    @Test
    fun testApplyTarget_skipsSettleDelayAfterFinalWriteOnly() = runTest {
        val fake = FakeMonitorController()
        val current = status(Layout.OFF, emptyMap())

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = Layout.PBP_2_50_50,
            targetPaneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2)
        )

        // 1 layout-settle delay + 1 inter-source delay (between pane 1 and pane 2), but none
        // after the final write - getStatus()'s own retry loop absorbs that settle time instead.
        val expectedElapsed = MonitorActions.LAYOUT_SETTLE_DELAY_MS + MonitorActions.SOURCE_SETTLE_DELAY_MS
        assertEquals(expectedElapsed, testScheduler.currentTime)
    }

    @Test
    fun testApplyTarget_usesSetMainSourceWhenTargetLayoutIsOff() = runTest {
        val fake = FakeMonitorController()
        val current = status(Layout.PBP_2_50_50, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2))

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = Layout.OFF,
            targetPaneSources = mapOf(PaneChannel.PANE_1 to SourceCode.DP)
        )

        assertEquals(
            listOf(
                FakeMonitorController.Call.SetLayout(Layout.OFF),
                FakeMonitorController.Call.SetMainSource(SourceCode.DP)
            ),
            fake.calls
        )
    }

    @Test
    fun testApplyTarget_revertRestoresAudioSource() = runTest {
        // Regression test: the original revert/preset-apply code paths never restored audio on
        // revert, silently leaving it changed even though MonitorStatus already carries it.
        val fake = FakeMonitorController()
        val current = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1), audio = 2)
        val previous = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1), audio = 1)

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = previous.layout,
            targetPaneSources = previous.paneSources,
            targetAudioSource = previous.audioSource
        )

        assertEquals(listOf<FakeMonitorController.Call>(FakeMonitorController.Call.SetAudioSource(1)), fake.calls)
    }

    @Test
    fun testApplyTarget_doesNotWriteAudioWhenTargetIsNull() = runTest {
        val fake = FakeMonitorController()
        val current = status(Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1), audio = 2)

        MonitorActions.applyTarget(
            controller = fake,
            currentStatus = current,
            targetLayout = Layout.OFF,
            targetPaneSources = mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1),
            targetAudioSource = null
        )

        assertEquals(emptyList<FakeMonitorController.Call>(), fake.calls)
    }
}
