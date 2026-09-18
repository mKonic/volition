package dev.mkonic.volition.ui

import android.graphics.Rect

/**
 * Something on screen a caller can name - a button, a switch, a field, a line of text - read from the app's own view
 * and semantics trees, so it is addressed by what it says rather than by where it happens to be drawn.
 */
internal class Element(
    val kind: String,
    /** What it says or is described as, its label first. */
    val texts: List<String>,
    /** What else it answers to: a test tag, a view id. */
    val names: List<String>,
    /** A field's content. */
    val value: String?,
    val flags: List<String>,
    /** On screen, in pixels - the space `input tap` works in. */
    val bounds: Rect,
    val enabled: Boolean,
    val click: (() -> Boolean)?,
    val longClick: (() -> Boolean)?,
    val setText: ((String) -> Boolean)?,
    val submit: (() -> Boolean)?,
) {
    val focused: Boolean get() = FOCUSED in flags

    fun answersTo(query: String, exact: Boolean): Boolean {
        val wanted = query.trim()
        if (wanted.isEmpty()) return false
        return (texts + names).any {
            if (exact) it.equals(wanted, ignoreCase = true) else it.contains(wanted, ignoreCase = true)
        }
    }

    /** `button "Retry"` - enough to say which one was meant, without the rest of what it says. */
    fun summary(): String = "$kind ${label(texts.take(1))}"

    /** One line of `ui`: what it is, what it says, its state and where its middle is. */
    fun describe(): String = buildString {
        append(kind.padEnd(KIND_WIDTH)).append(' ').append(label(texts))
        value?.let { append(" = ").append(quoted(it)) }
        flags.forEach { append("  ").append(it) }
        append("  @").append(bounds.centerX()).append(',').append(bounds.centerY())
    }

    private fun label(shown: List<String>): String = buildString {
        append(if (shown.isEmpty()) "(unlabeled)" else quoted(shown.joinToString(" · ")))
        if (names.isNotEmpty()) append(" [").append(names.joinToString(", ")).append(']')
    }

    private fun quoted(text: String): String {
        val line = text.replace('\n', ' ')
        return "\"" + (if (line.length > MAX_SHOWN) line.take(MAX_SHOWN - 1) + "…" else line) + "\""
    }

    companion object {
        const val FOCUSED = "focused"
        private const val KIND_WIDTH = 9
        private const val MAX_SHOWN = 80
    }
}

/** One of the app's windows - an activity, a dialog, a popup - and what it shows, top to bottom. */
internal class Window(val name: String, val elements: List<Element>)
