package dev.proofjava.intellij.engine.java.hover

import com.intellij.model.Pointer
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation

/**
 * Wraps an already-computed [HoverContent] - all the real work happened in
 * [CoverageHoverTargetProvider.documentationTargets], so this class only
 * renders it. [content] is plain immutable data (never PSI-derived), so a
 * [Pointer.hardPointer] is correct: unlike a PSI-backed target, there is
 * nothing here that could be invalidated by a write action between the
 * initial hover and a later re-dereference.
 */
class CoverageHoverTarget(val project: Project, private val content: HoverContent) : DocumentationTarget {

    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(content.title).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(content.html)
}
