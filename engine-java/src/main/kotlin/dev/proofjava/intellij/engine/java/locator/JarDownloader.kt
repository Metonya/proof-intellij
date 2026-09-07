package dev.proofjava.intellij.engine.java.locator

import com.intellij.openapi.progress.ProgressIndicator
import dev.proofjava.intellij.engine.java.net.arrayField
import dev.proofjava.intellij.engine.java.net.githubGetBytes
import dev.proofjava.intellij.engine.java.net.githubGetText
import dev.proofjava.intellij.engine.java.net.parseJsonObject
import dev.proofjava.intellij.engine.java.net.stringField
import java.security.MessageDigest

private const val JAR_REPO = "Metonya/proof-java"
private const val JAR_ASSET_NAME = "proof-java.jar"
private const val CHECKSUMS_ASSET_NAME = "SHA-256SUMS"

data class JarDownloadResult(val content: ByteArray, val version: String)

/**
 * Port of `proof-vscode`'s `cli/jarDownloader.ts` - downloads `proof-java.jar`
 * from proof-java's latest GitHub Release and, when the release also
 * publishes a `SHA-256SUMS` asset (its own `release.yml` CI already does),
 * verifies the download against that checksum rather than trusting the
 * bytes GitHub happened to serve - same reasoning the TS source gives.
 * `fetchText`/`fetchBytes` injectable for tests (mirrors the TS source's own
 * injectable `fetchText`/`fetchBuffer` parameters), default to the real
 * GitHub client.
 */
fun downloadLatestJar(
    indicator: ProgressIndicator? = null,
    fetchText: (String) -> String = { githubGetText(it, indicator) },
    fetchBytes: (String) -> ByteArray = { githubGetBytes(it, indicator) },
): JarDownloadResult {
    val release = parseJsonObject(fetchText("https://api.github.com/repos/$JAR_REPO/releases/latest"))
    val tagName = release.stringField("tag_name")
    val assets = release.arrayField("assets")
    if (tagName == null || assets == null) {
        throw IllegalStateException("unexpected response shape from the GitHub API (no tag_name/assets)")
    }

    val jarAsset = assets.mapNotNull { it as? com.google.gson.JsonObject }
        .firstOrNull { it.stringField("name") == JAR_ASSET_NAME }
        ?: throw IllegalStateException("the latest release ($tagName) has no \"$JAR_ASSET_NAME\" asset")
    val jarUrl = jarAsset.stringField("browser_download_url")
        ?: throw IllegalStateException("the latest release ($tagName)'s \"$JAR_ASSET_NAME\" asset has no download URL")
    val content = fetchBytes(jarUrl)

    val checksumsAsset = assets.mapNotNull { it as? com.google.gson.JsonObject }
        .firstOrNull { it.stringField("name") == CHECKSUMS_ASSET_NAME }
    val checksumsUrl = checksumsAsset?.stringField("browser_download_url")
    if (checksumsUrl != null) {
        val expected = parseSha256Sums(fetchText(checksumsUrl), JAR_ASSET_NAME)
        // No entry for the jar in SHA-256SUMS: still install it (hard rule
        // 3a would say "don't guess a checksum", not "refuse a legitimate
        // asset because a side-file's format changed") - just nothing to
        // verify against, same as the TS source.
        if (expected != null) {
            val actual = sha256Hex(content)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IllegalStateException(
                    "downloaded \"$JAR_ASSET_NAME\" does not match its published SHA-256 checksum " +
                        "(expected $expected, got $actual) - the download may be corrupted or tampered with",
                )
            }
        }
    }

    return JarDownloadResult(content, tagName)
}

/**
 * `SHA-256SUMS` format (coreutils `sha256sum` output): `<hex digest> *<filename>`
 * in binary mode, `<hex digest>  <filename>` in text mode, one per line -
 * same deliberately unambiguous regex as the TS source's own
 * `parseSha256Sums` (typescript:S5852 - anchoring the filename group to a
 * real filename character leaves exactly one way to match a run of spaces).
 */
private val SHA256_SUMS_LINE = Regex("""^([0-9a-f]{64})\s+\*?(\S.*)$""")

internal fun parseSha256Sums(raw: String, fileName: String): String? {
    for (line in raw.split("\n")) {
        val match = SHA256_SUMS_LINE.find(line.trim()) ?: continue
        if (match.groupValues[2] == fileName) return match.groupValues[1]
    }
    return null
}

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
