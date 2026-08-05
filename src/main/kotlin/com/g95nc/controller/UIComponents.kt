package com.g95nc.controller

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun Dashboard(
    status: MonitorStatus?,
    controller: MonitorController,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSavingPreset by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("G95NC Control Dashboard", style = MaterialTheme.typography.h5)
            Row {
                Button(onClick = onRefresh, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Refresh")
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Filled.Settings, "Settings")
                }
            }
        }
        Divider(modifier = Modifier.padding(vertical = 16.dp))
        
        if (status == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading status or unable to read from monitor...")
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
            OutlinedButton(onClick = { expandedLayout = true }) {
                Text(status.layout?.description ?: "Select Layout")
            }
            DropdownMenu(expanded = expandedLayout, onDismissRequest = { expandedLayout = false }) {
                Layout.entries.forEach { layout ->
                    DropdownMenuItem(onClick = {
                        expandedLayout = false
                        scope.launch {
                            controller.setLayout(layout)
                            onRefresh()
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
            
            PaneSourceSelector(if (is3Way || is2WayOrPip) "Left" else "Main", PaneChannel.PANE_1, controller, status, onRefresh)
            
            if (is3Way) {
                PaneSourceSelector("Middle", PaneChannel.PANE_2, controller, status, onRefresh)
                PaneSourceSelector("Right", PaneChannel.PANE_3, controller, status, onRefresh)
            } else if (is2WayOrPip) {
                PaneSourceSelector("Right/Sub", PaneChannel.PANE_2, controller, status, onRefresh)
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
            Button(onClick = { isSavingPreset = true }) {
                Text("Save Current as Preset")
            }
        }

        if (isSavingPreset) {
            Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name") },
                    modifier = Modifier.weight(1f)
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
                        val updatedConfig = ConfigManager.config.copy(
                            presets = ConfigManager.config.presets + newPreset
                        )
                        ConfigManager.updateConfig(updatedConfig)
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
            items(ConfigManager.config.presets) { preset ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 4.dp) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(preset.name, fontWeight = FontWeight.Bold)
                            Text(preset.layout.description, style = MaterialTheme.typography.caption)
                        }
                        Button(onClick = {
                            scope.launch {
                                controller.setLayout(preset.layout)
                                preset.paneSources.forEach { (channel, source) ->
                                    controller.setSource(channel, source)
                                }
                                if (preset.audioSource != null) {
                                    controller.setAudioSource(preset.audioSource)
                                }
                                onRefresh()
                            }
                        }) {
                            Text("Apply")
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
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val currentSource = status.paneSources[channel]
    
    Column {
        Text(label, style = MaterialTheme.typography.caption)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(currentSource?.description ?: "Select Source")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SourceCode.entries.filter { it != currentSource }.forEach { src ->
                    DropdownMenuItem(onClick = {
                        expanded = false
                        scope.launch {
                            controller.setSource(channel, src)
                            onRefresh()
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

    Column(modifier = Modifier.padding(24.dp).fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(24.dp))
        
        OutlinedTextField(
            value = ddcPath,
            onValueChange = { ddcPath = it },
            label = { Text("Linux: ddcutil executable path") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = bdPath,
            onValueChange = { bdPath = it },
            label = { Text("macOS: betterdisplaycli executable path") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {
                onSave(config.copy(ddcutilPath = ddcPath, betterdisplaycliPath = bdPath))
            }) {
                Text("Save Configuration")
            }
        }
    }
}
