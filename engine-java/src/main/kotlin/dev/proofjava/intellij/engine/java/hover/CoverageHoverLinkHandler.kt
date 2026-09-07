package dev.proofjava.intellij.engine.java.hover

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import java.net.URI
import java.net.URLDecoder

/**
 * Resolves [navLink]'s `coverage-nav://open?file=...&line=...` URIs -
 * the IntelliJ equivalent of the TS source's `command:vscode.open?...`
 * links, which VS Code resolves through its own built-in command registry.
 * IntelliJ's `DocumentationLinkHandler` has no such built-in "open this
 * file at this line" command target, so this handler does the navigation
 * itself as a side effect and returns `null` (no new documentation target
 * to load - this link's whole purpose is the navigation, not showing more
 * hover content).
 */
class CoverageHoverLinkHandler : DocumentationLinkHandler {

    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (!url.startsWith("coverage-nav://")) {
            return null
        }
        val hoverTarget = target as? CoverageHoverTarget ?: return null
        val query = URI(url).query ?: return null
        val params = query.split("&").associate { pair ->
            val parts = pair.split("=", limit = 2)
            parts[0] to parts.getOrElse(1) { "" }
        }
        val filePath = params["file"]?.let { URLDecoder.decode(it, "UTF-8") } ?: return null
        val line = params["line"]?.toIntOrNull() ?: 1
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null

        ApplicationManager.getApplication().invokeLater {
            OpenFileDescriptor(hoverTarget.project, virtualFile, line - 1, 0).navigate(true)
        }
        return null
    }
}
