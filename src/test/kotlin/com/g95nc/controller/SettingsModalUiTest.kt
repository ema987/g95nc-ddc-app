package com.g95nc.controller

import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class SettingsModalUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun testSaveConfiguration_passesEditedPathAndToggledCheckbox() {
        var saved: AppConfig? = null
        composeRule.setContent {
            MaterialTheme(colors = darkColors()) {
                SettingsModal(
                    config = AppConfig(ddcutilPath = "ddcutil", betterdisplaycliPath = "betterdisplaycli", enableSafetyCountdown = true),
                    onSave = { saved = it }
                )
            }
        }

        composeRule.onNodeWithTag("ddcutilPathField").performTextClearance()
        composeRule.onNodeWithTag("ddcutilPathField").performTextInput("/usr/local/bin/ddcutil")

        composeRule.onAllNodes(isToggleable()).onFirst().performClick()

        composeRule.onNodeWithText("Save Configuration").performClick()

        assertEquals("/usr/local/bin/ddcutil", saved?.ddcutilPath)
        assertEquals("betterdisplaycli", saved?.betterdisplaycliPath)
        assertEquals(false, saved?.enableSafetyCountdown)
    }
}
