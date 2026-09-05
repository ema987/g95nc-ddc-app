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

    @Test
    fun testIsToolReachable_trueWhenCommandSucceedsWithOutput() = runBlocking {
        val runner = FakeCommandRunner { "ddcutil 2.1.0" }
        assertTrue(isToolReachable(runner, "ddcutil", listOf("--version")))
    }

    @Test
    fun testIsToolReachable_falseWhenCommandThrows() = runBlocking {
        val runner = FakeCommandRunner { throw RuntimeException("No such file or directory") }
        assertEquals(false, isToolReachable(runner, "/bad/path", listOf("--version")))
    }

    @Test
    fun testIsToolReachable_falseWhenCommandSucceedsButPrintsNothing() = runBlocking {
        val runner = FakeCommandRunner { "" }
        assertEquals(false, isToolReachable(runner, "ddcutil", listOf("--version")))
    }

    @Test
    fun testResolveExecutable_returnsAbsolutePathAlreadyContainingPathSeparatorUnchanged() {
        val result = resolveExecutable("/usr/bin/true", currentPath = "/nonexistent", extraDirs = emptyList())
        assertEquals("/usr/bin/true", result)
    }

    @Test
    fun testResolveExecutable_returnsRelativePathContainingSeparatorUnchanged() {
        val result = resolveExecutable("./local-tool", currentPath = "/nonexistent", extraDirs = emptyList())
        assertEquals("./local-tool", result)
    }

    @Test
    fun testResolveExecutable_findsABareNameOnTheCurrentPath() {
        if (isWindows()) return

        // /bin/sh always exists on macOS/Linux and /bin is a completely ordinary PATH entry.
        val result = resolveExecutable("sh", currentPath = "/bin", extraDirs = emptyList())
        assertEquals("/bin/sh", result)
    }

    @Test
    fun testResolveExecutable_fallsBackToExtraDirsWhenNotOnCurrentPath() {
        if (isWindows()) return

        val result = resolveExecutable("sh", currentPath = "/nonexistent-dir", extraDirs = listOf("/definitely/not/here", "/bin"))
        assertEquals("/bin/sh", result)
    }

    @Test
    fun testResolveExecutable_prefersCurrentPathOverExtraDirs() {
        if (isWindows()) return

        val tempDir = kotlin.io.path.createTempDirectory("g95nc-resolve-test").toFile()
        try {
            val onCurrentPath = java.io.File(tempDir, "probe")
            onCurrentPath.writeText("#!/bin/sh\n")
            onCurrentPath.setExecutable(true)

            val result = resolveExecutable("probe", currentPath = tempDir.absolutePath, extraDirs = listOf("/usr/bin"))

            assertEquals(onCurrentPath.absolutePath, result)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testResolveExecutable_returnsBareNameUnchangedWhenNotFoundAnywhere() {
        val result = resolveExecutable("totally_made_up_tool_xyz", currentPath = "/nonexistent", extraDirs = listOf("/also/nonexistent"))
        assertEquals("totally_made_up_tool_xyz", result)
    }

    @Test
    fun testResolveExecutable_ignoresANonExecutableFileWithTheSameName() {
        val tempDir = kotlin.io.path.createTempDirectory("g95nc-resolve-test").toFile()
        try {
            val notExecutable = java.io.File(tempDir, "probe")
            notExecutable.writeText("just data, not a script")
            notExecutable.setExecutable(false)

            val result = resolveExecutable("probe", currentPath = tempDir.absolutePath, extraDirs = emptyList())

            assertEquals("probe", result, "a non-executable match should not be returned")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRun_stillResolvesAnOrdinaryBareCommandNameCorrectly() = runBlocking {
        if (isWindows()) return@runBlocking

        // Sanity check that wiring resolveExecutable() into run() didn't break the common case:
        // "sh" is virtually always reachable via the real PATH with no absolute path needed.
        val output = ProcessUtils.run(listOf("sh", "-c", "echo bare-name-still-works"))

        assertEquals("bare-name-still-works", output)
    }
}
