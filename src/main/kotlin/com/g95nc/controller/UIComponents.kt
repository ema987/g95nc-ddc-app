package com.g95nc.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
    // Single source of truth is the config passed in from above - no separate local copy that
    // can drift from what's actually persisted (and what the Settings modal shows).
    val safeMode = config.enableSafetyCountdown

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dashboard", style = MaterialTheme.typography.h5)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = safeMode,
                    onCheckedChange = { onConfigChange(config.copy(enableSafetyCountdown = it)) }
                )
                Text("Safe Mode", style = MaterialTheme.typography.caption)
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
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        if (pendingChange != null) {
            Card(
                backgroundColor = MaterialTheme.colors.primaryVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keep these display settings?\nReverting in ${pendingChange.countdown}s...",
                        color = Color.White
                    )
                    Row {
                        OutlinedButton(
                            onClick = onRevertChange,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Revert")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmChange,
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White, contentColor = Color.Black)
                        ) {
                            Text("Confirm")
                        }
                    }
                }
            }
        }
        
        if (status == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (recoveryExhausted) {
                        Text(
                            "Couldn't reach the monitor after several attempts.",
                            color = MaterialTheme.colors.error
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
            return
        }

        // Current Layout
        Text("Current Layout: ${status.layout?.description ?: "Unknown (0x${status.layoutStatusRaw.toString(16)})"}")
        Spacer(modifier = Modifier.height(16.dp))

        // Controls
        Text("Change Layout:", fontWeight = FontWeight.Bold)
        var expandedLayout by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { expandedLayout = true }, enabled = !isBusy) {
                Text(status.layout?.description ?: "Select Layout")
            }
            DropdownMenu(expanded = expandedLayout, onDismissRequest = { expandedLayout = false }) {
                Layout.entries.forEach { layout ->
                    DropdownMenuItem(onClick = {
                        expandedLayout = false
                        onExecuteAction(safeMode) { _ ->
                            controller.setLayout(layout)
                        }
                    }) {
                        Text(layout.description)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Source Routing:", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val is3Way = status.layout?.name?.startsWith("PBP_3") == true
            val is2WayOrPip = status.layout?.name?.startsWith("PBP_2") == true || status.layout == Layout.PIP

            PaneSourceSelector(if (is3Way || is2WayOrPip) "Left" else "Main", PaneChannel.PANE_1, controller, status, isBusy) { action -> onExecuteAction(safeMode, action) }

            if (is3Way) {
                PaneSourceSelector("Middle", PaneChannel.PANE_2, controller, status, isBusy) { action -> onExecuteAction(safeMode, action) }
                PaneSourceSelector("Right", PaneChannel.PANE_3, controller, status, isBusy) { action -> onExecuteAction(safeMode, action) }
            } else if (is2WayOrPip) {
                PaneSourceSelector("Right/Sub", PaneChannel.PANE_2, controller, status, isBusy) { action -> onExecuteAction(safeMode, action) }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        // Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Presets", style = MaterialTheme.typography.h6)
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

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(config.presets, key = { it.id }) { preset ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 4.dp) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(preset.name, fontWeight = FontWeight.Bold)
                                Text(preset.layout.description, style = MaterialTheme.typography.caption)
                                Text(formatPresetSources(preset), style = MaterialTheme.typography.caption)
                            }
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
                                onClick = { onConfigChange(config.copy(presets = config.presets.withoutPreset(preset.id))) },
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
                }
            }
        }
    }
}

@Composable
fun PaneSourceSelector(
    label: String,
    channel: PaneChannel,
    controller: MonitorController,
    status: MonitorStatus,
    isBusy: Boolean,
    onExecuteAction: (suspend (MonitorStatus?) -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentSource = status.paneSources[channel]

    Column {
        Text(label, style = MaterialTheme.typography.caption)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !isBusy) {
                Text(currentSource?.description ?: "Select Source")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SourceCode.entries.filter { it != currentSource }.forEach { src ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        onExecuteAction { _ ->
                            if (status.layout == Layout.OFF) {
                                controller.setMainSource(src)
                            } else {
                                controller.setSource(channel, src)
                            }
                        }
                    }) {
                        Text(src.description)
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWizard(onOpenSettings: () -> Unit, onCheckAgain: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val os = System.getProperty("os.name").lowercase()
        Text("Required Tools Not Found", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.error)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (os.contains("mac")) {
            Text("This app requires 'betterdisplaycli' on macOS.")
            Text("You can install it via Homebrew: 'brew install betterdisplaycli' or configure its path if already installed.")
        } else if (os.contains("nix") || os.contains("nux")) {
            Text("This app requires 'ddcutil' on Linux.")
            Text("Please install it via your package manager (e.g. 'sudo apt install ddcutil') or configure its path.")
        } else if (os.contains("win")) {
            Text("Windows native tools failed to load.")
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
fun SettingsModal(config: AppConfig, onSave: (AppConfig) -> Unit) {
    var ddcPath by remember { mutableStateOf(config.ddcutilPath) }
    var bdPath by remember { mutableStateOf(config.betterdisplaycliPath) }
    var enableCountdown by remember { mutableStateOf(config.enableSafetyCountdown) }

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = ddcPath,
            onValueChange = { ddcPath = it },
            label = { Text("Linux: ddcutil executable path") },
            modifier = Modifier.fillMaxWidth().testTag("ddcutilPathField")
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = bdPath,
            onValueChange = { bdPath = it },
            label = { Text("macOS: betterdisplaycli executable path") },
            modifier = Modifier.fillMaxWidth().testTag("betterdisplaycliPathField")
        )
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
                    enableSafetyCountdown = enableCountdown
                ))
            }) {
                Text("Save Configuration")
            }
        }
    }
}
