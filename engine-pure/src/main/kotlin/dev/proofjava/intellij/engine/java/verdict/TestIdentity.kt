package dev.proofjava.intellij.engine.java.verdict

/**
 * Port of `proof-vscode/src/verdict/testIdentity.ts` (itself ported from
 * proof-java-cli's `TestIdentity.java`, D-49). Lives in `engine-java`, not
 * `core.verdict`: unlike the schema types M1 ported, a raw test id string
 * (`perTest.modules[].entries[].lines[].tests[]`) is opaque in the schema
 * itself - its actual format (JUnit5 `UniqueId`, or proof-java's own
 * `Class#method(...)`) is a JUnit/JVM convention a future engine's own test
 * ids would not share (pytest ids look nothing like this), so parsing it
 * is engine-specific even though it is not "Maven vs Gradle" specific.
 *
 * A raw test id can be a JUnit5 `UniqueId`
 * (`[engine:...]/[class:X]/[method:Y()]` for a plain test, or
 * `[class:X]/[test-template:Y(args)]/[test-template-invocation:#N]` for
 * one invocation of a `@ParameterizedTest`/`@RepeatedTest`) or proof-java's
 * own `Class#method(...)` shape. An unrecognized id is displayed verbatim
 * rather than guessed at (hard rule 3a).
 */

data class ParsedTestIdentity(
    val className: String?,
    val methodName: String?,
    /** Last segment of [className] (`Outer$Inner` kept as-is). Same value as [className] when it has no package. */
    val simpleClassName: String?,
    /** Set only for a `@ParameterizedTest`/`@RepeatedTest` invocation (`[test-template-invocation:#N]`). */
    val invocation: String?,
    /** Always set: the best available human-readable form - `Class#method()` (optionally ` #N`) when parsed, the raw id otherwise. */
    val display: String,
)

private val JUNIT5_CLASS = Regex("""\[class:([^]]+)]""")
private val JUNIT5_METHOD = Regex("""\[method:([^](]+)""")
private val JUNIT5_TEST_TEMPLATE = Regex("""\[test-template:([^](]+)""")
private val JUNIT5_INVOCATION = Regex("""\[test-template-invocation:#(\d+)]""")

fun parseTestIdentity(rawTestId: String): ParsedTestIdentity {
    val classMatch = JUNIT5_CLASS.find(rawTestId)
    if (classMatch != null) {
        parseJUnit5(rawTestId, classMatch.groupValues[1])?.let { return it }
    }

    val hash = rawTestId.indexOf('#')
    if (hash in 1 until rawTestId.length - 1) {
        val className = rawTestId.substring(0, hash)
        val rest = rawTestId.substring(hash + 1)
        val paren = rest.indexOf('(')
        val methodName = if (paren >= 0) rest.substring(0, paren) else rest
        if (methodName.isNotEmpty()) {
            return ParsedTestIdentity(className, methodName, simpleName(className), null, "${simpleName(className)}#$methodName()")
        }
    }

    return ParsedTestIdentity(null, null, null, null, rawTestId)
}

/** `[class:X]` was found; still may be neither `[method:]` nor `[test-template:]` (e.g. a container-level UniqueId) - `null` falls through to the raw-id fallback rather than guessing. */
private fun parseJUnit5(rawTestId: String, className: String): ParsedTestIdentity? {
    val methodMatch = JUNIT5_METHOD.find(rawTestId)
    if (methodMatch != null) {
        val methodName = methodMatch.groupValues[1]
        return ParsedTestIdentity(className, methodName, simpleName(className), null, "${simpleName(className)}#$methodName()")
    }

    val templateMatch = JUNIT5_TEST_TEMPLATE.find(rawTestId)
    if (templateMatch != null) {
        val methodName = templateMatch.groupValues[1]
        val invocation = JUNIT5_INVOCATION.find(rawTestId)?.groupValues?.get(1)
        val suffix = if (invocation != null) " #$invocation" else ""
        return ParsedTestIdentity(className, methodName, simpleName(className), invocation, "${simpleName(className)}#$methodName()$suffix")
    }

    return null
}

private fun simpleName(fqcn: String): String {
    val dot = fqcn.lastIndexOf('.')
    return if (dot < 0) fqcn else fqcn.substring(dot + 1)
}
