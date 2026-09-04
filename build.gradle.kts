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
            // The artifact/folder name (kept kebab-case: it ends up in .dmg/.deb/.msi filenames
            // and Linux .deb package names are picky about spaces/casing).
            packageName = "g95nc-ddc-app"
            packageVersion = appVersion

            macOS {
                // Overrides packageName above just for macOS: this is what Finder, the Dock tile
                // tooltip, and the "About <Name>" panel actually display, none of which want the
                // kebab-case artifact name.
                packageName = appDisplayName
                dockName = appDisplayName
                bundleID = "com.g95nc.controller"
                iconFile.set(project.file("packaging/icon.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDisplayName</key>
                        <string>$appDisplayName</string>
                    """.trimIndent()
                }
            }
            linux {
                iconFile.set(project.file("packaging/icon.png"))
            }
            // Windows needs a .ico, which isn't generated yet (see packaging/README.md) - the
            // Msi build falls back to Compose Desktop's own default icon until that's added.
            // The taskbar/Start Menu display name there already comes from packageName above,
            // no Windows-specific override needed once the icon exists.
        }
    }
}

kotlin {
    jvmToolchain(21)
}
