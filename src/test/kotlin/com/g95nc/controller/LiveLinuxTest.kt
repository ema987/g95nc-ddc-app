package com.g95nc.controller

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull

class LiveLinuxTest {

    @Test
    fun testLiveStatusFetchOnLinux() = runBlocking {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            val controller = DdcutilController("ddcutil")
            if (controller.isToolAvailable()) {
                val status = controller.getStatus()
                println("Fetched live Linux status: $status")
                assertNotNull(status, "Status should not be null when ddcutil is available and monitor is connected")
            }
        }
    }
}
