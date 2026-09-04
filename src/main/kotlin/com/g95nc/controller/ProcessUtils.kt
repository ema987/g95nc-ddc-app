package com.g95nc.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

interface CommandRunner {
    suspend fun run(command: List<String>, timeoutMillis: Long = 5000): String
}

object ProcessUtils : CommandRunner {
    override suspend fun run(command: List<String>, timeoutMillis: Long): String = withContext(Dispatchers.IO) {
        // No shell involved: command is passed straight to exec() as an argv array,
        // so user-configured tool paths/arguments can never be interpreted as shell syntax.
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = StringBuilder()
        // Drain stdout on its own thread: Process.waitFor(timeout) below is a genuine bounded
        // wait, but if we don't also read the pipe concurrently, a chatty command could fill the
        // OS pipe buffer and deadlock instead of ever hitting that timeout.
        val readerThread = Thread({
            process.inputStream.bufferedReader().forEachLine { line ->
                output.append(line).append('\n')
            }
        }, "process-output-reader").apply { isDaemon = true; start() }

        try {
            val finishedInTime = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finishedInTime) {
                process.destroyForcibly()
                readerThread.join(1000)
                throw TimeoutException("Command timed out after ${timeoutMillis}ms: ${command.joinToString(" ")}")
            }
            readerThread.join(1000)
            output.toString().trim()
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}

/** Whether running `<path> <versionArgs>` succeeds and prints something - the same check
 * DdcutilController/BetterDisplayCliController use for isToolAvailable(), exposed standalone so
 * the Settings screen can validate a path before the user ever tries to use it for real. */
suspend fun isToolReachable(commandRunner: CommandRunner, path: String, versionArgs: List<String>): Boolean =
    try {
        commandRunner.run(listOf(path) + versionArgs, timeoutMillis = 3000).isNotEmpty()
    } catch (e: Exception) {
        false
    }
