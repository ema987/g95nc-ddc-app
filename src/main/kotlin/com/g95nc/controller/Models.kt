package com.g95nc.controller

import kotlinx.serialization.Serializable

@Serializable
enum class Layout(val code: Int, val description: String) {
    OFF(0x00, "Off / Single Source"),
    PBP_2_LAST(0x01, "PBP 2-way (last split)"),
    PBP_2_LARGE_LEFT(0x02, "PBP 2-way (large-left / small-right)"),
    PBP_2_50_50(0x03, "PBP 2-way (50/50)"),
    PBP_2_21_9_LEFT(0x04, "PBP 2-way (21:9 left pane)"),
    PBP_2_UNEVEN(0x05, "PBP 2-way (uneven)"),
    PBP_3_SQUARE_CENTER(0x06, "3PBP (square center)"),
    PBP_3_50_25_25(0x07, "3PBP (50/25/25 large-left)"),
    PBP_3_25_50_25(0x08, "3PBP (25/50/25 large-middle)"),
    PBP_3_25_25_50(0x09, "3PBP (25/25/50 large-right)"),
    PIP(0x10, "PIP");

    companion object {
        fun fromCode(code: Int): Layout? = entries.find { it.code == code }
        
        // E2 status readback returns a 0x39xx status, not the direct code.
        // Formula: low byte is 2*command - 5 for 3PBP?
        // Let's implement status parsing if needed, but the docs say:
        // 2-way on (0x01/0x03) -> 0x3901
        // 0x07 -> 0x3909
        // 0x08 -> 0x390b
        // 0x09 -> 0x390d
        // PIP (0x10) -> 0x390f
    }
}

@Serializable
enum class SourceCode(val e3Code: Int, val mccsCode: Int, val description: String) {
    HDMI1(0x00, 0x11, "HDMI 1"),
    HDMI2(0x01, 0x12, "HDMI 2"),
    HDMI3(0x02, 0x32, "HDMI 3"),
    DP(0x10, 0x0F, "DisplayPort");

    companion object {
        fun fromE3Code(code: Int): SourceCode? = entries.find { it.e3Code == code }
    }
}

enum class PaneChannel(val channel: Int, val description: String) {
    PANE_1(0x00, "Pane 1 (Left/Main)"),
    PANE_2(0x01, "Pane 2 (Middle/Sub)"),
    PANE_3(0x02, "Pane 3 (Right)")
}

@Serializable
data class MonitorStatus(
    val layout: Layout?,
    val layoutStatusRaw: Int,
    val paneSources: Map<PaneChannel, SourceCode>,
    val audioSource: Int, // E8
    val mainInputSource: Int // 60
)

data class PendingChange(
    val previousStatus: MonitorStatus,
    val countdown: Int
)

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val layout: Layout,
    val paneSources: Map<PaneChannel, SourceCode>,
    val audioSource: Int? = null
)

@Serializable
data class AppConfig(
    val presets: List<Preset> = emptyList(),
    val ddcutilPath: String = "ddcutil",
    val betterdisplaycliPath: String = "betterdisplaycli",
    val enableSafetyCountdown: Boolean = true
)

fun List<Preset>.withoutPreset(id: String): List<Preset> = filterNot { it.id == id }

fun List<Preset>.renamed(id: String, newName: String): List<Preset> =
    map { if (it.id == id) it.copy(name = newName) else it }

/** Moves the preset with this id one position earlier; a no-op if it's missing or already first. */
fun List<Preset>.movedUp(id: String): List<Preset> {
    val index = indexOfFirst { it.id == id }
    if (index <= 0) return this
    return toMutableList().apply { add(index - 1, removeAt(index)) }
}

/** Moves the preset with this id one position later; a no-op if it's missing or already last. */
fun List<Preset>.movedDown(id: String): List<Preset> {
    val index = indexOfFirst { it.id == id }
    if (index == -1 || index >= size - 1) return this
    return toMutableList().apply { add(index + 1, removeAt(index)) }
}

/**
 * Whether the monitor is currently sitting exactly on this preset - layout and every pane source
 * must match; audio only has to match if the preset actually pins one (null means "don't care",
 * consistent with how applying a preset already treats a null audioSource).
 */
fun Preset.matchesStatus(status: MonitorStatus): Boolean =
    layout == status.layout &&
        paneSources == status.paneSources &&
        (audioSource == null || audioSource == status.audioSource)

/** Which pane channels a layout uses, and what to call each one - the single source of truth for
 * pane labeling, shared by the source-routing controls, the layout diagram, and preset display. */
fun paneSlotsFor(layout: Layout?): List<Pair<PaneChannel, String>> = when {
    layout == null -> listOf(PaneChannel.PANE_1 to "Main")
    layout.name.startsWith("PBP_3") ->
        listOf(PaneChannel.PANE_1 to "Left", PaneChannel.PANE_2 to "Middle", PaneChannel.PANE_3 to "Right")
    layout.name.startsWith("PBP_2") || layout == Layout.PIP ->
        listOf(PaneChannel.PANE_1 to "Left", PaneChannel.PANE_2 to "Right/Sub")
    else -> listOf(PaneChannel.PANE_1 to "Main")
}

/** Human-readable per-pane routing for a preset, e.g. "Left: HDMI 1  ·  Right/Sub: DisplayPort". */
fun formatPresetSources(preset: Preset): String =
    paneSlotsFor(preset.layout).joinToString("  ·  ") { (channel, label) ->
        "$label: ${preset.paneSources[channel]?.description ?: "—"}"
    }
