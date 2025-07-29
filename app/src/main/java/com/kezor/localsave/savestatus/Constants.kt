package com.kezor.localsave.savestatus

import android.os.Environment
import java.io.File

object Constants {

    const val KEY_THEME_MODE = "theme_mode"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM_DEFAULT = "system_default"
    const val KEY_SAVE_FOLDER_URI = "save_folder_uri"
    const val PREFS_NAME = "app_preferences"
    const val KEY_AUTO_SAVE_TOGGLE = "auto_save_toggle"
    const val KEY_SAVE_OLDER_TOGGLE = "save_older_toggle"

    // Request codes
    const val REQUEST_CODE_MANAGE_ALL_FILES_ACCESS = 1001
    const val REQUEST_CODE_READ_WRITE_STORAGE_PERMISSIONS = 1002
    const val REQUEST_CODE_PICK_FOLDER = 1003 // For changing save folder

    // Media types
    const val MEDIA_TYPE_IMAGE = "image"
    const val MEDIA_TYPE_VIDEO = "video"

    // Theme values (String representation for preferences)

    // Default WhatsApp status paths
    val WHATSAPP_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    val WHATSAPP_BUSINESS_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"

    // Default folder name for saving statuses within the app's external files directory

    // Preference keys

    const val KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH = "/Android/media/com.kezor/.Auto_Save_All_Status" //default save location of auto save all

    const val KEY_SAVE_FOLDER_PATH = "/Android/media/com.kezor" //default save location
    const val KEY_AUTO_SAVE_STATUSES = "auto_save_all_statuses"
    const val APP_SAVE_SUBDIRECTORY_NAME = "SaveStatus" //default save sub folder name
    const val KEY_SAVE_OLDER_STATUS = "save_older_status"


    const val THEME_SELECTION = "theme_selection"
    const val AUTO_SAVE_TOGGLE = "auto_save_toggle"
    const val SAVE_OLDER_TOGGLE = "save_older_toggle"
    const val GO_TO_AUTO_SAVED = "go_to_auto_saved"
    const val CLEAR_ALL_AUTO_SAVED = "clear_all_auto_saved"
    const val CHANGE_SAVE_FOLDER = "change_save_folder"
}
