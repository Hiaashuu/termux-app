package com.termux.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.termux.R
import com.termux.app.fragments.settings.TermuxAPIPreferencesFragment
import com.termux.app.fragments.settings.TermuxFloatPreferencesFragment
import com.termux.app.fragments.settings.TermuxTaskerPreferencesFragment
import com.termux.app.fragments.settings.TermuxWidgetPreferencesFragment
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.theme.NightMode

class LegacyPluginPreferencesActivity : AppCompatActivity() {

    companion object {

        const val EXTRA_FRAGMENT_KEY = "fragment_key"

        fun start(context: Context, fragmentKey: String) {
            val intent = Intent(context, LegacyPluginPreferencesActivity::class.java)
            intent.putExtra(EXTRA_FRAGMENT_KEY, fragmentKey)
            context.startActivity(intent)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true)

        setContentView(R.layout.activity_settings)

        val fragmentKey = intent.getStringExtra(EXTRA_FRAGMENT_KEY)

        val fragment: PreferenceFragmentCompat = when (fragmentKey) {
            "termux_api" -> TermuxAPIPreferencesFragment()
            "termux_float" -> TermuxFloatPreferencesFragment()
            "termux_tasker" -> TermuxTaskerPreferencesFragment()
            "termux_widget" -> TermuxWidgetPreferencesFragment()
            else -> TermuxAPIPreferencesFragment()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
        }

        AppCompatActivityUtils.setToolbar(this, com.termux.shared.R.id.toolbar)
        AppCompatActivityUtils.setShowBackButtonInActionBar(this, true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

}