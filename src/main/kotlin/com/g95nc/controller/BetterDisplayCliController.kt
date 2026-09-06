package com.g95nc.controller

import kotlinx.coroutines.delay

class BetterDisplayCliController(
    private var exePath: String,
    private val commandRunner: CommandRunner = ProcessUtils
) : MonitorController {

    private var cachedTagId: String? = null

    fun updatePath(newPath: String) {
        exePath = newPath
        cachedTagId = null
    }

    override suspend fun isToolAvailable(): Boolean {
        return try {
            val output = commandRunner.run(listOf(exePath, "-v"))
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getMonitorTagId(forceRefresh: Boolean = false): String? {
        if (!forceRefresh && cachedTagId != null) return cachedTagId

        return try {
            val output = commandRunner.run(listOf(exePath, "get", "--identifiers"))
            val tags = parseAllIdentifiersOutput(output)

            for (tag in tags) {
                val testOutput = commandRunner.run(listOf(exePath, "get", "--tagID=$tag", "--feature=ddcAlt", "--vcp=0xE2"))
                if (parseVcpOutput(testOutput) != null) {
                    cachedTagId = tag
                    return tag
                }
            }
            null
        } catch (e: Exception) {
            LastControllerError.record(e)
            null
        }
    }

    private suspend fun getVcpValue(vcp: String): Int? {
        val tagId = getMonitorTagId() ?: return null
        return try {
            val output = commandRunner.run(listOf(exePath, "get", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp"))

            val vcpInt = parseVcpOutput(output)
            if (vcpInt == null && (!output.any { it.isDigit() } || output.contains("Error", ignoreCase = true) || output.contains("timed out", ignoreCase = true))) {
                delay(500)
                // Retrying without refreshing identifiers to avoid hanging the IPC queue
                val retryOutput = commandRunner.run(listOf(exePath, "get", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp"))
                return parseVcpOutput(retryOutput)
            }
            return vcpInt
        } catch (e: Exception) {
            LastControllerError.record(e)
            null
        }
    }

    companion object {
        fun parseAllIdentifiersOutput(output: String): List<String> {
            val blocks = output.split(Regex("\\},\\{"))
            val g95ncBlocks = blocks.filter { it.contains("G95NC", ignoreCase = true) }
            return g95ncBlocks.mapNotNull { block ->
                Regex(""""tagID"\s*:\s*"(-?\d+)"""").find(block)?.groupValues?.get(1)
            }
        }

        fun parseVcpOutput(output: String): Int? {
            val lines = output.trim().lines().filter { it.isNotBlank() }
            val lastLine = lines.lastOrNull() ?: return null
            val hexMatch = Regex("""0x([0-9a-fA-F]+)""").find(lastLine)
            if (hexMatch != null) {
                return hexMatch.groupValues[1].toInt(16)
            }
            val intMatch = Regex("""\b(\d+)\b""").findAll(lastLine).lastOrNull()
            return intMatch?.groupValues?.get(1)?.toInt()
        }
    }

    private suspend fun setVcpValue(vcp: String, value: String): Boolean {
        val tagId = getMonitorTagId() ?: return false
        return try {
            commandRunner.run(listOf(exePath, "set", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp", "--value=$value"))
            delay(1500) // Hardware delay after write to clear DDC buffer
            true
        } catch (e: Exception) {
            LastControllerError.record(e)
            false
        }
    }

    override suspend fun getStatus(): MonitorStatus? {
        LastControllerError.clear()
        return DdcStatusReader.readStatus(
            DdcStatusReader.PollingConfig(maxAttempts = 15, pollDelayMs = 1000, zeroConfirmations = 3)
        ) { vcp -> getVcpValue("0x$vcp") }
    }

    override suspend fun setLayout(layout: Layout): Boolean {
        val hexValue = "0x" + layout.code.toString(16).padStart(2, '0')
        return setVcpValue("0xE2", hexValue)
    }

    override suspend fun setMainSource(sourceCode: SourceCode): Boolean {
        val hexValue = "0x" + sourceCode.mccsCode.toString(16).padStart(2, '0')
        return setVcpValue("0x60", hexValue)
    }

    override suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean {
        val e3Val = (channel.channel shl 8) or sourceCode.e3Code
        val hexValue = "0x" + e3Val.toString(16).padStart(4, '0')
        return setVcpValue("0xE3", hexValue)
    }

    override suspend fun setAudioSource(audioCode: Int): Boolean {
        val hexValue = "0x" + audioCode.toString(16).padStart(2, '0')
        return setVcpValue("0xE8", hexValue)
    }
}
