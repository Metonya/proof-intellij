package dev.proofjava.intellij.engine.java.source

/**
 * Port of `proof-vscode/src/model/classNameDetector.ts`. Best-effort FQCN
 * for a Java source file: the file's own `package` declaration plus its
 * base name (the standard one-public-top-level-class-per-file convention
 * `perTest`'s `className` values already assume). Deliberately narrow: a
 * file with more than one top-level class, or a nonstandard filename/
 * class-name mismatch, is not detected here - degrades to "class not
 * found in perTest evidence" rather than guessing which class it meant
 * (hard rule 3a).
 */
private val PACKAGE_DECLARATION = Regex("""^\s*package\s+([\w.]+)\s*;""", RegexOption.MULTILINE)

fun detectClassName(sourceText: String, fileBaseNameWithoutExtension: String): String {
    val match = PACKAGE_DECLARATION.find(sourceText)
    return if (match != null) "${match.groupValues[1]}.$fileBaseNameWithoutExtension" else fileBaseNameWithoutExtension
}
