import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
    jacoco
}

// engine-java = everything Maven/Gradle/JaCoCo/PIT-specific: build-tool
// detection, module discovery, report binding, classpath resolution,
// Maven/Gradle test-run drivers and their error interpreters. Depends on
// :core (the Engine interface it implements, plus verdict/model types);
// :core must never depend back on this module.
dependencies {
    implementation(project(":core"))
    api(project(":engine-pure"))
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
    // Same reasoning as core/build.gradle.kts: coverage must be read from
    // the instrumented classes the test task actually executes, not the
    // plain compiled ones jacocoTestReport defaults to.
    classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}
