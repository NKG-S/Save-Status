@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.kezor.localsave.savestatus

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Utils {

    fun calculateFileHash(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Fallback to file name and size if hash fails
            "${file.name}_${file.length()}"
        }
    }

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
        ) { _, _ ->
            // Scan completed
        }
    }

    /**
     * Deletes a file from storage. It first attempts a direct file deletion.
     * If that fails (commonly due to Scoped Storage on Android 10+), it tries to
     * delete the file using the MediaStore ContentResolver for better compatibility.
     *
     * @param context The application context.
     * @param file The file to be deleted.
     * @return True if the file was successfully deleted, false otherwise.
     */
    fun deleteFile(context: Context, file: File): Boolean {
        try {
            // First, try a direct deletion. This is fast and works for app-specific files.
            if (file.exists() && file.delete()) {
                scanMediaFile(context, file.absolutePath)
                return true
            }

            // If direct deletion fails, fall back to using ContentResolver.
            // This is the modern way and handles Scoped Storage permissions.
            val contentUri = getUriFromFile(context, file)
            if (contentUri != null) {
                val rowsDeleted = context.contentResolver.delete(contentUri, null, null)
                // If rows were deleted, the file is gone from the MediaStore and the disk.
                if (rowsDeleted > 0) {
                    return true
                }
            }

            // If we reach here, all deletion attempts have failed.
            return false

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Helper function to query the MediaStore and get a content URI for a given file path.
     */
    private fun getUriFromFile(context: Context, file: File): Uri? {
        val mediaStoreUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)
        val sortOrder = null // No specific order needed

        context.contentResolver.query(mediaStoreUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return ContentUris.withAppendedId(mediaStoreUri, id)
            }
        }
        return null
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}