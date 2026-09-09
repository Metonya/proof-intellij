import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

// core-pure = the subset of :core with zero `com.intellij.*` imports (verdict-JSON
// parsing, model/join logic, generic CLI-argv/progress-parser plumbing) - split
// into its own plain Kotlin/JVM module (no `org.jetbrains.intellij.platform.module`)
// so its tests run in an ordinary JVM. Tests inside :core/:engine-java run against
// bytecode loaded through the IntelliJ Platform's own PathClassLoader
// (`idea.force.use.core.classloader=true`), which JaCoCo's javaagent cannot
// instrument - confirmed by inspecting a test.exec recorded there: zero
// `dev.proofjava.*` classes, only Gradle-worker/JDK bootstrap classes. Moving
// pure logic here is what makes its coverage actually show up in Sonar.
dependencies {
    // The root project sets kotlin.stdlib.default.dependency=false (the
    // IntelliJ Platform SDK supplies its own stdlib to :core/:engine-java) -
    // this module has no such SDK on its classpath, so it needs the real one.
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.13.2")
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
