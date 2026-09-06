package com.g95nc.controller

class DdcutilController(
    private var exePath: String,
    private val commandRunner: CommandRunner = ProcessUtils
) : MonitorController {

    private var cachedBus: String? = null

    fun updatePath(newPath: String) {
        exePath = newPath
        cachedBus = null
    }

    override suspend fun isToolAvailable(): Boolean {
        return try {
            val output = commandRunner.run(listOf(exePath, "--version"))
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getMonitorI2cBus(forceRefresh: Boolean = false): String? {
        if (!forceRefresh && cachedBus != null) return cachedBus

        return try {
            val output = commandRunner.run(listOf(exePath, "detect"))
            val lines = output.split("\n")
            var currentBus: String? = null
            for (line in lines) {
                if (line.contains("I2C bus:", ignoreCase = true)) {
                    currentBus = Regex("""/dev/i2c-(\d+)""").find(line)?.groupValues?.get(1)
                }
                if (line.contains("Model:", ignoreCase = true) &&
                   (line.contains("G95NC", ignoreCase = true) || line.contains("Odyssey", ignoreCase = true) || line.contains("G9", ignoreCase = true))) {
                    cachedBus = currentBus
                    return cachedBus
                }
            }
            if (currentBus != null) {
                cachedBus = currentBus
                return cachedBus
            }
            null
        } catch (e: Exception) {
            LastControllerError.record(e)
            null
        }
    }

    private suspend fun getVcpValue(vcp: String): Int? {
        val bus = getMonitorI2cBus()
        val cmd = mutableListOf(exePath, "getvcp", vcp, "--terse")
        if (bus != null) {
            cmd.add("--bus")
            cmd.add(bus)
        }
        return try {
            val output = commandRunner.run(cmd)
            var result = parseVcpOutput(output)
            if (result == null && bus != null) {
                // Try refreshing bus once in case bus ID changed
                val refreshedBus = getMonitorI2cBus(forceRefresh = true)
                if (refreshedBus != null && refreshedBus != bus) {
                    val retryCmd = mutableListOf(exePath, "getvcp", vcp, "--terse", "--bus", refreshedBus)
                    val retryOutput = commandRunner.run(retryCmd)
                    result = parseVcpOutput(retryOutput)
                }
            }
            result
        } catch (e: Exception) {
            LastControllerError.record(e)
            null
        }
    }

    private suspend fun setVcpValue(vcp: String, value: String): Boolean {
        val bus = getMonitorI2cBus()
        val cmd = mutableListOf(exePath, "setvcp", vcp, value)
        if (bus != null) {
            cmd.add("--bus")
            cmd.add(bus)
        }
        return try {
            commandRunner.run(cmd)
            true
        } catch (e: Exception) {
            LastControllerError.record(e)
            false
        }
    }

    override suspend fun getStatus(): MonitorStatus? {
        LastControllerError.clear()
        return DdcStatusReader.readStatus(
            DdcStatusReader.PollingConfig(maxAttempts = 10, pollDelayMs = 500, zeroConfirmations = 2)
        ) { vcp -> if (vcp == "E2") getVcpValue("E2") ?: getVcpValue("0xE2") else getVcpValue(vcp) }
    }

    override suspend fun setLayout(layout: Layout): Boolean {
        val hexValue = "0x" + layout.code.toString(16).padStart(2, '0')
        return setVcpValue("E2", hexValue)
    }

    override suspend fun setMainSource(sourceCode: SourceCode): Boolean {
        val hexValue = "0x" + sourceCode.mccsCode.toString(16).padStart(2, '0')
        return setVcpValue("60", hexValue)
    }

    override suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean {
        val e3Val = (channel.channel shl 8) or sourceCode.e3Code
        val hexValue = "0x" + e3Val.toString(16).padStart(4, '0')
        return setVcpValue("E3", hexValue)
    }

    override suspend fun setAudioSource(audioCode: Int): Boolean {
        val hexValue = "0x" + audioCode.toString(16).padStart(2, '0')
        return setVcpValue("E8", hexValue)
    }

    companion object {
        fun parseVcpOutput(output: String): Int? {
            if (output.isBlank()) return null
            val tokens = output.trim().split(Regex("\\s+"))

            // Check for CNC (Complex Non-Continuous): VCP <vcp> CNC <mh> <ml> <sh> <sl>
            val cncIndex = tokens.indexOfFirst { it.equals("CNC", ignoreCase = true) }
            if (cncIndex != -1 && tokens.size > cncIndex + 4) {
                val sh = parseHexByte(tokens[cncIndex + 3]) ?: 0
                val sl = parseHexByte(tokens[cncIndex + 4]) ?: 0
                return (sh shl 8) or sl
            }

            // Check for SNC (Simple Non-Continuous): VCP <vcp> SNC <sl> or VCP <vcp> SNC <sh> <sl>
            val sncIndex = tokens.indexOfFirst { it.equals("SNC", ignoreCase = true) }
            if (sncIndex != -1 && tokens.size > sncIndex + 1) {
                val sl = parseHexByte(tokens.last()) ?: return null
                val sh = if (tokens.size > sncIndex + 2) (parseHexByte(tokens[sncIndex + 1]) ?: 0) else 0
                return (sh shl 8) or sl
            }

            // Fallback for non-terse output with sh=0x.. sl=0x..
            val shMatch = Regex("""sh=0x?([0-9a-fA-F]+)""").find(output)
            val slMatch = Regex("""sl=0x?([0-9a-fA-F]+)""").find(output)
            if (slMatch != null) {
                val sl = slMatch.groupValues[1].toIntOrNull(16) ?: 0
                val sh = shMatch?.groupValues?.get(1)?.toIntOrNull(16) ?: 0
                return (sh shl 8) or sl
            }

            // Generic hex match as last resort (e.g. "Value: 0x3901")
            val hexMatch = Regex("""0x([0-9a-fA-F]+)""").find(output)
            if (hexMatch != null) {
                return hexMatch.groupValues[1].toIntOrNull(16)
            }

            // Plain integer match
            val intMatch = Regex("""\b(\d+)\b""").findAll(output).lastOrNull()
            return intMatch?.groupValues?.get(1)?.toIntOrNull()
        }

        fun parseHexByte(str: String): Int? {
            val clean = str.removePrefix("0x").removePrefix("x")
            return clean.toIntOrNull(16)
        }
    }
}
