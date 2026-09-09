package dev.proofjava.intellij.engine.java.buildtool

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class JavaProjectKindTest {

    @Test
    fun `a root pom-xml means Maven`(@TempDir tempDir: Path) {
        File(tempDir.toFile(), "pom.xml").writeText("<project/>")
        assertEquals(JavaProjectKind.MAVEN, detectProjectKind(tempDir.toString()))
    }

    @Test
    fun `a committed gradlew wrapper (no pom-xml) means Gradle`(@TempDir tempDir: Path) {
        // A real committed Gradle wrapper ships both scripts (it is meant to
        // be cross-platform) - `resolveGradleWrapper` only ever looks for
        // the one this OS would actually run (mirrors gradleTestTask.ts's
        // own platform check), so the fixture must have both to pass
        // regardless of which platform runs this test.
        File(tempDir.toFile(), "gradlew").writeText("#!/bin/sh")
        File(tempDir.toFile(), "gradlew.bat").writeText("@echo off")
        assertEquals(JavaProjectKind.GRADLE, detectProjectKind(tempDir.toString()))
    }

    @Test
    fun `a committed gradlew-bat wrapper also means Gradle`(@TempDir tempDir: Path) {
        File(tempDir.toFile(), "gradlew.bat").writeText("@echo off")
        assertEquals(JavaProjectKind.GRADLE, detectProjectKind(tempDir.toString()))
    }

    @Test
    fun `Maven takes priority when both a pom-xml and a gradlew wrapper are present`(@TempDir tempDir: Path) {
        File(tempDir.toFile(), "pom.xml").writeText("<project/>")
        File(tempDir.toFile(), "gradlew").writeText("#!/bin/sh")
        assertEquals(JavaProjectKind.MAVEN, detectProjectKind(tempDir.toString()))
    }

    @Test
    fun `neither a pom-xml nor a wrapper means nothing runnable`(@TempDir tempDir: Path) {
        assertNull(detectProjectKind(tempDir.toString()))
    }

    @Test
    fun `resolveGradleWrapper never falls back to a bare gradle on PATH - absent wrapper is null, not a guess`(@TempDir tempDir: Path) {
        assertNull(resolveGradleWrapper(tempDir.toString()))
    }
}
