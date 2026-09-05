package com.g95nc.controller

interface MonitorController {
    suspend fun getStatus(): MonitorStatus?
    suspend fun setLayout(layout: Layout): Boolean
    suspend fun setMainSource(sourceCode: SourceCode): Boolean
    suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean
    suspend fun setAudioSource(audioCode: Int): Boolean
    suspend fun isToolAvailable(): Boolean
    
    companion object {
        fun create(config: AppConfig): MonitorController = when (Platform.detect()) {
            Platform.WINDOWS -> Win32Dxva2Controller()
            Platform.MAC -> BetterDisplayCliController(config.betterdisplaycliPath)
            Platform.LINUX -> DdcutilController(config.ddcutilPath)
            Platform.OTHER -> throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
        }
    }
}
