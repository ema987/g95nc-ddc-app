package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MonitorControllerTest {

    @Test
    fun testCreate_returnsDdcutilControllerOnLinux() {
        assertTrue(MonitorController.create(AppConfig(), Platform.LINUX) is DdcutilController)
    }

    @Test
    fun testCreate_returnsBetterDisplayCliControllerOnMac() {
        assertTrue(MonitorController.create(AppConfig(), Platform.MAC) is BetterDisplayCliController)
    }

    @Test
    fun testCreate_returnsWin32Dxva2ControllerOnWindows() {
        assertTrue(MonitorController.create(AppConfig(), Platform.WINDOWS) is Win32Dxva2Controller)
    }

    @Test
    fun testCreate_throwsOnUnsupportedPlatform() {
        assertFailsWith<UnsupportedOperationException> {
            MonitorController.create(AppConfig(), Platform.OTHER)
        }
    }
}
