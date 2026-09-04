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
    var recoveryExhausted by remember { mutableStateOf(false) }
    // True while a monitor action is in flight. The dashboard stays showing the last known
    // status during this (instead of blanking to a full-page spinner) and disables its controls.
    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    suspend fun runBusy(block: suspend () -> Unit) {
        isBusy = true
        try {
            block()
        } finally {
            isBusy = false
        }
    }

    LaunchedEffect(controller) {
        isToolAvailable = controller.isToolAvailable()
        if (!isToolAvailable) {
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
                pendingChange = null

                runBusy {
                    MonitorActions.applyTarget(
                        controller = controller,
                        currentStatus = currentStatus,
                        targetLayout = change.previousStatus.layout,
                        targetPaneSources = change.previousStatus.paneSources,
                        targetAudioSource = change.previousStatus.audioSource
                    )

                    status = controller.getStatus()
                }
            }
        }
    }

    // Recovery Polling: if status is null (e.g. after a failed DDC read during a monitor reboot)
    // we poll to recover the connection, but give up after a bounded number of attempts instead
    // of retrying forever - the user can still hit Refresh manually (see onRefresh below).
    LaunchedEffect(status, isDashboardOpen, isToolAvailable) {
        if (isDashboardOpen && isToolAvailable && status == null && pendingChange == null) {
            recoveryExhausted = false
            val recovered = RecoveryPoller.pollUntilRecovered { controller.getStatus() }
            if (recovered != null) {
                status = recovered
            } else {
                recoveryExhausted = true
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
                    if (!isBusy) {
                        scope.launch {
                            val currentStatus = status
                            runBusy {
                                MonitorActions.applyTarget(
                                    controller = controller,
                                    currentStatus = currentStatus,
                                    targetLayout = preset.layout,
                                    targetPaneSources = preset.paneSources,
                                    targetAudioSource = preset.audioSource
                                )

                                status = controller.getStatus()
                                if (currentStatus != null && ConfigManager.config.enableSafetyCountdown) {
                                    pendingChange = PendingChange(currentStatus, 10)
                                }
                            }
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
                            recoveryExhausted = recoveryExhausted,
                            isBusy = isBusy,
                            onSettingsClick = { isSettingsOpen = true },
                            onConfirmChange = { pendingChange = null },
                            onRevertChange = {
                                val change = pendingChange
                                if (change != null && !isBusy) {
                                    scope.launch {
                                        val currentStatus = status
                                        pendingChange = null

                                        runBusy {
                                            MonitorActions.applyTarget(
                                                controller = controller,
                                                currentStatus = currentStatus,
                                                targetLayout = change.previousStatus.layout,
                                                targetPaneSources = change.previousStatus.paneSources,
                                                targetAudioSource = change.previousStatus.audioSource
                                            )

                                            status = controller.getStatus()
                                        }
                                    }
                                }
                            },
                            onExecuteAction = { safeMode, action ->
                                if (!isBusy) {
                                    scope.launch {
                                        val currentStatus = status
                                        runBusy {
                                            action(currentStatus)
                                            status = controller.getStatus()
                                            if (currentStatus != null && safeMode) {
                                                pendingChange = PendingChange(currentStatus, 15)
                                            }
                                        }
                                    }
                                }
                            },
                            onRefresh = {
                                if (!isBusy) {
                                    scope.launch {
                                        runBusy {
                                            recoveryExhausted = false
                                            val result = controller.getStatus()
                                            status = result
                                            if (result == null) {
                                                recoveryExhausted = true
                                            }
                                        }
                                    }
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
