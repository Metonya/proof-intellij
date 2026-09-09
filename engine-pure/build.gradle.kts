import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

// engine-pure = the subset of :engine-java with zero `com.intellij.*` imports
// (build-tool detection, module/source discovery, report/classpath binding,
// error interpreters) - same reasoning as :core-pure's own doc comment: a
// plain Kotlin/JVM module so its tests run outside the IntelliJ Platform's
// PathClassLoader, where JaCoCo can actually record coverage.
dependencies {
    // See core-pure/build.gradle.kts: root sets kotlin.stdlib.default.dependency=false.
    implementation(kotlin("stdlib"))
    implementation(project(":core-pure"))
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
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
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}
