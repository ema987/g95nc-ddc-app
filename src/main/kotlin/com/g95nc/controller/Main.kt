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
import kotlinx.coroutines.delay

// This is a minimal placeholder for the icon. In a real app, load an image.
// For now we'll just use a default painter if possible, or omit the icon if null.
fun main() = application {
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDashboardOpen by remember { mutableStateOf(true) }
    
    val config = ConfigManager.config
    var controller by remember { mutableStateOf(MonitorController.create(config)) }
    var isToolAvailable by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<MonitorStatus?>(null) }
    var pendingChange by remember { mutableStateOf<PendingChange?>(null) }
    
    val scope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        isToolAvailable = controller.isToolAvailable()
        if (isToolAvailable) {
            status = controller.getStatus()
        } else {
            isSettingsOpen = true // Force onboarding
        }
    }

    // Failsafe Timer Loop
    LaunchedEffect(pendingChange) {
        if (pendingChange != null && pendingChange!!.countdown > 0) {
            delay(1000)
            pendingChange = pendingChange!!.copy(countdown = pendingChange!!.countdown - 1)
        } else if (pendingChange != null && pendingChange!!.countdown == 0) {
            // Auto revert!
            val change = pendingChange!!
            
            // LAUNCH IN SCOPE SO IT SURVIVES PENDINGCHANGE=NULL
            scope.launch {
                val currentStatus = status
                val targetLayout = change.previousStatus.layout
                val needsLayoutRevert = targetLayout != null && targetLayout != currentStatus?.layout
                
                pendingChange = null
                status = null // Show spinner
                
                if (needsLayoutRevert) {
                    controller.setLayout(targetLayout!!)
                    delay(4000) // Layout change resets DDC stack, wait before sending sources
                }
                change.previousStatus.paneSources.forEach { (channel, source) ->
                    if (currentStatus?.paneSources?.get(channel) != source) {
                        controller.setSource(channel, source)
                        delay(4000) // Source change triggers HDMI handshake, wait before next command
                    }
                }
                
                status = controller.getStatus()
            }
        }
    }

    // Recovery Polling: if status is null (e.g. after a failed DDC read during a monitor reboot)
    // we poll every 3 seconds to recover the connection, but stop polling once we succeed!
    LaunchedEffect(status, isDashboardOpen, isToolAvailable) {
        if (isDashboardOpen && isToolAvailable && status == null && pendingChange == null) {
            while (true) {
                delay(3000)
                val newStatus = controller.getStatus()
                if (newStatus != null) {
                    status = newStatus
                    break
                }
            }
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
                        val currentStatus = status
                        status = null
                        if (currentStatus?.layout != preset.layout) {
                            controller.setLayout(preset.layout)
                            delay(4000)
                        }
                        preset.paneSources.forEach { (channel, source) ->
                            if (currentStatus?.paneSources?.get(channel) != source) {
                                controller.setSource(channel, source)
                                delay(4000)
                            }
                        }
                        if (preset.audioSource != null && currentStatus?.audioSource != preset.audioSource) {
                            controller.setAudioSource(preset.audioSource)
                        }
                        status = controller.getStatus()
                        if (currentStatus != null) {
                            pendingChange = PendingChange(currentStatus, 10)
                        }
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
                            pendingChange = pendingChange,
                            onSettingsClick = { isSettingsOpen = true },
                            onConfirmChange = { pendingChange = null },
                            onRevertChange = {
                                val change = pendingChange
                                if (change != null) {
                                    scope.launch {
                                        val currentStatus = status
                                        val targetLayout = change.previousStatus.layout
                                        val needsLayoutRevert = targetLayout != null && targetLayout != currentStatus?.layout
                                        
                                        pendingChange = null
                                        status = null
                                        if (needsLayoutRevert) {
                                            controller.setLayout(targetLayout!!)
                                            delay(4000)
                                        }
                                        change.previousStatus.paneSources.forEach { (channel, source) ->
                                            if (currentStatus?.paneSources?.get(channel) != source) {
                                                controller.setSource(channel, source)
                                                delay(4000)
                                            }
                                        }
                                        
                                        status = controller.getStatus()
                                    }
                                }
                            },
                            onExecuteAction = { action ->
                                scope.launch {
                                    val currentStatus = status
                                    status = null
                                    action(currentStatus)
                                    status = controller.getStatus()
                                    if (currentStatus != null) {
                                        pendingChange = PendingChange(currentStatus, 15)
                                    }
                                }
                            },
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
