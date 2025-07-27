package com.kezor.localsave.savestatus

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.core.net.toUri

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<Preference>(Constants.KEY_THEME_MODE)?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            ThemeManager.setThemeMode(theme)
            activity?.recreate()
            true
        }

        findPreference<Preference>(getString(R.string.settings_change_save_folder))?.setOnPreferenceClickListener {
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            true
        }

        findPreference<Preference>(Constants.KEY_AUTO_SAVE_STATUSES)?.setOnPreferenceChangeListener { _, newValue ->
            Utils.showToast(requireContext(), "Auto Save: ${if (newValue as Boolean) "Enabled" else "Disabled"}")
            true
        }

        findPreference<Preference>(Constants.KEY_SAVE_OLDER_STATUS)?.setOnPreferenceChangeListener { _, newValue ->
            Utils.showToast(requireContext(), "Save Older Status: ${if (newValue as Boolean) "Enabled" else "Disabled"}")
            true
        }

        findPreference<Preference>(getString(R.string.settings_go_to_saved_all_statuses))?.setOnPreferenceClickListener {
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            true
        }

        findPreference<Preference>(getString(R.string.settings_clear_all))?.setOnPreferenceClickListener {
            showClearAllConfirmationDialog()
            true
        }

        findPreference<Preference>(getString(R.string.settings_privacy_policy))?.setOnPreferenceClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW,
                "https://www.example.com/privacy_policy".toUri())
            startActivity(browserIntent)
            true
        }

        findPreference<Preference>(getString(R.string.settings_rate_us))?.setOnPreferenceClickListener {
            val appPackageName = requireContext().packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    "market://details?id=$appPackageName".toUri()))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$appPackageName".toUri()))
            }
            true
        }

        findPreference<Preference>(getString(R.string.settings_share_app))?.setOnPreferenceClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, "Check out this amazing WhatsApp Status Saver app: [App Play Store Link Here]")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_app)))
            true
        }

        findPreference<Preference>(getString(R.string.settings_developer_info))?.setOnPreferenceClickListener {
            Utils.showToast(requireContext(), "Developed by Nethmin")
            true
        }

        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            findPreference<Preference>(getString(R.string.settings_app_version))?.summary =
                getString(R.string.settings_app_version_placeholder, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            findPreference<Preference>(getString(R.string.settings_app_version))?.summary =
                getString(R.string.settings_app_version_placeholder, "N/A")
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle(getString(R.string.settings_clear_all_dialog_title))
            .setMessage(getString(R.string.settings_clear_all_dialog_message))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                dialog.dismiss()
                Utils.showToast(requireContext(), "All auto-saved statuses cleared (simulated).")
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
