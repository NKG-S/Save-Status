package com.kezor.localsave.savestatus

import android.os.Environment
import java.io.File

object Constants {

    // Default save folder for the app
    // This will be created within the app-specific directory or a user-selected directory
    const val DEFAULT_SAVE_FOLDER_NAME = "/Android/media/com.kezor/SaveStatus"
    val DEFAULT_AUTO_SAVE_SUBFOLDER_NAME = "AutoSaved"

    // Preferences keys
    const val PREFS_NAME = "app_preferences"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_AUTO_SAVE_TOGGLE = "auto_save_toggle"
    const val KEY_SAVE_OLDER_TOGGLE = "save_older_toggle"

    // Request codes
    const val REQUEST_CODE_MANAGE_ALL_FILES_ACCESS = 1001
    const val REQUEST_CODE_READ_WRITE_STORAGE_PERMISSIONS = 1002
    const val REQUEST_CODE_PICK_FOLDER = 1003 // For changing save folder

    // Media types
    const val MEDIA_TYPE_IMAGE = "image"
    const val MEDIA_TYPE_VIDEO = "video"



    // Theme values
    const val KEY_APP_THEME = "app_theme"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM_DEFAULT = "system_default"


    // Default WhatsApp status paths
    val WHATSAPP_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    val WHATSAPP_BUSINESS_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"


    // Default folder name for saving statuses within the app's external files directory

    // Preference keys
    const val KEY_SAVE_FOLDER_PATH = "/Android/media/com.kezor/SaveStatus"
//    const val KEY_SAVE_FOLDER_PATH = "save_folder_path"
    const val KEY_AUTO_SAVE_STATUSES = "auto_save_statuses"
    const val KEY_SAVE_OLDER_STATUS = "save_older_status"






}
