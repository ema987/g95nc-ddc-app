package com.g95nc.controller

interface MonitorController {
    suspend fun getStatus(): MonitorStatus?
    suspend fun setLayout(layout: Layout): Boolean
    suspend fun setMainSource(sourceCode: SourceCode): Boolean
    suspend fun setSource(channel: PaneChannel, sourceCode: SourceCode): Boolean
    suspend fun setAudioSource(audioCode: Int): Boolean
    suspend fun isToolAvailable(): Boolean

    /**
     * Reads a raw VCP code (e.g. "E2", "ED") with no interpretation - for the Settings debug
     * panel and future protocol investigation, not for normal app logic. Null if unreadable.
     */
    suspend fun getRawVcp(vcp: String): Int?

    companion object {
        fun create(config: AppConfig, platform: Platform = Platform.detect()): MonitorController = when (platform) {
            Platform.WINDOWS -> Win32Dxva2Controller()
            Platform.MAC -> BetterDisplayCliController(config.betterdisplaycliPath)
            Platform.LINUX -> DdcutilController(config.ddcutilPath)
            Platform.OTHER -> throw UnsupportedOperationException("Unsupported OS: ${System.getProperty("os.name")}")
        }
    }
}
