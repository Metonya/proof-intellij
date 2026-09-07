package dev.proofjava.intellij.core.engine

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Looks up [Engine] implementations registered via the
 * `dev.proofjava.intellij.engine` extension point (`plugin.xml`).
 *
 * [firstRegisteredEngine] is a deliberate simplification for now: with a
 * single engine (`JavaEngine`) ever registered, "the first one" and "the
 * right one" are the same thing. A real "which engine owns this project"
 * resolution (needed once a second engine exists) is explicitly deferred
 * in the plan - building a picker for a scenario with zero present
 * evidence would be speculative (hard rule 3a).
 */
object EngineRegistry {
    private val EP_NAME: ExtensionPointName<Engine> = ExtensionPointName.create("dev.proofjava.intellij.engine")

    fun allEngines(): List<Engine> = EP_NAME.extensionList

    fun firstRegisteredEngine(): Engine? = EP_NAME.extensionList.firstOrNull()
}
