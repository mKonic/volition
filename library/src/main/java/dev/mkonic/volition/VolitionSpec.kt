package dev.mkonic.volition

/** One place the app can be told to go. [takesArgument] only shapes how `destinations` prints it. */
class Destination internal constructor(
    internal val takesArgument: Boolean,
    internal val description: String?,
    internal val go: suspend (String?) -> String,
)

/**
 * The app's map, built inside [Volition.register]. Nothing here runs at registration time; the lambdas run when a
 * caller asks, on whatever thread the bridge is answering on, so anything touching the UI has to reach the main
 * thread itself - which the adapters ([dev.mkonic.volition.voyager.voyager]) already do.
 */
class VolitionSpec internal constructor() {

    internal val destinations = LinkedHashMap<String, Destination>()
    internal val aliases = LinkedHashMap<String, String>()
    internal val commands = LinkedHashMap<String, suspend (String?) -> String>()
    internal val stateProviders = LinkedHashMap<String, suspend () -> Any?>()

    /**
     * A place the app can go, under [name] and any [aliases] - `go library`, or `go manga:42` for one that reads the
     * argument. Return what the caller should see; by convention `ok: <what happened>`.
     */
    fun destination(
        name: String,
        vararg aliases: String,
        takesArgument: Boolean = false,
        description: String? = null,
        go: suspend (argument: String?) -> String,
    ) {
        destinations[name] = Destination(takesArgument, description, go)
        aliases.forEach { this.aliases[it] = name }
    }

    /** Another name for a destination, for when the short one is what you would actually type. */
    fun alias(alias: String, name: String) {
        aliases[alias] = name
    }

    /**
     * Something the app can do that is not a place: `volition <name> <argument>`. Seeding test data, clearing a
     * cache, forcing a sync - whatever a session keeps doing by hand.
     */
    fun command(name: String, run: suspend (argument: String?) -> String) {
        commands[name] = run
    }

    /**
     * A field the app adds to `where`. Keep them cheap: they are read every time anyone asks where the app is.
     * Maps and lists come out as JSON objects and arrays, anything else as its `toString`.
     */
    fun state(name: String, provide: suspend () -> Any?) {
        stateProviders[name] = provide
    }
}
