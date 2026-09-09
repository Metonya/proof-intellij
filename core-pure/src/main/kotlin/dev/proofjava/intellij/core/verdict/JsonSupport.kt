package dev.proofjava.intellij.core.verdict

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Tiny, hand-written accessors over Gson's [JsonElement] tree - Gson here
 * plays the same role `JSON.parse` plays in `proof-vscode/src/verdict/parse.ts`:
 * a trusted low-level tokenizer only. Every actual shape decision (which
 * field is required, what counts as valid) is hand-written type-guard code
 * in [VerdictParser], never Gson's reflection/annotation-based
 * deserialization - same discipline as proof-java's own config reader
 * (D-40: hand-rolled over pulling in a schema-validator dependency).
 */

internal fun JsonElement?.asObjectOrNull(): JsonObject? = (this as? JsonObject)

internal fun JsonElement?.asArrayOrNull(): JsonArray? = (this as? JsonArray)

internal fun JsonObject.stringOrNull(field: String): String? =
    get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

internal fun JsonObject.string(field: String, default: String = ""): String = stringOrNull(field) ?: default

internal fun JsonObject.longOrNull(field: String): Long? =
    get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

internal fun JsonObject.doubleOrNullField(field: String): Double? =
    get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

/**
 * `percent: number | null` - present-and-null is a real, distinct state from
 * absent (a zero-denominator metric), never coerced to 0.0. Returns null for
 * "field missing or not one of {number, null}" (caller treats as invalid),
 * [PercentValue.Present] for a real value.
 */
internal fun JsonObject.percentField(field: String): PercentValue? {
    val element = get(field) ?: return null
    if (element.isJsonNull) {
        return PercentValue.Null
    }
    if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
        return PercentValue.Present(element.asJsonPrimitive.asDouble)
    }
    return null
}

internal sealed interface PercentValue {
    data object Null : PercentValue
    data class Present(val value: Double) : PercentValue
}

internal fun JsonObject.array(field: String): JsonArray? = get(field).asArrayOrNull()

internal fun JsonObject.obj(field: String): JsonObject? = get(field).asObjectOrNull()

/**
 * Safe alternatives to Gson's own `.asInt`/`.asString`, which throw
 * `UnsupportedOperationException`/`NumberFormatException` on a shape
 * mismatch (e.g. a string where a number was expected) - using those
 * directly on array elements would violate `VerdictParser`'s "never
 * throws" invariant on a genuinely malformed document, not just an
 * absent field.
 */
internal fun JsonElement?.asIntOrNull(): Int? =
    (this as? com.google.gson.JsonPrimitive)?.takeIf { it.isNumber }?.asInt

internal fun JsonElement?.asStringOrNull(): String? =
    (this as? com.google.gson.JsonPrimitive)?.takeIf { it.isString }?.asString
