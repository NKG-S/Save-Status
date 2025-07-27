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
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.kezor.localsave.savestatus.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var permissionDialog: AlertDialog? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false

        if (readGranted && writeGranted) {
            Log.d("MainActivity", "READ/WRITE permissions granted.")
            hidePermissionRequiredUI()
            setupNavigation()
        } else {
            Log.w("MainActivity", "READ/WRITE permissions denied. Showing dialog again.")
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

    override fun onResume() {
        super.onResume()
        if (!checkStoragePermission()) {
            showPermissionRequiredUI()
        } else {
            hidePermissionRequiredUI()
            if (binding.navHostFragmentActivityMain.visibility == View.VISIBLE && !::navController.isInitialized) {
                setupNavigation()
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
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

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                manageExternalStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageExternalStorageLauncher.launch(intent)
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun showPermissionRequiredUI() {
        binding.navHostFragmentActivityMain.visibility = View.GONE
        binding.navView.visibility = View.GONE
        binding.permissionOverlay.visibility = View.VISIBLE

        binding.permissionOverlay.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_grant_permission).setOnClickListener {
            requestStoragePermission()
        }
        binding.permissionOverlay.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_go_to_settings).setOnClickListener {
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
        if (!::navController.isInitialized) {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
            navController = navHostFragment.navController

            val navView: BottomNavigationView = binding.navView

            val appBarConfiguration = androidx.navigation.ui.AppBarConfiguration(
                setOf(
                    R.id.navigation_status, R.id.navigation_saved, R.id.navigation_settings
                )
            )
            navView.setupWithNavController(navController)
        }
    }
}
