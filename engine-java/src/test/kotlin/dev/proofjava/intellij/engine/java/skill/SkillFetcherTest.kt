package dev.proofjava.intellij.engine.java.skill

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Faithful port of `skillFetcher.test.ts` - same fixture shapes, same cases. */
class SkillFetcherTest {

    private fun fakeTreeResponse(paths: List<String>): String {
        val fixed = listOf(
            """{"path":"README.md","type":"blob"}""",
            """{"path":"skills","type":"tree"}""",
            """{"path":"skills/proof-java","type":"tree"}""",
        )
        val extra = paths.map { """{"path":"$it","type":"blob"}""" }
        return """{"sha":"abc123","tree":[${(fixed + extra).joinToString(",")}]}"""
    }

    @Test
    fun `filters the tree to skills-proof-java blobs and strips the prefix`() {
        val paths = listOf("skills/proof-java/SKILL.md", "skills/proof-java/reference/rules.md", "skills/proof-java/reference/invocations.md")
        val fetchedUrls = mutableListOf<String>()
        val files = fetchSkillFiles(
            fetchText = { fakeTreeResponse(paths) },
            fetchBytes = { url -> fetchedUrls.add(url); "content of $url".toByteArray() },
        )
        assertEquals(
            listOf("SKILL.md", "reference/invocations.md", "reference/rules.md"),
            files.map { it.relativePath }.sorted(),
        )
        assertTrue(fetchedUrls.all { it.startsWith("https://raw.githubusercontent.com/Metonya/proof-java/main/skills/proof-java/") }, "must fetch raw content from the exact tree paths, not guessed URLs")
    }

    @Test
    fun `a tree with no skills-proof-java entries throws rather than silently installing nothing`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            fetchSkillFiles(fetchText = { fakeTreeResponse(emptyList()) }, fetchBytes = { ByteArray(0) })
        }
        assertTrue(ex.message!!.contains("no files found"))
    }

    @Test
    fun `non-blob entries (directories) under skills-proof-java are excluded, not fetched as files`() {
        val raw = """{"tree":[{"path":"skills/proof-java/reference","type":"tree"},{"path":"skills/proof-java/SKILL.md","type":"blob"}]}"""
        val fetchedUrls = mutableListOf<String>()
        val files = fetchSkillFiles(
            fetchText = { raw },
            fetchBytes = { url -> fetchedUrls.add(url); "x".toByteArray() },
        )
        assertEquals(1, files.size)
        assertEquals("SKILL.md", files[0].relativePath)
    }

    @Test
    fun `malformed JSON from the tree endpoint is a clear error, not a thrown parse exception leaking upward unexplained`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            fetchSkillFiles(fetchText = { "not json" }, fetchBytes = { ByteArray(0) })
        }
        assertTrue(ex.message!!.contains("valid JSON"))
    }

    @Test
    fun `a response with no tree array is rejected with a clear error`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            fetchSkillFiles(fetchText = { """{"sha":"abc"}""" }, fetchBytes = { ByteArray(0) })
        }
        assertTrue(ex.message!!.contains("unexpected response shape"))
    }
}
