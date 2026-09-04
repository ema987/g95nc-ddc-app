package com.g95nc.controller

import kotlin.test.Test
import kotlin.test.assertEquals

class PresetTest {

    private fun preset(
        id: String,
        name: String,
        layout: Layout,
        panes: Map<PaneChannel, SourceCode> = emptyMap()
    ) = Preset(id = id, name = name, layout = layout, paneSources = panes)

    @Test
    fun testWithoutPreset_removesOnlyTheMatchingId() {
        val presets = listOf(
            preset("1", "A", Layout.OFF),
            preset("2", "B", Layout.OFF),
            preset("3", "C", Layout.OFF)
        )

        assertEquals(listOf("1", "3"), presets.withoutPreset("2").map { it.id })
    }

    @Test
    fun testWithoutPreset_isNoopWhenIdNotFound() {
        val presets = listOf(preset("1", "A", Layout.OFF))
        assertEquals(presets, presets.withoutPreset("does-not-exist"))
    }

    @Test
    fun testRenamed_updatesOnlyTheMatchingId() {
        val presets = listOf(preset("1", "A", Layout.OFF), preset("2", "B", Layout.OFF))

        val renamed = presets.renamed("2", "B renamed")

        assertEquals("A", renamed[0].name)
        assertEquals("B renamed", renamed[1].name)
    }

    @Test
    fun testRenamed_isNoopWhenIdNotFound() {
        val presets = listOf(preset("1", "A", Layout.OFF))
        assertEquals(presets, presets.renamed("does-not-exist", "New Name"))
    }

    @Test
    fun testFormatPresetSources_offLayoutShowsMainOnly() {
        val p = preset("1", "Main only", Layout.OFF, mapOf(PaneChannel.PANE_1 to SourceCode.DP))
        assertEquals("Main: DisplayPort", formatPresetSources(p))
    }

    @Test
    fun testFormatPresetSources_2WayShowsLeftAndRightSub() {
        val p = preset(
            "1", "2-way", Layout.PBP_2_50_50,
            mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.HDMI2)
        )
        assertEquals("Left: HDMI 1  ·  Right/Sub: HDMI 2", formatPresetSources(p))
    }

    @Test
    fun testFormatPresetSources_pipUsesLeftAndRightSubLikeA2Way() {
        val p = preset(
            "1", "PIP", Layout.PIP,
            mapOf(PaneChannel.PANE_1 to SourceCode.HDMI3, PaneChannel.PANE_2 to SourceCode.DP)
        )
        assertEquals("Left: HDMI 3  ·  Right/Sub: DisplayPort", formatPresetSources(p))
    }

    @Test
    fun testFormatPresetSources_3WayShowsAllThreePanes() {
        val p = preset(
            "1", "3-way", Layout.PBP_3_25_50_25,
            mapOf(
                PaneChannel.PANE_1 to SourceCode.HDMI1,
                PaneChannel.PANE_2 to SourceCode.DP,
                PaneChannel.PANE_3 to SourceCode.HDMI3
            )
        )
        assertEquals("Left: HDMI 1  ·  Middle: DisplayPort  ·  Right: HDMI 3", formatPresetSources(p))
    }

    @Test
    fun testFormatPresetSources_missingPaneShowsPlaceholder() {
        val p = preset("1", "Incomplete", Layout.PBP_2_50_50, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1))
        assertEquals("Left: HDMI 1  ·  Right/Sub: —", formatPresetSources(p))
    }
}
