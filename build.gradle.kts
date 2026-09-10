plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

group = "com.g95nc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.material3)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    
    // JNA for Windows
    implementation(libs.jna)
    implementation(libs.jna.platform)
    
    // SystemTray for Linux
    implementation(libs.dorkbox.systemtray)
    
    // Test
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs)
}

// Keep in sync with AppInfo.NAME/VERSION in src/main/kotlin/.../AppInfo.kt - this build script and
// the app's own Kotlin sources are separate compilation units, so this is the closest we can get
// to one source of truth for the display name/version across a Gradle boundary.
val appDisplayName = "G95NC Controller"
val appVersion = "1.0.0"

compose.desktop {
    application {
        mainClass = "com.g95nc.controller.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            // This becomes jpackage's --name: the actual .app bundle folder name (which is what
            // Finder/Spotlight/Dock display - CFBundleDisplayName does NOT override it, despite
            // documentation suggesting otherwise; confirmed by inspecting the real jpackage
            // invocation Gradle builds, not just reading the DSL). Also the Start Menu/taskbar
            // name on Windows. A space here is fine on both platforms.
            packageName = appDisplayName
            packageVersion = appVersion

            macOS {
                // NOT the same as the top-level packageName above despite the identical property
                // name on this nested type - this one maps to jpackage's --mac-package-name,
                // which only sets CFBundleDisplayName (a real field, just not the one Finder
                // reads for its label). Set anyway since it's free and correct.
                packageName = appDisplayName
                dockName = appDisplayName
                bundleID = "com.g95nc.controller"
                iconFile.set(project.file("packaging/icon.icns"))
            }
            linux {
                // Linux's own packageName maps to --linux-package-name - the Debian control
                // file's "Package:" field, which must be lowercase/no-spaces unlike the
                // human-readable top-level name above.
                packageName = "g95nc-ddc-app"
                iconFile.set(project.file("packaging/icon.png"))
            }
            windows {
                iconFile.set(project.file("packaging/icon.ico"))
            }
            // Untested on an actual Windows machine (built here on macOS with ImageMagick, no
            // Windows box available) - see packaging/README.md.
        }
    }
}

kotlin {
    jvmToolchain(21)
}
