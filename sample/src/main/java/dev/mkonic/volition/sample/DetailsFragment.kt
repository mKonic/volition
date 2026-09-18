package dev.mkonic.volition.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class DetailsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val counter = view.findViewById<TextView>(R.id.count)
        show = { counter.text = getString(R.string.counted, count) }
        view.findViewById<Button>(R.id.count_up).setOnClickListener {
            count++
            show?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        show = null
    }

    companion object {
        /** Published as a `state`, so a caller can wait for `count>3` rather than for a line of text. */
        @Volatile
        var count = 0
            private set

        private var show: (() -> Unit)? = null

        /** A `command`: something the app can do that is not a place to go. */
        fun reset() {
            count = 0
            show?.invoke()
        }
    }
}
