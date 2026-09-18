package dev.mkonic.volition.navigation

import android.app.Activity
import androidx.navigation.NavController
import dev.mkonic.volition.Volition
import dev.mkonic.volition.VolitionSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Jetpack Navigation support - the same [NavController] Fragments and Compose Navigation are both driven by, so this
 * one adapter covers either. The current route and the one before it in `where`, `back` and `home` for free, and
 * [route] for declaring a destination as the route it navigates to.
 *
 * ```kotlin
 * Volition.register {
 *     navigation(MainActivity::class.java) { navControllerOrNull }
 *     route("settings") { "settings" }
 *     route("manga", takesArgument = true) { id -> "manga/$id" }
 * }
 * ```
 *
 * The app passes a lookup rather than a controller, because the controller belongs to a host that comes and goes.
 *
 * Compiled against Navigation 2.9; everything it calls has been there since 2.6.
 *
 * This file is the only part of Volition that touches Navigation, and it is only loaded by an app that calls
 * [navigation].
 */
fun VolitionSpec.navigation(host: Class<out Activity>? = null, controller: () -> NavController?) {
    controllerLookup = controller
    hostActivity = host

    state("route") { onMain { controller()?.currentBackStackEntry?.destination?.route } }
    // The whole back stack is Navigation's own business (currentBackStack is restricted to its library group), so
    // what a caller gets is where it is and where it came from.
    state("from") { onMain { controller()?.previousBackStackEntry?.destination?.route } }

    destination("back", description = "up one destination, or close the activity when there is none") {
        onMain {
            // Another activity on top of the one holding the graph is what back means right now; popping the graph
            // underneath it would move a destination nobody is looking at.
            val onTop = Volition.currentActivity
            val below = hostActivity
            if (below != null && onTop != null && !below.isInstance(onTop)) {
                onTop.finish()
                return@onMain "ok: closed ${onTop.javaClass.simpleName}"
            }
            val navigation = controller() ?: return@onMain "error: no navigation graph"
            if (navigation.popBackStack()) {
                "ok: back"
            } else {
                val closing = Volition.currentActivity
                closing?.finish()
                "ok: closed ${closing?.javaClass?.simpleName ?: "screen"}"
            }
        }
    }

    destination("home", description = "back to the start of the graph") {
        onMain {
            val navigation = controller() ?: return@onMain "error: no navigation graph"
            navigation.popBackStack(navigation.graph.startDestinationId, false)
            "ok: home"
        }
    }
}

/**
 * A destination that is a route. With [takesArgument] the factory gets what followed the colon (`go manga:42`),
 * otherwise it gets an empty string. [popFirst] returns to the start of the graph first, which is what a destination
 * that is really a tab underneath everything else wants.
 */
fun VolitionSpec.route(
    name: String,
    vararg aliases: String,
    takesArgument: Boolean = false,
    popFirst: Boolean = false,
    description: String? = null,
    factory: (argument: String) -> String?,
) {
    destination(
        name,
        *aliases,
        takesArgument = takesArgument,
        description = description,
    ) { argument ->
        val route = factory(argument.orEmpty())
            ?: return@destination "error: '$name' cannot open '${argument.orEmpty()}'"
        onMain {
            val navigation = controllerLookup?.invoke() ?: return@onMain "error: the app is not showing a graph"
            if (popFirst) navigation.popBackStack(navigation.graph.startDestinationId, false)
            try {
                navigation.navigate(route)
                "ok: $route"
            } catch (e: IllegalArgumentException) {
                // What the graph calls its routes is the app's business; say what was tried rather than crashing it.
                "error: no route '$route' in this graph"
            }
        }
    }
}

/** Where [route] finds the controller, set by [navigation]. */
@Volatile
private var controllerLookup: (() -> NavController?)? = null

/** The activity the graph belongs to, so back can tell it from one sitting on top of it. */
@Volatile
private var hostActivity: Class<out Activity>? = null

private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }
