package com.kezor.localsave.savestatus // Standardized package name

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Data class representing a single media item (image or video).
 * @param file The actual file object.
 * @param uri The URI of the media file.
 * @param type The type of media (Constants.MEDIA_TYPE_IMAGE or Constants.MEDIA_TYPE_VIDEO).
 * @param lastModified The last modified timestamp of the file.
 * @param isSelected Boolean indicating if the item is selected (for selection mode).
 */
@Parcelize
data class MediaItem(
    val file: File,
    val uri: String,
    val type: String,
    val lastModified: Long,
    var isSelected: Boolean = false // Mutable for selection mode
) : Parcelable
