package com.g95nc.controller

interface MonitorController {
    suspend fun getStatus(): MonitorStatus?
    suspend fun setLayout(layout: Layout): Boolean
    suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean
    suspend fun setAudioSource(audioCode: Int): Boolean
    suspend fun isToolAvailable(): Boolean
    
    companion object {
        fun create(config: AppConfig): MonitorController {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> Win32Dxva2Controller()
                os.contains("mac") -> BetterDisplayCliController(config.betterdisplaycliPath)
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> DdcutilController(config.ddcutilPath)
                else -> throw UnsupportedOperationException("Unsupported OS: $os")
            }
        }
    }
}
