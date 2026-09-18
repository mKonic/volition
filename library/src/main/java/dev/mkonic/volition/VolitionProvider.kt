package dev.mkonic.volition

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * How adb reaches [Volition]:
 *
 * ```
 * adb shell content call --uri content://<applicationId>.volition --method go --arg settings_webgpu
 * ```
 *
 * A provider rather than a broadcast, because `content call` hands the answer straight back - the point is to ask the
 * app where it is, not to read it off a screenshot. It comes with the artifact, so an app that adds Volition as
 * `debugImplementation` declares nothing: this manifest merges into that build and no other.
 *
 * Creating a provider is also the app's earliest hook, which is where [Volition.install] starts watching activities.
 */
class VolitionProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let(Volition::install)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = context ?: return Bundle().apply { putString(RESULT, "error: no context") }
        return Bundle().apply { putString(RESULT, Volition.call(context, method, arg)) }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        /** The key the answer travels under, in the Bundle `content call` prints. */
        const val RESULT = "result"
    }
}
