import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
    jacoco
}

// core = the language/build-tool-agnostic layer: verdict-JSON parsing,
// model/join logic, generic CLI-runner/argv/progress-parser plumbing, and
// generic UI surfaces (gutter, tree views, status bar). It must NEVER
// depend on :engine-java - that's the compile-time boundary the plan
// (`Yol-Haritasi-TODO.md` §3 plan file) is built around, enforced here by
// simply never adding such a dependency, not by convention.
//
// No IntelliJ-Platform-specific dependencies{} block here on purpose: a
// module submodule with no own `intellijPlatform {}` declaration inherits
// the root project's target IntelliJ Platform SDK automatically (JetBrains
// 2.x multi-module docs). The plain JUnit dependency below is unrelated -
// `core.verdict`/`core.cli.AnalyzeArgsBuilder`/`core.cli.ProgressLineParser`
// have zero `com.intellij.*` imports and must stay testable with plain
// JUnit alone, no IntelliJ Platform test fixture needed.
dependencies {
    api(project(":core-pure"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25) // must match root's toolchain - IntelliJ Platform 2026.2.2 requires JDK 25
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    // The IntelliJ Platform Gradle plugin runs tests against instrumented
    // (NotNull-checked) bytecode under build/instrumented/instrumentCode,
    // not the plain build/classes/kotlin/main jacocoTestReport defaults to -
    // those two class files have different CRCs, so the real coverage data
    // in test.exec never matches anything there and every line shows as
    // missed. Point at the instrumented classes actually executed instead.
    classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}
