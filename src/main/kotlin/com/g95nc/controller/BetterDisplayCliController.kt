package com.g95nc.controller

class BetterDisplayCliController(private var exePath: String) : MonitorController {
    
    private var cachedTagId: String? = null
    
    fun updatePath(newPath: String) {
        exePath = newPath
    }

    override suspend fun isToolAvailable(): Boolean {
        return try {
            val output = ProcessUtils.runCommand(listOf(exePath, "-v"))
            // Check if command runs without throwing exception and returns something
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getMonitorTagId(forceRefresh: Boolean = false): String? {
        if (!forceRefresh && cachedTagId != null) return cachedTagId
        
        return try {
            val output = ProcessUtils.runCommand(listOf(exePath, "get", "--identifiers"))
            java.io.File("cli_debug.log").appendText("Identifiers output length: ${output.length}, output: [$output]\n")
            cachedTagId = parseIdentifiersOutput(output)
            java.io.File("cli_debug.log").appendText("Parsed tagID: $cachedTagId\n")
            cachedTagId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun getVcpValue(vcp: String): Int? {
        var tagId = getMonitorTagId() ?: return null
        return try {
            var output = ProcessUtils.runCommand(listOf(exePath, "get", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp"))
            java.io.File("cli_debug.log").appendText("Cmd for $vcp output: $output\n")
            
            val vcpInt = parseVcpOutput(output)
            if (vcpInt == null && (!output.any { it.isDigit() } || output.contains("Error", ignoreCase = true) || output.contains("timed out", ignoreCase = true))) {
                kotlinx.coroutines.delay(500)
                // Retrying without refreshing identifiers to avoid hanging the IPC queue
                val retryOutput = ProcessUtils.runCommand(listOf(exePath, "get", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp"))
                java.io.File("cli_debug.log").appendText("Retry cmd for $vcp output: $retryOutput\n")
                return parseVcpOutput(retryOutput)
            }
            return vcpInt
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    companion object {
        fun parseIdentifiersOutput(output: String): String? {
            val blocks = output.split(Regex("\\},\\{"))
            val g95ncBlock = blocks.find { it.contains("G95NC", ignoreCase = true) } ?: return null
            val match = Regex(""""tagID"\s*:\s*"(-?\d+)"""").find(g95ncBlock)
            return match?.groupValues?.get(1)
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
            val output = ProcessUtils.runCommand(listOf(exePath, "set", "--tagID=$tagId", "--feature=ddcAlt", "--vcp=$vcp", "--value=$value"))
            kotlinx.coroutines.delay(1500) // Hardware delay after write to clear DDC buffer
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getStatus(): MonitorStatus? {
        // The Odyssey G9 DDC bus can be busy or echo previous commands for up to 10 seconds 
        // after an HDMI source change. We must robustly validate the outputs before accepting them.
        var consecutiveZeros = 0
        
        for (attempt in 1..15) {
            val e2 = getVcpValue("0xE2")
            java.io.File("cli_debug.log").appendText("E2 value: $e2\n")
            
            if (e2 == null) {
                consecutiveZeros = 0
                kotlinx.coroutines.delay(1000)
                continue
            }
            
            if (e2 == 0 || e2 == 0x3900) {
                // Potential "busy" or actual Single Source mode. 
                // We MUST NOT read 0x60 yet, because if this is a busy PBP state, reading 0x60 
                // will corrupt the DDC bus and leave stale bytes for the next read!
                consecutiveZeros++
                
                if (consecutiveZeros < 3) {
                    // Wait it out to see if it's just a temporary busy 0
                    kotlinx.coroutines.delay(1000)
                    continue
                }
                
                // If we get 0 for 3 consecutive seconds, it is genuinely Single Source mode!
                // ONLY NOW is it mathematically safe to read 0x60.
                kotlinx.coroutines.delay(1000)
                val v60 = getVcpValue("0x60") ?: 0
                java.io.File("cli_debug.log").appendText("Cmd for 0x60 output: $v60\n")
                
                return MonitorStatus(
                    layout = Layout.OFF,
                    layoutStatusRaw = 0,
                    paneSources = SourceCode.entries.find { it.mccsCode == v60 }?.let { mapOf(PaneChannel.PANE_1 to it) } ?: emptyMap(),
                    audioSource = 0,
                    mainInputSource = v60
                )
            }
            
            // If we are in PBP mode, e2 must be 0x39xx (excluding 0x3900 which is Single Source)
            consecutiveZeros = 0
            if ((e2 and 0xFF00) == 0x3900 && e2 != 0x3900) {
                kotlinx.coroutines.delay(1000)
                
                // Read E3
                val e3 = getVcpValue("0xE3")
                java.io.File("cli_debug.log").appendText("E3 value: $e3\n")
                
                if (e3 == null || e3 == 0 || ((e3 and 0xFF00) != 0x1000 && (e3 and 0xFF00) != 0xc000)) {
                    // Monitor is echoing or returning garbage (e.g. 14593 for E3). It's still busy!
                    kotlinx.coroutines.delay(1000)
                    continue // Retry entire E2 fetch!
                }
                
                // Everything looks solid!
                kotlinx.coroutines.delay(1000)
                val e8 = getVcpValue("0xE8") ?: 0
                
                val layout = when (e2) {
                    0x3901, 0x3903 -> Layout.PBP_2_50_50
                    0x3909 -> Layout.PBP_3_50_25_25
                    0x390b -> Layout.PBP_3_25_50_25
                    0x390d -> Layout.PBP_3_25_25_50
                    0x390f -> Layout.PIP
                    else -> Layout.OFF // Should not happen
                }
                
                val panes = mutableMapOf<PaneChannel, SourceCode>()
                val leftCode = (e3 shr 8) and 0xFF
                val rightCode = e3 and 0xFF
                SourceCode.fromE3Code(leftCode)?.let { panes[PaneChannel.PANE_1] = it }
                SourceCode.fromE3Code(rightCode)?.let { panes[PaneChannel.PANE_2] = it }
                
                return MonitorStatus(
                    layout = layout,
                    layoutStatusRaw = e2,
                    paneSources = panes,
                    audioSource = e8,
                    mainInputSource = 0
                )
            }
            
            // If E2 is neither 0 nor 0x39xx, it's garbage (e.g. echoing 0x60). 
            kotlinx.coroutines.delay(1000)
        }
        
        return null // Gave up after 15 seconds
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
