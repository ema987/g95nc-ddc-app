package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BetterDisplayCliControllerTest {

    private val identifiersOutput = """
        {
          "UUID" : "AAA",
          "name" : "Some Other Monitor",
          "tagID" : "1"
        },{
          "UUID" : "BBB",
          "name" : "Odyssey G95NC",
          "tagID" : "6"
        },{
          "UUID" : "CCC",
          "name" : "Odyssey G95NC",
          "tagID" : "7"
        }
    """.trimIndent()

    @Test
    fun testTagDiscovery_skipsNonRespondingTagAndSelectsFirstWorkingG95NCTag() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("--identifiers") -> identifiersOutput
                cmd.contains("--tagID=6") -> "Failed. Request timed out."
                cmd.contains("--tagID=7") -> "0x3900"
                else -> ""
            }
        }
        val controller = BetterDisplayCliController("betterdisplaycli", runner)

        val result = controller.setMainSource(SourceCode.HDMI1)

        assertTrue(result)
        val setCmd = runner.invocations.first { it.contains("set") }
        assertTrue(setCmd.contains("--tagID=7"), "should have selected the tag that actually answers ddcAlt queries")
    }

    @Test
    fun testTagDiscovery_isCachedAcrossCalls() = runTest {
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("--identifiers") -> identifiersOutput
                cmd.contains("--tagID=6") -> "0x3900"
                else -> ""
            }
        }
        val controller = BetterDisplayCliController("betterdisplaycli", runner)

        controller.setMainSource(SourceCode.HDMI1)
        controller.setMainSource(SourceCode.HDMI2)

        assertEquals(1, runner.countInvocationsContaining("--identifiers"))
    }

    @Test
    fun testGetVcpValue_retriesOnceAfterTimeoutError() = runTest {
        // e2Calls tracks every "tagID=6, vcp=0xE2" query: #1 is the tag-discovery probe, #2 is
        // the first real status read (simulated timeout), #3+ are the retried/confirmed reads.
        var e2Calls = 0
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("--identifiers") -> identifiersOutput
                cmd.contains("--tagID=6") && cmd.any { it == "--vcp=0xE2" } -> {
                    e2Calls++
                    if (e2Calls == 2) "Failed. Request timed out. BetterDisplay might not be running." else "0x3900"
                }
                cmd.contains("--tagID=6") && cmd.any { it == "--vcp=0x60" } -> "0x0f" // DP
                cmd.contains("--tagID=6") && cmd.any { it == "--vcp=0xE8" } -> "0x00"
                else -> ""
            }
        }
        val controller = BetterDisplayCliController("betterdisplaycli", runner)

        val status = controller.getStatus()

        assertTrue(e2Calls >= 3, "expected discovery + a failed read + a retried read")
        assertEquals(Layout.OFF, status?.layout)
        assertEquals(SourceCode.DP, status?.paneSources?.get(PaneChannel.PANE_1))
    }

    @Test
    fun testUpdatePath_clearsCachedTag() = runTest {
        var identifierCalls = 0
        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("--identifiers") -> { identifierCalls++; identifiersOutput }
                cmd.contains("--tagID=6") -> "0x3900"
                else -> ""
            }
        }
        val controller = BetterDisplayCliController("betterdisplaycli", runner)

        controller.setMainSource(SourceCode.HDMI1)
        controller.updatePath("/opt/homebrew/bin/betterdisplaycli")
        controller.setMainSource(SourceCode.HDMI1)

        assertEquals(2, identifierCalls, "changing the executable path should force re-discovery of the tag")
    }

    @Test
    fun testNoDebugLogFileIsWrittenDuringOperation() = runTest {
        val debugLog = File("cli_debug.log")
        val existedBefore = debugLog.exists()
        val sizeBefore = if (existedBefore) debugLog.length() else 0L

        val runner = FakeCommandRunner { cmd ->
            when {
                cmd.contains("--identifiers") -> identifiersOutput
                cmd.contains("--tagID=6") -> "0x3900"
                else -> ""
            }
        }
        val controller = BetterDisplayCliController("betterdisplaycli", runner)
        controller.setMainSource(SourceCode.HDMI1)
        controller.getStatus()

        if (existedBefore) {
            assertEquals(sizeBefore, debugLog.length(), "cli_debug.log must not grow during normal operation")
        } else {
            assertFalse(debugLog.exists(), "cli_debug.log should not be created by normal operation")
        }
    }
}
