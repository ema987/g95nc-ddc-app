package com.g95nc.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun Dashboard(
    status: MonitorStatus?,
    controller: MonitorController,
    pendingChange: PendingChange?,
    recoveryExhausted: Boolean,
    isBusy: Boolean,
    config: AppConfig,
    onConfigChange: (AppConfig) -> Unit,
    onSettingsClick: () -> Unit,
    onConfirmChange: () -> Unit,
    onRevertChange: () -> Unit,
    onExecuteAction: (Boolean, suspend (MonitorStatus?) -> Unit) -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSavingPreset by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var renamingPresetId by remember { mutableStateOf<String?>(null) }
    var renamePresetText by remember { mutableStateOf("") }
    var presetPendingDelete by remember { mutableStateOf<Preset?>(null) }
    // Single source of truth is the config passed in from above - no separate local copy that
    // can drift from what's actually persisted (and what the Settings modal shows).
    val safeMode = config.enableSafetyCountdown

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("G95NC", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = safeMode,
                    onCheckedChange = { onConfigChange(config.copy(enableSafetyCountdown = it)) }
                )
                Text("Safe Mode", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onRefresh, enabled = !isBusy) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (pendingChange != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keep these display settings?\nReverting in ${pendingChange.countdown}s...",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row {
                        OutlinedButton(onClick = onRevertChange) {
                            Text("Revert")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onConfirmChange) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }

        // status == null gets a fixed-height centered box (not scrollable content) so the loading/
        // give-up message actually centers in the window instead of wrapping to the top of an
        // unbounded-height scrollable area.
        if (status == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (recoveryExhausted) {
                        Text(
                            "Couldn't reach the monitor after several attempts.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("Try Again")
                        }
                    } else {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading status or waiting for the monitor to respond...")
                    }
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(modifier = Modifier.height(20.dp))

                // The picture of how the screen is currently split - the anchor of the dashboard,
                // read before any of the text controls below it. Kept compact (not full-width) and
                // clickable per pane, so it doubles as the source picker instead of a separate list.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LayoutDiagram(
                        status = status,
                        controller = controller,
                        isBusy = isBusy,
                        onExecuteAction = { action -> onExecuteAction(safeMode, action) },
                        modifier = Modifier.fillMaxWidth(0.55f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    status.layout?.description ?: "Unknown (0x${status.layoutStatusRaw.toString(16)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))
                Text("Change layout", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                var expandedLayout by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expandedLayout = true }, enabled = !isBusy) {
                        LayoutGlyph(status.layout ?: Layout.OFF)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(status.layout?.description ?: "Select Layout")
                    }
                    DropdownMenu(expanded = expandedLayout, onDismissRequest = { expandedLayout = false }) {
                        Layout.entries.forEach { layout ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        LayoutGlyph(layout)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(layout.description)
                                    }
                                },
                                onClick = {
                                    expandedLayout = false
                                    onExecuteAction(safeMode) { _ ->
                                        controller.setLayout(layout)
                                    }
                                }
                            )
                        }
                    }
                }

                AudioSourceSelector(status, controller, isBusy) { action -> onExecuteAction(safeMode, action) }

                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Presets", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { isSavingPreset = true }, enabled = !isBusy) {
                        Text("Save Current as Preset")
                    }
                }

                if (isSavingPreset) {
                    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            label = { Text("Preset Name") },
                            modifier = Modifier.weight(1f).testTag("newPresetNameField")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (newPresetName.isNotBlank() && status.layout != null) {
                                val newPreset = Preset(
                                    id = UUID.randomUUID().toString(),
                                    name = newPresetName,
                                    layout = status.layout,
                                    paneSources = status.paneSources, // Needs accurate pane sources
                                    audioSource = status.audioSource
                                )
                                onConfigChange(config.copy(presets = config.presets + newPreset))
                            }
                            isSavingPreset = false
                            newPresetName = ""
                        }) {
                            Text("Save")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (config.presets.isEmpty()) {
                    Text(
                        "No presets yet - save your current layout above to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    config.presets.forEachIndexed { index, preset ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (renamingPresetId != preset.id) {
                                    Column {
                                        IconButton(
                                            onClick = { onConfigChange(config.copy(presets = config.presets.movedUp(preset.id))) },
                                            enabled = !isBusy && index > 0,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move preset up", modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(
                                            onClick = { onConfigChange(config.copy(presets = config.presets.movedDown(preset.id))) },
                                            enabled = !isBusy && index < config.presets.lastIndex,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move preset down", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                if (renamingPresetId == preset.id) {
                                    OutlinedTextField(
                                        value = renamePresetText,
                                        onValueChange = { renamePresetText = it },
                                        modifier = Modifier.weight(1f).padding(end = 8.dp).testTag("renamePresetField"),
                                        singleLine = true
                                    )
                                    IconButton(onClick = {
                                        if (renamePresetText.isNotBlank()) {
                                            onConfigChange(config.copy(presets = config.presets.renamed(preset.id, renamePresetText)))
                                        }
                                        renamingPresetId = null
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "Confirm rename")
                                    }
                                    IconButton(onClick = { renamingPresetId = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel rename")
                                    }
                                } else {
                                    Column(modifier = Modifier.padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                                            if (preset.matchesStatus(status)) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        "Active",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            preset.layout.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    PresetThumbnail(preset, modifier = Modifier.width(56.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            renamePresetText = preset.name
                                            renamingPresetId = preset.id
                                        },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename preset")
                                    }
                                    IconButton(
                                        onClick = { presetPendingDelete = preset },
                                        enabled = !isBusy
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete preset")
                                    }
                                    Button(
                                        onClick = {
                                            onExecuteAction(safeMode) { currentStatus ->
                                                MonitorActions.applyTarget(
                                                    controller = controller,
                                                    currentStatus = currentStatus,
                                                    targetLayout = preset.layout,
                                                    targetPaneSources = preset.paneSources,
                                                    targetAudioSource = preset.audioSource
                                                )
                                            }
                                        },
                                        enabled = !isBusy
                                    ) {
                                        Text("Apply")
                                    }
                                }
                            }
                            if (renamingPresetId != preset.id) {
                                val slots = paneSlotsFor(preset.layout)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    slots.forEach { (channel, label) ->
                                        SourceChip(label, preset.paneSources[channel])
                                    }
                                    if (slots.size > 1) {
                                        slots.firstOrNull { it.first.channel == preset.audioSource }?.let { (_, label) ->
                                            AudioChip(label)
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    presetPendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { presetPendingDelete = null },
            title = { Text("Delete preset?") },
            text = { Text("\"${preset.name}\" will be removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onConfigChange(config.copy(presets = config.presets.withoutPreset(preset.id)))
                    presetPendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/** PBP/PIP audio only has one "which pane are we listening to" control (VCP 0xE8), not a
 * per-pane one - so unlike layout/pane sources, this is a single picker, and only meaningful once
 * there's more than one pane to choose between. */
@Composable
private fun AudioSourceSelector(
    status: MonitorStatus,
    controller: MonitorController,
    isBusy: Boolean,
    onExecuteAction: (suspend (MonitorStatus?) -> Unit) -> Unit
) {
    val slots = paneSlotsFor(status.layout)
    if (slots.size < 2) return

    var expanded by remember { mutableStateOf(false) }
    val currentLabel = slots.firstOrNull { it.first.channel == status.audioSource }?.second
        ?: "Unknown (0x${status.audioSource.toString(16)})"

    Spacer(modifier = Modifier.height(20.dp))
    Text("Audio source", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = !isBusy,
            modifier = Modifier.testTag("audioSourceButton")
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(currentLabel)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            slots.forEach { (channel, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    modifier = Modifier.testTag("audioSourceOption_${channel.name}"),
                    onClick = {
                        expanded = false
                        onExecuteAction { _ -> controller.setAudioSource(channel.channel) }
                    }
                )
            }
        }
    }
}

@Composable
fun OnboardingWizard(
    onOpenSettings: () -> Unit,
    onCheckAgain: () -> Unit,
    platform: Platform = Platform.detect()
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Required Tools Not Found", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))

        when (platform) {
            Platform.MAC -> {
                Text("This app requires 'betterdisplaycli' on macOS.")
                Text("You can install it via Homebrew: 'brew install betterdisplaycli' or configure its path if already installed.")
            }
            Platform.LINUX -> {
                Text("This app requires 'ddcutil' on Linux.")
                Text("Please install it via your package manager (e.g. 'sudo apt install ddcutil') or configure its path.")
            }
            Platform.WINDOWS -> {
                Text("Windows native tools failed to load.")
            }
            Platform.OTHER -> {}
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row {
            OutlinedButton(onClick = onCheckAgain) {
                Text("Check Again")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
fun SettingsModal(
    config: AppConfig,
    onSave: (AppConfig) -> Unit,
    commandRunner: CommandRunner = ProcessUtils,
    platform: Platform = Platform.detect()
) {
    var ddcPath by remember { mutableStateOf(config.ddcutilPath) }
    var bdPath by remember { mutableStateOf(config.betterdisplaycliPath) }
    var enableCountdown by remember { mutableStateOf(config.enableSafetyCountdown) }
    var themeMode by remember { mutableStateOf(config.themeMode) }
    var expandedTheme by remember { mutableStateOf(false) }
    var ddcTestResult by remember { mutableStateOf<Boolean?>(null) }
    var bdTestResult by remember { mutableStateOf<Boolean?>(null) }
    var isTestingDdc by remember { mutableStateOf(false) }
    var isTestingBd by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // Only the tool this platform actually uses makes sense to configure here - showing a
        // "Linux: ddcutil path" field while running on macOS just invites confusion.
        if (platform == Platform.LINUX) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = ddcPath,
                    onValueChange = { ddcPath = it; ddcTestResult = null },
                    label = { Text("ddcutil executable path") },
                    modifier = Modifier.weight(1f).testTag("ddcutilPathField")
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isTestingDdc) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            isTestingDdc = true
                            ddcTestResult = isToolReachable(commandRunner, ddcPath, listOf("--version"))
                            isTestingDdc = false
                        }
                    }) {
                        Text("Test")
                    }
                }
                when (ddcTestResult) {
                    true -> Icon(Icons.Default.CheckCircle, contentDescription = "ddcutil path is reachable", tint = AppColors.signalActive)
                    false -> Icon(Icons.Default.Error, contentDescription = "ddcutil path is not reachable", tint = MaterialTheme.colorScheme.error)
                    null -> {}
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (platform == Platform.MAC) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = bdPath,
                    onValueChange = { bdPath = it; bdTestResult = null },
                    label = { Text("betterdisplaycli executable path") },
                    modifier = Modifier.weight(1f).testTag("betterdisplaycliPathField")
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isTestingBd) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            isTestingBd = true
                            bdTestResult = isToolReachable(commandRunner, bdPath, listOf("-v"))
                            isTestingBd = false
                        }
                    }) {
                        Text("Test")
                    }
                }
                when (bdTestResult) {
                    true -> Icon(Icons.Default.CheckCircle, contentDescription = "betterdisplaycli path is reachable", tint = AppColors.signalActive)
                    false -> Icon(Icons.Default.Error, contentDescription = "betterdisplaycli path is not reachable", tint = MaterialTheme.colorScheme.error)
                    null -> {}
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (platform == Platform.WINDOWS || platform == Platform.OTHER) {
            Text(
                "No additional configuration is needed on this platform.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Appearance", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expandedTheme = true }, modifier = Modifier.testTag("themeModeButton")) {
                Text(themeMode.label)
            }
            DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                ThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label) },
                        onClick = {
                            themeMode = mode
                            expandedTheme = false
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = enableCountdown,
                onCheckedChange = { enableCountdown = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Safety Countdown (auto-revert changes)")
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onSave(config.copy(
                    ddcutilPath = ddcPath,
                    betterdisplaycliPath = bdPath,
                    enableSafetyCountdown = enableCountdown,
                    themeMode = themeMode
                ))
            }) {
                Text("Save Configuration")
            }
        }
    }
}
