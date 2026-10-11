package com.g95nc.controller

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacLoginItemTest {
    private val agentsDir = createTempDirectory("g95nc-launchagents").toFile()
    private val appExe = "/Applications/G95NC Controller.app/Contents/MacOS/G95NC Controller"

    @AfterTest
    fun cleanUp() {
        agentsDir.deleteRecursively()
    }

    @Test
    fun testEnable_writesAgentThatStartsTheAppInTheBackground() {
        val item = MacLoginItem(agentsDir, appExe)

        item.setEnabled(true)

        assertTrue(item.isEnabled)
        val plist = item.plistFile.readText()
        assertTrue("<string>$appExe</string>" in plist)
        assertTrue("<string>--background</string>" in plist)
        assertTrue("<key>RunAtLoad</key>\n    <true/>" in plist)
        assertTrue("<string>${MacLoginItem.BUNDLE_ID}</string>" in plist)
    }

    @Test
    fun testDisable_removesTheAgent() {
        val item = MacLoginItem(agentsDir, appExe)
        item.setEnabled(true)

        item.setEnabled(false)

        assertFalse(item.isEnabled)
        assertFalse(item.plistFile.exists())
    }

    @Test
    fun testEnable_createsTheLaunchAgentsFolderIfMissing() {
        val item = MacLoginItem(agentsDir.resolve("not/there/yet"), appExe)

        item.setEnabled(true)

        assertTrue(item.isEnabled)
    }

    @Test
    fun testRefresh_repointsAnEnabledAgentAfterTheAppMoved() {
        MacLoginItem(agentsDir, "/Users/me/Downloads/G95NC Controller.app/Contents/MacOS/G95NC Controller").setEnabled(true)

        val moved = MacLoginItem(agentsDir, appExe)
        moved.refresh()

        assertTrue("<string>$appExe</string>" in moved.plistFile.readText())
    }

    @Test
    fun testRefresh_doesNotEnableADisabledAgent() {
        val item = MacLoginItem(agentsDir, appExe)

        item.refresh()

        assertFalse(item.isEnabled)
    }

    @Test
    fun testPlist_escapesXmlSpecialCharactersInThePath() {
        val item = MacLoginItem(agentsDir, "/Users/me/Apps & Tools/G95NC Controller.app/Contents/MacOS/G95NC Controller")

        item.setEnabled(true)

        assertTrue("Apps &amp; Tools" in item.plistFile.readText())
    }

    @Test
    fun testUnsupported_whenNotRunningFromAnAppBundle() {
        val item = MacLoginItem(agentsDir, appExecutable = null)

        assertFalse(item.isSupported)
        assertFailsWith<IllegalStateException> { item.setEnabled(true) }
        assertFalse(item.isEnabled)
    }

    @Test
    fun testAppExecutableFrom_onlyTrustsPathsInsideAnAppBundle() {
        assertEquals(appExe, MacLoginItem.appExecutableFrom(appExe))
        assertNull(MacLoginItem.appExecutableFrom(null))
        assertNull(MacLoginItem.appExecutableFrom("/home/me/Apps/G95NC-Controller/bin/G95NC Controller"))
    }
}
