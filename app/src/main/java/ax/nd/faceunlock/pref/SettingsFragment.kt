package ax.nd.faceunlock.pref

import android.os.Bundle
import android.graphics.Color
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import ax.nd.faceunlock.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(Color.TRANSPARENT)
        val density = resources.displayMetrics.density
        listView.setPadding(
            (8 * density).toInt(),
            (4 * density).toInt(),
            (8 * density).toInt(),
            (12 * density).toInt()
        )
        listView.clipToPadding = false
        listView.isNestedScrollingEnabled = false
    }
}
