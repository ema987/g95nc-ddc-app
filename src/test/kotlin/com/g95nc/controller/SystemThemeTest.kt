package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemThemeTest {

    @Test
    fun testMac_appleInterfaceStyleDark_isDark() = runTest {
        val runner = FakeCommandRunner { "Dark" }
        assertTrue(SystemTheme.isDark(runner, Platform.MAC))
    }

    @Test
    fun testMac_appleInterfaceStyleMissing_isLight() = runTest {
        // The real `defaults` prints an error (no "Dark" value) when the key is absent - Light is
        // the default appearance and has no explicit key at all.
        val runner = FakeCommandRunner { "The domain/default pair does not exist" }
        assertFalse(SystemTheme.isDark(runner, Platform.MAC))
    }

    @Test
    fun testWindows_appsUseLightThemeZero_isDark() = runTest {
        val runner = FakeCommandRunner { "    AppsUseLightTheme    REG_DWORD    0x0" }
        assertTrue(SystemTheme.isDark(runner, Platform.WINDOWS))
    }

    @Test
    fun testWindows_appsUseLightThemeOne_isLight() = runTest {
        val runner = FakeCommandRunner { "    AppsUseLightTheme    REG_DWORD    0x1" }
        assertFalse(SystemTheme.isDark(runner, Platform.WINDOWS))
    }

    @Test
    fun testLinux_preferDarkColorScheme_isDark() = runTest {
        val runner = FakeCommandRunner { "'prefer-dark'" }
        assertTrue(SystemTheme.isDark(runner, Platform.LINUX))
    }

    @Test
    fun testLinux_defaultColorScheme_isLight() = runTest {
        val runner = FakeCommandRunner { "'default'" }
        assertFalse(SystemTheme.isDark(runner, Platform.LINUX))
    }

    @Test
    fun testDetectionThrows_fallsBackToDark() = runTest {
        val runner = FakeCommandRunner { throw RuntimeException("command not found") }
        assertTrue(SystemTheme.isDark(runner, Platform.LINUX))
    }

    @Test
    fun testOtherPlatform_defaultsToDark() = runTest {
        val runner = FakeCommandRunner { "" }
        assertTrue(SystemTheme.isDark(runner, Platform.OTHER))
    }
}
