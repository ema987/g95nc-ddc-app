package com.g95nc.controller

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ProcessUtilsTest {

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun testRun_doesNotInterpretShellMetacharactersInArguments() = runBlocking {
        if (isWindows()) return@runBlocking // no /bin/echo on Windows

        // If this were still routed through `/bin/sh -c`, the command substitution and the
        // trailing "; echo INJECTED" would both execute, producing more than one literal line.
        val payload = "\$(whoami); echo INJECTED"
        val output = ProcessUtils.run(listOf("/bin/echo", payload))

        assertEquals(payload, output, "argv must reach the process literally, with no shell involved")
    }

    @Test
    fun testRun_passesEachArgumentIndependently() = runBlocking {
        if (isWindows()) return@runBlocking

        // A single argument containing spaces must not be split into multiple argv entries.
        val output = ProcessUtils.run(listOf("/bin/echo", "one two", "three"))

        assertEquals("one two three", output)
    }

    @Test
    fun testRun_timesOutInsteadOfBlockingForever() = runBlocking {
        if (isWindows()) return@runBlocking

        val start = System.currentTimeMillis()
        var timedOut = false
        try {
            ProcessUtils.run(listOf("/bin/sleep", "5"), timeoutMillis = 300)
            fail("expected a timeout exception")
        } catch (e: Exception) {
            timedOut = true
        }
        val elapsed = System.currentTimeMillis() - start

        assertTrue(timedOut)
        assertTrue(elapsed < 2000, "timeout should cut the command off well before it finishes on its own, took ${elapsed}ms")
    }

    @Test
    fun testRun_trimsTrailingNewline() = runBlocking {
        if (isWindows()) return@runBlocking

        val output = ProcessUtils.run(listOf("/bin/echo", "hello"))
        assertEquals("hello", output)
    }
}
