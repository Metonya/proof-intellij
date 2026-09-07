package dev.proofjava.intellij.core.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Hand-written holder, not the codegen `XxxIcons.java` machinery bundled
 * plugins use (that relies on `IconManager.loadRasterizedIcon`, an
 * `@ApiStatus.Internal` API not meant for 3rd-party plugins - verified
 * against real bundled examples in `JetBrains/intellij-community`, e.g.
 * `GithubIcons.java`'s own "DO NOT EDIT IT BY HAND" header). `IconLoader.getIcon`
 * is the real, public, standard API for this instead. `ToolWindow` is the
 * plugin's own SVG port of `proof-vscode`'s `media/activity-bar-icon.svg` -
 * `_dark.svg` companion picked up automatically by IntelliJ's own
 * light/dark icon convention (same one `pluginIcon.svg`/`pluginIcon_dark.svg`
 * follows for the Marketplace listing).
 */
object ProofIcons {
    @JvmField
    val ToolWindow: Icon = IconLoader.getIcon("/icons/proof.svg", ProofIcons::class.java)
}
