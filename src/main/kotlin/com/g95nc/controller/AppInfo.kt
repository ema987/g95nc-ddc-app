package com.g95nc.controller

/**
 * The one place the app's display name/version live. Referenced everywhere the app identifies
 * itself to the OS or the user - the macOS menu bar name, every window title, the "already
 * running"/About dialogs, and (via build.gradle.kts) the packaged app's name/dock name/Info.plist
 * - so there is exactly one string to change, not five that can drift out of sync.
 *
 * VERSION is a plain constant, not read from the build: this project has no generated
 * BuildConfig/manifest version wiring yet, so keep it matching nativeDistributions.packageVersion
 * in build.gradle.kts by hand until that's worth setting up.
 */
object AppInfo {
    const val NAME = "G95NC Controller"
    const val VERSION = "1.0.0"
}
