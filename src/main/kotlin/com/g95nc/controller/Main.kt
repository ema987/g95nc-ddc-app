package com.g95nc.controller

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

private val singleInstanceLock = SingleInstanceLock(
    java.io.File(System.getProperty("user.home"), ".config/g95nc-controller/app.lock")
)

// Set by the macOS "About G95NC Controller" menu item's handler (registered in main(), fires on
// an AWT/AppKit callback outside Compose's composition) so runMainApplication() can show it.
private var showAboutDialog by mutableStateOf(false)

/** A height that comfortably fits the dashboard's content (diagram + layout picker + a couple of
 * presets) without the large empty margin a fixed tall default leaves once the diagram is small -
 * capped to the actual screen so it's never taller than the viewport on a small display. Anything
 * beyond this (many presets, the rename/save-preset fields) is still reachable via the existing
 * scrollable content area. */
private fun preferredDashboardHeight(): Dp {
    val desired = 700.dp
    val usableScreenHeight = try {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height.dp
    } catch (e: Exception) {
        return desired
    }
    return minOf(desired, usableScreenHeight - 40.dp)
}

fun main() {
    // Must be set (and the Dock icon applied) before any AWT/Swing/Compose window is created -
    // macOS otherwise shows the JVM's main class name ("MainKt") and the default Java icon for a
    // plain, unpackaged java process.
    System.setProperty("apple.awt.application.name", AppInfo.NAME)
    applyRuntimeDockIcon()
    registerAboutHandler { showAboutDialog = true }

    // Two instances polling the same ddcutil/betterdisplaycli channel contend with each other
    // and produce the exact DDC/IPC timeouts this app exists to avoid - so only one may run.
    if (!singleInstanceLock.acquire()) {
        singleWindowApplication(
            title = AppInfo.NAME,
            icon = AppIconPainter,
            state = WindowState(width = 420.dp, height = 220.dp),
            resizable = false
        ) {
            AppTheme(themeMode = ConfigManager.config.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Already Running", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${AppInfo.NAME} is already running. Check your menu bar / system tray.",
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = {
                            window.dispatchEvent(java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING))
                        }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
        return
    }

    application {
        runMainApplication()
    }
}

/** Registers a custom macOS "About G95NC Controller" panel in place of the generic default Java
 * one. Best-effort: platforms/JDKs without Desktop.Action.APP_ABOUT just keep no About item. */
private fun registerAboutHandler(onAboutRequested: () -> Unit) {
    try {
        if (java.awt.Desktop.isDesktopSupported()) {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_ABOUT)) {
                desktop.setAboutHandler { onAboutRequested() }
            }
        }
    } catch (e: Exception) {
        // Cosmetic only - never worth blocking startup over.
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, themeMode: ThemeMode) {
    Window(
        onCloseRequest = onDismiss,
        title = "About ${AppInfo.NAME}",
        icon = AppIconPainter,
        state = rememberWindowState(width = 360.dp, height = 280.dp),
        resizable = false
    ) {
        AppTheme(themeMode = themeMode) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painter = AppIconPainter, contentDescription = null, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(AppInfo.NAME, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Version ${AppInfo.VERSION}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun ApplicationScope.runMainApplication() {
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
            title = AppInfo.NAME,
            icon = AppIconPainter,
            state = rememberWindowState(width = 680.dp, height = preferredDashboardHeight())
        ) {
            AppTheme(themeMode = config.themeMode) {
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
                            config = config,
                            onConfigChange = { ConfigManager.updateConfig(it) },
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
            icon = AppIconPainter,
            state = rememberWindowState(width = 500.dp, height = 400.dp)
        ) {
            AppTheme(themeMode = config.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
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

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false }, themeMode = config.themeMode)
    }
}
