package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DdcutilControllerTest {

    private val detectOutput = """
        Display 1
           I2C bus:  /dev/i2c-7
           Monitor:  SAM:G95NC:ABC123
           Model:    Odyssey G95NC
    """.trimIndent()

    @Test
    fun testGetVcpValue_usesDiscoveredBusOnGetvcpCommand() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("getvcp") -> "VCP 60 SNC x11"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        val result = controller.setMainSource(SourceCode.HDMI1)

        assertTrue(result)
        val setvcpCmd = runner.invocations.first { it.contains("setvcp") }
        assertTrue(setvcpCmd.contains("--bus"), "expected --bus flag once the I2C bus is discovered")
        assertTrue(setvcpCmd.contains("7"), "expected the bus id parsed from `detect` output")
    }

    @Test
    fun testBusDiscovery_isCachedAcrossCalls() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("getvcp") -> "VCP 60 SNC x11"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        controller.setMainSource(SourceCode.HDMI1)
        controller.setMainSource(SourceCode.HDMI2)

        assertEquals(1, runner.countInvocationsContaining("detect"), "bus should only be (re)discovered once")
    }

    @Test
    fun testGetStatus_refreshesBusOnceWhenCachedBusGoesStale() = runTest {
        var detectCalls = 0
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> {
                    detectCalls++
                    if (detectCalls == 1) detectOutput else detectOutput.replace("i2c-7", "i2c-9")
                }
                cmd.contains("getvcp") && cmd.contains("E2") && cmd.contains("7") -> "" // stale bus: unparsable
                cmd.contains("getvcp") && cmd.contains("E2") && cmd.contains("9") -> "VCP E2 SNC x39 x01"
                cmd.contains("getvcp") && cmd.contains("E3") -> "VCP E3 SNC x00 x01" // HDMI1 | HDMI2
                cmd.contains("getvcp") && cmd.contains("E8") -> "VCP E8 SNC x00"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        val status = controller.getStatus()

        assertEquals(2, detectCalls, "expected one initial discovery plus one refresh after a stale read")
        assertEquals(Layout.PBP_2_50_50, status?.layout)
        assertEquals(SourceCode.HDMI1, status?.paneSources?.get(PaneChannel.PANE_1))
        assertEquals(SourceCode.HDMI2, status?.paneSources?.get(PaneChannel.PANE_2))
    }

    @Test
    fun testUpdatePath_clearsCachedBus() = runTest {
        var detectCalls = 0
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> { detectCalls++; detectOutput }
                cmd.contains("getvcp") -> "VCP 60 SNC x11"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        controller.setMainSource(SourceCode.HDMI1)
        controller.updatePath("/usr/local/bin/ddcutil")
        controller.setMainSource(SourceCode.HDMI1)

        assertEquals(2, detectCalls, "changing the executable path should force re-discovery of the bus")
    }

    @Test
    fun testIsToolAvailable_falseWhenCommandThrows() = runTest {
        val runner = FakeCommandRunner { throw RuntimeException("command not found") }
        val controller = DdcutilController("ddcutil", runner)

        assertEquals(false, controller.isToolAvailable())
    }

    @Test
    fun testIsToolAvailable_recordsTheRealErrorWhenCommandThrows() = runTest {
        LastControllerError.clear()
        val runner = FakeCommandRunner { throw RuntimeException("command not found") }
        val controller = DdcutilController("ddcutil", runner)

        controller.isToolAvailable()

        assertEquals("command not found", LastControllerError.message)
    }

    @Test
    fun testGetRawVcp_returnsTheRawValueForAnyCode() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("getvcp") && cmd.contains("ED") -> "VCP ED SNC x2a"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        assertEquals(0x2a, controller.getRawVcp("ED"))
    }

    @Test
    fun testGetStatus_reportsSingleSourceStatusEndToEnd() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("getvcp") && cmd.contains("E2") -> "VCP E2 SNC x00"
                cmd.contains("getvcp") && cmd.contains("60") -> "VCP 60 SNC x0f" // DP
                cmd.contains("getvcp") && cmd.contains("E8") -> "VCP E8 SNC x01"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        val status = controller.getStatus()

        assertEquals(Layout.OFF, status?.layout)
        assertEquals(SourceCode.DP, status?.paneSources?.get(PaneChannel.PANE_1))
        assertEquals(0x01, status?.audioSource)
    }

    @Test
    fun testSetAudioSource_retriesTheWriteIfTheMonitorDidNotApplyItYet() = runTest {
        // Reproduces a real observed bug: a "successful" setvcp command doesn't mean the monitor
        // actually applied it - this monitor sometimes needs the write itself retried, not just
        // the read. Here the monitor only "accepts" the write on the 2nd attempt.
        var setCalls = 0
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("setvcp") && cmd.contains("E8") -> { setCalls++; "" }
                cmd.contains("getvcp") && cmd.contains("E8") ->
                    if (setCalls >= 2) "VCP E8 SNC x01" else "VCP E8 SNC x00"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        val result = controller.setAudioSource(1)

        assertTrue(result)
        assertEquals(2, setCalls)
    }

    @Test
    fun testSetAudioSource_givesUpAfterThreeFailedAttempts() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> detectOutput
                cmd.contains("setvcp") && cmd.contains("E8") -> ""
                cmd.contains("getvcp") && cmd.contains("E8") -> "VCP E8 SNC x00" // never matches target 1
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        val result = controller.setAudioSource(1)

        assertFalse(result)
    }

    @Test
    fun testGetStatus_returnsNullWhenBusIsUnreachable() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("detect") -> "No displays found"
                else -> ""
            }
        }
        val controller = DdcutilController("ddcutil", runner)

        assertNull(controller.getStatus())
    }
}
