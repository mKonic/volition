package dev.mkonic.volition.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import java.lang.ref.WeakReference

/**
 * Two destinations, a field, a switch and a dialog: enough of an app to drive from the terminal.
 *
 * The map itself is in [SampleVolition], which only exists in the debug build.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val host = supportFragmentManager.findFragmentById(R.id.host) as NavHostFragment
        controller = WeakReference(host.navController)
    }

    companion object {
        // Held weakly, and read through a lookup: the controller belongs to a host that comes and goes.
        @Volatile
        private var controller: WeakReference<NavController>? = null

        val navController: NavController? get() = controller?.get()
    }
}
