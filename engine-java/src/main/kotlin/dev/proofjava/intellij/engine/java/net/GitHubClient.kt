package dev.proofjava.intellij.engine.java.net

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests

private const val USER_AGENT = "proof-intellij"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 20_000

/**
 * Port of `proof-vscode`'s `cli/githubFetch.ts`, minus its manual redirect
 * loop - `com.intellij.util.io.HttpRequests` (real, public platform API -
 * verified against `JetBrains/intellij-community`'s own
 * `platform/ide-core/src/com/intellij/util/io/HttpRequests.java`) already
 * follows redirects, negotiates gzip, and (unlike a raw `java.net` call)
 * respects the IDE's own configured HTTP proxy - all three the TS source
 * has to hand-roll for itself.
 */
fun githubGetText(url: String, indicator: ProgressIndicator?): String =
    HttpRequests.request(url).userAgent(USER_AGENT).connectTimeout(CONNECT_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS).readString(indicator)

fun githubGetBytes(url: String, indicator: ProgressIndicator?): ByteArray =
    HttpRequests.request(url).userAgent(USER_AGENT).connectTimeout(CONNECT_TIMEOUT_MS).readTimeout(READ_TIMEOUT_MS).readBytes(indicator)

/**
 * Minimal, hand-written Gson accessors for the two small GitHub API response
 * shapes this plugin reads (release info, git tree listing) - same "never
 * throw on a malformed shape, treat it as absent" discipline as
 * `core.verdict.JsonSupport`, not shared with it directly since Kotlin
 * `internal` is module-scoped - `engine-java` cannot see `core`'s internal
 * declarations even though it depends on the module.
 */
internal fun parseJsonObject(raw: String): JsonObject {
    val parsed = try {
        JsonParser.parseString(raw)
    } catch (e: Exception) {
        throw IllegalStateException("GitHub's response wasn't valid JSON: ${e.message}")
    }
    return parsed as? JsonObject ?: throw IllegalStateException("GitHub's response wasn't valid JSON: not an object")
}

internal fun JsonObject.stringField(field: String): String? =
    get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

internal fun JsonObject.arrayField(field: String): JsonArray? = get(field) as? JsonArray
