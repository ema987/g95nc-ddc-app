package com.g95nc.controller

import java.io.File

/**
 * "Start at login" on macOS, as a per-user LaunchAgent (~/Library/LaunchAgents/<label>.plist) that
 * runs the app with --background at login - just the menu bar icon, no dashboard window.
 *
 * A LaunchAgent rather than SMAppService (the API behind "Open at Login"): it's a plain file, so it
 * needs no native code and works for an unsigned app. macOS still lists it under System Settings >
 * General > Login Items > "Allow in the Background", where the user can switch it off too.
 *
 * Whether it's enabled is simply whether the plist exists - there's no separate saved setting that
 * could drift out of sync with it. Linux autostart is handled by install.sh instead (see
 * packaging/linux), and Windows has none yet.
 */
class MacLoginItem(
    private val launchAgentsDir: File = File(System.getProperty("user.home"), "Library/LaunchAgents"),
    private val appExecutable: String? = appExecutableFrom(System.getProperty("jpackage.app-path"))
) {
    val plistFile: File get() = File(launchAgentsDir, "$LABEL.plist")

    /** False when not running from a packaged .app (e.g. `./gradlew run`): there's no stable
     * executable to point launchd at, so the toggle is hidden. */
    val isSupported: Boolean get() = appExecutable != null

    val isEnabled: Boolean get() = plistFile.isFile

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            check(isSupported) { "Not running from a packaged app - nothing to start at login" }
            launchAgentsDir.mkdirs()
            plistFile.writeText(plistContent())
        } else {
            plistFile.delete()
        }
    }

    /** Called at startup: if start-at-login is on but the app has moved since (e.g. dragged out of
     * Downloads into Applications), repoint the agent at where it lives now. */
    fun refresh() {
        if (isEnabled && isSupported && plistFile.readText() != plistContent()) {
            plistFile.writeText(plistContent())
        }
    }

    internal fun plistContent(): String = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
        |<plist version="1.0">
        |<dict>
        |    <key>Label</key>
        |    <string>$LABEL</string>
        |    <key>ProgramArguments</key>
        |    <array>
        |        <string>${xmlEscape(appExecutable!!)}</string>
        |        <string>--background</string>
        |    </array>
        |    <key>RunAtLoad</key>
        |    <true/>
        |    <key>LimitLoadToSessionType</key>
        |    <string>Aqua</string>
        |    <key>AssociatedBundleIdentifiers</key>
        |    <array>
        |        <string>$BUNDLE_ID</string>
        |    </array>
        |</dict>
        |</plist>
        |""".trimMargin()

    companion object {
        const val LABEL = "com.g95nc.controller.login"

        /** Must match macOS.bundleID in build.gradle.kts - lets System Settings show this login item
         * under the app's name and icon instead of the bare executable. */
        const val BUNDLE_ID = "com.g95nc.controller"

        /** The jpackage launcher sets jpackage.app-path to its own path; only trust it when it's
         * really inside a .app bundle. */
        fun appExecutableFrom(appPath: String?): String? =
            appPath?.takeIf { it.contains(".app/Contents/MacOS/") }

        private fun xmlEscape(s: String): String =
            s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
