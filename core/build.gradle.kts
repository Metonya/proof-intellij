import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

// core = the language/build-tool-agnostic layer: verdict-JSON parsing,
// model/join logic, generic CLI-runner/argv/progress-parser plumbing, and
// generic UI surfaces (gutter, tree views, status bar). It must NEVER
// depend on :engine-java - that's the compile-time boundary the plan
// (`Yol-Haritasi-TODO.md` §3 plan file) is built around, enforced here by
// simply never adding such a dependency, not by convention.
//
// No dependencies{} block here on purpose: a module submodule with no own
// `intellijPlatform {}` declaration inherits the root project's target
// IntelliJ Platform SDK automatically (JetBrains 2.x multi-module docs).

kotlin {
    jvmToolchain(25) // must match root's toolchain - IntelliJ Platform 2026.2.2 requires JDK 25
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}
