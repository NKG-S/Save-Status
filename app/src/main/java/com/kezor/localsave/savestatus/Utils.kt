@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.kezor.localsave.savestatus

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    fun performHapticFeedback(view: View, type: Int) {
        view.performHapticFeedback(type)
    }

    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
    }

    fun copyFile(sourceFile: File, destFile: File): Boolean {
        if (!destFile.parentFile.exists()) {
            destFile.parentFile.mkdirs()
        }
        return try {
            FileInputStream(sourceFile).use { `in` ->
                FileOutputStream(destFile).use { out ->
                    `in`.copyTo(out)
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun scanMediaFile(context: Context, filePath: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            null
        ) { path, uri ->
        }
    }

    fun deleteFile(context: Context, file: File): Boolean {
        return try {
            val deleted = file.delete()
            if (deleted) {
                scanMediaFile(context, file.absolutePath)
            }
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
