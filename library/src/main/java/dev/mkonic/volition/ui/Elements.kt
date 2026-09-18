package dev.mkonic.volition.ui

import android.graphics.Rect
import android.os.Build
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inspector.WindowInspector
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import dev.mkonic.volition.Volition

/**
 * What the app is showing, read from its own windows, and the commands that act on it by name: `ui`, `find`,
 * `click`, `longclick`, `fill` and `submit`.
 *
 * Views are read as views and Compose through its semantics tree, so an app that mixes them - a Compose screen
 * hosting a map view, a Compose dialog over a Fragment - reads as one screen. Acting on an element runs what it
 * would have run - its click listener, its semantics click action, setText on its field - with no touch in between;
 * `volition tap <name>` is the path for exercising the real touch at the element's own position.
 *
 * Everything here runs on the main thread.
 */
internal object Elements {

    /** Separates a field's name from its text in `fill`'s single argument. */
    const val SEPARATOR = '\u001F'

    private val INDEXED = Regex("""^(.*)#(\d+)$""")

    private const val ROW_SLACK_DP = 8

    private val composeAvailable: Boolean by lazy {
        runCatching { Class.forName("androidx.compose.ui.node.RootForTest") }.isSuccess
    }

    /** `ui`: every element on screen, one per line, the window on top first. */
    fun list(): String {
        val windows = windows().asReversed()
        if (windows.isEmpty()) return "error: nothing on screen"
        return buildString {
            windows.forEachIndexed { index, window ->
                if (windows.size > 1) {
                    append("-- ").append(window.name)
                    if (index == 0) append(" (on top)")
                    append('\n')
                }
                window.elements.forEach { append(it.describe()).append('\n') }
            }
        }.trimEnd()
    }

    /**
     * `find`: what [query] names, as `ui` shows it - every match, numbered, when it names more than one. `tap <name>`
     * and `wait` are built on this.
     */
    fun find(query: String): String = answer {
        val (found, base, index) = lookup(query)
        when {
            index != null -> (found.getOrNull(index - 1) ?: throw Refused("only ${found.size} match '$base'")).describe()
            found.size == 1 -> found.first().describe()
            else -> found.mapIndexed { i, element -> "$base#${i + 1}  ${element.describe()}" }.joinToString("\n")
        }
    }

    fun click(query: String, long: Boolean): String = answer {
        val verb = if (long) "long-clicked" else "clicked"
        val element = pick(query, verb) { if (long) it.longClick != null else it.click != null }
        val done = if (long) element.longClick!!() else element.click!!()
        if (done) "ok: $verb ${element.summary()}" else "error: ${element.summary()} did not take it"
    }

    /**
     * `fill`: [argument] is the field's name and the text, split by [SEPARATOR]. No name means the focused field. A
     * slider takes a number.
     */
    fun fill(argument: String): String = answer {
        val parts = argument.split(SEPARATOR, limit = 2)
        if (parts.size < 2) return@answer "error: fill needs a field and the text for it"
        val (query, text) = parts
        val field = if (query.isBlank()) focusedField() else pick(query, "filled") { it.setText != null }
        if (field.setText!!(text)) "ok: filled ${field.summary()}" else "error: ${field.summary()} refused '$text'"
    }

    /** `submit`: the field's own action - search, go, done - as its keyboard's action key would. */
    fun submit(query: String): String = answer {
        val field = if (query.isBlank()) focusedField() else pick(query, "submitted") { it.submit != null }
        val action = field.submit ?: return@answer "error: ${field.summary()} has nothing to submit"
        if (action()) "ok: submitted ${field.summary()}" else "error: ${field.summary()} did not submit"
    }

    /** What typed text would land in, for `where`. */
    fun focused(): Element? = windows().asReversed().firstNotNullOfOrNull { window ->
        window.elements.lastOrNull { it.focused }
    }

    private class Refused(message: String) : Exception(message)

    private inline fun answer(block: () -> String): String = try {
        block()
    } catch (e: Refused) {
        "error: ${e.message}"
    }

    private fun focusedField(): Element = focused()?.takeIf { it.setText != null }
        ?: throw Refused("no field has focus - name one")

    /**
     * The one element [query] names that can be [verb]. When several could, the answer lists them; `name#2` picks the
     * second, counted top to bottom as `ui` lists them.
     */
    private fun pick(query: String, verb: String, can: (Element) -> Boolean): Element {
        val (found, base, index) = lookup(query)
        val chosen = if (index != null) {
            found.getOrNull(index - 1) ?: throw Refused("only ${found.size} match '$base'")
        } else {
            val usable = found.filter(can)
            when (usable.size) {
                0 -> found.first()
                1 -> usable.first()
                else -> throw Refused(
                    buildString {
                        append(usable.size).append(" match '").append(base).append("' - say which:")
                        found.forEachIndexed { i, element ->
                            if (can(element)) {
                                append("\n  ").append(base).append('#').append(i + 1).append("  ")
                                append(element.describe())
                            }
                        }
                    },
                )
            }
        }
        if (!can(chosen)) throw Refused("${chosen.summary()} cannot be $verb")
        if (!chosen.enabled) throw Refused("${chosen.summary()} is disabled")
        return chosen
    }

    private data class Lookup(val found: List<Element>, val query: String, val index: Int?)

    /**
     * What [query] matches. The window on top is asked first and a window that answers settles it - a dialog's
     * Cancel before the one on the screen behind it. Within a window an exact label beats one that only contains the
     * query. A trailing `#2` is read as an index only when the whole query matches nothing.
     */
    private fun lookup(query: String): Lookup {
        val windows = windows().asReversed()
        val attempts = listOfNotNull(
            query to null,
            INDEXED.matchEntire(query)?.let { it.groupValues[1] to it.groupValues[2].toInt() },
        )
        for ((name, index) in attempts) matches(windows, name)?.let { return Lookup(it, name, index) }
        // Nothing says it: a kind names every element of that kind, which is how an unlabeled field or slider is
        // reached - `fill slider 8`, `click switch#2`.
        for ((kind, index) in attempts) {
            windows.firstNotNullOfOrNull { window ->
                window.elements.filter { it.kind.equals(kind.trim(), ignoreCase = true) }.ifEmpty { null }
            }?.let { return Lookup(it, kind, index) }
        }
        throw Refused("nothing on screen is called '$query' - try ui")
    }

    private fun matches(windows: List<Window>, query: String): List<Element>? {
        for (window in windows) {
            window.elements.filter { it.answersTo(query, exact = true) }.ifEmpty { null }?.let { return it }
            window.elements.filter { it.answersTo(query, exact = false) }.ifEmpty { null }?.let { return it }
        }
        return null
    }

    /** Every visible window the app has, the one on top last. */
    private fun windows(): List<Window> = roots().mapNotNull { root ->
        val found = ArrayList<Element>()
        collect(root, found, merged = false)
        // Text drawn twice in one place - an outline under a fill - is one thing to anyone reading it.
        val distinct = found.distinctBy { Triple(it.kind, it.texts, it.bounds) }
        if (distinct.isEmpty()) {
            null
        } else {
            Window(nameOf(root), readingOrder(distinct, (ROW_SLACK_DP * root.resources.displayMetrics.density).toInt()))
        }
    }

    /**
     * Top to bottom, then left to right along a row - a row being elements whose middles sit within [slack] pixels
     * of each other, so a toolbar's title and its icons read as one line and two buttons a pixel apart do not
     * swap places.
     */
    private fun readingOrder(elements: List<Element>, slack: Int): List<Element> {
        val rows = ArrayList<MutableList<Element>>()
        for (element in elements.sortedBy { it.bounds.centerY() }) {
            val row = rows.lastOrNull()
            if (row != null && element.bounds.centerY() - row.first().bounds.centerY() <= slack) {
                row += element
            } else {
                rows += mutableListOf(element)
            }
        }
        return rows.flatMap { row -> row.sortedBy { it.bounds.left } }
    }

    private fun roots(): List<View> {
        val all = if (Build.VERSION.SDK_INT >= 29) WindowInspector.getGlobalWindowViews() else legacyRoots()
        return all.filter { it.isAttachedToWindow && it.windowVisibility == View.VISIBLE && it.isShown }
            .ifEmpty { listOfNotNull(Volition.currentActivity?.window?.decorView) }
    }

    /** Before WindowInspector there is only the field Espresso reads. */
    @Suppress("UNCHECKED_CAST", "PrivateApi", "DiscouragedPrivateApi")
    private fun legacyRoots(): List<View> = runCatching {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<View>).toList()
    }.getOrDefault(emptyList())

    /** An activity by its name; a dialog or popup by what it is, since it carries its activity's title. */
    private fun nameOf(root: View): String {
        val params = root.layoutParams as? WindowManager.LayoutParams
        return when (params?.type) {
            WindowManager.LayoutParams.TYPE_APPLICATION -> "dialog"
            in WindowManager.LayoutParams.FIRST_SUB_WINDOW..WindowManager.LayoutParams.LAST_SUB_WINDOW -> "popup"
            else -> params?.title?.toString().orEmpty().substringAfterLast('/').substringAfterLast('.').trim()
                .ifEmpty { "window" }
        }
    }

    /**
     * [merged] is true under a clickable view that has taken its descendants' text as its own label, as Compose's
     * merged tree does - a list row answers to the title inside it, and the title is not listed again.
     */
    private fun collect(view: View, into: MutableList<Element>, merged: Boolean) {
        if (!view.isShown) return
        val compose = composeAvailable && ComposeElements.read(view, into)
        if (!compose && (!merged || actsOnItsOwn(view))) viewElement(view)?.let(into::add)
        if (view is ViewGroup) {
            val absorbs = merged || (!compose && view.isClickable)
            for (i in 0 until view.childCount) collect(view.getChildAt(i), into, absorbs)
        }
    }

    private fun actsOnItsOwn(view: View) = view.isClickable || view is EditText

    private fun viewElement(view: View): Element? {
        val visible = Rect()
        if (!view.getGlobalVisibleRect(visible)) return null
        val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
        visible.offset(origin[0], origin[1])

        val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
        val description = view.contentDescription?.toString()?.trim().orEmpty()
        val kind = when {
            view is EditText -> "field"
            view is CompoundButton -> when {
                view is CheckBox -> "checkbox"
                view is RadioButton -> "radio"
                "Switch" in view.javaClass.simpleName -> "switch"
                else -> "toggle"
            }
            view is Button || view is ImageButton -> "button"
            view.isClickable -> "clickable"
            text.isNotEmpty() -> "text"
            view is ImageView && description.isNotEmpty() -> "icon"
            else -> return null
        }

        val texts = buildList {
            if (view is EditText) view.hint?.toString()?.trim()?.let(::add) else add(text)
            add(description)
            if (view is ViewGroup && view.isClickable) addDescendantTexts(view)
        }.filter(String::isNotEmpty).distinct()

        val flags = buildList {
            if (view is CompoundButton) add(if (view.isChecked) "on" else "off")
            if (view.isSelected) add("selected")
            if (view.isFocused) add(Element.FOCUSED)
            if (!view.isEnabled) add("disabled")
            (view as? TextView)?.error?.let { add("error: $it") }
        }

        val field = view as? EditText
        return Element(
            kind = kind,
            texts = texts,
            names = listOfNotNull(resourceName(view)),
            value = field?.let {
                val value = it.text.toString()
                if (it.transformationMethod is PasswordTransformationMethod) "•".repeat(value.length) else value
            },
            flags = flags,
            bounds = visible,
            enabled = view.isEnabled,
            click = if (view.isClickable) view::performClick else null,
            longClick = if (view.isLongClickable) view::performLongClick else null,
            setText = field?.let { edit ->
                { value: String ->
                    edit.setText(value)
                    edit.setSelection(edit.length())
                    true
                }
            },
            submit = field?.let { edit ->
                {
                    val action = (edit.imeOptions and EditorInfo.IME_MASK_ACTION)
                        .takeUnless { it == EditorInfo.IME_ACTION_UNSPECIFIED || it == EditorInfo.IME_ACTION_NONE }
                        ?: EditorInfo.IME_ACTION_DONE
                    edit.onEditorAction(action)
                    true
                }
            },
        )
    }

    /** The text a clickable container shows through its children, skipping children that act on their own. */
    private fun MutableList<String>.addDescendantTexts(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (!child.isShown || actsOnItsOwn(child)) continue
            (child as? TextView)?.text?.toString()?.trim()?.let(::add)
            child.contentDescription?.toString()?.trim()?.let(::add)
            if (child is ViewGroup) addDescendantTexts(child)
        }
    }

    private fun resourceName(view: View): String? {
        if (view.id == View.NO_ID) return null
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull()
    }
}
