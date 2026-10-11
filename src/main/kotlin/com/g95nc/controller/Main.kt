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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

private val singleInstanceLock = SingleInstanceLock(
    java.io.File(System.getProperty("user.home"), ".config/g95nc-controller/app.lock")
)

// Set by the tray / menu bar menu's "About" item - the only way to reach it on every OS, since the
// app has no macOS app menu (it runs as a menu-bar-only accessory app there, see main()).
private var showAboutDialog by mutableStateOf(false)

/** Caps a desired window height to the actual screen so it's never taller than the viewport on a
 * small display; anything beyond that is still reachable via the window's scrollable content. */
private fun capToScreenHeight(desired: Dp): Dp {
    val usableScreenHeight = try {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds.height.dp
    } catch (e: Exception) {
        return desired
    }
    return minOf(desired, usableScreenHeight - 40.dp)
}

/** A height that comfortably fits the dashboard's content (diagram + layout picker + a couple of
 * presets) without the large empty margin a fixed tall default leaves once the diagram is small.
 * Anything beyond this (many presets, the rename/save-preset fields) is still reachable via the
 * existing scrollable content area. Audio source selection lives on the pane cells themselves
 * now, not a separate section, so this doesn't need to budget extra height for it. */
private fun preferredDashboardHeight(): Dp = capToScreenHeight(700.dp)

/** A height that comfortably fits the Settings content (path field(s) + appearance + safety
 * countdown checkbox + macOS's start-at-login checkbox + collapsed debug toggle + Save button -
 * ~496dp of content on macOS, the tallest case) with a little breathing room below
 * the Save button - tuned by hand against the actual UI, not measured at runtime (an
 * intrinsic-measurement-based auto-size was tried and worked on the dev machine but not on
 * another, so this is a plain constant instead). Bump this if Settings grows a new field/section;
 * anything beyond it is still reachable via the window's scrollable content. */
private fun preferredSettingsHeight(): Dp = capToScreenHeight(570.dp)

/** macOS draws each window's title bar chrome itself, entirely outside Compose - without this
 * hint it just follows the OS's own current appearance regardless of what [AppTheme] resolved to,
 * which reads as broken the moment the two disagree (Dark/Light forced explicitly, or Follow
 * System resolving differently than whatever the OS's setting is when the window happens to
 * open). `ComposeWindow` is a `JFrame` under the hood, so this is the same Swing/AWT client
 * property JetBrains' own IDEs use for the same purpose. */
@Composable
private fun WindowScope.syncTitleBarAppearance(isDark: Boolean) {
    if (Platform.detect() != Platform.MAC) return
    SideEffect {
        (window as? javax.swing.JFrame)?.rootPane?.putClientProperty(
            "apple.awt.windowAppearance",
            if (isDark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua"
        )
    }
}

/** On macOS the app is a menu-bar-only "accessory" app (no Dock icon, see main()), so it isn't the
 * active app when one of its windows opens from the menu bar - without this, the window can open
 * behind whatever app is in front. Harmless elsewhere. */
@Composable
private fun WindowScope.bringToFrontOnOpen() {
    LaunchedEffect(Unit) {
        try {
            val desktop = java.awt.Desktop.getDesktop()
            if (desktop.isSupported(java.awt.Desktop.Action.APP_REQUEST_FOREGROUND)) {
                desktop.requestForeground(true)
            }
        } catch (e: Exception) {
            // Best effort - toFront() below still covers most cases.
        }
        window.toFront()
        window.requestFocus()
    }
}

/** Command-line flag to start in the tray without opening the dashboard - used by Linux autostart
 * (start-on-login.sh), so logging in doesn't pop a window up. */
private const val BACKGROUND_FLAG = "--background"

fun main(args: Array<String>) {
    // Must be set (and the Dock icon applied) before any AWT/Swing/Compose window is created -
    // macOS otherwise shows the JVM's main class name ("MainKt") and the default Java icon for a
    // plain, unpackaged java process.
    System.setProperty("apple.awt.application.name", AppInfo.NAME)
    // macOS: run as a menu-bar-only accessory app (LSUIElement) - no Dock icon or Cmd+Tab entry,
    // since the menu bar icon already gives access to everything, About and Quit included.
    if (Platform.detect() == Platform.MAC) {
        System.setProperty("apple.awt.UIElement", "true")
    }
    applyRuntimeDockIcon()

    // Two instances polling the same ddcutil/betterdisplaycli channel contend with each other
    // and produce the exact DDC/IPC timeouts this app exists to avoid - so only one may run.
    if (!singleInstanceLock.acquire()) {
        singleWindowApplication(
            title = AppInfo.NAME,
            icon = AppIconPainter,
            state = WindowState(width = 420.dp, height = 220.dp),
            resizable = false
        ) {
            AppTheme(themeMode = ConfigManager.config.themeMode) { isDark ->
                syncTitleBarAppearance(isDark)
                bringToFrontOnOpen()
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
        runMainApplication(startInBackground = BACKGROUND_FLAG in args)
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
        AppTheme(themeMode = themeMode) { isDark ->
            syncTitleBarAppearance(isDark)
            bringToFrontOnOpen()
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
private fun ConfigWarningDialog(message: String, onDismiss: () -> Unit, themeMode: ThemeMode) {
    Window(
        onCloseRequest = onDismiss,
        title = "Configuration Warning",
        icon = AppIconPainter,
        state = rememberWindowState(width = 420.dp, height = 240.dp),
        resizable = false
    ) {
        AppTheme(themeMode = themeMode) { isDark ->
            syncTitleBarAppearance(isDark)
            bringToFrontOnOpen()
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Configuration Warning", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
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
private fun ApplicationScope.runMainApplication(startInBackground: Boolean = false) {
    val macLoginItem = remember { if (Platform.detect() == Platform.MAC) MacLoginItem() else null }
    // If start-at-login is on but the app was moved since, point it at the new location.
    LaunchedEffect(macLoginItem) {
        try {
            macLoginItem?.refresh()
        } catch (e: Exception) {
            // Best effort - the Settings toggle surfaces real write failures.
        }
    }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isDashboardOpen by remember { mutableStateOf(!startInBackground) }
    
    val config = ConfigManager.config
    var controller by remember { mutableStateOf(MonitorController.create(config)) }
    var isToolAvailable by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<MonitorStatus?>(null) }
    var pendingChange by remember { mutableStateOf<PendingChange?>(null) }
    var recoveryExhausted by remember { mutableStateOf(false) }
    // True while a monitor action is in flight. The dashboard stays showing the last known
    // status during this (instead of blanking to a full-page spinner) and disables its controls.
    var isBusy by remember { mutableStateOf(false) }
    // A write that reported failure even though a subsequent status read still succeeded (so
    // recoveryExhausted never fires) would otherwise be completely silent - see Dashboard's banner.
    var actionFailureMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val trayState = rememberTrayState()

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

    // Auto-dismiss the transient action-failure banner - it's meant to catch the user's attention
    // right after a failed write, not linger indefinitely.
    LaunchedEffect(actionFailureMessage) {
        if (actionFailureMessage != null) {
            delay(6000)
            actionFailureMessage = null
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
                    // Audio is deliberately left out here - it no longer goes through this
                    // pendingChange/Safe Mode machinery at all (see onExecuteAudioAction), so
                    // reverting a layout/source change must not also stomp on an audio switch the
                    // user made in the meantime, which previously-recorded previousStatus knows
                    // nothing about.
                    val succeeded = MonitorActions.applyTarget(
                        controller = controller,
                        currentStatus = currentStatus,
                        targetLayout = change.previousStatus.layout,
                        targetPaneSources = change.previousStatus.paneSources
                    )
                    if (!succeeded) {
                        actionFailureMessage = LastControllerError.message ?: "reverting to the previous layout may not have applied."
                    }

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

    val applyPreset: (Preset) -> Unit = { preset ->
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

                    val newStatus = controller.getStatus()
                    status = newStatus

                    // Applying from the tray has no window open to show the result in,
                    // so confirm it via a system notification instead - the same
                    // matchesStatus() check that drives the "Active" badge decides
                    // whether the readback actually landed on the target.
                    trayState.sendNotification(
                        if (newStatus != null && preset.matchesStatus(newStatus)) {
                            Notification(AppInfo.NAME, "Applied preset \"${preset.name}\"", Notification.Type.Info)
                        } else {
                            Notification(AppInfo.NAME, "Couldn't confirm preset \"${preset.name}\" was applied", Notification.Type.Warning)
                        }
                    )

                    if (currentStatus != null && ConfigManager.config.enableSafetyCountdown) {
                        pendingChange = PendingChange(currentStatus, 10)
                    }
                }
            }
        }
    }

    if (Platform.detect() == Platform.LINUX) {
        LinuxTray(
            onDashboardOpenChange = { isDashboardOpen = it },
            onSettingsOpenChange = { isSettingsOpen = it },
            presets = config.presets,
            isBusy = isBusy,
            onApplyPreset = applyPreset,
            onAboutClick = { showAboutDialog = true },
            onQuit = ::exitApplication
        )
    } else {
        Tray(
            icon = TrayIcon,
            state = trayState,
            onAction = { isDashboardOpen = true },
            menu = {
                Item("Open Dashboard", onClick = { isDashboardOpen = true })
                Item("Settings", onClick = { isSettingsOpen = true })
                Separator()
                // Presets
                config.presets.forEach { preset ->
                    Item("Preset: ${preset.name}", onClick = { applyPreset(preset) })
                }
                Separator()
                Item("About", onClick = { showAboutDialog = true })
                Item("Quit", onClick = ::exitApplication)
            }
        )
    }

    if (isDashboardOpen) {
        val dashboardWindowState = rememberWindowState(
            size = DpSize(
                config.dashboardWindowWidth?.dp ?: 680.dp,
                config.dashboardWindowHeight?.dp ?: preferredDashboardHeight()
            ),
            position = if (config.dashboardWindowX != null && config.dashboardWindowY != null) {
                WindowPosition.Absolute(config.dashboardWindowX.dp, config.dashboardWindowY.dp)
            } else {
                WindowPosition.PlatformDefault
            }
        )

        // Debounced rather than saved only on close - the app can also quit from the tray while
        // this window is still open, which wouldn't otherwise get a chance to persist a resize.
        LaunchedEffect(dashboardWindowState.size, dashboardWindowState.position) {
            delay(500)
            val pos = dashboardWindowState.position
            if (pos is WindowPosition.Absolute) {
                ConfigManager.updateConfig(
                    ConfigManager.config.copy(
                        dashboardWindowWidth = dashboardWindowState.size.width.value,
                        dashboardWindowHeight = dashboardWindowState.size.height.value,
                        dashboardWindowX = pos.x.value,
                        dashboardWindowY = pos.y.value
                    )
                )
            }
        }

        Window(
            onCloseRequest = { isDashboardOpen = false },
            title = AppInfo.NAME,
            icon = AppIconPainter,
            state = dashboardWindowState
        ) {
            AppTheme(themeMode = config.themeMode) { isDark ->
                syncTitleBarAppearance(isDark)
                bringToFrontOnOpen()
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
                                            // See the auto-revert effect above - audio is
                                            // intentionally excluded from what gets reverted here.
                                            val succeeded = MonitorActions.applyTarget(
                                                controller = controller,
                                                currentStatus = currentStatus,
                                                targetLayout = change.previousStatus.layout,
                                                targetPaneSources = change.previousStatus.paneSources
                                            )
                                            if (!succeeded) {
                                                actionFailureMessage = LastControllerError.message ?: "reverting to the previous layout may not have applied."
                                            }

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
                                            val succeeded = action(currentStatus)
                                            status = controller.getStatus()
                                            if (succeeded) {
                                                if (currentStatus != null && safeMode) {
                                                    pendingChange = PendingChange(currentStatus, 15)
                                                }
                                            } else {
                                                actionFailureMessage = LastControllerError.message ?: "the change may not have applied."
                                            }
                                        }
                                    }
                                }
                            },
                            actionFailureMessage = actionFailureMessage,
                            onDismissActionFailure = { actionFailureMessage = null },
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
            state = rememberWindowState(width = 500.dp, height = preferredSettingsHeight())
        ) {
            AppTheme(themeMode = config.themeMode) { isDark ->
                syncTitleBarAppearance(isDark)
                bringToFrontOnOpen()
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsModal(
                        config = config,
                        onSave = { newConfig ->
                            ConfigManager.updateConfig(newConfig)
                            controller = MonitorController.create(newConfig)
                            isSettingsOpen = false
                        },
                        controller = controller,
                        loginItem = macLoginItem
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false }, themeMode = config.themeMode)
    }

    ConfigManager.loadWarning?.let { warning ->
        ConfigWarningDialog(
            message = warning,
            onDismiss = { ConfigManager.dismissLoadWarning() },
            themeMode = config.themeMode
        )
    }
}
