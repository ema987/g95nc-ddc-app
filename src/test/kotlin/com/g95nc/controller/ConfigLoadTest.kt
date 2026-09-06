package com.g95nc.controller

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigLoadTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun parse(text: String): AppConfig = json.decodeFromString(text)

    private val validConfig = AppConfig(ddcutilPath = "/usr/local/bin/ddcutil")
    private val validConfigJson = json.encodeToString(AppConfig.serializer(), validConfig)

    private val backupConfig = AppConfig(ddcutilPath = "/opt/homebrew/bin/ddcutil")
    private val backupConfigJson = json.encodeToString(AppConfig.serializer(), backupConfig)

    @Test
    fun testValidMainContent_isUsedDirectlyWithNoWarning() {
        val result = resolveConfigLoad(mainContent = validConfigJson, backupContent = backupConfigJson, parse = ::parse)

        assertEquals(validConfig, result.config)
        assertNull(result.warning)
    }

    @Test
    fun testCorruptedMainContent_recoversFromBackupAndWarns() {
        val result = resolveConfigLoad(mainContent = "{not json", backupContent = backupConfigJson, parse = ::parse)

        assertEquals(backupConfig, result.config)
        assertTrue(result.warning!!.contains("backup", ignoreCase = true))
    }

    @Test
    fun testMissingMainContent_recoversFromBackupAndWarns() {
        val result = resolveConfigLoad(mainContent = null, backupContent = backupConfigJson, parse = ::parse)

        assertEquals(backupConfig, result.config)
        assertTrue(result.warning!!.contains("backup", ignoreCase = true))
    }

    @Test
    fun testCorruptedMainAndCorruptedBackup_resetsToDefaultsAndWarns() {
        val result = resolveConfigLoad(mainContent = "{not json", backupContent = "{also not json", parse = ::parse)

        assertEquals(AppConfig(), result.config)
        assertTrue(result.warning!!.contains("defaults", ignoreCase = true))
    }

    @Test
    fun testCorruptedMainAndMissingBackup_resetsToDefaultsAndWarns() {
        val result = resolveConfigLoad(mainContent = "{not json", backupContent = null, parse = ::parse)

        assertEquals(AppConfig(), result.config)
        assertTrue(result.warning!!.contains("defaults", ignoreCase = true))
    }
}
