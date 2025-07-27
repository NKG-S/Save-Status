package com.kezor.localsave.savestatus

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import androidx.core.content.edit

object ThemeManager {

    // Theme modes
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    /**
     * Applies the saved theme mode or the system default.
     * @param context The application context.
     */
    fun applyTheme(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val themeMode = prefs.getString(Constants.KEY_APP_THEME, Constants.THEME_SYSTEM_DEFAULT)

        setThemeMode(themeMode ?: Constants.THEME_SYSTEM_DEFAULT)
    }

    /**
     * Sets the night mode based on the provided theme mode.
     * @param themeMode The theme mode to apply (THEME_SYSTEM, THEME_LIGHT, THEME_DARK).
     */
    fun setNightMode(themeMode: Int) {
        when (themeMode) {
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }
    }

    /**
     * Saves the selected theme mode to shared preferences.
     * @param context The application context.
     * @param themeMode The theme mode to save.
     */
    fun saveThemeMode(context: Context, themeMode: Int) {
        PreferenceManager.getDefaultSharedPreferences(context).edit() {
            putInt(
                Constants.KEY_THEME_MODE,
                themeMode
            )
        }
    }

    /**
     * Retrieves the currently saved theme mode.
     * @param context The application context.
     * @return The saved theme mode.
     */
    fun getSavedThemeMode(context: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getInt(Constants.KEY_THEME_MODE, THEME_SYSTEM)
    }



    fun setThemeMode(themeMode: String) {
        when (themeMode) {
            Constants.THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            Constants.THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            Constants.THEME_SYSTEM_DEFAULT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }


}
