package com.kezor.localsave.savestatus

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

object ThemeManager {

    private const val TAG = "ThemeManager" // Tag for Log.e()

    /**
     * Applies the saved theme mode or the system default.
     * This should be called in the application's entry point (e.g., Application class or MainActivity).
     * @param context The application context.
     */
    fun applyTheme(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Retrieve theme mode using KEY_THEME_MODE, defaulting to system default
        val themeMode = prefs.getString(Constants.KEY_THEME_MODE, Constants.THEME_SYSTEM_DEFAULT)
        setThemeMode(themeMode ?: Constants.THEME_SYSTEM_DEFAULT) // Ensure non-null
        Log.e(TAG, "Applied theme on app start: $themeMode")
    }

    /**
     * Sets the night mode based on the provided theme mode string.
     * This is the primary function for applying theme changes.
     * @param themeMode The theme mode string to apply (Constants.THEME_LIGHT, Constants.THEME_DARK, Constants.THEME_SYSTEM_DEFAULT).
     */
    fun setThemeMode(themeMode: String) {
        when (themeMode) {
            Constants.THEME_LIGHT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                Log.e(TAG, "Setting theme to Light Mode")
            }
            Constants.THEME_DARK -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                Log.e(TAG, "Setting theme to Dark Mode")
            }
            Constants.THEME_SYSTEM_DEFAULT -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.e(TAG, "Setting theme to System Default")
            }
            else -> {
                // Fallback for any unexpected themeMode value
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.e(TAG, "Unknown theme mode '$themeMode'. Defaulting to System Default.")
            }
        }
    }
}