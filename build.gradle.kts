import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

compose.desktop {
    application {
        mainClass = "com.convx.windows.MainKt"

        jvmArgs += listOf("-Dskiko.renderApi=DIRECT3D")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Convx"
            packageVersion = "1.0.0"
            description = "Convx \u2014 Liquid Glass music player for Windows"
            copyright = "GPL-3.0"
            vendor = "omnizs38"

            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f0c2a41-6f91-4c1e-9f35-7b4c8a1d9c21"
            }
        }
    }
}
