package com.g95nc.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ConfigManager {
    private val configDir = File(System.getProperty("user.home"), ".config/g95nc-controller")
    private val configFile = File(configDir, "config.json")
    private val backupFile = File(configDir, "config.json.bak")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Compose state (not a plain var): any composable reading ConfigManager.config subscribes to
    // recomposition automatically, so e.g. the Dashboard's Safe Mode checkbox can write straight
    // through here instead of keeping its own separate, easily-out-of-sync local copy.
    var config: AppConfig by mutableStateOf(AppConfig())
        private set

    // Set when the main config file couldn't be read back - previously this just silently reset
    // to defaults (discarding every preset) with nothing but a console stack trace. Now the UI can
    // tell the user what actually happened.
    var loadWarning: String? by mutableStateOf(null)
        private set

    init {
        load()
    }

    fun dismissLoadWarning() {
        loadWarning = null
    }

    private fun load() {
        if (!configFile.exists()) {
            config = AppConfig()
            save()
            return
        }

        val result = resolveConfigLoad(
            mainContent = readTextOrNull(configFile),
            backupContent = readTextOrNull(backupFile),
            parse = { json.decodeFromString<AppConfig>(it) }
        )
        config = result.config
        loadWarning = result.warning

        // Only ever refresh the backup from a config file we just confirmed parses - never from a
        // recovery (that would overwrite the one good copy with whatever's still broken) and never
        // from the reset-to-defaults path (that would discard the broken file's last-known-good
        // backup instead of leaving it for a manual recovery attempt).
        if (result.warning == null) {
            try {
                configFile.copyTo(backupFile, overwrite = true)
            } catch (e: Exception) {
                // Best-effort - a failed backup refresh isn't worth blocking startup over.
            }
        }
    }

    private fun readTextOrNull(file: File): String? =
        try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }

    fun save() {
        try {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }

            // Write to a temp file and move it into place atomically, so a crash mid-write can
            // never leave config.json half-written/unparseable - the old file stays valid until
            // the new one is fully on disk.
            val tempFile = File.createTempFile("config", ".tmp", configDir)
            tempFile.writeText(json.encodeToString(config))
            Files.move(
                tempFile.toPath(), configFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )

            // The move only lands if the write fully completed, so configFile is now guaranteed
            // well-formed - safe to refresh the backup from it after every save, not just at
            // startup.
            configFile.copyTo(backupFile, overwrite = true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateConfig(newConfig: AppConfig) {
        config = newConfig
        save()
    }
}

data class ConfigLoadResult(val config: AppConfig, val warning: String?)

/**
 * Pure decision logic for [ConfigManager.load], kept separate from file I/O so it's directly
 * testable: given the raw text of the main config file and its backup (either may be null/
 * unreadable), decide what to load and whether the user needs to be told anything.
 */
fun resolveConfigLoad(mainContent: String?, backupContent: String?, parse: (String) -> AppConfig): ConfigLoadResult {
    if (mainContent != null) {
        val parsed = try { parse(mainContent) } catch (e: Exception) { null }
        if (parsed != null) return ConfigLoadResult(parsed, warning = null)
    }

    if (backupContent != null) {
        val parsed = try { parse(backupContent) } catch (e: Exception) { null }
        if (parsed != null) {
            return ConfigLoadResult(
                parsed,
                "Your configuration file was corrupted, so it was restored from the last automatic backup."
            )
        }
    }

    return ConfigLoadResult(
        AppConfig(),
        "Your configuration file was corrupted and no usable backup was found, so it was reset to defaults."
    )
}
