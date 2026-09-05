package com.g95nc.controller

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Icon standing in for a source's physical port - a genuine visual distinction, not decoration. */
fun sourceIcon(source: SourceCode): ImageVector = when (source) {
    SourceCode.HDMI1, SourceCode.HDMI2, SourceCode.HDMI3 -> Icons.Default.SettingsInputHdmi
    SourceCode.DP -> Icons.Default.Cable
}

/** Roughly how wide each pane is relative to its neighbors, for the schematic only - not a
 * precise physical measurement, just enough to make "large-left" read as large-left. */
private val layoutPaneWeights: Map<Layout, List<Float>> = mapOf(
    Layout.PBP_2_LARGE_LEFT to listOf(2f, 1f),
    Layout.PBP_2_50_50 to listOf(1f, 1f),
    Layout.PBP_2_21_9_LEFT to listOf(1.4f, 1f),
    Layout.PBP_2_UNEVEN to listOf(1.3f, 1f),
    Layout.PBP_2_LAST to listOf(1f, 1f),
    Layout.PBP_3_SQUARE_CENTER to listOf(1f, 1f, 1f),
    Layout.PBP_3_50_25_25 to listOf(2f, 1f, 1f),
    Layout.PBP_3_25_50_25 to listOf(1f, 2f, 1f),
    Layout.PBP_3_25_25_50 to listOf(1f, 1f, 2f)
)

/** The live picture of how the screen is currently split - the anchor of the whole dashboard.
 * Each pane is clickable to change its routed source directly, instead of a separate list of
 * source pickers below. */
@Composable
fun LayoutDiagram(
    status: MonitorStatus,
    controller: MonitorController,
    isBusy: Boolean,
    onExecuteAction: (suspend (MonitorStatus?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val layout = status.layout
    val paneSources = status.paneSources

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        val slots = paneSlotsFor(layout)

        fun setSource(channel: PaneChannel, src: SourceCode) {
            onExecuteAction { _ ->
                if (layout == Layout.OFF) controller.setMainSource(src) else controller.setSource(channel, src)
            }
        }

        when {
            layout == null || layout == Layout.OFF ->
                PaneCell(
                    slots[0].second, PaneChannel.PANE_1, paneSources[PaneChannel.PANE_1], isBusy,
                    onSelect = { setSource(PaneChannel.PANE_1, it) }, modifier = Modifier.fillMaxSize()
                )

            layout == Layout.PIP -> {
                PaneCell(
                    slots[0].second, PaneChannel.PANE_1, paneSources[PaneChannel.PANE_1], isBusy,
                    onSelect = { setSource(PaneChannel.PANE_1, it) }, modifier = Modifier.fillMaxSize()
                )
                PaneCell(
                    slots.getOrNull(1)?.second ?: "Sub",
                    PaneChannel.PANE_2,
                    paneSources[PaneChannel.PANE_2],
                    isBusy,
                    onSelect = { setSource(PaneChannel.PANE_2, it) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxSize(0.36f)
                        .aspectRatio(16f / 9f)
                        .padding(6.dp),
                    emphasized = true
                )
            }

            layout.name.startsWith("PBP_3") -> {
                val weights = layoutPaneWeights[layout] ?: listOf(1f, 1f, 1f)
                val channels = listOf(PaneChannel.PANE_1, PaneChannel.PANE_2, PaneChannel.PANE_3)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    channels.forEachIndexed { i, channel ->
                        PaneCell(
                            slots[i].second, channel, paneSources[channel], isBusy,
                            onSelect = { setSource(channel, it) }, modifier = Modifier.weight(weights[i]).fillMaxHeight()
                        )
                    }
                }
            }

            else -> {
                val weights = layoutPaneWeights[layout] ?: listOf(1f, 1f)
                val channels = listOf(PaneChannel.PANE_1, PaneChannel.PANE_2)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    channels.forEachIndexed { i, channel ->
                        PaneCell(
                            slots[i].second, channel, paneSources[channel], isBusy,
                            onSelect = { setSource(channel, it) }, modifier = Modifier.weight(weights[i]).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneCell(
    label: String,
    channel: PaneChannel,
    source: SourceCode?,
    isBusy: Boolean,
    onSelect: (SourceCode) -> Unit,
    modifier: Modifier,
    emphasized: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (emphasized) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            )
            .border(
                width = 1.dp,
                color = if (source != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = !isBusy) { expanded = true }
            .testTag("paneCell_${channel.name}"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = source?.let { sourceIcon(it) } ?: Icons.Default.QuestionMark,
                contentDescription = null,
                tint = if (source != null) AppColors.signalActive else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                source?.description ?: "No signal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SourceCode.entries.filter { it != source }.forEach { src ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(sourceIcon(src), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(src.description)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(src)
                    }
                )
            }
        }
    }
}

/** A tiny bar-schematic of a layout's pane split, used next to its name in the layout picker so
 * choosing a layout is a visual pick, not just reading text descriptions. */
@Composable
fun LayoutGlyph(layout: Layout, modifier: Modifier = Modifier) {
    if (layout == Layout.PIP) {
        Box(
            modifier
                .width(28.dp).height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(1.dp)
                    .size(10.dp, 7.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        return
    }

    val weights = when {
        layout == Layout.OFF -> listOf(1f)
        layout.name.startsWith("PBP_3") -> layoutPaneWeights[layout] ?: listOf(1f, 1f, 1f)
        else -> layoutPaneWeights[layout] ?: listOf(1f, 1f)
    }
    Row(modifier.width(28.dp).height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        weights.forEach { w ->
            Box(
                Modifier
                    .weight(w)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            )
        }
    }
}

/** Small icon+label chip for one pane's routed source, used in preset rows. */
@Composable
fun SourceChip(label: String, source: SourceCode?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = source?.let { sourceIcon(it) } ?: Icons.Default.QuestionMark,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text("$label: ${source?.description ?: "—"}", style = MaterialTheme.typography.labelSmall)
    }
}

/** Small static rendering of a preset's pane split, shown next to its name in the presets list -
 * the same visual grammar as the live [LayoutDiagram], just shrunk down and non-interactive. */
@Composable
fun PresetThumbnail(preset: Preset, modifier: Modifier = Modifier) {
    val layout = preset.layout
    val sources = preset.paneSources

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .padding(3.dp)
    ) {
        when {
            layout == Layout.PIP -> {
                ThumbnailCell(sources[PaneChannel.PANE_1], Modifier.fillMaxSize())
                ThumbnailCell(
                    sources[PaneChannel.PANE_2],
                    Modifier.align(Alignment.BottomEnd).fillMaxSize(0.36f).aspectRatio(16f / 9f).padding(2.dp),
                    emphasized = true
                )
            }

            layout.name.startsWith("PBP_3") -> {
                val weights = layoutPaneWeights[layout] ?: listOf(1f, 1f, 1f)
                val channels = listOf(PaneChannel.PANE_1, PaneChannel.PANE_2, PaneChannel.PANE_3)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    channels.forEachIndexed { i, channel ->
                        ThumbnailCell(sources[channel], Modifier.weight(weights[i]).fillMaxHeight())
                    }
                }
            }

            layout == Layout.OFF ->
                ThumbnailCell(sources[PaneChannel.PANE_1], Modifier.fillMaxSize())

            else -> {
                val weights = layoutPaneWeights[layout] ?: listOf(1f, 1f)
                val channels = listOf(PaneChannel.PANE_1, PaneChannel.PANE_2)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    channels.forEachIndexed { i, channel ->
                        ThumbnailCell(sources[channel], Modifier.weight(weights[i]).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCell(source: SourceCode?, modifier: Modifier, emphasized: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(
                if (emphasized) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = source?.let { sourceIcon(it) } ?: Icons.Default.QuestionMark,
            contentDescription = null,
            tint = if (source != null) AppColors.signalActive else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(10.dp)
        )
    }
}
