package dev.mkonic.volition.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val greeting = view.findViewById<TextView>(R.id.greeting)
        val name = view.findViewById<EditText>(R.id.name)

        view.findViewById<Button>(R.id.open_details).setOnClickListener {
            findNavController().navigate(R.id.details)
        }
        view.findViewById<Button>(R.id.greet).setOnClickListener {
            greeting.text = getString(R.string.greeting, name.text.toString())
        }
        view.findViewById<Button>(R.id.ask).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.sure)
                .setPositiveButton(R.string.yes) { _, _ -> greeting.setText(R.string.answered) }
                .setNegativeButton(R.string.no, null)
                .show()
        }
        view.findViewById<Switch>(R.id.notify).setOnCheckedChangeListener { _, checked ->
            greeting.text = getString(if (checked) R.string.notifications_on else R.string.notifications_off)
        }
    }
}
