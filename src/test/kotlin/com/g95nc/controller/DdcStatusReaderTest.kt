package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DdcStatusReaderTest {

    private val fastConfig = DdcStatusReader.PollingConfig(maxAttempts = 10, pollDelayMs = 10, zeroConfirmations = 2)

    @Test
    fun testSingleSourceMode_resolvesOnceZeroIsConfirmedTwice() = runTest {
        var e2Calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> { e2Calls++; 0 }
                "60" -> 0x11 // HDMI1
                "E8" -> 0x01
                else -> null
            }
        }

        assertEquals(2, e2Calls, "should wait for zeroConfirmations before trusting Single Source mode")
        assertEquals(Layout.OFF, status?.layout)
        assertEquals(SourceCode.HDMI1, status?.paneSources?.get(PaneChannel.PANE_1))
        assertEquals(0x11, status?.mainInputSource)
        assertEquals(0x01, status?.audioSource)
    }

    @Test
    fun testSingleSourceMode_transientZeroDoesNotResetOnNonZeroBusyValue() = runTest {
        // First read is a genuine transient busy 0, second read is 0x3900 (also "off"), which
        // must still count towards confirming Single Source rather than being treated as new info.
        var attempt = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> { attempt++; if (attempt == 1) 0 else 0x3900 }
                "60" -> 0x0F // DP
                "E8" -> 0
                else -> null
            }
        }

        assertEquals(Layout.OFF, status?.layout)
        assertEquals(SourceCode.DP, status?.paneSources?.get(PaneChannel.PANE_1))
    }

    @Test
    fun testPbp2Way_decodesLeftAndRightPanes() = runTest {
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> 0x0001 // left=HDMI1(0x00), right=HDMI2(0x01)
                "E8" -> 0x02
                else -> null
            }
        }

        assertEquals(Layout.PBP_2_50_50, status?.layout)
        assertEquals(SourceCode.HDMI1, status?.paneSources?.get(PaneChannel.PANE_1))
        assertEquals(SourceCode.HDMI2, status?.paneSources?.get(PaneChannel.PANE_2))
        assertEquals(0, status?.mainInputSource, "mainInputSource is not meaningful in PBP mode")
    }

    @Test
    fun testPbp3Way_mapsRawE2CodesToLayout() = runTest {
        val statusLeft = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3909
                "E3" -> 0x0002 // left=HDMI1, right=HDMI3
                "E8" -> 0
                else -> null
            }
        }
        assertEquals(Layout.PBP_3_50_25_25, statusLeft?.layout)

        val statusMiddle = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x390b
                "E3" -> 0x0010 // left=HDMI1, right=DP
                "E8" -> 0
                else -> null
            }
        }
        assertEquals(Layout.PBP_3_25_50_25, statusMiddle?.layout)

        val statusPip = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x390f
                "E3" -> 0x0100 // left=HDMI2, right=HDMI1
                "E8" -> 0
                else -> null
            }
        }
        assertEquals(Layout.PIP, statusPip?.layout)
    }

    @Test
    fun testGarbageE3_retriesUntilPaneCodesAreValid() = runTest {
        var e3Calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> {
                    e3Calls++
                    if (e3Calls == 1) 0x3901 // garbage echo of E2, not a valid pane pair
                    else 0x0001
                }
                "E8" -> 0
                else -> null
            }
        }

        assertEquals(2, e3Calls)
        assertEquals(Layout.PBP_2_50_50, status?.layout)
    }

    @Test
    fun testGarbageE2_retriesUntilRecognized() = runTest {
        // attempt 1: unrecognized garbage, attempt 2 & 3: two confirmations of Single Source mode.
        val e2Sequence = listOf(0x1234, 0, 0x3900)
        var e2Calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> e2Sequence[e2Calls++]
                "60" -> 0x12 // HDMI2
                "E8" -> 0
                else -> null
            }
        }

        assertEquals(3, e2Calls)
        assertEquals(Layout.OFF, status?.layout)
        assertEquals(SourceCode.HDMI2, status?.paneSources?.get(PaneChannel.PANE_1))
    }

    @Test
    fun testNullE2_givesUpAfterMaxAttempts() = runTest {
        var calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            if (vcp == "E2") { calls++; null } else null
        }

        assertNull(status)
        assertEquals(fastConfig.maxAttempts, calls)
    }

    @Test
    fun testAudioSourceReadback_masksOffTheNonZeroHighByte() = runTest {
        // Observed on real hardware: E8's readback carries a non-zero high byte (0x1002) even
        // though the documented value range is a single byte (max 0x02) - only the low byte is
        // the actual "which pane" value.
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> 0x0001
                "E8" -> 0x1002
                else -> null
            }
        }

        assertEquals(0x02, status?.audioSource)
    }

    @Test
    fun testAudioSourceReadback_doesNotTrustASingleUnconfirmedRead() = runTest {
        // Reproduces the real bug: switching the audio pane writes fine, but a single unguarded
        // read right after can still echo the pane that was active *before* the switch - E8 needs
        // the same "don't trust an unconfirmed read" treatment E2/E3 already get. The bus settles
        // on the new value starting with the 2nd read here, so two agreeing reads (2nd and 3rd)
        // is what actually confirms it.
        var e8Calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> 0x0001
                "E8" -> { e8Calls++; if (e8Calls == 1) 0x01 else 0x00 }
                else -> null
            }
        }

        assertEquals(3, e8Calls)
        assertEquals(0x00, status?.audioSource)
    }

    @Test
    fun testAudioSourceReadback_keepsPollingUntilTwoReadsAgree() = runTest {
        // The real bug this reproduces: right -> left kept showing right even after the earlier
        // "read twice, trust the second" fix, because the bus was still mid-transition (through an
        // intermediate stale value) on that second read specifically. Needs a third read that
        // actually agrees with the second before it's trustworthy.
        var e8Calls = 0
        val sequence = listOf(0x01, 0x00, 0x00) // right, left, left (confirmed)
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> 0x0001
                "E8" -> { val v = sequence[e8Calls]; e8Calls++; v }
                else -> null
            }
        }

        assertEquals(3, e8Calls)
        assertEquals(0x00, status?.audioSource)
    }

    @Test
    fun testAudioSourceReadback_neverSettling_fallsBackToTheLastReadingInstead() = runTest {
        var e8Calls = 0
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3901
                "E3" -> 0x0001
                "E8" -> { e8Calls++; e8Calls } // never repeats - never "settles"
                else -> null
            }
        }

        assertEquals(3, e8Calls) // 1 initial read + 2 retries (repeat(2)), then gives up
        assertEquals(3, status?.audioSource)
    }

    @Test
    fun testAudioSourceReadback_masksOffTheNonZeroHighByteInSingleSourceModeToo() = runTest {
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0
                "60" -> 0x11
                "E8" -> 0x1001
                else -> null
            }
        }

        assertEquals(0x01, status?.audioSource)
    }

    @Test
    fun testUnknownSourceCodeInSinglePane_yieldsEmptyPaneMap() = runTest {
        val status = DdcStatusReader.readStatus(fastConfig) { vcp ->
            when (vcp) {
                "E2" -> 0x3900
                "60" -> 0x99 // not a known SourceCode
                "E8" -> 0
                else -> null
            }
        }

        assertEquals(Layout.OFF, status?.layout)
        assertEquals(emptyMap(), status?.paneSources)
    }
}
