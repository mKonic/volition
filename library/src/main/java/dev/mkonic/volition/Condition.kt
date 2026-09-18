package dev.mkonic.volition

import org.json.JSONArray
import org.json.JSONObject

/**
 * A question about what `where` says, so a caller can wait for the app to arrive somewhere instead of sleeping and
 * hoping: `screen=MangaScreen`, `reader.page>3`, `stack~Settings`, `focused~Search`.
 *
 * The left side is a path into the answer `where` gives - a field, then a key or an index inside it. The right side
 * is what it should be. Numbers compare as numbers, everything else as text, ignoring case.
 */
internal object Condition {

    private val OPERATORS = listOf(">=", "<=", "!=", "=", ">", "<", "~")

    /** Whether [expression] reads as a condition at all, rather than the name of something on screen. */
    fun looksLikeOne(expression: String): Boolean = split(expression) != null

    /** `ok:` when [state] satisfies [expression], `error:` with what the path actually holds when it does not. */
    fun test(state: JSONObject, expression: String): String {
        val (path, operator, wanted) = split(expression)
            ?: return "error: '$expression' is not a condition - try screen=MangaScreen or reader.page>3"
        val actual = follow(state, path)
            ?: return "error: nothing at '$path' - where says ${state}"
        val holds = compare(actual, operator, wanted)
        return if (holds) "ok: $path is $actual" else "error: $path is $actual"
    }

    private fun split(expression: String): Triple<String, String, String>? {
        for (operator in OPERATORS) {
            val at = expression.indexOf(operator)
            // A path comes first, so an expression starting with the operator is not one.
            if (at <= 0) continue
            val path = expression.take(at).trim()
            val wanted = expression.substring(at + operator.length).trim()
            if (path.isNotEmpty() && wanted.isNotEmpty()) return Triple(path, operator, wanted)
        }
        return null
    }

    /** `reader.page`, `stack.0` - keys through objects, indices through arrays. */
    private fun follow(state: JSONObject, path: String): Any? {
        var here: Any? = state
        for (step in path.split('.')) {
            here = when (val at = here) {
                is JSONObject -> if (at.isNull(step)) null else at.opt(step)
                is JSONArray -> step.toIntOrNull()?.takeIf { it in 0 until at.length() }?.let(at::opt)
                else -> null
            } ?: return null
        }
        return here
    }

    private fun compare(actual: Any, operator: String, wanted: String): Boolean {
        val numbers = (actual as? Number)?.toDouble()?.let { left ->
            wanted.toDoubleOrNull()?.let { right -> left to right }
        }
        if (numbers != null) {
            val (left, right) = numbers
            return when (operator) {
                "=" -> left == right
                "!=" -> left != right
                ">" -> left > right
                "<" -> left < right
                ">=" -> left >= right
                "<=" -> left <= right
                "~" -> actual.toString().contains(wanted, ignoreCase = true)
                else -> false
            }
        }

        val text = actual.toString()
        return when (operator) {
            "=" -> text.equals(wanted, ignoreCase = true)
            "!=" -> !text.equals(wanted, ignoreCase = true)
            // A list holds what it holds; a string contains what it contains.
            "~" -> if (actual is JSONArray) actual.items().any { it.equals(wanted, ignoreCase = true) } else {
                text.contains(wanted, ignoreCase = true)
            }
            ">" -> text.compareTo(wanted, ignoreCase = true) > 0
            "<" -> text.compareTo(wanted, ignoreCase = true) < 0
            ">=" -> text.compareTo(wanted, ignoreCase = true) >= 0
            "<=" -> text.compareTo(wanted, ignoreCase = true) <= 0
            else -> false
        }
    }

    private fun JSONArray.items(): List<String> = (0 until length()).map { opt(it).toString() }
}
