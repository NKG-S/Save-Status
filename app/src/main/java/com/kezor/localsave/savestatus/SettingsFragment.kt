package com.kezor.localsave.savestatus

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log // Import Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kezor.localsave.savestatus.databinding.FragmentSettingsBinding // Make sure this import matches your layout file name
import androidx.core.content.edit
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences

    private val TAG = "SettingsFragment" // Tag for Log.e()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        setupThemeSettings()
        setupStorageSettings()
        setupAppInfoSettings()
    }

    private fun setupThemeSettings() {
        // Load the saved theme and check the correct radio button
        val currentTheme = sharedPreferences.getString(Constants.KEY_THEME_MODE, Constants.THEME_SYSTEM_DEFAULT)
        Log.e(TAG, "Current theme loaded from preferences: $currentTheme")

        when (currentTheme) {
            Constants.THEME_LIGHT -> binding.radioLightMode.isChecked = true
            Constants.THEME_DARK -> binding.radioDarkMode.isChecked = true
            Constants.THEME_SYSTEM_DEFAULT -> binding.radioSystemDefault.isChecked = true
            else -> { // Fallback in case of unexpected value
                binding.radioSystemDefault.isChecked = true
                Log.e(TAG, "Unexpected theme value '$currentTheme'. Defaulting to System Default.")
            }
        }

        // Listen for changes
        binding.radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_light_mode -> Constants.THEME_LIGHT
                R.id.radio_dark_mode -> Constants.THEME_DARK
                R.id.radio_system_default -> Constants.THEME_SYSTEM_DEFAULT
                else -> Constants.THEME_SYSTEM_DEFAULT // Should not happen with defined IDs
            }
            Log.e(TAG, "User selected theme: $newTheme")

            // Save the new theme value
            sharedPreferences.edit() { putString(Constants.KEY_THEME_MODE, newTheme) }
            Utils.showToast(requireContext(), "Theme set to ${newTheme.replace("_", " ").capitalize(
                Locale.ROOT)}") // User feedback

            // Apply the theme
            ThemeManager.setThemeMode(newTheme)

            // Recreate activity to apply theme change immediately
            activity?.recreate()
        }
    }

    private fun setupStorageSettings() {
        // Change Save Folder
        binding.itemChangeFolder.root.setOnClickListener {
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            Log.e(TAG, "Change Folder clicked - Coming Soon")
        }

        // Auto Save Toggle
        val autoSaveSwitch = binding.itemAutoSaveToggle.switchItem
        autoSaveSwitch.isChecked = sharedPreferences.getBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false)
        autoSaveSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(Constants.KEY_AUTO_SAVE_STATUSES, isChecked).apply()
            val message = "Auto Save: ${if (isChecked) "Enabled" else "Disabled"}"
            Utils.showToast(requireContext(), message)
            Log.e(TAG, message)
        }

        // Save Older Status Toggle
        val saveOlderSwitch = binding.itemSaveOlderToggle.switchItem
        saveOlderSwitch.isChecked = sharedPreferences.getBoolean(Constants.KEY_SAVE_OLDER_STATUS, false)
        saveOlderSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(Constants.KEY_SAVE_OLDER_STATUS, isChecked).apply()
            val message = "Save Older Status: ${if (isChecked) "Enabled" else "Disabled"}"
            Utils.showToast(requireContext(), message)
            Log.e(TAG, message)
        }

        // Go to Saved All Statuses Folder
        binding.itemGoToAutoSaved.root.setOnClickListener {
            Utils.showToast(requireContext(), getString(R.string.coming_soon))
            Log.e(TAG, "Go to Auto Saved clicked - Coming Soon")
        }

        // Clear All Auto-Saved
        binding.itemClearAutoSaved.root.setOnClickListener {
            showClearAllConfirmationDialog()
            Log.e(TAG, "Clear All Auto-Saved clicked")
        }
    }

    private fun setupAppInfoSettings() {
        // Privacy Policy
        binding.itemPrivacyPolicy.root.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, "https://www.example.com/privacy_policy".toUri())
            startActivity(browserIntent)
            Log.e(TAG, "Privacy Policy clicked")
        }

        // Rate Us
        binding.itemRateUs.root.setOnClickListener {
            val appPackageName = requireContext().packageName
            try {
                startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$appPackageName".toUri()))
                Log.e(TAG, "Rate Us clicked - Opening Play Store")
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$appPackageName".toUri()))
                Log.e(TAG, "Rate Us clicked - Play Store not found, opening browser")
            }
        }

        // Share App
        binding.itemShareApp.root.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                // Replace with your actual Play Store link
                putExtra(Intent.EXTRA_TEXT, "Check out this amazing WhatsApp Status Saver app: https://play.google.com/store/apps/details?id=${requireContext().packageName}")
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.settings_share_app)))
            Log.e(TAG, "Share App clicked")
        }

        // Developer Info
        binding.itemDeveloperInfo.root.setOnClickListener {
            Utils.showToast(requireContext(), "Developed by Nethmin")
            Log.e(TAG, "Developer Info clicked")
        }

        // App Version
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.itemAppVersion.textViewItemSubtitle.text = getString(R.string.settings_app_version_placeholder, packageInfo.versionName)
            Log.e(TAG, "App Version: ${packageInfo.versionName}")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Package name not found for app version", e)
            e.printStackTrace()
            binding.itemAppVersion.textViewItemSubtitle.text = getString(R.string.settings_app_version_placeholder, "N/A")
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle(getString(R.string.settings_clear_all_dialog_title))
            .setMessage(getString(R.string.settings_clear_all_dialog_message))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                // TODO: Add actual logic to delete files here
                dialog.dismiss()
                Utils.showToast(requireContext(), "All auto-saved statuses cleared.")
                Log.e(TAG, "Clear All Auto-Saved confirmed")
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
                Log.e(TAG, "Clear All Auto-Saved cancelled")
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}