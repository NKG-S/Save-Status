package com.kezor.localsave.savestatus

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
data class MediaItem(
    val uri: String,
    val file: File?,
    val type: String, // "image" or "video"
    val lastModified: Long,
    var isSelected: Boolean = false // For selection mode in SavedFragment
) : Parcelable
