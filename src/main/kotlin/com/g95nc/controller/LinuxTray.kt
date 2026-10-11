package com.g95nc.controller

import androidx.compose.runtime.*
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator

@Composable
fun LinuxTray(
    onDashboardOpenChange: (Boolean) -> Unit,
    onSettingsOpenChange: (Boolean) -> Unit,
    presets: List<Preset>,
    isBusy: Boolean,
    onApplyPreset: (Preset) -> Unit,
    onAboutClick: () -> Unit,
    onQuit: () -> Unit
) {
    DisposableEffect(Unit) {
        SystemTray.DEBUG = false
        val systemTray = SystemTray.get()
        if (systemTray == null) {
            println("SystemTray is not supported!")
            return@DisposableEffect onDispose {}
        }

        try {
            systemTray.setImage(renderAppIconImage(64))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        systemTray.setTooltip(AppInfo.NAME)
        
        onDispose {
            systemTray.shutdown()
        }
    }

    // Reactively update the menu when config presets or busy state changes
    LaunchedEffect(presets, isBusy) {
        val systemTray = SystemTray.get() ?: return@LaunchedEffect
        
        val menu = systemTray.menu
        menu.entries.toList().forEach { menu.remove(it) }
        
        menu.add(MenuItem("Open Dashboard") {
            onDashboardOpenChange(true)
        })
        menu.add(MenuItem("Settings") {
            onSettingsOpenChange(true)
        })
        menu.add(Separator())
        
        presets.forEach { preset ->
            val presetItem = MenuItem("Preset: ${preset.name}") {
                if (!isBusy) {
                    onApplyPreset(preset)
                }
            }
            if (isBusy) {
                presetItem.setEnabled(false)
            }
            menu.add(presetItem)
        }
        
        menu.add(Separator())
        menu.add(MenuItem("About") {
            onAboutClick()
        })
        menu.add(MenuItem("Quit") {
            onQuit()
        })
    }
}
