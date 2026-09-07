package dev.proofjava.intellij.core.verdict

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException

/**
 * Port of `proof-vscode/src/verdict/parse.ts`'s read path
 * (`parseVerdict`/`isVerdictDocument`/... and the D-86 test-id-interning
 * resolve step). Never throws (same invariant as the TS source): a
 * malformed or truncated verdict file is a real, expected shape (a killed
 * process, a disk full mid-write), never an exception the caller must
 * remember to catch.
 *
 * Deliberately NOT ported yet: `reinternPerTestIds`/`reinternMutationTestIds`
 * and the standalone `isPerTestBlock`/`isMutationBlock` exports. Those exist
 * in `proof-vscode` to support the `.proof` directory's JSON snapshot
 * validation and Export Report's write-back to `render-html` - both later
 * milestones (M3's snapshot persistence, M8's Export Report) that don't
 * have a caller yet.
 * Porting them now, before there is a concrete Kotlin caller or even a
 * decision on how snapshots round-trip, would be exactly the kind of
 * ahead-of-need work this project's own plan explicitly avoids elsewhere.
 */

sealed interface ParseResult<out T> {
    data class Ok<T>(val value: T) : ParseResult<T>
    data class Error(val message: String) : ParseResult<Nothing>
}

private val KNOWN_RULE_IDS = RuleId.entries.associateBy { it.name }

fun parseVerdict(raw: String): ParseResult<VerdictDocument> {
    val root: JsonElement = try {
        JsonParser.parseString(raw)
    } catch (e: JsonSyntaxException) {
        return ParseResult.Error("invalid JSON: ${e.message}")
    }
    val obj = root.asObjectOrNull()
        ?: return ParseResult.Error("does not look like a proof-java verdict document (missing a required top-level field)")

    val document = parseVerdictDocument(obj)
        ?: return ParseResult.Error("does not look like a proof-java verdict document (missing a required top-level field)")

    val fileCoverageElement = obj.get("fileCoverage")
    val fileCoverage = if (fileCoverageElement != null && !fileCoverageElement.isJsonNull) {
        parseFileCoverageBlock(fileCoverageElement.asObjectOrNull())
            ?: return ParseResult.Error("fileCoverage is present but malformed")
    } else {
        null
    }

    val perTestElement = obj.get("perTest")
    val perTest = if (perTestElement != null && !perTestElement.isJsonNull) {
        parsePerTestBlock(perTestElement.asObjectOrNull())
            ?: return ParseResult.Error("perTest is present but malformed")
    } else {
        null
    }

    val mutationElement = obj.get("mutation")
    val mutation = if (mutationElement != null && !mutationElement.isJsonNull) {
        parseMutationBlock(mutationElement.asObjectOrNull())
            ?: return ParseResult.Error("mutation is present but malformed")
    } else {
        null
    }

    return ParseResult.Ok(document.copy(fileCoverage = fileCoverage, perTest = perTest, mutation = mutation))
}

private fun parseVerdictDocument(obj: JsonObject): VerdictDocument? {
    val schemaVersion = obj.stringOrNull("schemaVersion") ?: return null

    val toolObj = obj.obj("tool") ?: return null
    val toolVersion = toolObj.stringOrNull("version") ?: return null
    val tool = ToolInfo(name = toolObj.string("name"), version = toolVersion)

    val analysisObj = obj.obj("analysis") ?: return null
    val status = analysisObj.stringOrNull("status")?.let { AnalysisStatus.fromWireValue(it) } ?: return null
    val analysis = Analysis(
        status = status,
        exitCode = analysisObj.longOrNull("exitCode")?.toInt() ?: return null,
        incompleteReasons = (analysisObj.array("incompleteReasons") ?: return null).map { parseReason(it.asObjectOrNull()) ?: return null },
    )

    val inputsObj = obj.obj("inputs") ?: return null
    val modulesArray = inputsObj.array("modules") ?: return null
    val modules = modulesArray.map { parseModuleInput(it.asObjectOrNull()) ?: return null }
    val inputs = Inputs(modules)

    val coverageObj = obj.obj("coverage") ?: return null
    val overall = parseMetricSet(coverageObj.obj("overall")) ?: return null
    val newCode = parseNewCodeCoverage(coverageObj.get("newCode")) ?: return null
    val coverage = Coverage(overall, newCode)

    val changedFilesArray = obj.array("changedFiles") ?: return null
    val changedFiles = changedFilesArray.map { parseChangedFile(it.asObjectOrNull()) ?: return null }

    val findingsArray = obj.array("findings") ?: return null
    val findings = findingsArray.map { parseFinding(it.asObjectOrNull()) ?: return null }

    val warningsArray = obj.array("warnings") ?: return null
    val warnings = warningsArray.map { parseReason(it.asObjectOrNull()) ?: return null }

    return VerdictDocument(
        schemaVersion = schemaVersion,
        tool = tool,
        analysis = analysis,
        inputs = inputs,
        coverage = coverage,
        changedFiles = changedFiles,
        findings = findings,
        warnings = warnings,
    )
}

private fun parseModuleInput(obj: JsonObject?): ModuleInput? {
    if (obj == null) return null
    val id = obj.stringOrNull("id") ?: return null
    val root = obj.stringOrNull("root") ?: return null
    val sourceRoots = (obj.array("sourceRoots") ?: return null).map { it.asStringOrNull() ?: return null }
    val testRoots = (obj.array("testRoots") ?: return null).map { it.asStringOrNull() ?: return null }
    return ModuleInput(id, root, sourceRoots, testRoots)
}

private fun parseReason(obj: JsonObject?): Reason? {
    if (obj == null) return null
    val code = obj.stringOrNull("code") ?: return null
    val message = obj.stringOrNull("message") ?: return null
    return Reason(code, message, obj.stringOrNull("path"), obj.stringOrNull("module"), obj.longOrNull("count"))
}

private fun parseMetric(obj: JsonObject?): Metric? {
    if (obj == null) return null
    // Mirrors `isMetric` in the TS source exactly: only numerator/denominator/percent
    // are actually validated there (a real, existing gap - *Name fields are typed
    // but not runtime-checked), so this port does not tighten that behavior.
    val numerator = obj.longOrNull("numerator") ?: return null
    val denominator = obj.longOrNull("denominator") ?: return null
    val percent = when (val p = obj.percentField("percent")) {
        is PercentValue.Null -> null
        is PercentValue.Present -> p.value
        null -> return null
    }
    return Metric(
        numeratorName = obj.string("numeratorName"),
        numerator = numerator,
        denominatorName = obj.string("denominatorName"),
        denominator = denominator,
        percent = percent,
    )
}

private fun parseMetricSet(obj: JsonObject?): MetricSet? {
    if (obj == null) return null
    val jacoco = parseMetric(obj.obj("jacoco-line")) ?: return null
    val strict = parseMetric(obj.obj("strict-line")) ?: return null
    val sonar = parseMetric(obj.obj("sonar-compatible")) ?: return null
    return MetricSet(jacoco, strict, sonar)
}

private fun parseNewCodeCoverage(element: JsonElement?): NewCodeCoverage? {
    val obj = element.asObjectOrNull() ?: return null
    parseMetricSet(obj)?.let { return NewCodeCoverage.Metrics(it) }
    val status = obj.stringOrNull("status") ?: return null
    return NewCodeCoverage.Status(status)
}

private fun parseChangedFile(obj: JsonObject?): ChangedFile? {
    if (obj == null) return null
    val path = obj.stringOrNull("path") ?: return null
    val classification = obj.stringOrNull("classification")?.let { ChangedFileClassification.fromWireValue(it) } ?: return null
    val ranges = obj.array("uncoveredNewRanges")?.map { rangeElement ->
        val rangeArray = rangeElement.asArrayOrNull() ?: return null
        if (rangeArray.size() != 2) return null
        Pair(rangeArray[0].asIntOrNull() ?: return null, rangeArray[1].asIntOrNull() ?: return null)
    }
    return ChangedFile(
        path = path,
        module = obj.stringOrNull("module"),
        classification = classification,
        newLines = obj.longOrNull("newLines"),
        coveredNewLines = obj.longOrNull("coveredNewLines"),
        uncoveredNewRanges = ranges,
    )
}

private fun parseFinding(obj: JsonObject?): Finding? {
    if (obj == null) return null
    val ruleRaw = obj.stringOrNull("rule") ?: return null
    val rule = KNOWN_RULE_IDS[ruleRaw] ?: return null
    val severity = obj.stringOrNull("severity")?.let { runCatching { Severity.valueOf(it) }.getOrNull() } ?: return null
    val confidence = obj.stringOrNull("confidence")?.let { runCatching { Confidence.valueOf(it) }.getOrNull() } ?: return null
    val module = obj.stringOrNull("module") ?: return null
    val path = obj.stringOrNull("path") ?: return null
    val startLine = obj.longOrNull("startLine")?.toInt() ?: return null
    val endLine = obj.longOrNull("endLine")?.toInt() ?: return null
    val message = obj.stringOrNull("message") ?: return null
    val suggestedAction = obj.stringOrNull("suggestedAction") ?: return null
    val fingerprint = obj.stringOrNull("fingerprint") ?: return null
    return Finding(
        rule = rule,
        severity = severity,
        confidence = confidence,
        module = module,
        path = path,
        startLine = startLine,
        endLine = endLine,
        message = message,
        suggestedAction = suggestedAction,
        fingerprint = fingerprint,
        testMethod = obj.stringOrNull("testMethod"),
        productionMethod = obj.stringOrNull("productionMethod"),
        relatedTestMethod = obj.stringOrNull("relatedTestMethod"),
        relatedPath = obj.stringOrNull("relatedPath"),
    )
}

private fun parseLineTuple(element: JsonElement): LineTuple? {
    val array = element.asArrayOrNull() ?: return null
    if (array.size() != 5) return null
    return LineTuple(
        array[0].asIntOrNull() ?: return null,
        array[1].asIntOrNull() ?: return null,
        array[2].asIntOrNull() ?: return null,
        array[3].asIntOrNull() ?: return null,
        array[4].asIntOrNull() ?: return null,
    )
}

private fun parseFileCoverageEntry(obj: JsonObject?): FileCoverageEntry? {
    if (obj == null) return null
    val module = obj.stringOrNull("module") ?: return null
    val path = obj.stringOrNull("path") ?: return null
    val metrics = parseMetricSet(obj.obj("metrics")) ?: return null
    val lines = (obj.array("lines") ?: return null).map { parseLineTuple(it) ?: return null }
    return FileCoverageEntry(module, path, metrics, lines)
}

private fun parseFileCoverageBlock(obj: JsonObject?): FileCoverageBlock? {
    if (obj == null) return null
    val files = (obj.array("files") ?: return null).map { parseFileCoverageEntry(it.asObjectOrNull()) ?: return null }
    val excluded = (obj.array("excluded") ?: return null).map { it.asStringOrNull() ?: return null }
    return FileCoverageBlock(files, excluded)
}

/**
 * D-86 (proof-java): a module's `tests: number[]` are indexes into its own
 * `testIds: string[]` array - resolved back to plain strings here so every
 * downstream consumer of [PerTestEntry]/[PerTestLine] only ever sees plain
 * strings, mirroring `resolveInternedTestIds` in the TS source. A module
 * with no `testIds` (an older proof-java build, pre-D-86) is assumed to
 * already carry plain strings.
 */
private fun resolveTestId(element: JsonElement, testIds: List<String>?): String? = when {
    element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
    element.isJsonPrimitive && element.asJsonPrimitive.isNumber && testIds != null -> testIds.getOrNull(element.asInt)
    else -> null
}

private fun parsePerTestLine(obj: JsonObject?, testIds: List<String>?): PerTestLine? {
    if (obj == null) return null
    val line = obj.longOrNull("line")?.toInt() ?: return null
    val tests = (obj.array("tests") ?: return null).map { resolveTestId(it, testIds) ?: return null }
    return PerTestLine(line, tests)
}

private fun parsePerTestEntry(obj: JsonObject?, testIds: List<String>?): PerTestEntry? {
    if (obj == null) return null
    val className = obj.stringOrNull("className") ?: return null
    val methodName = obj.stringOrNull("methodName") ?: return null
    val lines = (obj.array("lines") ?: return null).map { parsePerTestLine(it.asObjectOrNull(), testIds) ?: return null }
    return PerTestEntry(className, methodName, lines)
}

private fun parsePerTestModuleEvidence(obj: JsonObject?): PerTestModuleEvidence? {
    if (obj == null) return null
    val id = obj.stringOrNull("id") ?: return null
    val testIds = obj.array("testIds")?.map { it.asStringOrNull() ?: return null }
    val entries = (obj.array("entries") ?: return null).map { parsePerTestEntry(it.asObjectOrNull(), testIds) ?: return null }
    val ambient = (obj.array("ambient") ?: return null).map { parsePerTestEntry(it.asObjectOrNull(), testIds) ?: return null }
    return PerTestModuleEvidence(id, entries, ambient)
}

private fun parsePerTestBlock(obj: JsonObject?): PerTestBlock? {
    if (obj == null) return null
    val engine = obj.stringOrNull("engine") ?: return null
    val engineVersion = obj.stringOrNull("engineVersion") ?: return null
    val modules = (obj.array("modules") ?: return null).map { parsePerTestModuleEvidence(it.asObjectOrNull()) ?: return null }
    return PerTestBlock(engine, engineVersion, modules)
}

private fun parseMutant(obj: JsonObject?, testIds: List<String>?): Mutant? {
    if (obj == null) return null
    val mutator = obj.stringOrNull("mutator") ?: return null
    val line = obj.longOrNull("line")?.toInt() ?: return null
    val status = obj.stringOrNull("status") ?: return null
    val killingTests = (obj.array("killingTests") ?: return null).map { resolveTestId(it, testIds) ?: return null }
    return Mutant(mutator, line, status, killingTests)
}

private fun parseMutatedMethod(obj: JsonObject?, testIds: List<String>?): MutatedMethod? {
    if (obj == null) return null
    val className = obj.stringOrNull("className") ?: return null
    val methodName = obj.stringOrNull("methodName") ?: return null
    val methodDescription = obj.stringOrNull("methodDescription") ?: return null
    val firstLine = obj.longOrNull("firstLine")?.toInt() ?: return null
    val lastLine = obj.longOrNull("lastLine")?.toInt() ?: return null
    val mutants = (obj.array("mutants") ?: return null).map { parseMutant(it.asObjectOrNull(), testIds) ?: return null }
    return MutatedMethod(className, methodName, methodDescription, firstLine, lastLine, mutants)
}

private fun parseMutationModuleEvidence(obj: JsonObject?): MutationModuleEvidence? {
    if (obj == null) return null
    val id = obj.stringOrNull("id") ?: return null
    val testIds = obj.array("testIds")?.map { it.asStringOrNull() ?: return null }
    val methods = (obj.array("methods") ?: return null).map { parseMutatedMethod(it.asObjectOrNull(), testIds) ?: return null }
    return MutationModuleEvidence(id, methods)
}

private fun parseMutationBlock(obj: JsonObject?): MutationBlock? {
    if (obj == null) return null
    val engine = obj.stringOrNull("engine") ?: return null
    val engineVersion = obj.stringOrNull("engineVersion") ?: return null
    val modules = (obj.array("modules") ?: return null).map { parseMutationModuleEvidence(it.asObjectOrNull()) ?: return null }
    return MutationBlock(engine, engineVersion, modules)
}

/**
 * Port of `proof-vscode/src/ui/commands.ts`'s `PerTestSnapshot` - the
 * `.proof/pertest-current.json` shape a Deep Scan writes and a later
 * project open restores from, independent of `verdict-current.json`
 * (mirrors the TS source's own "each snapshot lives in its own file, one
 * being missing/corrupt must not take the others down with it" design).
 * Not built until a real caller existed (M6's Deep Scan action, plus a
 * direct 2026-09-07 user request after comparing against the real
 * `proof-vscode` behavior side by side) - this file's own header comment
 * flagged it as deliberately deferred, not forgotten, until then.
 */
data class PerTestSnapshot(
    val perTest: PerTestBlock,
    val warnings: List<Reason>,
    val targets: List<String>,
    val ranAtMs: Long,
)

/** Plain Gson reflection serialization is fine here (unlike the read path's D-40-driven manual parsing) - this writes our own already-validated Kotlin objects, never untrusted external input. */
fun writePerTestSnapshotJson(snapshot: PerTestSnapshot): String = Gson().toJson(snapshot)

fun parsePerTestSnapshot(raw: String): PerTestSnapshot? {
    val root: JsonElement = try {
        JsonParser.parseString(raw)
    } catch (e: JsonSyntaxException) {
        return null
    }
    val obj = root.asObjectOrNull() ?: return null
    val perTest = parsePerTestBlock(obj.obj("perTest")) ?: return null
    val warnings = (obj.array("warnings") ?: return null).map { parseReason(it.asObjectOrNull()) ?: return null }
    val targets = (obj.array("targets") ?: return null).map { it.asStringOrNull() ?: return null }
    val ranAtMs = obj.longOrNull("ranAtMs") ?: return null
    return PerTestSnapshot(perTest, warnings, targets, ranAtMs)
}
