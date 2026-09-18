package dev.mkonic.volition.voyager

import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import dev.mkonic.volition.Volition
import dev.mkonic.volition.VolitionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Voyager support: the screen stack in `where`, `back` and `home` for free, and [screen] for declaring a destination
 * as the screen it opens rather than as the navigation that opens it.
 *
 * ```kotlin
 * Volition.register {
 *     voyager { navigatorOrNull }
 *     screen("settings_webgpu", "webgpu") { SettingsWebGpuScreen }
 *     screen("manga", takesArgument = true) { id -> MangaScreen(id.toLong()) }
 * }
 * ```
 *
 * The app passes a lookup rather than a navigator, because the navigator belongs to a composition that comes and
 * goes: hold it weakly where it is created and let this read it each time.
 *
 * This file is the only part of Volition that touches Voyager, and it is only loaded by an app that calls [voyager].
 */
fun VolitionSpec.voyager(navigator: () -> Navigator?) {
    navigatorLookup = navigator

    state("screen") { onMain { navigator()?.lastItem?.let { it::class.simpleName } } }
    state("stack") { onMain { navigator()?.items?.map { it::class.simpleName } } }

    destination("back", description = "up one screen, or close the activity when there is none") {
        onMain {
            val stack = navigator() ?: return@onMain "error: no screen stack"
            if (stack.canPop) {
                stack.pop()
                "ok: back"
            } else {
                // Read the name before finishing it: afterwards this is a finishing activity and reads as none.
                val closing = Volition.currentActivity
                closing?.finish()
                "ok: closed ${closing?.javaClass?.simpleName ?: "screen"}"
            }
        }
    }

    destination("home", description = "back to the root of the stack") {
        onMain {
            val stack = navigator() ?: return@onMain "error: no screen stack"
            stack.popUntilRoot()
            "ok: home"
        }
    }
}

/**
 * A destination that is a screen. With [takesArgument] the factory gets what followed the colon (`go manga:42`),
 * otherwise it gets an empty string. [popFirst] clears the stack first, which is what a destination that is
 * really a tab underneath everything else wants.
 */
fun VolitionSpec.screen(
    name: String,
    vararg aliases: String,
    takesArgument: Boolean = false,
    popFirst: Boolean = false,
    description: String? = null,
    factory: (argument: String) -> Screen?,
) {
    destination(
        name,
        *aliases,
        takesArgument = takesArgument,
        description = description,
    ) { argument ->
        val screen = factory(argument.orEmpty())
            ?: return@destination "error: '$name' cannot open '${argument.orEmpty()}'"
        onMain {
            val stack = navigatorLookup?.invoke() ?: return@onMain "error: the app is not showing its screen stack"
            if (popFirst) stack.popUntilRoot()
            stack.push(screen)
            "ok: ${screen::class.simpleName}"
        }
    }
}

/** Where [screen] finds the navigator, set by [voyager]. */
@Volatile
private var navigatorLookup: (() -> Navigator?)? = null

private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
