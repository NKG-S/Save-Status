package com.kezor.localsave.savestatus

import android.content.Context
import android.media.MediaScannerConnection
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

    /**
     * Triggers haptic feedback.
     * @param view The view that triggered the feedback (for accessibility).
     * @param type The type of haptic feedback (e.g., HapticFeedbackConstants.LONG_PRESS).
     */
    fun performHapticFeedback(view: View, type: Int) {
        view.performHapticFeedback(type)
    }

    /**
     * Shows a short Toast message.
     * @param context The context.
     * @param message The message to display.
     */
    fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Shows a Snackbar message.
     * @param view The view to attach the Snackbar to.
     * @param message The message to display.
     */
    fun showSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show()
    }

    /**
     * Copies a file from source to destination.
     * @param sourceFile The source file.
     * @param destFile The destination file.
     * @return True if copy is successful, false otherwise.
     */
    fun copyFile(sourceFile: File, destFile: File): Boolean {
        // Corrected: Safely access parentFile and create directories if needed
        destFile.parentFile?.let { parentDir ->
            if (!parentDir.exists()) {
                parentDir.mkdirs() // Create parent directories if they don't exist
            }
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

    /**
     * Scans a file to make it visible in the device's media gallery.
     * @param context The context.
     * @param filePath The path of the file to scan.
     */
    fun scanMediaFile(context: Context, filePath: String) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(filePath),
            null
        ) { path, uri ->
            // Log.d("Utils", "Scanned $path: $uri")
        }
    }

    /**
     * Deletes a file and scans the media store to remove it.
     * @param context The context.
     * @param file The file to delete.
     * @return True if deletion is successful, false otherwise.
     */
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

    /**
     * Formats a timestamp into a readable date string.
     * @param timestamp The timestamp in milliseconds.
     * @return Formatted date string (e.g., "July 27, 2025").
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
