package com.g95nc.controller

import kotlinx.coroutines.delay

/**
 * Shared "apply a target layout/sources/audio" sequence, used for both preset application and
 * safety-countdown revert. Centralized so a protocol-timing fix only needs to be made once.
 */
object MonitorActions {
    // TODO: this flat delay is applied to every layout transition alike, but the g95nc-ddc
    // protocol notes specifically call out 3-way PBP (PBP_3_*) transitions as briefly dropping
    // DDC entirely while the monitor re-enumerates - a bigger disruption than a 2-way/PIP switch.
    // A single fixed delay may be needlessly long for 2-way/PIP and yet still not long enough for
    // 3-way. Not changed here without real-hardware data (see README) to size a 3-way-specific
    // delay safely rather than guessing one.
    const val LAYOUT_SETTLE_DELAY_MS = 4000L
    const val SOURCE_SETTLE_DELAY_MS = 4000L

    /** Returns whether every write actually attempted (targets already matching are skipped and
     * don't count against this) reported success - the caller decides how to surface a failure. */
    suspend fun applyTarget(
        controller: MonitorController,
        currentStatus: MonitorStatus?,
        targetLayout: Layout?,
        targetPaneSources: Map<PaneChannel, SourceCode>,
        targetAudioSource: Int? = null
    ): Boolean {
        var allSucceeded = true

        if (targetLayout != null && targetLayout != currentStatus?.layout) {
            if (!controller.setLayout(targetLayout)) allSucceeded = false
            delay(LAYOUT_SETTLE_DELAY_MS)
        }

        val pendingSourceWrites = targetPaneSources.entries
            .filter { (channel, source) -> currentStatus?.paneSources?.get(channel) != source }

        pendingSourceWrites.forEachIndexed { index, (channel, source) ->
            val wroteOk = if (targetLayout == Layout.OFF && channel == PaneChannel.PANE_1) {
                controller.setMainSource(source)
            } else {
                controller.setSource(channel, source)
            }
            if (!wroteOk) allSucceeded = false
            // No settle delay after the final write: getStatus() already polls/waits for the
            // DDC bus to stabilize before returning, so a blind sleep here is pure dead time.
            if (index != pendingSourceWrites.lastIndex) {
                delay(SOURCE_SETTLE_DELAY_MS)
            }
        }

        if (targetAudioSource != null && currentStatus?.audioSource != targetAudioSource) {
            if (!controller.setAudioSource(targetAudioSource)) allSucceeded = false
        }

        return allSucceeded
    }
}
