package com.g95nc.controller

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter

/**
 * Vector-drawn app identity: a rounded "screen" split into two panes, the same motif as the
 * in-app layout diagram. Drawn with plain shapes (no bundled image assets) so it scales cleanly
 * to whatever size the tray/dock/window requests.
 *
 * The tray icon and the app icon share this one drawing, at two levels of detail - per the idea
 * that the tray icon is just the small/simple rendering of the same mark, not a different logo.
 */
private fun DrawScope.drawScreenGlyph(detailed: Boolean) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * 0.22f, h * 0.22f)

    drawRoundRect(color = AppColors.accent, size = Size(w, h), cornerRadius = corner)

    val inset = w * 0.14f
    val gap = w * 0.05f
    val paneWidth = (w - inset * 2 - gap) / 2f
    val paneHeight = h - inset * 2
    val paneCorner = CornerRadius(w * 0.06f, w * 0.06f)

    drawRoundRect(
        color = Color.White,
        topLeft = Offset(inset, inset),
        size = Size(paneWidth, paneHeight),
        cornerRadius = paneCorner
    )
    drawRoundRect(
        color = if (detailed) AppColors.signalActive else Color.White,
        topLeft = Offset(inset + paneWidth + gap, inset),
        size = Size(paneWidth, paneHeight),
        cornerRadius = paneCorner
    )
}

val TrayIcon: Painter = object : Painter() {
    override val intrinsicSize = Size(24f, 24f)
    override fun DrawScope.onDraw() = drawScreenGlyph(detailed = false)
}

val AppIconPainter: Painter = object : Painter() {
    override val intrinsicSize = Size(512f, 512f)
    override fun DrawScope.onDraw() = drawScreenGlyph(detailed = true)
}
