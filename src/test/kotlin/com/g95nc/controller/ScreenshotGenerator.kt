package com.g95nc.controller

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Not a real test - renders the README screenshots off-screen from fake monitor state, so they can
 * be regenerated without the monitor attached. Skipped unless a target directory is passed:
 *
 *     ./gradlew test --tests '*ScreenshotGenerator*' -PscreenshotsDir=docs/screenshots
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotGenerator {

    private val outDir: String? = System.getProperty("screenshotsDir")

    private val presets = listOf(
        Preset("1", "Work: Mac + PC", Layout.PBP_2_50_50, mapOf(PaneChannel.PANE_1 to SourceCode.DP, PaneChannel.PANE_2 to SourceCode.HDMI1)),
        Preset("2", "Three machines", Layout.PBP_3_25_50_25, mapOf(PaneChannel.PANE_1 to SourceCode.HDMI1, PaneChannel.PANE_2 to SourceCode.DP, PaneChannel.PANE_3 to SourceCode.HDMI2))
    )

    private val pbpStatus = MonitorStatus(
        layout = Layout.PBP_2_50_50,
        layoutStatusRaw = 0,
        paneSources = mapOf(PaneChannel.PANE_1 to SourceCode.DP, PaneChannel.PANE_2 to SourceCode.HDMI1),
        audioSource = 0,
        mainInputSource = 0
    )

    private fun capture(name: String, widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        val dir = outDir
        assumeTrue("screenshotsDir not set", dir != null)
        val scale = 2f
        runSkikoComposeUiTest(size = Size(widthDp * scale, heightDp * scale), density = Density(scale)) {
            setContent(content)
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            File(dir!!).mkdirs()
            ImageIO.write(image, "png", File(dir, "$name.png"))
        }
    }

    @Composable
    private fun DashboardShot(theme: ThemeMode) {
        AppTheme(themeMode = theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Dashboard(
                    status = pbpStatus,
                    controller = FakeMonitorController(statusToReturn = pbpStatus),
                    pendingChange = null,
                    recoveryExhausted = false,
                    isBusy = false,
                    config = AppConfig(presets = presets),
                    onConfigChange = {},
                    onSettingsClick = {},
                    onConfirmChange = {},
                    onRevertChange = {},
                    onExecuteAction = { _, _ -> },
                    onRefresh = {},
                    actionFailureMessage = null,
                    onDismissActionFailure = {}
                )
            }
        }
    }

    @Test
    fun dashboardDark() = capture("dashboard-dark", 680, 780) { DashboardShot(ThemeMode.DARK) }

    @Test
    fun dashboardLight() = capture("dashboard-light", 680, 780) { DashboardShot(ThemeMode.LIGHT) }

    @Test
    fun settingsDark() = capture("settings-dark", 500, 500) {
        // A fake .app path just so the macOS-only "Start at login" option shows, and an empty temp
        // folder so it doesn't reflect whether the machine generating this has it enabled.
        val loginItem = MacLoginItem(
            launchAgentsDir = kotlin.io.path.createTempDirectory("g95nc-screenshot").toFile(),
            appExecutable = "/Applications/G95NC Controller.app/Contents/MacOS/G95NC Controller"
        )
        AppTheme(themeMode = ThemeMode.DARK) {
            Surface(modifier = Modifier.fillMaxSize()) {
                SettingsModal(config = AppConfig(), onSave = {}, controller = FakeMonitorController(), platform = Platform.MAC, loginItem = loginItem)
            }
        }
    }
}
