package com.g95nc.controller

import kotlinx.coroutines.delay

/**
 * Shared "apply a target layout/sources/audio" sequence, used for both preset application and
 * safety-countdown revert. Centralized so a protocol-timing fix only needs to be made once.
 */
object MonitorActions {
    const val LAYOUT_SETTLE_DELAY_MS = 4000L
    const val SOURCE_SETTLE_DELAY_MS = 4000L

    suspend fun applyTarget(
        controller: MonitorController,
        currentStatus: MonitorStatus?,
        targetLayout: Layout?,
        targetPaneSources: Map<PaneChannel, SourceCode>,
        targetAudioSource: Int? = null
    ) {
        if (targetLayout != null && targetLayout != currentStatus?.layout) {
            controller.setLayout(targetLayout)
            delay(LAYOUT_SETTLE_DELAY_MS)
        }

        val pendingSourceWrites = targetPaneSources.entries
            .filter { (channel, source) -> currentStatus?.paneSources?.get(channel) != source }

        pendingSourceWrites.forEachIndexed { index, (channel, source) ->
            if (targetLayout == Layout.OFF && channel == PaneChannel.PANE_1) {
                controller.setMainSource(source)
            } else {
                controller.setSource(channel, source)
            }
            // No settle delay after the final write: getStatus() already polls/waits for the
            // DDC bus to stabilize before returning, so a blind sleep here is pure dead time.
            if (index != pendingSourceWrites.lastIndex) {
                delay(SOURCE_SETTLE_DELAY_MS)
            }
        }

        if (targetAudioSource != null && currentStatus?.audioSource != targetAudioSource) {
            controller.setAudioSource(targetAudioSource)
        }
    }
}
