package com.g95nc.controller

/**
 * The one place the app's display name/version live. Referenced everywhere the app identifies
 * itself to the OS or the user - the macOS menu bar name, every window title, the "already
 * running"/About dialogs, and (via build.gradle.kts) the packaged app's name/dock name/Info.plist
 * - so there is exactly one string to change, not five that can drift out of sync.
 *
 * VERSION comes from the git tag at release time (generated into AppVersion.kt by
 * build.gradle.kts) and is "dev" for local builds - never edit it by hand.
 */
object AppInfo {
    const val NAME = "G95NC Controller"
    const val VERSION = APP_VERSION
}
