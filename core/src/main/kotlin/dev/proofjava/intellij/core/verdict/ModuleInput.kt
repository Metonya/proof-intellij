package dev.proofjava.intellij.core.verdict

data class ModuleInput(
    val id: String,
    val root: String,
    val sourceRoots: List<String>,
    val testRoots: List<String>,
)
