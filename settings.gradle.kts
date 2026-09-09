import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "proof-intellij"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.3.20"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Settings-level plugin: pins the version for the whole IntelliJ
    // Platform Gradle Plugin family (so `core`/`engine-java`/root can each
    // apply their own `org.jetbrains.intellij.platform[.module]` plugin
    // with NO version, matching JetBrains' own multi-module template
    // exactly) and centralizes repository resolution via
    // `defaultRepositories()` below.
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

// Root project itself applies the main `org.jetbrains.intellij.platform`
// plugin and holds plugin.xml (the assembly point) — `core`/`engine-java`
// apply the lighter `org.jetbrains.intellij.platform.module` plugin instead.
// This is the JetBrains-documented multi-module pattern for the 2.x Gradle
// plugin: the *root* module IS the plugin, not a separate leaf module.
include(":core-pure")
include(":core")
include(":engine-pure")
include(":engine-java")
