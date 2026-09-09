package dev.proofjava.intellij.core.verdict

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kotlin port of `proof-vscode/src/test/unit/verdict/parse.test.ts` -
 * mirrors its fixtures and assertions test-by-test for everything
 * [parseVerdict] here actually covers. NOT ported: the
 * `reinternPerTestIds`/`reinternMutationTestIds` tests - those functions
 * are deliberately deferred (see `VerdictParser.kt`'s own doc comment), no
 * Kotlin caller exists yet.
 */
class VerdictParserTest {

    private fun metric(numerator: Int = 1, denominator: Int = 2, percent: Number? = 50): JsonObject = JsonObject().apply {
        addProperty("numeratorName", "a")
        addProperty("numerator", numerator)
        addProperty("denominatorName", "b")
        addProperty("denominator", denominator)
        if (percent == null) add("percent", JsonNull.INSTANCE) else addProperty("percent", percent)
    }

    private fun metricSet(m: JsonObject = metric(), engineMode: String = "jacoco-line"): JsonObject = JsonObject().apply {
        add(engineMode, m)
        add("strict-line", m)
        add("sonar-compatible", m)
    }

    private fun minimalDocument(): JsonObject = JsonObject().apply {
        addProperty("schemaVersion", "0.1.0")
        add("tool", JsonObject().apply { addProperty("name", "proof-java"); addProperty("version", "0.1.0") })
        add(
            "analysis",
            JsonObject().apply {
                addProperty("status", "complete")
                addProperty("exitCode", 0)
                add("incompleteReasons", JsonArray())
            },
        )
        add(
            "inputs",
            JsonObject().apply {
                add(
                    "modules",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", "root")
                                addProperty("root", ".")
                                add("sourceRoots", JsonArray().apply { add("src/main/java") })
                                add("testRoots", JsonArray().apply { add("src/test/java") })
                            },
                        )
                    },
                )
            },
        )
        add(
            "coverage",
            JsonObject().apply {
                add("overall", metricSet())
                add("newCode", JsonObject().apply { addProperty("status", "unavailable_no_vcs") })
            },
        )
        add("changedFiles", JsonArray())
        add("findings", JsonArray())
        add("warnings", JsonArray())
    }

    private fun okOrFail(json: JsonObject): VerdictDocument {
        val result = parseVerdict(json.toString())
        check(result is ParseResult.Ok) { "expected Ok, got $result" }
        return result.value
    }

    @Test
    fun `a real shaped document parses`() {
        val doc = okOrFail(minimalDocument())
        assertEquals(50.0, doc.coverage.overall.engineLine.percent)
    }

    @Test
    fun `malformed JSON never throws - returns an error result`() {
        val result = parseVerdict("{ this is not json")
        check(result is ParseResult.Error)
        assertTrue(result.message.contains("invalid JSON"))
    }

    @Test
    fun `valid JSON missing the required shape is rejected, not partially accepted`() {
        val result = parseVerdict("""{"hello":"world"}""")
        assertTrue(result is ParseResult.Error)
    }

    @Test
    fun `a null percent (denominator 0) is preserved, not coerced to a number`() {
        val doc = minimalDocument()
        doc.getAsJsonObject("coverage").add("overall", metricSet(metric(denominator = 0, percent = null)))
        val parsed = okOrFail(doc)
        assertNull(parsed.coverage.overall.engineLine.percent)
    }

    @Test
    fun `a well-formed fileCoverage block parses through`() {
        val doc = minimalDocument()
        doc.add(
            "fileCoverage",
            JsonObject().apply {
                add(
                    "files",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("module", "root")
                                addProperty("path", "src/main/java/Calc.java")
                                add("metrics", metricSet())
                                add("lines", JsonArray().apply { add(JsonArray().apply { listOf(14, 0, 3, 0, 0).forEach { add(it) } }) })
                            },
                        )
                    },
                )
                add("excluded", JsonArray().apply { add("src/main/java/Generated.java") })
            },
        )
        val parsed = okOrFail(doc)
        assertEquals("src/main/java/Calc.java", parsed.fileCoverage?.files?.get(0)?.path)
        assertEquals("src/main/java/Generated.java", parsed.fileCoverage?.excluded?.get(0))
    }

    @Test
    fun `a document with no fileCoverage at all parses with it left null`() {
        assertNull(okOrFail(minimalDocument()).fileCoverage)
    }

    @Test
    fun `a malformed fileCoverage block (a line tuple with the wrong arity) is rejected`() {
        val doc = minimalDocument()
        doc.add(
            "fileCoverage",
            JsonObject().apply {
                add(
                    "files",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("module", "root")
                                addProperty("path", "src/main/java/Calc.java")
                                add("metrics", metricSet())
                                add("lines", JsonArray().apply { add(JsonArray().apply { listOf(14, 0, 3).forEach { add(it) } }) })
                            },
                        )
                    },
                )
                add("excluded", JsonArray())
            },
        )
        assertTrue(parseVerdict(doc.toString()) is ParseResult.Error)
    }

    private fun perTestLine(line: Int, vararg tests: Any) = JsonObject().apply {
        addProperty("line", line)
        add("tests", JsonArray().apply { tests.forEach { if (it is String) add(it) else add(it as Int) } })
    }

    @Test
    fun `a well-formed perTest block parses through, entries and ambient both`() {
        val doc = minimalDocument()
        doc.add(
            "perTest",
            JsonObject().apply {
                addProperty("engine", "pitest")
                addProperty("engineVersion", "1.15.8")
                add(
                    "modules",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", "root")
                                add(
                                    "entries",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "dev.proofjava.playground.Calculator")
                                                addProperty("methodName", "add")
                                                add("lines", JsonArray().apply { add(perTestLine(7, "CalcTest#addsTwoNumbers()")) })
                                            },
                                        )
                                    },
                                )
                                add(
                                    "ambient",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "dev.proofjava.playground.Calculator")
                                                addProperty("methodName", "<clinit>")
                                                add("lines", JsonArray().apply { add(perTestLine(3, "CalcTest#addsTwoNumbers()")) })
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals("CalcTest#addsTwoNumbers()", parsed.perTest?.modules?.get(0)?.entries?.get(0)?.lines?.get(0)?.tests?.get(0))
        assertEquals("<clinit>", parsed.perTest?.modules?.get(0)?.ambient?.get(0)?.methodName)
    }

    @Test
    fun `D-86 perTest interned testIds resolve back to plain test-id strings`() {
        val doc = minimalDocument()
        doc.add(
            "perTest",
            JsonObject().apply {
                addProperty("engine", "pitest")
                addProperty("engineVersion", "1.15.8")
                add(
                    "modules",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", "root")
                                add("testIds", JsonArray().apply { add("CalcTest#addsTwoNumbers()"); add("CalcTest#subtractsTwoNumbers()") })
                                add(
                                    "entries",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "C"); addProperty("methodName", "add")
                                                add("lines", JsonArray().apply { add(perTestLine(7, 0, 1)) })
                                            },
                                        )
                                    },
                                )
                                add(
                                    "ambient",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "C"); addProperty("methodName", "<clinit>")
                                                add("lines", JsonArray().apply { add(perTestLine(3, 0)) })
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals(
            listOf("CalcTest#addsTwoNumbers()", "CalcTest#subtractsTwoNumbers()"),
            parsed.perTest?.modules?.get(0)?.entries?.get(0)?.lines?.get(0)?.tests,
        )
        assertEquals(listOf("CalcTest#addsTwoNumbers()"), parsed.perTest?.modules?.get(0)?.ambient?.get(0)?.lines?.get(0)?.tests)
    }

    @Test
    fun `a document with no perTest at all parses with it left null`() {
        assertNull(okOrFail(minimalDocument()).perTest)
    }

    @Test
    fun `a malformed perTest block (a line with no tests array) is rejected`() {
        val doc = minimalDocument()
        doc.add(
            "perTest",
            JsonObject().apply {
                addProperty("engine", "pitest")
                addProperty("engineVersion", "1.15.8")
                add(
                    "modules",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", "root")
                                add(
                                    "entries",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "C"); addProperty("methodName", "m")
                                                add("lines", JsonArray().apply { add(JsonObject().apply { addProperty("line", 1) }) })
                                            },
                                        )
                                    },
                                )
                                add("ambient", JsonArray())
                            },
                        )
                    },
                )
            },
        )
        assertTrue(parseVerdict(doc.toString()) is ParseResult.Error)
    }

    @Test
    fun `D-86 mutation interned testIds resolve back to plain test-id strings`() {
        val doc = minimalDocument()
        doc.add(
            "mutation",
            JsonObject().apply {
                addProperty("engine", "pitest")
                addProperty("engineVersion", "1.15.8")
                add(
                    "modules",
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("id", "root")
                                add("testIds", JsonArray().apply { add("CalcTest#addsTwoNumbers()"); add("CalcTest#subtractsTwoNumbers()") })
                                add(
                                    "methods",
                                    JsonArray().apply {
                                        add(
                                            JsonObject().apply {
                                                addProperty("className", "dev.proofjava.playground.Calculator")
                                                addProperty("methodName", "add")
                                                addProperty("methodDescription", "(II)I")
                                                addProperty("firstLine", 6)
                                                addProperty("lastLine", 8)
                                                add(
                                                    "mutants",
                                                    JsonArray().apply {
                                                        add(
                                                            JsonObject().apply {
                                                                addProperty("mutator", "PrimitiveReturnsMutator")
                                                                addProperty("line", 7)
                                                                addProperty("status", "KILLED")
                                                                add("killingTests", JsonArray().apply { add(1); add(0) })
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals(
            listOf("CalcTest#subtractsTwoNumbers()", "CalcTest#addsTwoNumbers()"),
            parsed.mutation?.modules?.get(0)?.methods?.get(0)?.mutants?.get(0)?.killingTests,
        )
    }

    @Test
    fun `coverage newCode as a real metricSet (a diff that ran fine) parses through`() {
        val doc = minimalDocument()
        val freshMetric = metric(numerator = 1, denominator = 4, percent = 25)
        doc.getAsJsonObject("coverage").add("newCode", metricSet(freshMetric))
        val parsed = okOrFail(doc)
        val newCode = parsed.coverage.newCode
        check(newCode is NewCodeCoverage.Metrics) { "expected a real metricSet, not a status object" }
        assertEquals(25.0, newCode.metricSet.engineLine.percent)
    }

    @Test
    fun `a well-formed finding parses through, including a SUBSUMED_TEST-only field`() {
        val doc = minimalDocument()
        doc.add(
            "findings",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("rule", "SUBSUMED_TEST")
                        addProperty("severity", "INFO")
                        addProperty("confidence", "MEDIUM")
                        addProperty("module", "root")
                        addProperty("path", "src/test/java/CalcTest.java")
                        addProperty("startLine", 10)
                        addProperty("endLine", 12)
                        addProperty("message", "dominated")
                        addProperty("suggestedAction", "consider removing")
                        addProperty("fingerprint", "abc123")
                        addProperty("testMethod", "narrowCase")
                        addProperty("relatedTestMethod", "wideCase")
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals(RuleId.SUBSUMED_TEST, parsed.findings[0].rule)
        assertEquals("wideCase", parsed.findings[0].relatedTestMethod)
    }

    @Test
    fun `a finding with an unrecognized rule id is rejected`() {
        val doc = minimalDocument()
        doc.add(
            "findings",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("rule", "NOT_A_REAL_RULE")
                        addProperty("severity", "WARNING")
                        addProperty("confidence", "HIGH")
                        addProperty("module", "root")
                        addProperty("path", "x")
                        addProperty("startLine", 1)
                        addProperty("endLine", 1)
                        addProperty("message", "m")
                        addProperty("suggestedAction", "s")
                        addProperty("fingerprint", "f")
                    },
                )
            },
        )
        assertTrue(parseVerdict(doc.toString()) is ParseResult.Error)
    }

    @Test
    fun `a well-formed mapped changedFile with uncoveredNewRanges parses through`() {
        val doc = minimalDocument()
        doc.add(
            "changedFiles",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("path", "src/main/java/Calc.java")
                        addProperty("module", "root")
                        addProperty("classification", "mapped")
                        addProperty("newLines", 14)
                        addProperty("coveredNewLines", 10)
                        add(
                            "uncoveredNewRanges",
                            JsonArray().apply {
                                add(JsonArray().apply { add(42); add(44) })
                                add(JsonArray().apply { add(51); add(51) })
                            },
                        )
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals(listOf(42 to 44, 51 to 51), parsed.changedFiles[0].uncoveredNewRanges)
    }

    @Test
    fun `an unmapped changedFile without the mapped-only fields still parses`() {
        val doc = minimalDocument()
        doc.add(
            "changedFiles",
            JsonArray().apply {
                add(JsonObject().apply { addProperty("path", "README.md"); addProperty("classification", "unsupported") })
            },
        )
        assertTrue(parseVerdict(doc.toString()) is ParseResult.Ok)
    }

    @Test
    fun `warnings carry through with their optional path, module and count fields`() {
        val doc = minimalDocument()
        doc.add(
            "warnings",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("code", "PER_TEST_TRUNCATED")
                        addProperty("message", "evidence dropped")
                        addProperty("module", "root")
                        addProperty("count", 3)
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals("PER_TEST_TRUNCATED", parsed.warnings[0].code)
        assertEquals(3L, parsed.warnings[0].count)
    }

    /**
     * Regression test for a real bug a live Deep Scan caught: `analysis.incompleteReasons`
     * items are the exact same `Reason` shape `warnings[]` uses (schema
     * `$defs/reason`, confirmed against the real schema before fixing this) -
     * this used to be typed `List<String>` and parsed with a bare
     * `it.toString()`, so a real reason like `PER_TEST_JDK_UNSUPPORTED`
     * came through as raw untyped JSON text instead of a queryable `Reason`.
     */
    @Test
    fun `analysis_incompleteReasons parses as real Reason objects, not raw JSON text`() {
        val doc = minimalDocument()
        (doc.getAsJsonObject("analysis")).add(
            "incompleteReasons",
            JsonArray().apply {
                add(
                    JsonObject().apply {
                        addProperty("code", "PER_TEST_JDK_UNSUPPORTED")
                        addProperty("message", "--per-test-report needs a JDK of 22 or lower; this analysis is running on 25.")
                    },
                )
            },
        )
        val parsed = okOrFail(doc)
        assertEquals(1, parsed.analysis.incompleteReasons.size)
        assertEquals("PER_TEST_JDK_UNSUPPORTED", parsed.analysis.incompleteReasons[0].code)
        assertTrue(parsed.analysis.incompleteReasons[0].message.contains("JDK"))
    }

    @Test
    fun `never throws on a completely empty string either`() {
        val result = parseVerdict("")
        assertFalse(result is ParseResult.Ok)
    }

    @Test
    fun `a proof-python verdict keeps its own engine mode id`() {
        // The first mode is named after the engine whose counter it
        // reproduces (proof-java D-99). Reading a sibling's document must not
        // relabel it with this engine's name, and must not fail either.
        val document = minimalDocument().apply {
            add("tool", JsonObject().apply { addProperty("name", "proof-python"); addProperty("version", "0.1.0") })
            add("coverage", JsonObject().apply {
                add("overall", metricSet(engineMode = "coverage-line"))
                add("newCode", JsonObject().apply { addProperty("status", "unavailable_no_vcs") })
            })
        }
        val parsed = okOrFail(document)
        assertEquals("coverage-line", parsed.coverage.overall.engineModeId)
        assertEquals(1L, parsed.coverage.overall.engineLine.numerator)
    }

    @Test
    fun `a proof-java verdict still reports jacoco-line`() {
        assertEquals("jacoco-line", okOrFail(minimalDocument()).coverage.overall.engineModeId)
    }
}
