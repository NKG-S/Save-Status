package com.kezor.localsave.savestatus

import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import java.io.File

@Parcelize
data class MediaItem(
    val file: File? = null,
    val uri: String,
    val type: String,
    val lastModified: Long,
    val isSelected: Boolean = false
) : Parcelable {

    // Custom equals to ensure proper comparison
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaItem

        if (uri != other.uri) return false
        if (type != other.type) return false
        if (lastModified != other.lastModified) return false
        if (isSelected != other.isSelected) return false

        return true
    }




    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + isSelected.hashCode()
        return result
    }
}
