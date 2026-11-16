package com.g95nc.controller

import androidx.compose.runtime.*
import dorkbox.systemTray.SystemTray
import dorkbox.systemTray.MenuItem
import dorkbox.systemTray.Separator
import kotlinx.coroutines.awaitCancellation

@Composable
fun LinuxTray(
    isDark: Boolean = true,
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
            systemTray.setImage(renderTrayIconImage(64, isDark = isDark))
        } catch (e: Exception) {
            System.err.println("Failed to set tray icon: ${e.message}")
        }
        
        systemTray.setTooltip(AppInfo.NAME)
        
        onDispose {
            systemTray.shutdown()
        }
    }

    LaunchedEffect(isDark) {
        val systemTray = SystemTray.get() ?: return@LaunchedEffect
        try {
            systemTray.setImage(renderTrayIconImage(64, isDark = isDark))
        } catch (e: Exception) {
            System.err.println("Failed to update tray icon: ${e.message}")
        }
    }

    // Compose's application() only keeps running while some window or effect coroutine is still
    // active (its Recomposer is closed right after startup). Compose's own Tray counts as one, but
    // this dorkbox tray is just a DisposableEffect - without this, the app would quit as soon as
    // the dashboard closes, or immediately when started with --background.
    LaunchedEffect(Unit) { awaitCancellation() }

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
