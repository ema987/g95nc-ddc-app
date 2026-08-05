package com.g95nc.controller

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

// This is a minimal placeholder for the icon. In a real app, load an image.
// For now we'll just use a default painter if possible, or omit the icon if null.
fun main() = application {
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDashboardOpen by remember { mutableStateOf(true) }
    
    val config = ConfigManager.config
    var controller by remember { mutableStateOf(MonitorController.create(config)) }
    var isToolAvailable by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<MonitorStatus?>(null) }
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        isToolAvailable = controller.isToolAvailable()
        if (isToolAvailable) {
            status = controller.getStatus()
        } else {
            isSettingsOpen = true // Force onboarding
        }
    }

    // Tray Icon
    Tray(
        icon = TrayIcon,
        menu = {
            Item("Open Dashboard", onClick = { isDashboardOpen = true })
            Item("Settings", onClick = { isSettingsOpen = true })
            Separator()
            // Presets
            config.presets.forEach { preset ->
                Item("Preset: ${preset.name}", onClick = {
                    scope.launch {
                        status = null
                        controller.setLayout(preset.layout)
                        preset.paneSources.forEach { (channel, source) ->
                            controller.setSource(channel, source)
                        }
                        if (preset.audioSource != null) {
                            controller.setAudioSource(preset.audioSource)
                        }
                        status = controller.getStatus()
                    }
                })
            }
            Separator()
            Item("Quit", onClick = ::exitApplication)
        }
    )

    if (isDashboardOpen) {
        Window(
            onCloseRequest = { isDashboardOpen = false },
            title = "G95NC Controller",
            state = rememberWindowState(width = 600.dp, height = 700.dp)
        ) {
            MaterialTheme(colors = darkColors()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isToolAvailable) {
                        OnboardingWizard(
                            onOpenSettings = { isSettingsOpen = true },
                            onCheckAgain = {
                                scope.launch {
                                    isToolAvailable = controller.isToolAvailable()
                                }
                            }
                        )
                    } else {
                        Dashboard(
                            status = status,
                            controller = controller,
                            onSettingsClick = { isSettingsOpen = true },
                            onRefresh = {
                                scope.launch {
                                    status = null
                                    status = controller.getStatus()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (isSettingsOpen) {
        Window(
            onCloseRequest = { isSettingsOpen = false },
            title = "Settings & Tools",
            state = rememberWindowState(width = 500.dp, height = 400.dp)
        ) {
            MaterialTheme(colors = darkColors()) {
                SettingsModal(
                    config = config,
                    onSave = { newConfig ->
                        ConfigManager.updateConfig(newConfig)
                        controller = MonitorController.create(newConfig)
                        isSettingsOpen = false
                    }
                )
            }
        }
    }
}

// Placeholder empty icon
val TrayIcon = object : androidx.compose.ui.graphics.painter.Painter() {
    override val intrinsicSize = androidx.compose.ui.geometry.Size(24f, 24f)
    override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {}
}
