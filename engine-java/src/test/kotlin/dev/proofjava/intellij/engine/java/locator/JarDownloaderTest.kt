package dev.proofjava.intellij.engine.java.locator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Faithful port of `jarDownloader.test.ts` - same fixture shapes, same cases. */
class JarDownloaderTest {

    private fun fakeReleaseResponse(assetNames: List<String>): String {
        val assets = assetNames.joinToString(",") { name ->
            """{"name":"$name","browser_download_url":"https://github.com/Metonya/proof-java/releases/download/v0.1.0/$name"}"""
        }
        return """{"tag_name":"v0.1.0","assets":[$assets]}"""
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `fetches the jar asset and returns its content plus the release tag`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val fetched = mutableListOf<String>()
        val result = downloadLatestJar(
            fetchText = { fakeReleaseResponse(listOf("proof-java.jar")) },
            fetchBytes = { url -> fetched.add(url); jarBytes },
        )
        assertEquals("v0.1.0", result.version)
        assertTrue(result.content.contentEquals(jarBytes))
        assertEquals(listOf("https://github.com/Metonya/proof-java/releases/download/v0.1.0/proof-java.jar"), fetched)
    }

    @Test
    fun `verifies the download against a real SHA-256SUMS asset when present`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val digest = sha256Hex(jarBytes)
        val sums = "$digest *proof-java.jar\nabc123 *NOTICE\n"

        val result = downloadLatestJar(
            fetchText = { url -> if (url.contains("SHA-256SUMS")) sums else fakeReleaseResponse(listOf("proof-java.jar", "SHA-256SUMS")) },
            fetchBytes = { jarBytes },
        )
        assertTrue(result.content.contentEquals(jarBytes))
    }

    @Test
    fun `coreutils text-mode lines (two spaces, no asterisk) verify too`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val digest = sha256Hex(jarBytes)
        val sums = "$digest  proof-java.jar\n"

        val result = downloadLatestJar(
            fetchText = { url -> if (url.contains("SHA-256SUMS")) sums else fakeReleaseResponse(listOf("proof-java.jar", "SHA-256SUMS")) },
            fetchBytes = { jarBytes },
        )
        assertTrue(result.content.contentEquals(jarBytes))
    }

    @Test
    fun `trailing whitespace and CRLF line endings still verify`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val digest = sha256Hex(jarBytes)
        val sums = "$digest *proof-java.jar   \r\n"

        val result = downloadLatestJar(
            fetchText = { url -> if (url.contains("SHA-256SUMS")) sums else fakeReleaseResponse(listOf("proof-java.jar", "SHA-256SUMS")) },
            fetchBytes = { jarBytes },
        )
        assertTrue(result.content.contentEquals(jarBytes))
    }

    @Test
    fun `a checksum mismatch is a hard failure, not a silent install`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val sums = "${"deadbeef".repeat(8)} *proof-java.jar\n"

        val ex = assertThrows(IllegalStateException::class.java) {
            downloadLatestJar(
                fetchText = { url -> if (url.contains("SHA-256SUMS")) sums else fakeReleaseResponse(listOf("proof-java.jar", "SHA-256SUMS")) },
                fetchBytes = { jarBytes },
            )
        }
        assertTrue(ex.message!!.contains("does not match its published SHA-256 checksum"))
    }

    @Test
    fun `no SHA-256SUMS asset at all - installs the jar anyway, nothing to verify against`() {
        val jarBytes = "fake jar bytes".toByteArray()
        val result = downloadLatestJar(
            fetchText = { fakeReleaseResponse(listOf("proof-java.jar")) },
            fetchBytes = { jarBytes },
        )
        assertTrue(result.content.contentEquals(jarBytes))
    }

    @Test
    fun `a release with no proof-java jar asset is a clear error`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            downloadLatestJar(
                fetchText = { fakeReleaseResponse(listOf("LICENSE", "NOTICE")) },
                fetchBytes = { ByteArray(0) },
            )
        }
        assertTrue(ex.message!!.contains("has no \"proof-java.jar\" asset"))
    }

    @Test
    fun `malformed JSON from the release endpoint is a clear error`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            downloadLatestJar(fetchText = { "not json" }, fetchBytes = { ByteArray(0) })
        }
        assertTrue(ex.message!!.contains("valid JSON"))
    }

    @Test
    fun `a response missing tag_name or assets is rejected with a clear error`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            downloadLatestJar(fetchText = { """{"id":1}""" }, fetchBytes = { ByteArray(0) })
        }
        assertTrue(ex.message!!.contains("unexpected response shape"))
    }
}
