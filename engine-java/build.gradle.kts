import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

// engine-java = everything Maven/Gradle/JaCoCo/PIT-specific: build-tool
// detection, module discovery, report binding, classpath resolution,
// Maven/Gradle test-run drivers and their error interpreters. Depends on
// :core (the Engine interface it implements, plus verdict/model types);
// :core must never depend back on this module.
dependencies {
    implementation(project(":core"))
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
}
