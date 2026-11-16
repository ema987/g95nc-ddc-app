package com.g95nc.controller

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckerTest {

    @Test
    fun testIsNewerVersion_comparesNumericSegments() {
        assertTrue(UpdateChecker.isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(UpdateChecker.isNewerVersion("1.10.0", "1.2.0"))
        assertTrue(UpdateChecker.isNewerVersion("v2.0.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.2.0", "dev"))
    }

    @Test
    fun testParseReleaseJson_stripsLeadingVAndExtractsUrl() {
        val json = """{"tag_name": "v1.2.3", "html_url": "https://github.com/ema987/g95nc-ddc-app/releases/tag/v1.2.3"}"""
        val parsed = UpdateChecker.parseReleaseJson(json)

        assertEquals(ReleaseInfo("1.2.3", "https://github.com/ema987/g95nc-ddc-app/releases/tag/v1.2.3"), parsed)
    }

    @Test
    fun testParseReleaseJson_invalidOrIncompleteReturnsNull() {
        assertNull(UpdateChecker.parseReleaseJson("{}"))
        assertNull(UpdateChecker.parseReleaseJson("not json"))
    }

    @Test
    fun testCheckForUpdate_returnsUpdateAvailableWhenNewer() = runTest {
        val release = ReleaseInfo("1.2.0", "https://example.com/v1.2.0")
        val result = UpdateChecker.checkForUpdate(currentVersion = "1.0.0") { release }

        val available = assertIs<UpdateCheckResult.UpdateAvailable>(result)
        assertEquals(release, available.release)
    }

    @Test
    fun testCheckForUpdate_returnsUpToDateWhenSameOrOlder() = runTest {
        val result = UpdateChecker.checkForUpdate(currentVersion = "1.2.0") {
            ReleaseInfo("1.2.0", "https://example.com/v1.2.0")
        }

        assertIs<UpdateCheckResult.UpToDate>(result)
    }

    @Test
    fun testCheckForUpdate_returnsDevBuildForDevVersion() = runTest {
        val release = ReleaseInfo("1.2.0", "https://example.com/v1.2.0")
        val result = UpdateChecker.checkForUpdate(currentVersion = "dev") { release }

        val dev = assertIs<UpdateCheckResult.DevBuild>(result)
        assertEquals(release, dev.latestRelease)
    }

    @Test
    fun testCheckForUpdate_returnsErrorWhenFetchFails() = runTest {
        val result = UpdateChecker.checkForUpdate(currentVersion = "1.0.0") {
            throw RuntimeException("network unreachable")
        }

        val err = assertIs<UpdateCheckResult.Error>(result)
        assertEquals("network unreachable", err.message)
    }
}
