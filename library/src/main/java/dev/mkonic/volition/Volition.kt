package dev.mkonic.volition

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/**
 * An app that says where it can go, so a tool outside it can drive it by name.
 *
 * The usual way to drive an app from a terminal is to read a screenshot, guess at coordinates and tap. The app knows
 * all of this already - its screens have names, its stack has a shape - so Volition has it answer for itself:
 *
 * ```
 * $ volition go settings_webgpu
 * ok: SettingsWebGpuScreen
 * $ volition where
 * {"activity":"MainActivity","ready":true,"screen":"SettingsWebGpuScreen"}
 * ```
 *
 * An app declares its map once:
 *
 * ```kotlin
 * Volition.register {
 *     voyager { navigator }
 *     screen("settings_webgpu", "webgpu") { SettingsWebGpuScreen }
 *     destination("library") { openTab(Tab.Library()); "ok: library" }
 *     command("seed") { seedTestData(); "ok" }
 *     state("theme") { preferences.theme().get().name }
 * }
 * ```
 *
 * Reached over adb through [VolitionProvider]. Add the artifact as `debugImplementation` and the provider comes with
 * it; nothing else has to be declared. On a build that is not debuggable it refuses to answer unless the app calls
 * [allowOnAnyBuild].
 */
object Volition {

    private const val TIMEOUT_MS = 10_000L

    private val destinations = LinkedHashMap<String, Destination>()
    private val aliases = LinkedHashMap<String, String>()
    private val commands = LinkedHashMap<String, suspend (String?) -> String>()
    private val stateProviders = LinkedHashMap<String, suspend () -> Any?>()

    @Volatile
    private var resumed: WeakReference<Activity>? = null

    @Volatile
    private var allowOnAnyBuild = false

    @Volatile
    private var installedIn: Application? = null

    /** What the app is showing, or null when none of its activities is on screen. */
    val currentActivity: Activity?
        get() = resumed?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    /**
     * Answers only when the app is debuggable. A build that is not - a signed internal or beta build a tester runs -
     * has to say so itself, because the provider is exported and navigating an app is a thing to hand out on purpose.
     */
    fun allowOnAnyBuild() {
        allowOnAnyBuild = true
    }

    /** Declares what this app can be asked for. Call it as many times as suits; later entries win a name clash. */
    fun register(block: VolitionSpec.() -> Unit) {
        val spec = VolitionSpec()
        spec.block()
        synchronized(this) {
            destinations.putAll(spec.destinations)
            aliases.putAll(spec.aliases)
            commands.putAll(spec.commands)
            stateProviders.putAll(spec.stateProviders)
        }
    }

    /** True when this build may answer at all - see [allowOnAnyBuild]. */
    fun isEnabled(context: Context): Boolean {
        if (allowOnAnyBuild) return true
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    /** Starts tracking which activity is on screen. [VolitionProvider] does this; calling it twice is harmless. */
    fun install(application: Application) {
        synchronized(this) {
            if (installedIn === application) return
            installedIn = application
        }
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    resumed = WeakReference(activity)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    /**
     * Runs one command and returns what to print. A failure comes back as a line starting with `error:` rather than
     * an exception, so a caller always gets an answer it can read.
     */
    fun call(context: Context, method: String, argument: String?): String {
        if (!isEnabled(context)) return "error: this build does not answer - see Volition.allowOnAnyBuild()"
        return try {
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    when (method) {
                        "help", "commands" -> help()
                        "destinations" -> destinations()
                        "where", "state" -> state()
                        "go" -> go(argument ?: return@withTimeout "error: go needs a destination")
                        "pref" -> pref(context, argument ?: return@withTimeout "error: pref needs a key")
                        else -> synchronized(this@Volition) { commands[method] }?.invoke(argument)
                            ?: "error: no command '$method' - try help"
                    }
                }
            }
        } catch (e: Exception) {
            "error: ${e::class.simpleName}: ${e.message}"
        }
    }

    private fun help(): String {
        val own = synchronized(this) { commands.keys.toList() }
        return (BUILT_IN + own).joinToString("\n")
    }

    private fun destinations(): String = synchronized(this) {
        val byName = aliases.entries.groupBy({ it.value }, { it.key })
        destinations.map { (name, destination) ->
            buildString {
                append(name)
                if (destination.takesArgument) append(":<argument>")
                byName[name]?.let { append("  (").append(it.joinToString(", ")).append(")") }
                destination.description?.let { append("  - ").append(it) }
            }
        }.joinToString("\n").ifEmpty { "error: this app has registered no destinations" }
    }

    private suspend fun go(target: String): String {
        val (rawName, argument) = target.split(':', limit = 2)
            .let { it[0].trim() to it.getOrNull(1)?.trim() }
        val name = synchronized(this) { aliases[rawName] ?: rawName }
        val destination = synchronized(this) { destinations[name] }
            ?: return "error: no destination '$rawName' - try destinations"
        return destination.go(argument)
    }

    private suspend fun state(): String {
        val json = JSONObject()
        val activity = currentActivity
        json.put("activity", activity?.javaClass?.simpleName ?: "none")
        // A provider call starts the process on its own, so an app can answer before it has an activity or its
        // dependency graph. A caller that is about to drive it waits for this.
        json.put("ready", activity != null)
        val providers = synchronized(this) { stateProviders.toMap() }
        for ((name, provide) in providers) {
            val value = try {
                provide()
            } catch (e: Exception) {
                "error: ${e::class.simpleName}: ${e.message}"
            }
            json.put(name, value.toJson())
        }
        return json.toString()
    }

    /**
     * `key` reads a preference from the app's default store, `key=value` writes one. The value's type follows what is
     * already stored, so a preference the app reads as a boolean cannot be turned into a string that breaks its
     * reader. Writing goes to the live store, so the app sees it at once - no restart, no root.
     */
    private fun pref(context: Context, argument: String): String {
        val (key, value) = argument.split('=', limit = 2).let { it[0].trim() to it.getOrNull(1)?.trim() }
        if (key.isEmpty()) return "error: pref needs a key"

        val preferences = context.getSharedPreferences(
            "${context.packageName}_preferences",
            Context.MODE_PRIVATE,
        )
        val current = preferences.all[key]
        if (value == null) {
            return if (preferences.contains(key)) "$key=$current" else "error: no preference '$key'"
        }

        val editor = preferences.edit()
        when (current) {
            is Boolean -> editor.putBoolean(key, value.toBooleanStrictOrNull() ?: return notA(value, "true or false"))
            is Int -> editor.putInt(key, value.toIntOrNull() ?: return notA(value, "whole number"))
            is Long -> editor.putLong(key, value.toLongOrNull() ?: return notA(value, "whole number"))
            is Float -> editor.putFloat(key, value.toFloatOrNull() ?: return notA(value, "number"))
            is String, null -> editor.putString(key, value)
            else -> return "error: '$key' holds a ${current::class.simpleName}, which this cannot write"
        }
        editor.apply()
        return "ok: $key=$value"
    }

    private fun notA(value: String, kind: String) = "error: '$value' is not a $kind"

    private fun Any?.toJson(): Any = when (this) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { json -> forEach { (k, v) -> json.put(k.toString(), v.toJson()) } }
        is Iterable<*> -> JSONArray().also { array -> forEach { array.put(it.toJson()) } }
        is Boolean, is Number, is String -> this
        else -> toString()
    }

    private val BUILT_IN = listOf(
        "help          what this app answers",
        "destinations  where it can be told to go",
        "where         what is on screen, and whether it is ready to be driven",
        "go <name>     open a destination, from wherever the app is",
        "pref <key>    read a preference, or write one with <key>=<value>",
    )
}
