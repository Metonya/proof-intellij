import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // No versions here - both are pinned once at the settings level
    // (`org.jetbrains.intellij.platform.settings` in settings.gradle.kts),
    // matching JetBrains' own multi-module template exactly.
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("pluginVersion").get()

// No repositories{} block here: settings.gradle.kts's
// `dependencyResolutionManagement` already centralizes repository
// resolution (mavenCentral + intellijPlatform.defaultRepositories()) for
// every project in this build, root included.

dependencies {
    // M0: `core` and `engine-java` are merged into this plugin's own jar via
    // plain project dependencies. This is the simplest correct packaging for
    // v1 - IntelliJ Platform's separate "plugin content module" mechanism
    // (pluginComposedModule) is for lazily-loaded optional modules, which
    // core/engine-java are not (engine-java is always needed for this
    // single-engine v1; see the plan's "v1 için bilinçli kapsam dışı").
    implementation(project(":core"))
    implementation(project(":engine-java"))

    intellijPlatform {
        // Not `intellijIdeaCommunity(...)` - that function is deprecated as
        // of the unified-IJ-distribution change (JetBrains announced IC
        // stops existing as a separate *development* target starting
        // 2025.3; end users on Community can still install plugins built
        // against `intellijIdea(...)`, this only changes what artifact the
        // build compiles against). Verified against JetBrains' own current
        // plugin template before writing this.
        intellijIdea(providers.gradleProperty("platformVersion").get())
        pluginVerifier()
    }
}

kotlin {
    jvmToolchain(25) // IntelliJ Platform 2026.2.2's own requirement (verified via verifyPluginProjectConfiguration, not assumed)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.proofjava.intellij"
        name = "Proof"
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild").get()
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.7.1"
    }
}
