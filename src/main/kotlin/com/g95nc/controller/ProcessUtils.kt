package com.g95nc.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessUtils {
    suspend fun runCommand(command: List<String>): String = withContext(Dispatchers.IO) {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val pbCommand = if (isWindows) {
            listOf("cmd.exe", "/c") + command.joinToString(" ")
        } else {
            listOf("/bin/sh", "-c", command.joinToString(" ") { if (it.contains(" ")) "\"$it\"" else it })
        }
        
        val process = ProcessBuilder(pbCommand)
            .redirectErrorStream(true)
            .start()
        
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        process.waitFor()
        output.toString().trim()
    }
}
