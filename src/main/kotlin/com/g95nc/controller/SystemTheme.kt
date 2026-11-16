package com.g95nc.controller

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

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

            // XDG Desktop Portal works across KDE Plasma, GNOME, and Wayland compositors:
            // uint32 1 = prefer-dark, 2 = prefer-light, 0 = no preference (light).
            Platform.LINUX -> detectLinuxDark(commandRunner)

            Platform.OTHER -> true
        }
    } catch (e: Exception) {
        true
    }

    /**
     * Best-effort detection of whether the OS *tray/panel* background is dark (for choosing white
     * vs. dark tray icon ink). Differs from [isDark] on Windows, where the taskbar uses
     * `SystemUsesLightTheme` independently of `AppsUseLightTheme`.
     */
    suspend fun isTrayDark(
        commandRunner: CommandRunner = ProcessUtils,
        platform: Platform = Platform.detect()
    ): Boolean = try {
        when (platform) {
            Platform.MAC, Platform.LINUX -> isDark(commandRunner, platform)
            Platform.WINDOWS -> {
                val output = commandRunner.run(
                    listOf(
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "SystemUsesLightTheme"
                    )
                )
                Regex("0x([01])").find(output)?.groupValues?.get(1) != "1"
            }
            Platform.OTHER -> true
        }
    } catch (e: Exception) {
        true
    }

    private suspend fun detectLinuxDark(commandRunner: CommandRunner): Boolean {
        val portal = try {
            commandRunner.run(
                listOf(
                    "gdbus", "call", "--session",
                    "--dest", "org.freedesktop.portal.Desktop",
                    "--object-path", "/org/freedesktop/portal/desktop",
                    "--method", "org.freedesktop.portal.Settings.Read",
                    "org.freedesktop.appearance", "color-scheme"
                )
            )
        } catch (_: Exception) {
            ""
        }
        val portalValue = Regex("""uint32\s+(\d+)""").find(portal)?.groupValues?.get(1)?.toIntOrNull()
        if (portalValue != null) return portalValue == 1

        isKdeDark()?.let { return it }

        val colorScheme = try {
            commandRunner.run(listOf("gsettings", "get", "org.gnome.desktop.interface", "color-scheme"))
        } catch (_: Exception) {
            ""
        }
        if (colorScheme.contains("dark", ignoreCase = true)) return true

        val gtkTheme = try {
            commandRunner.run(listOf("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme"))
        } catch (_: Exception) {
            ""
        }
        if (gtkTheme.contains("dark", ignoreCase = true)) return true

        if (colorScheme.isNotBlank() || gtkTheme.isNotBlank()) return false
        return true
    }

    /**
     * Emits the initial [isTrayDark] value and then keeps [onChanged] updated as the OS tray
     * appearance changes. Uses an event-driven `gdbus monitor` stream on Linux for near-instant
     * updates, native template images on macOS (no polling needed), and fast polling on Windows.
     */
    suspend fun observeTrayDark(
        platform: Platform = Platform.detect(),
        onChanged: (Boolean) -> Unit
    ) {
        onChanged(isTrayDark(platform = platform))
        when (platform) {
            Platform.MAC, Platform.OTHER -> return
            Platform.WINDOWS -> {
                while (currentCoroutineContext().isActive) {
                    delay(1500)
                    onChanged(isTrayDark(platform = platform))
                }
            }
            Platform.LINUX -> {
                val monitored = monitorLinuxPortalTheme(onChanged)
                if (!monitored) {
                    while (currentCoroutineContext().isActive) {
                        delay(1500)
                        onChanged(isTrayDark(platform = platform))
                    }
                }
            }
        }
    }

    internal fun parsePortalSettingChangedLine(line: String): Boolean? {
        if (!line.contains("SettingChanged") || !line.contains("color-scheme")) return null
        val value = Regex("""uint32\s+(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
        return value?.let { it == 1 }
    }

    private suspend fun monitorLinuxPortalTheme(onChanged: (Boolean) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                process = ProcessBuilder(
                    "gdbus", "monitor", "--session",
                    "--dest", "org.freedesktop.portal.Desktop",
                    "--object-path", "/org/freedesktop/portal/desktop"
                ).redirectErrorStream(true).start()

                process.inputStream.bufferedReader().use { reader ->
                    while (currentCoroutineContext().isActive) {
                        val line = reader.readLine() ?: break
                        parsePortalSettingChangedLine(line)?.let(onChanged)
                    }
                }
                true
            } catch (_: Exception) {
                false
            } finally {
                process?.destroyForcibly()
            }
        }

    internal fun isKdeDark(
        kdeGlobals: File = File(System.getProperty("user.home"), ".config/kdeglobals")
    ): Boolean? = try {
        if (!kdeGlobals.exists()) null
        else {
            val lines = kdeGlobals.readLines()
            val colorSchemeLine = lines.firstOrNull { it.startsWith("ColorScheme=", ignoreCase = true) }
            if (colorSchemeLine != null) {
                colorSchemeLine.contains("dark", ignoreCase = true)
            } else {
                lines.firstOrNull { it.startsWith("LookAndFeelPackage=", ignoreCase = true) }
                    ?.contains("dark", ignoreCase = true)
            }
        }
    } catch (_: Exception) {
        null
    }
}
