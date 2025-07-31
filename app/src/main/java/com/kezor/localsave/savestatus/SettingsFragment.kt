@file:Suppress("DEPRECATION")
package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.kezor.localsave.savestatus.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.jvm.java

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var sharedPreferences: SharedPreferences
    private val TAG = "SettingsFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
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
        updateCurrentSavePathDisplay()
    }

    private fun setupThemeSettings() {
        val currentTheme = sharedPreferences.getString(Constants.KEY_THEME_MODE, Constants.THEME_SYSTEM_DEFAULT)

        when (currentTheme) {
            Constants.THEME_LIGHT -> binding.chipLightMode.isChecked = true
            Constants.THEME_DARK -> binding.chipDarkMode.isChecked = true
            Constants.THEME_SYSTEM_DEFAULT -> binding.chipSystemDefault.isChecked = true
        }

        binding.chipGroupTheme.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val checkedId = checkedIds[0]
                val newTheme = when (checkedId) {
                    R.id.chip_light_mode -> Constants.THEME_LIGHT
                    R.id.chip_dark_mode -> Constants.THEME_DARK
                    R.id.chip_system_default -> Constants.THEME_SYSTEM_DEFAULT
                    else -> Constants.THEME_SYSTEM_DEFAULT
                }

                sharedPreferences.edit {
                    putString(Constants.KEY_THEME_MODE, newTheme)
                }

                ThemeManager.setThemeMode(newTheme)
                showSnackbar("Theme changed to ${getThemeDisplayName(newTheme)}")
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

    private val autoSaveHandler = Handler(Looper.getMainLooper())
    private val autoSaveRunnable = object : Runnable {
        override fun run() {
            performAutoSaveIfEnabled(requireContext())
            autoSaveHandler.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun setupStorageSettings() {
        binding.itemChangeFolder.setOnClickListener {
            openFolderPicker()
        }

        val isAutoSaveEnabled = sharedPreferences.getBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false)
        binding.switchAutoSaveAll.isChecked = isAutoSaveEnabled

        binding.switchAutoSaveAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                sharedPreferences.edit {
                    putBoolean(Constants.KEY_AUTO_SAVE_STATUSES, true)
                }
                showSnackbar("Auto Save enabled")
                startRepeatingAutoSave()
            } else {
                showDisableAutoSaveConfirmation()
            }
        }

        if (isAutoSaveEnabled) {
            startRepeatingAutoSave()
        }

        binding.itemGoToAutoSaved.setOnClickListener {
            navigateToSavedStatuses()
        }

        binding.itemClearAutoSaved.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    private fun startRepeatingAutoSave() {
        performAutoSaveIfEnabled(requireContext())
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
        autoSaveHandler.postDelayed(autoSaveRunnable, 15 * 60 * 1000L)
    }

    private fun stopRepeatingAutoSave() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable)
    }

    private fun showDisableAutoSaveConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Disable Auto Save")
            .setMessage("Do you want to clear all auto-saved media from the folder?")
            .setPositiveButton("Clear") { dialog, _ ->
                clearAutoSaveFolder()
                sharedPreferences.edit {
                    putBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false)
                }
                stopRepeatingAutoSave()
                showSnackbar("Auto Save disabled and folder cleared")
                dialog.dismiss()
            }
            .setNegativeButton("Keep Media") { dialog, _ ->
                binding.switchAutoSaveAll.isChecked = false
                showSnackbar("Didn't delete all media save until now")
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear All Saved Statuses")
            .setMessage("This will permanently delete all saved statuses.")
            .setPositiveButton("Delete") { dialog, _ ->
                clearAutoSaveFolder()
                showSnackbar("All saved statuses have been cleared")
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAutoSaveFolder() {
        val autoSaveDir = File(Environment.getExternalStorageDirectory(), Constants.KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH)
        if (autoSaveDir.exists() && autoSaveDir.isDirectory) {
            autoSaveDir.listFiles()?.forEach { file ->
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.e("AutoSave", "Failed to delete file: ${file.name}", e)
                }
            }
        }
        clearCopiedPrefs()
    }

    @SuppressLint("UseKtx")
    private fun clearCopiedPrefs() {
        val prefs = sharedPreferences
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("COPIED_") }
            .forEach { key -> editor.remove(key) }
        editor.apply()
    }

    private fun wasFileAlreadyCopied(file: File, prefs: SharedPreferences): Boolean {
        val key = "COPIED_${file.absolutePath.hashCode()}"
        return prefs.getLong(key, -1L) == file.lastModified()
    }

    @SuppressLint("UseKtx")
    private fun markFileAsCopied(file: File, prefs: SharedPreferences) {
        val key = "COPIED_${file.absolutePath.hashCode()}"
        prefs.edit().putLong(key, file.lastModified()).apply()
    }

    @SuppressLint("SimpleDateFormat")
    fun performAutoSaveIfEnabled(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(Constants.KEY_AUTO_SAVE_STATUSES, false)) return

        val autoSaveDir = File(Environment.getExternalStorageDirectory(), Constants.KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH)
        if (!autoSaveDir.exists()) autoSaveDir.mkdirs()

        val sourceDirs = listOf(
            File(Constants.WHATSAPP_STATUS_PATH),
            File(Constants.WHATSAPP_BUSINESS_STATUS_PATH)
        )

        sourceDirs.forEach { sourceDir ->
            if (sourceDir.exists() && sourceDir.isDirectory) {
                sourceDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension == "jpg" || file.extension == "mp4")) {
                        if (!wasFileAlreadyCopied(file, prefs)) {
                            val destFileName = generateDeterministicFileName(file)
                            val destFile = File(autoSaveDir, destFileName)

                            if (!destFile.exists()) {
                                try {
                                    file.copyTo(destFile)
                                    markFileAsCopied(file, prefs)
                                    Log.d("AutoSave", "Saved: ${destFile.name}")
                                } catch (e: Exception) {
                                    Log.e("AutoSave", "Failed to copy: ${file.name}", e)
                                }
                            } else {
                                markFileAsCopied(file, prefs)
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun generateDeterministicFileName(file: File): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date(file.lastModified()))
        val baseName = file.nameWithoutExtension
        return "${baseName}_$timestamp.${file.extension}"
    }

    @SuppressLint("SetTextI18n")
    private fun updateCurrentSavePathDisplay() {
        val currentPath = getSavedFolderPath()
        binding.textCurrentSavePath.text = "Current save location: $currentPath"
    }

    @SuppressLint("UseKtx")
    private fun getSavedFolderPath(): String {
        val customUriString = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
        return if (!customUriString.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUriString)
                return uri.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "Custom location (Error parsing URI)"
            }
        } else {
            getDefaultSavePath()
        }
    }

    private fun getDefaultSavePath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
    }

    @SuppressLint("QueryPermissionsNeeded", "ObsoleteSdkInt")
    private fun openFolderPicker() {
        try {
            val safIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        Environment.getExternalStorageDirectory().toUri())
                }
            }

            val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }

            val chooserIntent = Intent.createChooser(safIntent, "Select folder").apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(fallbackIntent))
            }

            if (chooserIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivityForResult(chooserIntent, REQUEST_CODE_FOLDER_PICKER)
            } else {
                showSnackbar("No file manager app found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening folder picker", e)
            showSnackbar("Unable to open folder picker")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_FOLDER_PICKER && data != null) {
            data.data?.let { uri ->
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    if (canWriteToUri(uri)) {
                        sharedPreferences.edit {
                            putString(Constants.KEY_SAVE_FOLDER_URI, uri.toString())
                            putString(Constants.KEY_SAVE_FOLDER_PATH,
                                DocumentFile.fromTreeUri(requireContext(), uri)?.uri?.path ?: "")
                        }
                        showSnackbar("Save location updated successfully")
                        updateCurrentSavePathDisplay()
                        (activity as? AppCompatActivity)?.supportFragmentManager?.fragments?.forEach {
                            if (it is SavedFragment) {
                                it.loadMedia(it.currentMediaType)
                            }
                        }
                    } else {
                        showSnackbar("Cannot write to selected location")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting save folder", e)
                    showSnackbar("Failed to set save folder")
                }
            }
        }
    }
    private fun navigateToSavedStatuses() {
        try {
            val intent = Intent(requireContext(), SaveAll::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to saved statuses", e)
            showSnackbar("Unable to open saved statuses")
        }
    }

    private fun canWriteToUri(uri: Uri): Boolean {
        return try {
            val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
            val testFile = docFile?.createFile("text/plain", ".test")
            if (testFile != null) {
                testFile.delete()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun setupAppInfoSettings() {
        binding.itemRateUs.setOnClickListener { openPlayStore() }
        binding.itemPrivacyPolicy.setOnClickListener { openPrivacyPolicy() }
        binding.itemShareApp.setOnClickListener { shareApp() }
        binding.itemDeveloperInfo.setOnClickListener { showDeveloperInfo() }
        setAppVersion()
    }

    @SuppressLint("UseKtx", "QueryPermissionsNeeded")
    private fun openPlayStore() {
        try {
            val packageName = requireContext().packageName
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))

            if (playStoreIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(playStoreIntent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Play Store", e)
            showSnackbar("Unable to open Play Store")
        }
    }

//    @SuppressLint("UseKtx", "QueryPermissionsNeeded")
//    private fun openPrivacyPolicy() {
//        try {
//            // IMPORTANT: Replace this with your actual privacy policy URL
//            val privacyPolicyUrl = getString(R.string.Privacy_policy_url)
//            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
//
//            // The previous conditional check can sometimes fail. A more robust way is to try and
//            // start the activity, and catch the exception if no app is found.
//            startActivity(intent)
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Error opening privacy policy", e)
//            showSnackbar("No browser app found")
//        }
//    }

    private fun openPrivacyPolicy() {
        try {
            // Create an explicit intent to open your PrivacyPolicy activity
            val intent = Intent(requireContext(), PrivacyPolicy::class.java)

            // Start the activity
            startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Error opening privacy policy activity", e)
            showSnackbar("Unable to open privacy policy screen")
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
            }

            startActivity(Intent.createChooser(shareIntent, "Share $appName"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing app", e)
            showSnackbar("Unable to share app")
        }
    }
    private fun showDeveloperInfo() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.developer_information))
            .setMessage(getString(R.string.developer_information_message))
            .setPositiveButton("WhatsApp") { _, _ ->
                contactWhatsApp()
            }
            .setNeutralButton("Email") { _, _ ->
                contactDeveloper()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun contactWhatsApp() {
        try {
            val appName = getString(R.string.app_name)
            val defaultMessage = getString(R.string.whatsapp_default_message, appName)

            // URL-encode the message to handle spaces and special characters
            val encodedMessage = URLEncoder.encode(defaultMessage, "UTF-8")

            val whatsappNumber = getString(R.string.whatsapp_number)
            val whatsappUri = "http://wa.me/$whatsappNumber?text=$encodedMessage".toUri()
            val intent = Intent(Intent.ACTION_VIEW, whatsappUri)

            val isWhatsAppInstalled = isPackageInstalled("com.whatsapp")
            val isWhatsAppBusinessInstalled = isPackageInstalled("com.whatsapp.w4b")

            if (isWhatsAppInstalled) {
                intent.setPackage("com.whatsapp")
                startActivity(intent)
            } else if (isWhatsAppBusinessInstalled) {
                intent.setPackage("com.whatsapp.w4b")
                startActivity(intent)
            } else {
                showSnackbar("WhatsApp is not installed on this device. Opening in browser...")
                val webIntent = Intent(Intent.ACTION_VIEW, whatsappUri)
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WhatsApp", e)
            showSnackbar("Unable to open WhatsApp: ${e.localizedMessage}")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @SuppressLint("QueryPermissionsNeeded", "UseKtx")
    private fun contactDeveloper() {
        try {
            val recipient = getString(R.string.developer_contact)
            val subject = "User Feedback Of ${getString(R.string.app_name)}"

            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$recipient")
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }

            if (emailIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(emailIntent)
            } else {
                val fallbackEmailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                }

                if (fallbackEmailIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(Intent.createChooser(fallbackEmailIntent, "Send email using..."))
                } else {
                    showSnackbar("No email app found or configured on this device.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening email app", e)
            showSnackbar("Unable to open email app: ${e.localizedMessage}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setAppVersion() {
        try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.textAppVersion.text = "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.textAppVersion.text = getString(R.string.app_name)
            Log.e(TAG, "Error getting app version", e)
        }
    }


    private fun showSnackbar(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show()
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