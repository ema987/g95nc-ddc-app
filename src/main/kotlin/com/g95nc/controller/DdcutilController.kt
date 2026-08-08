package com.g95nc.controller

class DdcutilController(private var exePath: String) : MonitorController {
    
    fun updatePath(newPath: String) {
        exePath = newPath
    }

    override suspend fun isToolAvailable(): Boolean {
        return try {
            val output = ProcessUtils.runCommand(listOf(exePath, "--version"))
            output.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getMonitorI2cBus(): String? {
        // ddcutil detect gives a list of displays. We can look for the G95NC or just rely on default if there's only one.
        // For now, let's assume ddcutil without bus selection works if there's only one monitor,
        // or we parse `ddcutil detect` to find the bus number.
        return try {
            val output = ProcessUtils.runCommand(listOf(exePath, "detect"))
            val lines = output.split("\n")
            var currentBus: String? = null
            for (line in lines) {
                if (line.contains("I2C bus:")) {
                    currentBus = Regex("""/dev/i2c-(\d+)""").find(line)?.groupValues?.get(1)
                }
                if (line.contains("Model:") && line.contains("G95NC", ignoreCase = true)) {
                    return currentBus
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
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
            val output = ProcessUtils.runCommand(cmd)
            // Terse output format: VCP 0E2 C 0x390b 0x00ff
            val parts = output.split(Regex("\\s+"))
            if (parts.size >= 4) {
                val hexStr = parts[3].removePrefix("0x")
                hexStr.toIntOrNull(16)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            ProcessUtils.runCommand(cmd)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun getStatus(): MonitorStatus? {
        val e2 = getVcpValue("E2") ?: return null
        val e3 = getVcpValue("E3") ?: return null
        val e8 = getVcpValue("E8") ?: 0
        val v60 = getVcpValue("60") ?: 0

        val layout = when (e2) {
            0x3901 -> Layout.PBP_2_50_50 
            0x3909 -> Layout.PBP_3_50_25_25
            0x390b -> Layout.PBP_3_25_50_25
            0x390d -> Layout.PBP_3_25_25_50
            0x390f -> Layout.PIP
            else -> Layout.OFF
        }
        
        return MonitorStatus(
            layout = layout,
            layoutStatusRaw = e2,
            paneSources = emptyMap(),
            audioSource = e8,
            mainInputSource = v60
        )
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
}
