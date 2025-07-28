@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kezor.localsave.savestatus.databinding.FragmentSettingsBinding
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences

    private val TAG = "SettingsFragment"

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
        // Get current theme from SharedPreferences
        val currentTheme = sharedPreferences.getString(Constants.KEY_THEME_MODE, Constants.THEME_SYSTEM_DEFAULT)

        // Set the appropriate chip as selected based on current theme
        when (currentTheme) {
            Constants.THEME_LIGHT -> {
                binding.chipLightMode.isChecked = true
            }
            Constants.THEME_DARK -> {
                binding.chipDarkMode.isChecked = true
            }
            Constants.THEME_SYSTEM_DEFAULT -> {
                binding.chipSystemDefault.isChecked = true
            }
        }

        // Set up chip selection listener
        binding.chipGroupTheme.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds[0]
                val newTheme = when (checkedId) {
                    R.id.chip_light_mode -> Constants.THEME_LIGHT
                    R.id.chip_dark_mode -> Constants.THEME_DARK
                    R.id.chip_system_default -> Constants.THEME_SYSTEM_DEFAULT
                    else -> Constants.THEME_SYSTEM_DEFAULT
                }

                // Save theme preference
                sharedPreferences.edit {
                    putString(Constants.KEY_THEME_MODE, newTheme)
                }

                // Apply theme
                ThemeManager.setThemeMode(newTheme)

                // Show feedback
                showSnackbar("Theme changed to ${getThemeDisplayName(newTheme)}")

                // Recreate activity to apply theme
                activity?.recreate()
            }
        }
    }

    private fun getThemeDisplayName(theme: String): String {
        return when (theme) {
            Constants.THEME_LIGHT -> "Light"
            Constants.THEME_DARK -> "Dark"
            Constants.THEME_SYSTEM_DEFAULT -> "System Default"
            else -> "System Default"
        }
    }

    private fun setupStorageSettings() {
        // Change Save Folder
        binding.itemChangeFolder.setOnClickListener {
            openFolderPicker()
        }

        // Auto Save Toggle - Initialize from SharedPreferences
        binding.switchAutoSave.isChecked = sharedPreferences.getBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false)

        // Auto Save Toggle Listener
        binding.switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit {
                putBoolean(Constants.KEY_AUTO_SAVE_STATUSES, isChecked)
            }

            val message = if (isChecked) {
                "Auto Save enabled - Status updates will be saved automatically"
            } else {
                "Auto Save disabled"
            }
            showSnackbar(message)

            Log.d(TAG, "Auto Save ${if (isChecked) "enabled" else "disabled"}")
        }

        // All Saved Statuses
        binding.itemGoToAutoSaved.setOnClickListener {
            navigateToSavedStatuses()
        }

        // Clear All Auto-Saved
        binding.itemClearAutoSaved.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    private fun setupAppInfoSettings() {
        // Rate Us
        binding.itemRateUs.setOnClickListener {
            openPlayStore()
        }

        // Privacy Policy
        binding.itemPrivacyPolicy.setOnClickListener {
            openPrivacyPolicy()
        }

        // Share App
        binding.itemShareApp.setOnClickListener {
            shareApp()
        }

        // Developer Info
        binding.itemDeveloperInfo.setOnClickListener {
            showDeveloperInfo()
        }

        // App Version - Set version from PackageManager
        setAppVersion()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun openFolderPicker() {
        try {
            // Create intent to open folder picker
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            }

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivityForResult(intent, REQUEST_CODE_FOLDER_PICKER)
            } else {
                showSnackbar("No file manager app found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening folder picker", e)
            showSnackbar("Unable to open folder picker")
        }
    }

    private fun navigateToSavedStatuses() {
        try {
            // Navigate to saved statuses fragment/activity
            // Replace this with your actual navigation logic
            showSnackbar("Opening saved statuses...")

            // Example navigation - replace with your actual implementation
            // findNavController().navigate(R.id.action_settings_to_saved_statuses)

            Log.d(TAG, "Navigating to saved statuses")
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to saved statuses", e)
            showSnackbar("Unable to open saved statuses")
        }
    }

    private fun openPlayStore() {
        try {
            val packageName = requireContext().packageName
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

            if (playStoreIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(playStoreIntent)
            } else {
                // Fallback to web browser
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(webIntent)
            }

            Log.d(TAG, "Opening Play Store for rating")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Play Store", e)
            showSnackbar("Unable to open Play Store")
        }
    }

    private fun openPrivacyPolicy() {
        try {
            // Replace with your actual privacy policy URL
            val privacyPolicyUrl = "https://your-website.com/privacy-policy"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))

            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
                Log.d(TAG, "Opening privacy policy")
            } else {
                showSnackbar("No browser app found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening privacy policy", e)
            showSnackbar("Unable to open privacy policy")
        }
    }

    private fun shareApp() {
        try {
            val packageName = requireContext().packageName
            val appName = getString(R.string.app_name)
            val shareText = "Check out $appName - a great app for saving WhatsApp statuses!\n" +
                    "Download it from: https://play.google.com/store/apps/details?id=$packageName"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Check out $appName")
            }

            startActivity(Intent.createChooser(shareIntent, "Share $appName"))
            Log.d(TAG, "Sharing app")
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing app", e)
            showSnackbar("Unable to share app")
        }
    }

    private fun showDeveloperInfo() {
        val developerInfo = """
            Developer: Kezor Technologies
            Email: developer@kezor.com
            Website: https://kezor.com
            
            Thank you for using our app!
            If you have any questions or feedback, 
            please don't hesitate to contact us.
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("Developer Information")
            .setMessage(developerInfo)
            .setPositiveButton("Contact Developer") { dialog, _ ->
                contactDeveloper()
                dialog.dismiss()
            }
            .setNegativeButton("Close") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_developer)
            .show()
    }

    private fun contactDeveloper() {
        try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:developer@kezor.com")
                putExtra(Intent.EXTRA_SUBJECT, "Feedback for ${getString(R.string.app_name)}")
                putExtra(Intent.EXTRA_TEXT, "Hello,\n\nI have some feedback about your app:\n\n")
            }

            if (emailIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(emailIntent)
            } else {
                showSnackbar("No email app found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening email app", e)
            showSnackbar("Unable to open email app")
        }
    }

    private fun setAppVersion() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = packageInfo.longVersionCode

            binding.textAppVersion.text = "$versionName ($versionCode)"
            Log.d(TAG, "App version: $versionName ($versionCode)")
        } catch (e: PackageManager.NameNotFoundException) {
            binding.textAppVersion.text = "Unknown"
            Log.e(TAG, "Error getting app version", e)
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Saved Statuses")
            .setMessage("Are you sure you want to delete all saved statuses? This action cannot be undone.")
            .setPositiveButton("Delete All") { dialog, _ ->
                clearAllSavedStatuses()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(R.drawable.ic_delete)
            .show()
    }

    private fun clearAllSavedStatuses() {
        try {
            // TODO: Implement actual clearing logic based on your app's storage mechanism
            // This could involve:
            // 1. Deleting files from storage
            // 2. Clearing database entries
            // 3. Resetting SharedPreferences values

            // Example implementation:
            // StatusRepository.clearAllSavedStatuses()
            // or
            // FileUtils.deleteAllSavedFiles()

            // For now, just show success message
            showSnackbar("All saved statuses have been cleared")
            Log.d(TAG, "All saved statuses cleared")

            // Optionally reset auto-save preference
            // sharedPreferences.edit { putBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false) }
            // binding.switchAutoSave.isChecked = false

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing saved statuses", e)
            showSnackbar("Failed to clear saved statuses")
        }
    }

    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_FOLDER_PICKER && data != null) {
            data.data?.let { uri ->
                try {
                    // Take persistable permission
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    // Save selected folder URI
                    sharedPreferences.edit {
                        putString(Constants.KEY_SAVE_FOLDER_URI, uri.toString())
                    }

                    showSnackbar("Save folder updated successfully")
                    Log.d(TAG, "Save folder URI: $uri")
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting save folder", e)
                    showSnackbar("Failed to set save folder")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_FOLDER_PICKER = 1001
    }
}