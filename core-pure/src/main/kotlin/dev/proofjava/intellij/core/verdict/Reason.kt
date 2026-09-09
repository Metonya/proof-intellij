package dev.proofjava.intellij.core.verdict

data class Reason(
    val code: String,
    val message: String,
    val path: String? = null,
    val module: String? = null,
    val count: Long? = null,
)
