package com.kezor.localsave.savestatus

import android.os.Environment

object Constants {

    const val KEY_THEME_MODE = "theme_mode"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"
    const val THEME_SYSTEM_DEFAULT = "system_default"
    const val KEY_SAVE_FOLDER_URI = "save_folder_uri"

    // Media types
    const val MEDIA_TYPE_IMAGE = "image"
    const val MEDIA_TYPE_VIDEO = "video"

    // WhatsApp types for spinner
    const val WHATSAPP_TYPE_REGULAR = 0
    const val WHATSAPP_TYPE_BUSINESS = 1

    // Default WhatsApp status paths
    val WHATSAPP_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
    val WHATSAPP_BUSINESS_STATUS_PATH = "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"

    // Default folder name for saving statuses within the app's external files directory
    const val KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH = "/Android/media/com.kezor/.Auto_Save_All_Status" //default save location of auto save all
    const val KEY_SAVE_FOLDER_PATH = "/Android/media/com.kezor" //default save location
    const val APP_SAVE_SUBDIRECTORY_NAME = "SaveStatus" // sub folder
    const val KEY_AUTO_SAVE_STATUSES = "auto_save_all_statuses" //default save sub folder name
}