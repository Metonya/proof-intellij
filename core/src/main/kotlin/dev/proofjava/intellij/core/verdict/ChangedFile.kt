package dev.proofjava.intellij.core.verdict

enum class ChangedFileClassification(val wireValue: String) {
    MAPPED("mapped"),
    EXCLUDED("excluded"),
    NON_EXECUTABLE("non-executable"),
    UNSUPPORTED("unsupported"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromWireValue(value: String): ChangedFileClassification? = entries.find { it.wireValue == value }
    }
}

/**
 * One file touched by the diff. Port of `verdict/types.ts`'s `ChangedFile`:
 * [newLines]/[coveredNewLines]/[uncoveredNewRanges] are present only when
 * [classification] is [ChangedFileClassification.MAPPED]. There is no
 * per-line "covered and new" list in the schema - only
 * [uncoveredNewRanges] enumerates actual line numbers; a "new and covered"
 * gutter decoration cannot be derived from this data alone.
 */
data class ChangedFile(
    val path: String,
    val module: String? = null,
    val classification: ChangedFileClassification,
    val newLines: Long? = null,
    val coveredNewLines: Long? = null,
    val uncoveredNewRanges: List<Pair<Int, Int>>? = null,
)
