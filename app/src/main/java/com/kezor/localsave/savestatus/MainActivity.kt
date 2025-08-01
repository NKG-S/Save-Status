package com.kezor.localsave.savestatus

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.kezor.localsave.savestatus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "Permission results: $permissions")

        if (checkStoragePermission()) {
            Log.d("MainActivity", "All required permissions granted.")
            hidePermissionRequiredUI()
            setupNavigation()
        } else {
            Log.w("MainActivity", "Some permissions denied. Showing dialog again.")
            showPermissionRequiredUI()
        }
    }

    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (checkStoragePermission()) {
            Log.d("MainActivity", "MANAGE_EXTERNAL_STORAGE permission granted.")
            hidePermissionRequiredUI()
            setupNavigation()
        } else {
            Log.w("MainActivity", "MANAGE_EXTERNAL_STORAGE permission denied. Showing dialog again.")
            showPermissionRequiredUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the theme before calling super.onCreate() to ensure it takes effect
        // immediately upon activity creation.
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!checkStoragePermission()) {
            showPermissionRequiredUI()
        } else {
            setupNavigation()
        }
    }

    @SuppressLint("UseKtx")
    override fun onResume() {
        super.onResume()
        // Re-check permissions and adjust UI visibility on resume
        if (!checkStoragePermission()) {
            showPermissionRequiredUI()
        } else {
            hidePermissionRequiredUI()
            // Only attempt to setup navigation if the NavHostFragment is visible
            // and navController hasn't been initialized yet.
            // This prevents re-initializing if already set up and visible.
            if (binding.navHostFragmentActivityMain.visibility == View.VISIBLE && !::navController.isInitialized) {
                setupNavigation()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33+) - Only check granular media permissions and MANAGE_EXTERNAL_STORAGE
                val readMediaImages = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED

                val readMediaVideo = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED

                val manageStorage = Environment.isExternalStorageManager()

                Log.d("MainActivity", "Android 13+ - ReadMediaImages: $readMediaImages, ReadMediaVideo: $readMediaVideo, ManageStorage: $manageStorage")
                readMediaImages && readMediaVideo && manageStorage
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12 (API 30-32) - Only check MANAGE_EXTERNAL_STORAGE
                val manageStorage = Environment.isExternalStorageManager()
                Log.d("MainActivity", "Android 11-12 - ManageStorage: $manageStorage")
                manageStorage
            }
            else -> {
                // Android 10 (API 29) - Only check legacy storage permissions
                val readPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val writePermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED

                Log.d("MainActivity", "Android 10 - Read: $readPermission, Write: $writePermission")
                readPermission && writePermission
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ (API 33+) - Request only granular media permissions first
                Log.d("MainActivity", "Requesting Android 13+ permissions")

                val mediaPermissions = mutableListOf<String>()

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                    mediaPermissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                    != PackageManager.PERMISSION_GRANTED) {
                    mediaPermissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                }

                if (mediaPermissions.isNotEmpty()) {
                    Log.d("MainActivity", "Requesting media permissions: $mediaPermissions")
                    requestPermissionLauncher.launch(mediaPermissions.toTypedArray())
                } else if (!Environment.isExternalStorageManager()) {
                    // Media permissions already granted, request MANAGE_EXTERNAL_STORAGE
                    Log.d("MainActivity", "Media permissions granted, requesting MANAGE_EXTERNAL_STORAGE")
                    requestManageExternalStorage()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11-12 (API 30-32) - Request only MANAGE_EXTERNAL_STORAGE
                Log.d("MainActivity", "Requesting Android 11-12 permissions - MANAGE_EXTERNAL_STORAGE only")
                requestManageExternalStorage()
            }
            else -> {
                // Android 10 (API 29) - Request only legacy storage permissions
                Log.d("MainActivity", "Requesting Android 10 permissions - Legacy storage only")
                val legacyPermissions = mutableListOf<String>()

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    legacyPermissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    legacyPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                if (legacyPermissions.isNotEmpty()) {
                    Log.d("MainActivity", "Requesting legacy permissions: $legacyPermissions")
                    requestPermissionLauncher.launch(legacyPermissions.toTypedArray())
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("UseKtx")
    private fun requestManageExternalStorage() {
        Log.d("MainActivity", "Launching MANAGE_EXTERNAL_STORAGE permission request")
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
            manageExternalStorageLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to launch specific manage storage intent, using general one", e)
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageExternalStorageLauncher.launch(intent)
        }
    }

    private fun showPermissionRequiredUI() {
        binding.navHostFragmentActivityMain.visibility = View.GONE
        binding.navView.visibility = View.GONE
        binding.permissionOverlay.visibility = View.VISIBLE

        binding.permissionOverlay.findViewById<MaterialButton>(R.id.btn_grant_permission).setOnClickListener {
            requestStoragePermission()
        }
        binding.permissionOverlay.findViewById<MaterialButton>(R.id.btn_go_to_settings).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri
            startActivity(intent)
        }
    }

    private fun hidePermissionRequiredUI() {
        binding.permissionOverlay.visibility = View.GONE
        binding.navHostFragmentActivityMain.visibility = View.VISIBLE
        binding.navView.visibility = View.VISIBLE
    }

    private fun setupNavigation() {
        // Ensure NavController is initialized only once
        if (!::navController.isInitialized) {
            // Get the NavHostFragment and then its NavController
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            navController = navHostFragment.navController

            val navView: BottomNavigationView = binding.navView

            AppBarConfiguration(
                setOf(
                    R.id.navigation_status,
                    R.id.navigation_saved,
                    R.id.navigation_settings
                )
            )
            navView.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.mediaViewerFragment -> {
                        binding.navView.visibility = View.GONE
                    }
                    else -> {
                        binding.navView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}