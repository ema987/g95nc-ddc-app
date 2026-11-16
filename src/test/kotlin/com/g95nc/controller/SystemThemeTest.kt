package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun testTrayDark_windowsUsesSystemUsesLightTheme() = runTest {
        assertTrue(SystemTheme.isTrayDark(FakeCommandRunner { "    SystemUsesLightTheme    REG_DWORD    0x0" }, Platform.WINDOWS))
        assertFalse(SystemTheme.isTrayDark(FakeCommandRunner { "    SystemUsesLightTheme    REG_DWORD    0x1" }, Platform.WINDOWS))
    }

    @Test
    fun testTrayDark_linuxFollowsSystemTheme() = runTest {
        assertTrue(SystemTheme.isTrayDark(FakeCommandRunner { "'prefer-dark'" }, Platform.LINUX))
        assertFalse(SystemTheme.isTrayDark(FakeCommandRunner { "'default'" }, Platform.LINUX))
    }

    @Test
    fun testLinux_gtkThemeDarkFallback_isDark() = runTest {
        val runner = FakeCommandRunner { cmd ->
            if (cmd.contains("gtk-theme")) "'Yaru-dark'" else "'default'"
        }
        assertTrue(SystemTheme.isDark(runner, Platform.LINUX))
    }

    @Test
    fun testKdeGlobals_breezeDark_isDark() {
        val temp = File.createTempFile("kdeglobals", ".tmp")
        try {
            temp.writeText("[General]\nColorScheme=BreezeDark\n")
            assertEquals(true, SystemTheme.isKdeDark(temp))
            temp.writeText("[General]\nColorScheme=BreezeLight\nLookAndFeelPackage=org.kde.breezedark.desktop\n")
            assertEquals(false, SystemTheme.isKdeDark(temp))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun testLinux_xdgPortalColorScheme_isRespected() = runTest {
        val darkRunner = FakeCommandRunner { cmd ->
            if (cmd.contains("gdbus")) "(<uint32 1>,)" else ""
        }
        val lightRunner = FakeCommandRunner { cmd ->
            if (cmd.contains("gdbus")) "(<uint32 2>,)" else ""
        }
        assertTrue(SystemTheme.isTrayDark(darkRunner, Platform.LINUX))
        assertFalse(SystemTheme.isTrayDark(lightRunner, Platform.LINUX))
    }

    @Test
    fun testParsePortalSettingChangedLine() {
        assertEquals(
            true,
            SystemTheme.parsePortalSettingChangedLine(
                "/org/freedesktop/portal/desktop: org.freedesktop.portal.Settings.SettingChanged ('org.freedesktop.appearance', 'color-scheme', <uint32 1>)"
            )
        )
        assertEquals(
            false,
            SystemTheme.parsePortalSettingChangedLine(
                "/org/freedesktop/portal/desktop: org.freedesktop.portal.Settings.SettingChanged ('org.freedesktop.appearance', 'color-scheme', <uint32 2>)"
            )
        )
        assertNull(
            SystemTheme.parsePortalSettingChangedLine(
                "/org/freedesktop/portal/desktop: org.freedesktop.portal.Settings.SettingChanged ('org.freedesktop.appearance', 'accent-color', <(0.2, 0.4, 0.8)>)"
            )
        )
    }
}
