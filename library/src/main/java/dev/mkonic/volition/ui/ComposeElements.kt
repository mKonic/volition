package dev.mkonic.volition.ui

import android.graphics.Rect
import android.view.View
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString

/**
 * Reads a Compose root through its semantics - the tree screen readers and Compose's own tests read - so an element's
 * label is what it says and clicking it runs its click action, with no touch involved. It reads the merged tree, as a
 * screen reader does: a button's text belongs to the button rather than standing as a line of its own.
 *
 * Only loaded once Compose is known to be on the classpath, so an app without it never resolves these classes.
 */
internal object ComposeElements {

    /** Adds what [view] shows when it is a Compose root; false when it is not one. */
    fun read(view: View, into: MutableList<Element>): Boolean {
        val root = view as? RootForTest ?: return false
        val origin = IntArray(2).also(view::getLocationOnScreen)
        root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true)
            .mapNotNullTo(into) { element(it, origin) }
        return true
    }

    private fun element(node: SemanticsNode, origin: IntArray): Element? {
        // Clipped to what is visible: a list item scrolled out of its viewport has nothing left to show.
        val box = node.boundsInRoot
        if (box.width <= 0f || box.height <= 0f) return null

        val config = node.config
        val text = config.getOrNull(SemanticsProperties.Text)
        val texts = buildList {
            text?.forEach { add(it.text) }
            config.getOrNull(SemanticsProperties.ContentDescription)?.let(::addAll)
        }.map(String::trim).filter(String::isNotEmpty).distinct()

        val onClick = config.getOrNull(SemanticsActions.OnClick)?.action
        val onLongClick = config.getOrNull(SemanticsActions.OnLongClick)?.action
        val setText = config.getOrNull(SemanticsActions.SetText)?.action
        val onImeAction = config.getOrNull(SemanticsActions.OnImeAction)?.action
        val setProgress = config.getOrNull(SemanticsActions.SetProgress)?.action
        val range = config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        val toggle = config.getOrNull(SemanticsProperties.ToggleableState)
        val role = config.getOrNull(SemanticsProperties.Role)

        val kind = when {
            role != null -> kindOf(role.toString())
            setText != null -> "field"
            setProgress != null -> "slider"
            range != null -> "progress"
            toggle != null -> "toggle"
            onClick != null -> "clickable"
            text != null -> "text"
            texts.isNotEmpty() -> "icon"
            else -> return null
        }

        // A spinner reports a range with nothing in it.
        val spinning = range != null && range.range.start == range.range.endInclusive
        val disabled = SemanticsProperties.Disabled in config
        val password = SemanticsProperties.Password in config
        val flags = buildList {
            when (toggle) {
                ToggleableState.On -> add("on")
                ToggleableState.Off -> add("off")
                ToggleableState.Indeterminate -> add("mixed")
                null -> Unit
            }
            if (config.getOrNull(SemanticsProperties.Selected) == true) add("selected")
            if (config.getOrNull(SemanticsProperties.Focused) == true) add(Element.FOCUSED)
            when {
                range == null -> Unit
                spinning -> add("indeterminate")
                else -> add("${range.range.start.plain()}..${range.range.endInclusive.plain()}")
            }
            if (disabled) add("disabled")
            config.getOrNull(SemanticsProperties.Error)?.let { add("error: $it") }
        }

        return Element(
            kind = kind,
            texts = texts,
            names = listOfNotNull(config.getOrNull(SemanticsProperties.TestTag)),
            value = config.getOrNull(SemanticsProperties.EditableText)?.text
                ?.let { if (password) "•".repeat(it.length) else it }
                ?: range?.takeUnless { spinning }?.current?.plain(),
            flags = flags,
            bounds = Rect(
                box.left.toInt() + origin[0],
                box.top.toInt() + origin[1],
                box.right.toInt() + origin[0],
                box.bottom.toInt() + origin[1],
            ),
            enabled = !disabled,
            click = onClick?.let { action -> { action() } },
            longClick = onLongClick?.let { action -> { action() } },
            setText = setText?.let { action -> { value: String -> action(AnnotatedString(value)) } }
                ?: setProgress?.let { action -> { value: String -> value.toFloatOrNull()?.let(action) ?: false } },
            submit = onImeAction?.let { action -> { action() } },
        )
    }

    private fun Float.plain(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun kindOf(role: String) = when (role) {
        "RadioButton" -> "radio"
        "DropdownList" -> "dropdown"
        "ValuePicker" -> "picker"
        else -> role.lowercase()
    }
}
