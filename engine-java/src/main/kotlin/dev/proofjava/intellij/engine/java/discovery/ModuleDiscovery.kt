package dev.proofjava.intellij.engine.java.discovery

/**
 * Port of `proof-vscode/src/cli/reportDiscovery.ts`. Pure: infers module
 * bindings from discovered report/pom paths, or parses `settings.gradle(.kts)`
 * text - never touches the filesystem itself (the caller does the actual
 * glob/read).
 *
 * Why this exists: a `reportPath` setting is resolved relative to the
 * project root, which is correct for a single-module repo but breaks the
 * moment someone opens a multi-module Maven checkout at its aggregator
 * root - the aggregator pom has no report of its own. A second distinction
 * matters too: a directory holding several *unrelated* repos side by side
 * is not the same shape as one real multi-module project, even though both
 * can produce several `jacoco.xml` candidates - [isProjectRoot]/
 * [describeSiblingProjects] tell them apart, so unrelated repos are never
 * offered as if picking among them were a real choice.
 */

/** Every known "report path ends here" convention this engine can attribute to a module root, tried in order. Deliberately not attempting Android/AGP's variant-named paths - the variant segment makes a single fixed suffix meaningless, and a wrong guess would silently bind the wrong module. */
private val KNOWN_REPORT_SUFFIXES = listOf(
    "/target/site/jacoco/jacoco.xml",
    "/build/reports/jacoco/test/jacocoTestReport.xml",
)
private const val JACOCO_AT_ROOT = "target/site/jacoco/jacoco.xml"
private const val GRADLE_JACOCO_AT_ROOT = "build/reports/jacoco/test/jacocoTestReport.xml"

/**
 * Always `id == "root"` - the same id every other flow (state keys,
 * `--per-test-classpath`/`--mutation-classpath` bindings) hardcodes for the
 * single-module fast path. Only the module's own root path varies with
 * discovery.
 */
data class DiscoveredModule(val id: String, val root: String, val reportPath: String)

data class ModuleRoot(val id: String, val root: String)

/** Only reports found at the standard Maven+JaCoCo layout (`<module>/target/site/jacoco/jacoco.xml`, or the Gradle plain-Java equivalent) are attributed to a module root; anything else binds at the repo root rather than guessing at an unfamiliar layout. [repoRelativeReportPath] must already use forward slashes. */
fun describeModuleForReport(repoRelativeReportPath: String): ModuleRoot {
    if (repoRelativeReportPath == JACOCO_AT_ROOT || repoRelativeReportPath == GRADLE_JACOCO_AT_ROOT) {
        return ModuleRoot("root", ".")
    }
    val suffix = KNOWN_REPORT_SUFFIXES.find { repoRelativeReportPath.endsWith(it) } ?: return ModuleRoot("root", ".")
    return ModuleRoot("root", repoRelativeReportPath.dropLast(suffix.length))
}

/** Binds every discovered report to a real, distinct module id - the multi-module counterpart to [describeModuleForReport]'s single-report shorthand. */
fun bindModules(repoRelativeReportPaths: List<String>): List<DiscoveredModule> {
    val roots = repoRelativeReportPaths.map { describeModuleForReport(it).root }
    return assignIds(roots).mapIndexed { i, assigned -> DiscoveredModule(assigned.id, assigned.root, repoRelativeReportPaths[i]) }
}

/** A pom.xml's own repo-relative path directly names its module root - unlike a jacoco.xml, no report has to exist first. Returns just the root (no id yet - [discoverModuleRootsFromPoms] assigns one across the whole set via [assignIds]), matching the TS source's own `{root}`-only shape here. */
fun describeModuleForPom(repoRelativePomPath: String): String =
    if (repoRelativePomPath == "pom.xml") "." else repoRelativePomPath.removeSuffix("/pom.xml")

/** The pom.xml counterpart to [bindModules] - same id-assignment rule, different discovery input. */
fun discoverModuleRootsFromPoms(repoRelativePomPaths: List<String>): List<ModuleRoot> =
    assignIds(repoRelativePomPaths.map { describeModuleForPom(it) })

/**
 * Matches `include` and any same-purpose wrapper function whose name
 * starts with it (`includeProject(...)`), excluding real Gradle APIs that
 * are not subprojects of this build: `includeBuild` (a composite build),
 * `includeFlat` (a sibling directory, `../name`, which a repo-relative
 * path cannot express), and `includeGroup`/`includeModule`/`includeVersion`
 * (repository content-filter methods - Google's own Now in Android has
 * `includeGroupByRegex(...)`, which read as a project until it did not).
 * Deliberately identical to `GradleProjectScanner.java`'s own
 * `INCLUDE_KEYWORD` in the CLI - both run on the same settings files and
 * must agree about which modules exist.
 */
private val GRADLE_INCLUDE_KEYWORD = Regex("""\binclude(?!Build\b)(?!Flat\b)(?!Group)(?!Module)(?!Version)[A-Za-z]*\b""")
private val GRADLE_QUOTED_ARG = Regex("""['"]([^'"]+)['"]""")

/** Raw Gradle project paths (`:core`, `:modules:service-a`) as literally written in a `settings.gradle(.kts)`. A wrapper function's own definition line carries no quoted argument, so it contributes nothing - only real call sites do. */
fun parseSettingsGradleProjectPaths(settingsText: String): List<String> {
    val paths = mutableListOf<String>()
    for (line in settingsText.split('\n')) {
        if (!GRADLE_INCLUDE_KEYWORD.containsMatchIn(line)) continue
        for (match in GRADLE_QUOTED_ARG.findAll(line)) {
            paths += match.groupValues[1]
        }
    }
    return paths
}

/**
 * The `settings.gradle(.kts)` counterpart to [discoverModuleRootsFromPoms].
 * Gradle's root project is not declared by an `include(...)` at all, and
 * whether it is a real module worth running tests in is a filesystem fact
 * (does it have test sources of its own?) this pure function cannot see -
 * hence [includeRootProject], decided by the caller.
 */
fun discoverModuleRootsFromSettingsGradle(settingsText: String, includeRootProject: Boolean = false): List<ModuleRoot> {
    val roots = mutableListOf<String>()
    if (includeRootProject) roots += "."
    for (gradlePath in parseSettingsGradleProjectPaths(settingsText)) {
        val root = (if (gradlePath.startsWith(":")) gradlePath.substring(1) else gradlePath).replace(":", "/")
        if (root.isEmpty() || isEscapingRepoRoot(root) || !isPlausibleDirectoryPath(root) || roots.contains(root)) continue
        roots += root
    }
    return assignIds(roots)
}

/** A quoted string on an `include`-ish line is not automatically a directory name: a glob/regex character means the line was something else. Mirrors the CLI's own scanner check exactly. */
private fun isPlausibleDirectoryPath(candidate: String): Boolean =
    !Regex("""[*?"<>|]""").containsMatchIn(candidate) && candidate.codePoints().noneMatch { it < 0x20 }

/** The Kotlin twin of the CLI's `RepoPaths.isEscapingRepoRoot` - an absolute path, or one that climbs above the repo root once `.`/`..` collapse, is never a module of this repo. */
private fun isEscapingRepoRoot(normalizedPath: String): Boolean {
    if (normalizedPath.startsWith("/") || Regex("""^[A-Za-z]:""").containsMatchIn(normalizedPath)) return true
    var depth = 0
    for (segment in normalizedPath.split("/")) {
        when (segment) {
            "", "." -> continue
            ".." -> {
                depth--
                if (depth < 0) return true
            }
            else -> depth++
        }
    }
    return false
}

/** Shared by [bindModules]/[discoverModuleRootsFromPoms]/[discoverModuleRootsFromSettingsGradle]: base id from the root's own last path segment, a numeric suffix on a real collision rather than silently merging two modules. */
private fun assignIds(roots: List<String>): List<ModuleRoot> {
    val usedIds = mutableSetOf<String>()
    return roots.map { root ->
        val base = if (root == ".") "root" else sanitizeModuleId(root.substringAfterLast('/'))
        var id = base
        var suffix = 2
        while (usedIds.contains(id)) {
            id = "$base-${suffix++}"
        }
        usedIds += id
        ModuleRoot(id, root)
    }
}

/** picocli's `<id>=<value>` parsing splits on the first `=`; also kept free of characters that would make a log line or a shell-quoted arg confusing. */
private fun sanitizeModuleId(raw: String): String {
    val cleaned = raw.replace(Regex("""[^A-Za-z0-9_.-]"""), "-")
    return cleaned.ifEmpty { "module" }
}

/** Which bound module a repo-relative path falls under - longest-root-prefix wins, `root == "."` is the lowest-priority fallback since it matches everything. `null` means the path is not under any bound module's root at all - callers must not guess a target's module in that case (hard rule 3a). */
fun moduleForPath(repoRelativePath: String, modules: List<ModuleRoot>): String? {
    var bestId: String? = null
    var bestPrefixLength = -1
    for (m in modules) {
        if (m.root == ".") {
            if (bestId == null) {
                bestId = m.id
                bestPrefixLength = 0
            }
            continue
        }
        val prefix = "${m.root}/"
        if (repoRelativePath.startsWith(prefix) && prefix.length > bestPrefixLength) {
            bestId = m.id
            bestPrefixLength = prefix.length
        }
    }
    return bestId
}

/** Converts an absolute filesystem path to a repo-relative, forward-slash path. Platform-agnostic: never assumes `/` is already there. */
fun toRepoRelativePosix(absolutePath: String, repoRoot: String): String {
    val normalizedAbs = absolutePath.replace('\\', '/')
    val normalizedRoot = repoRoot.replace('\\', '/').trimEnd('/')
    return if (normalizedAbs.startsWith("$normalizedRoot/")) normalizedAbs.substring(normalizedRoot.length + 1) else normalizedAbs
}

/** Filenames whose presence at a directory means "this directory is itself one Maven/Gradle project" (single- or multi-module). The caller checks these against the real filesystem. */
val PROJECT_ROOT_MARKER_FILES: List<String> = listOf("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts")

/** The real distinction: "this folder is one multi-module project, pick which module" vs. "this folder just happens to contain several unrelated repos side by side". [presentMarkers] is whatever subset of [PROJECT_ROOT_MARKER_FILES] the caller found at a given directory. */
fun isProjectRoot(presentMarkers: List<String>): Boolean = PROJECT_ROOT_MARKER_FILES.any { presentMarkers.contains(it) }

/** The message shown when the project root is not itself a project and one or more of its immediate children are. Lists the candidates by name only - it never picks one, because there is no honest way to prefer one independent repo over another. */
fun describeSiblingProjects(projectDirNames: List<String>): String =
    "Proof: this folder is not itself a single project - found ${projectDirNames.size} different project(s) inside it: ${projectDirNames.joinToString(", ")}. " +
        "Open the project you want to analyze as a separate project root instead."
