package com.kezor.localsave.savestatus

import android.content.Intent
import android.content.pm.PackageManager // ADDED: Import PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
// Removed: import com.kezor.localsave.savestatus.BuildConfig // No longer needed for app version
import com.kezor.localsave.savestatus.databinding.FragmentSettingsBinding // Assuming you have this binding

class SettingsFragment : PreferenceFragmentCompat() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        // Theme Selection Preference
        findPreference<Preference>(Constants.KEY_APP_THEME)?.setOnPreferenceChangeListener { _, newValue ->
            val theme = newValue as String
            ThemeManager.setThemeMode(theme)
            activity?.recreate() // Recreate activity to apply theme change
            true
        }

        // Saved Status Storage Location (Change Folder)
        findPreference<Preference>(getString(R.string.settings_change_save_folder))?.setOnPreferenceClickListener {
            // Implement folder picker logic here if needed, or show a Toast for now
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            true
        }

        // Auto Save Toggle
        findPreference<Preference>(Constants.KEY_AUTO_SAVE_TOGGLE)?.setOnPreferenceChangeListener { _, newValue ->
            // Handle auto save logic
            Utils.showToast(requireContext(), "Auto Save: ${if (newValue as Boolean) "Enabled" else "Disabled"}")
            true
        }

        // Save Older Status Toggle
        findPreference<Preference>(Constants.KEY_SAVE_OLDER_TOGGLE)?.setOnPreferenceChangeListener { _, newValue ->
            // Handle save older status logic
            Utils.showToast(requireContext(), "Save Older Status: ${if (newValue as Boolean) "Enabled" else "Disabled"}")
            true
        }

        // Go to Automatically Saved
        findPreference<Preference>(getString(R.string.settings_go_to_auto_saved))?.setOnPreferenceClickListener {
            // Navigate to SavedFragment and filter for auto-saved items if applicable
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            true
        }

        // Clear All Automatically Saved
        findPreference<Preference>(getString(R.string.settings_clear_all_auto_saved))?.setOnPreferenceClickListener {
            showClearAllConfirmationDialog()
            true
        }

        // Privacy Policy
        findPreference<Preference>(getString(R.string.settings_privacy_policy))?.setOnPreferenceClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/privacy_policy"))
            startActivity(browserIntent)
            true
        }

        // Rate Us
        findPreference<Preference>(getString(R.string.settings_rate_us))?.setOnPreferenceClickListener {
            val appPackageName = requireContext().packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
            }
            true
        }

        // Share App
        findPreference<Preference>(getString(R.string.settings_share_app))?.setOnPreferenceClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                putExtra(Intent.EXTRA_TEXT, "Check out this amazing WhatsApp Status Saver app: [App Play Store Link Here]")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_app)))
            true
        }

        // Developer Info
        findPreference<Preference>(getString(R.string.settings_developer_info))?.setOnPreferenceClickListener {
            Utils.showToast(requireContext(), "Developed by Nethmin")
            true
        }

        // App Version
        // Retrieve app version using PackageManager
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up the toolbar/app bar if you have one in fragment_settings.xml
        // (This assumes you'll have a MaterialToolbar or similar in your settings layout)
        // binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle(getString(R.string.settings_clear_all_dialog_title))
            .setMessage(getString(R.string.settings_clear_all_dialog_message))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                dialog.dismiss()
                // Implement actual clear all logic here
                Utils.showToast(requireContext(), "All auto-saved statuses cleared (simulated).")
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}
