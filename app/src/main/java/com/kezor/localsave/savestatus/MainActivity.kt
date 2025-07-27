package com.kezor.localsave.savestatus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment // Import NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kezor.localsave.savestatus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var permissionDialog: AlertDialog? = null

    // ActivityResultLauncher for requesting runtime permissions (Android 10 and below)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all necessary permissions are granted
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false

        if (readGranted && writeGranted) {
            // Permissions granted, proceed with app initialization
            Log.d("MainActivity", "READ/WRITE permissions granted.")
            hidePermissionRequiredUI()
            setupNavigation() // Call setupNavigation after permissions are granted
        } else {
            // Permissions denied, show dialog again
            Log.w("MainActivity", "READ/WRITE permissions denied. Showing dialog again.")
            showPermissionRequiredUI()
        }
    }

    // ActivityResultLauncher for managing all files access (Android 11+)
    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkStoragePermission()) {
            // Permission granted, proceed with app initialization
            Log.d("MainActivity", "MANAGE_EXTERNAL_STORAGE permission granted.")
            hidePermissionRequiredUI()
            setupNavigation() // Call setupNavigation after permissions are granted
        } else {
            // Permission denied, show dialog again
            Log.w("MainActivity", "MANAGE_EXTERNAL_STORAGE permission denied. Showing dialog again.")
            showPermissionRequiredUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the saved theme before calling super.onCreate()
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial check for permissions
        if (!checkStoragePermission()) {
            // If permissions are not granted, show the permission required UI
            showPermissionRequiredUI()
        } else {
            // Permissions are already granted, set up navigation
            setupNavigation()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when the activity resumes (e.g., after user returns from settings)
        if (!checkStoragePermission()) {
            showPermissionRequiredUI()
        } else {
            hidePermissionRequiredUI()
            // If navigation was not set up because of permissions, set it up now
            // This check ensures we don't try to setup navigation if the main content is already visible
            // and navigation was already setup.
            if (binding.navHostFragmentActivityMain.visibility == View.VISIBLE && !::navController.isInitialized) {
                setupNavigation()
            }
        }
    }

    /**
     * Checks if the necessary storage permissions are granted.
     * Differentiates between Android 10- and Android 11+.
     * @return True if permissions are granted, false otherwise.
     */
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 (API 30) and above, check MANAGE_EXTERNAL_STORAGE
            Environment.isExternalStorageManager()
        } else {
            // For Android 10 (API 29) and below, check READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
            val readPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            val writePermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            readPermission && writePermission
        }
    }

    /**
     * Requests storage permissions based on Android version.
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, direct user to settings for MANAGE_EXTERNAL_STORAGE
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                manageExternalStorageLauncher.launch(intent)
            } catch (e: Exception) {
                // Fallback for some devices where the above intent might not work
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageExternalStorageLauncher.launch(intent)
            }
        } else {
            // For Android 10 and below, request runtime permissions
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    /**
     * Shows a custom UI explaining why permission is needed and offers to grant it.
     */
    private fun showPermissionRequiredUI() {
        // Hide the main content and navigation
        binding.navHostFragmentActivityMain.visibility = View.GONE
        binding.navView.visibility = View.GONE
        binding.permissionOverlay.visibility = View.VISIBLE

        // Set up button click listeners for the permission overlay
        binding.permissionOverlay.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_grant_permission).setOnClickListener {
            requestStoragePermission()
        }
        binding.permissionOverlay.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_go_to_settings).setOnClickListener {
            // Direct user to app settings if they repeatedly deny
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    /**
     * Hides the permission required UI and shows the main app content.
     */
    private fun hidePermissionRequiredUI() {
        binding.permissionOverlay.visibility = View.GONE
        binding.navHostFragmentActivityMain.visibility = View.VISIBLE
        binding.navView.visibility = View.VISIBLE
    }

    /**
     * Sets up the Bottom Navigation View with the Navigation Component.
     */
    private fun setupNavigation() {
        // Ensure NavController is initialized only once
        if (!::navController.isInitialized) {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            navController = navHostFragment.navController

            val navView: BottomNavigationView = binding.navView

            // Passing each menu ID as a set of Ids because each
            // menu should be considered as top level destinations.
            val appBarConfiguration = androidx.navigation.ui.AppBarConfiguration(
                setOf(
                    R.id.navigation_status, R.id.navigation_saved, R.id.navigation_settings
                )
            )
            // No action bar in this app, so no need to set up with AppBarConfiguration
            // setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)
        }
    }
}
