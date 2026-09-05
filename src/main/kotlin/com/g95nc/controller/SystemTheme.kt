package com.g95nc.controller

/**
 * Best-effort detection of whether the OS is currently in a dark appearance, for
 * [ThemeMode.SYSTEM]. There's no cross-platform Compose Desktop API for this, so each platform is
 * queried with its own native mechanism; anything that fails to detect (missing tool, unsupported
 * desktop environment) falls back to dark, since that's this app's original and primary look.
 */
object SystemTheme {
    suspend fun isDark(
        commandRunner: CommandRunner = ProcessUtils,
        platform: Platform = Platform.detect()
    ): Boolean = try {
        when (platform) {
            // No key at all (the default) means Light - `defaults read` prints an error to
            // stderr in that case, which ProcessUtils merges into stdout, so it just won't
            // match "Dark" below rather than needing to be treated as a thrown exception.
            Platform.MAC -> commandRunner.run(listOf("defaults", "read", "-g", "AppleInterfaceStyle"))
                .trim().equals("Dark", ignoreCase = true)

            // AppsUseLightTheme is a DWORD: 1 = light apps, 0 = dark apps.
            Platform.WINDOWS -> {
                val output = commandRunner.run(
                    listOf(
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme"
                    )
                )
                Regex("0x([01])").find(output)?.groupValues?.get(1) != "1"
            }

            // GNOME (and most GNOME-based DEs) expose this as 'default' or 'prefer-dark'/'prefer-light'.
            Platform.LINUX -> commandRunner.run(
                listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme")
            ).contains("dark", ignoreCase = true)

            Platform.OTHER -> true
        }
    } catch (e: Exception) {
        true
    }
}
