package dev.mkonic.volition.sample

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import dev.mkonic.volition.Volition
import dev.mkonic.volition.navigation.navigation
import dev.mkonic.volition.navigation.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This app's map. A provider because that is the earliest the app runs, and it is declared only in the debug build,
 * where Volition is a dependency at all.
 */
class SampleVolitionProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Volition.register {
            navigation(MainActivity::class.java) { MainActivity.navController }

            route("details", "d") { "details" }
            route("start", popFirst = true) { "home" }

            state("count") { DetailsFragment.count }
            command("reset") {
                withContext(Dispatchers.Main) { DetailsFragment.reset() }
                "ok: back to zero"
            }
        }
        return true
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
}
