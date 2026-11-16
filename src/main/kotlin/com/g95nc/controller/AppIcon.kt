package com.g95nc.controller

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter

/**
 * Vector-drawn app identity: a rounded "screen" split into two panes (one white, one the
 * signal-active teal), the same motif as the in-app layout diagram. Drawn with plain shapes (no
 * bundled image assets) so it scales cleanly to whatever size the tray/dock/window requests.
 *
 * The tray icon and the app icon are the exact same drawing, just at different sizes - they used
 * to differ (the tray version had two white panes, "simplified" for legibility at 16-24px), but
 * that just reads as two different icons side by side in the same UI rather than one identity at
 * two sizes, and the two-tone version is still perfectly legible that small.
 */
private fun DrawScope.drawScreenGlyph() {
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
        color = AppColors.signalActive,
        topLeft = Offset(inset + paneWidth + gap, inset),
        size = Size(paneWidth, paneHeight),
        cornerRadius = paneCorner
    )
}

/**
 * Monochrome split-screen monitor silhouette for the system tray / menu bar across macOS, Linux,
 * and Windows. Unlike [AppIconPainter] (which is full-color for the Dock/launcher/windows), OS
 * tray areas expect a single-color silhouette with a transparent background.
 *
 * On macOS with `apple.awt.enableTemplateImages=true`, pure black + alpha is tinted natively by
 * NSStatusBar to match Light/Dark menu bars; on Linux and Windows, [isDark] selects white vs.
 * dark ink.
 */
private fun DrawScope.drawTrayGlyph(tint: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.095f
    val left = w * 0.06f
    val top = h * 0.20f
    val frameW = w * 0.88f
    val frameH = h * 0.60f
    val corner = CornerRadius(w * 0.12f, w * 0.12f)

    drawRoundRect(
        color = tint,
        topLeft = Offset(left, top),
        size = Size(frameW, frameH),
        cornerRadius = corner,
        style = Stroke(width = stroke)
    )
    drawLine(
        color = tint,
        start = Offset(w * 0.5f, top),
        end = Offset(w * 0.5f, top + frameH),
        strokeWidth = stroke
    )
}

fun trayIconPainter(isDark: Boolean, platform: Platform = Platform.detect()): Painter {
    val tint = when {
        platform == Platform.MAC -> Color.Black
        isDark -> Color.White
        else -> Color(0xFF333333)
    }
    return object : Painter() {
        override val intrinsicSize = Size(24f, 24f)
        override fun DrawScope.onDraw() = drawTrayGlyph(tint)
    }
}

val AppIconPainter: Painter = object : Painter() {
    override val intrinsicSize = Size(512f, 512f)
    override fun DrawScope.onDraw() = drawScreenGlyph()
}

private fun Color.toAwtColor(): java.awt.Color = java.awt.Color(red, green, blue, alpha)

/**
 * The same glyph as [AppIconPainter], rendered with plain java.awt.Graphics2D into a raster
 * image instead of a Compose Painter. Compose's Window(icon=...) only reaches the title bar on
 * platforms that have one - it does not reach the macOS Dock for a plain (unpackaged) java
 * process - so the Dock icon has to be set via java.awt.Taskbar, which needs a plain AWT Image,
 * not a Compose Painter. Also used to render the source PNGs for the packaged app's .icns/.ico.
 */
fun renderAppIconImage(size: Int = 512): java.awt.image.BufferedImage {
    val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)

    val corner = (size * 0.22f).toInt()
    g.color = AppColors.accent.toAwtColor()
    g.fillRoundRect(0, 0, size, size, corner, corner)

    val inset = size * 0.14f
    val gap = size * 0.05f
    val paneWidth = (size - inset * 2 - gap) / 2f
    val paneHeight = size - inset * 2
    val paneCorner = (size * 0.06f).toInt()

    g.color = java.awt.Color.WHITE
    g.fillRoundRect(inset.toInt(), inset.toInt(), paneWidth.toInt(), paneHeight.toInt(), paneCorner, paneCorner)

    g.color = AppColors.signalActive.toAwtColor()
    g.fillRoundRect((inset + paneWidth + gap).toInt(), inset.toInt(), paneWidth.toInt(), paneHeight.toInt(), paneCorner, paneCorner)

    g.dispose()
    return image
}

/**
 * Raster version of [trayIconPainter] for [LinuxTray] (dorkbox SystemTray takes an AWT Image).
 */
fun renderTrayIconImage(size: Int = 64, isDark: Boolean = true): java.awt.image.BufferedImage {
    val image = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)

    val stroke = size * 0.095f
    val left = size * 0.06f
    val top = size * 0.20f
    val frameW = size * 0.88f
    val frameH = size * 0.60f
    val arc = size * 0.24f

    g.color = if (isDark) java.awt.Color.WHITE else java.awt.Color(0x33, 0x33, 0x33)
    g.stroke = java.awt.BasicStroke(stroke)
    g.draw(java.awt.geom.RoundRectangle2D.Float(left, top, frameW, frameH, arc, arc))
    g.draw(java.awt.geom.Line2D.Float(size * 0.5f, top, size * 0.5f, top + frameH))

    g.dispose()
    return image
}

/** Sets the Dock icon (macOS) / taskbar icon at runtime, for the current process - works in dev
 * mode (`./gradlew run`) too, not just a packaged, properly-iconned .app bundle. Best-effort:
 * unsupported platforms/JDKs just keep whatever default icon they already show. */
fun applyRuntimeDockIcon() {
    try {
        if (java.awt.Taskbar.isTaskbarSupported()) {
            val taskbar = java.awt.Taskbar.getTaskbar()
            if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                taskbar.iconImage = renderAppIconImage()
            }
        }
    } catch (e: Exception) {
        // Cosmetic only - never worth blocking startup over.
    }
}
