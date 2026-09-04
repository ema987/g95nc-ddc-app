package com.g95nc.controller

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ConfigManager {
    private val configDir = File(System.getProperty("user.home"), ".config/g95nc-controller")
    private val configFile = File(configDir, "config.json")

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // Compose state (not a plain var): any composable reading ConfigManager.config subscribes to
    // recomposition automatically, so e.g. the Dashboard's Safe Mode checkbox can write straight
    // through here instead of keeping its own separate, easily-out-of-sync local copy.
    var config: AppConfig by mutableStateOf(AppConfig())
        private set

    init {
        load()
    }

    private fun load() {
        if (!configFile.exists()) {
            config = AppConfig()
            save()
            return
        }
        try {
            val content = configFile.readText()
            config = json.decodeFromString<AppConfig>(content)
        } catch (e: Exception) {
            e.printStackTrace()
            config = AppConfig()
        }
    }

    fun save() {
        try {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            configFile.writeText(json.encodeToString(config))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateConfig(newConfig: AppConfig) {
        config = newConfig
        save()
    }
}
