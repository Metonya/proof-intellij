package dev.proofjava.intellij.core.verdict

/** The six rule codes the L0 oracle-quality engine can emit (`RuleIds.java`) - port of `verdict/types.ts`'s `RuleId` union. Enum constant names equal the wire strings verbatim, so [VerdictParser] matches by name, never a hand-maintained string table. */
enum class RuleId {
    NO_RECOGNIZED_ORACLE,
    TAUTOLOGICAL_ORACLE,
    CATCH_ORACLE_WITHOUT_FAIL,
    NULL_CHECK_ONLY,
    PSEUDO_TESTED_METHOD,
    SUBSUMED_TEST,
}

enum class Severity { INFO, WARNING }

/** NOT the same field as [Severity] - the CLI's stdout text report shows confidence next to each finding, easy to mistake for severity when building a UI from memory of that output (same warning as the TS source). */
enum class Confidence { HIGH, MEDIUM, LOW, INCONCLUSIVE }

/** A test-oracle-quality finding. Port of `verdict/types.ts`'s `Finding`. */
data class Finding(
    val rule: RuleId,
    val severity: Severity,
    val confidence: Confidence,
    val module: String,
    val path: String,
    val startLine: Int,
    val endLine: Int,
    val message: String,
    val suggestedAction: String,
    val fingerprint: String,
    val testMethod: String? = null,
    val productionMethod: String? = null,
    /** SUBSUMED_TEST only. */
    val relatedTestMethod: String? = null,
    /** SUBSUMED_TEST only. */
    val relatedPath: String? = null,
)
