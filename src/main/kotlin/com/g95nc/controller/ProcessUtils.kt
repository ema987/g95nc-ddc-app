package com.g95nc.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

interface CommandRunner {
    suspend fun run(command: List<String>, timeoutMillis: Long = 5000): String
}

/** Common install locations for CLI tools that GUI-launched macOS/Linux apps don't otherwise
 * see - a double-clicked (or `open`ed) .app is started by launchd, not a login shell, so it never
 * picks up whatever a .zshrc/.bash_profile normally adds to PATH (Homebrew's `brew shellenv`,
 * Linuxbrew, etc.). Only matters for the bare command names ("ddcutil"/"betterdisplaycli") the
 * default config ships with; an absolute path the user pastes into Settings is unaffected. */
private val EXTRA_PATH_DIRS = listOf(
    "/opt/homebrew/bin", "/opt/homebrew/sbin", // Homebrew, Apple Silicon
    "/usr/local/bin", "/usr/local/sbin", // Homebrew, Intel; also common on Linux
    "/home/linuxbrew/.linuxbrew/bin", "/home/linuxbrew/.linuxbrew/sbin" // Linuxbrew
)

/**
 * Resolves a bare command name (no path separator) to an absolute path by searching [currentPath]
 * then [extraDirs], in that order. Returns [command] unchanged if it already contains a path
 * separator (absolute or relative - the caller's choice is respected either way) or if no match
 * is found anywhere (falls through to whatever ProcessBuilder's own resolution does, unchanged
 * from before this existed).
 *
 * This exists because `ProcessBuilder(...).environment()["PATH"] = ...` does NOT do this job:
 * that map only becomes the *child* process's environment after exec - the JVM resolves a bare
 * command name against its own real PATH beforehand, ignoring whatever the caller put in that
 * map. Confirmed empirically, not just from documentation, since this is a common enough
 * misconception that the docs don't loudly warn about it.
 */
fun resolveExecutable(command: String, currentPath: String?, extraDirs: List<String> = EXTRA_PATH_DIRS): String {
    if (command.contains(File.separatorChar)) return command

    val searchDirs = (currentPath?.split(File.pathSeparator)?.filter { it.isNotBlank() } ?: emptyList()) + extraDirs
    for (dir in searchDirs) {
        val candidate = File(dir, command)
        if (candidate.isFile && candidate.canExecute()) {
            return candidate.absolutePath
        }
    }
    return command
}

object ProcessUtils : CommandRunner {
    override suspend fun run(command: List<String>, timeoutMillis: Long): String = withContext(Dispatchers.IO) {
        // No shell involved: command is passed straight to exec() as an argv array,
        // so user-configured tool paths/arguments can never be interpreted as shell syntax.
        val resolvedCommand = if (command.isEmpty()) {
            command
        } else {
            listOf(resolveExecutable(command[0], System.getenv("PATH"))) + command.drop(1)
        }

        val process = ProcessBuilder(resolvedCommand).redirectErrorStream(true).start()

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
