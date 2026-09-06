package com.g95nc.controller

import kotlinx.coroutines.delay

/**
 * Shared VCP polling/decoding state machine used by every [MonitorController] implementation.
 *
 * The Odyssey G9's DDC bus can echo stale/busy values (0x00 or 0x3900) for a while after a
 * source or layout switch, so a single read is not trustworthy. This reads E2 repeatedly until
 * it settles, then decodes E3 (pane sources) and E8 (audio) only once the readback looks sane.
 */
object DdcStatusReader {
    data class PollingConfig(
        val maxAttempts: Int = 12,
        val pollDelayMs: Long = 500,
        val zeroConfirmations: Int = 2
    )

    fun layoutFromE2(e2: Int): Layout = when (e2) {
        0x3901, 0x3903 -> Layout.PBP_2_50_50
        0x3909 -> Layout.PBP_3_50_25_25
        0x390b -> Layout.PBP_3_25_50_25
        0x390d -> Layout.PBP_3_25_25_50
        0x390f -> Layout.PIP
        else -> Layout.OFF
    }

    /**
     * @param readVcp reads a single VCP feature by its canonical hex code ("E2", "60", "E3", "E8"),
     * returning null on failure. Bus/tag discovery and CLI-specific formatting stay in the caller.
     */
    suspend fun readStatus(
        config: PollingConfig = PollingConfig(),
        readVcp: suspend (vcp: String) -> Int?
    ): MonitorStatus? {
        var consecutiveZeros = 0

        repeat(config.maxAttempts) {
            val e2 = readVcp("E2")

            if (e2 == null) {
                consecutiveZeros = 0
                delay(config.pollDelayMs)
                return@repeat
            }

            if (e2 == 0 || e2 == 0x3900) {
                consecutiveZeros++
                if (consecutiveZeros < config.zeroConfirmations) {
                    delay(config.pollDelayMs)
                    return@repeat
                }

                // Confirmed Single Source mode - only now is it safe to read 0x60.
                val v60 = readVcp("60") ?: 0
                val mainSource = SourceCode.entries.find { it.mccsCode == v60 }
                return MonitorStatus(
                    layout = Layout.OFF,
                    layoutStatusRaw = e2,
                    paneSources = mainSource?.let { mapOf(PaneChannel.PANE_1 to it) } ?: emptyMap(),
                    // E8 is documented as a single-byte value (max 0x02, three options), but this
                    // monitor's readback carries a non-zero high byte too (observed: 0x1002) - the
                    // same "extra byte" pattern as E2/E3's status reads, just not called out in
                    // the protocol notes for E8. Masking to the low byte is what actually matches
                    // the three documented options.
                    audioSource = (readVcp("E8") ?: 0) and 0xFF,
                    mainInputSource = v60
                )
            }

            consecutiveZeros = 0

            if ((e2 and 0xFF00) == 0x3900) {
                val e3 = readVcp("E3")
                if (e3 == null) {
                    delay(config.pollDelayMs)
                    return@repeat
                }

                // KNOWN GAP: this decode only recovers two pane sources (high byte / low byte),
                // which is all a 2-way split needs - but a 3-way split (PBP_3_*) has a third pane
                // that's simply never populated here. The g95nc-ddc protocol notes only vaguely
                // describe the 3-way E3 readback ("high byte 0xc0, low bytes encode current pane
                // sources") without the exact bit layout, and this project's own convention is to
                // verify protocol behavior empirically rather than guess it - a wrong guess here
                // could easily make 3-way status resolution worse (e.g. never validating and
                // falling through to recoveryExhausted) rather than better. Needs a real `getvcp
                // E3` (or betterdisplaycli equivalent) reading taken while the monitor is actually
                // in a 3-way layout before this can be fixed for real.
                val leftCode = (e3 shr 8) and 0xFF
                val rightCode = e3 and 0xFF
                if (SourceCode.fromE3Code(leftCode) == null || SourceCode.fromE3Code(rightCode) == null) {
                    // Bus is still echoing garbage - retry the whole E2 fetch.
                    delay(config.pollDelayMs)
                    return@repeat
                }

                val panes = mutableMapOf<PaneChannel, SourceCode>()
                SourceCode.fromE3Code(leftCode)?.let { panes[PaneChannel.PANE_1] = it }
                SourceCode.fromE3Code(rightCode)?.let { panes[PaneChannel.PANE_2] = it }

                return MonitorStatus(
                    layout = layoutFromE2(e2),
                    layoutStatusRaw = e2,
                    paneSources = panes,
                    // E8 is documented as a single-byte value (max 0x02, three options), but this
                    // monitor's readback carries a non-zero high byte too (observed: 0x1002) - the
                    // same "extra byte" pattern as E2/E3's status reads, just not called out in
                    // the protocol notes for E8. Masking to the low byte is what actually matches
                    // the three documented options.
                    audioSource = (readVcp("E8") ?: 0) and 0xFF,
                    mainInputSource = 0
                )
            }

            // E2 is neither 0/0x3900 nor 0x39xx - garbage readback, keep waiting.
            delay(config.pollDelayMs)
        }

        return null
    }
}
