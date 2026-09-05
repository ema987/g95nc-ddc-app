package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformTest {

    @Test
    fun testDetect_mac() {
        assertEquals(Platform.MAC, Platform.detect("Mac OS X"))
    }

    @Test
    fun testDetect_windows() {
        assertEquals(Platform.WINDOWS, Platform.detect("Windows 11"))
    }

    @Test
    fun testDetect_linux() {
        assertEquals(Platform.LINUX, Platform.detect("Linux"))
    }

    @Test
    fun testDetect_aix() {
        assertEquals(Platform.LINUX, Platform.detect("AIX"))
    }

    @Test
    fun testDetect_unknownFallsBackToOther() {
        assertEquals(Platform.OTHER, Platform.detect("FreeBSD"))
    }

    @Test
    fun testDetect_isCaseInsensitive() {
        assertEquals(Platform.MAC, Platform.detect("MAC OS X"))
    }
}
