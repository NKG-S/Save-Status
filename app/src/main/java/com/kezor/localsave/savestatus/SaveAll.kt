@file:Suppress("UNCHECKED_CAST", "DEPRECATION")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import com.kezor.localsave.savestatus.Constants.KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SaveAll : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: StatusAdapter
    private var allMedia: List<MediaItem> = listOf()
    private var filteredMedia: List<MediaItem> = listOf()
    private var actionMode: ActionMode? = null
    private val selectedItems = mutableSetOf<MediaItem>()
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val TAG = "SaveAll"
        // Auto Save All directory path
        private const val AUTO_SAVE_ALL_DIRECTORY = ".Auto_Save_All_Status" // This constant is still here but its value is now redundant if KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH is used directly everywhere.
    }

    // prefListener now listens to KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH instead of KEY_SAVE_FOLDER_URI
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH) { // Changed this line
            loadMedia(currentMediaType())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_save_all)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        initViews()
        setupRecyclerView()
        setupSwipeRefresh()
        setupTabLayout()
        loadMedia(currentMediaType())
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        loadMedia(currentMediaType())
    }


    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_view_saved)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        tabLayout = findViewById(R.id.tab_layout)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = StatusAdapter(
            onItemClick = { mediaItem, position ->
                if (actionMode != null) {
                    toggleSelection(mediaItem, position)
                } else {
                    // Handle item click - you can add navigation to media viewer here
                    openMediaViewer(mediaItem, position)
                }
            },
            onItemLongClick = { mediaItem, view ->
                val position = recyclerView.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION) {
                    if (actionMode == null) {
                        startSelectionMode(mediaItem, position)
                    } else {
                        toggleSelection(mediaItem, position)
                    }
                }
                true
            },
            onSelectionChanged = { _, _ ->
                // Kept for potential future use
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadMedia(currentMediaType())
        }
    }

    private fun setupTabLayout() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                actionMode?.finish() // Clear selection when switching tabs
                loadMedia(currentMediaType())
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {
                loadMedia(currentMediaType())
            }
        })
    }

    private fun currentMediaType(): String {
        return when (tabLayout.selectedTabPosition) {
            0 -> Constants.MEDIA_TYPE_IMAGE
            1 -> Constants.MEDIA_TYPE_VIDEO
            else -> Constants.MEDIA_TYPE_IMAGE
        }
    }

    @SuppressLint("UseKtx")
    private fun loadMedia(type: String) {
        swipeRefreshLayout.isRefreshing = true
        findViewById<View>(R.id.text_view_empty_state).visibility = View.GONE
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            try {
                // Now, only load from the single designated folder
                loadMediaFromAutoSaveAll(mediaList, type) // This is now the ONLY loading path

                // Sort by last modified (newest first)
                mediaList.sortByDescending { it.lastModified }

                withContext(Dispatchers.Main) {
                    allMedia = mediaList
                    filteredMedia = mediaList
                    adapter.submitList(filteredMedia)
                    swipeRefreshLayout.isRefreshing = false

                    findViewById<View>(R.id.text_view_empty_state).visibility =
                        if (filteredMedia.isEmpty()) View.VISIBLE else View.GONE

                    if (filteredMedia.isEmpty()) {
                        Toast.makeText(
                            this@SaveAll,
                            "No saved media found in the designated folder. Make sure files are present.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    findViewById<View>(R.id.text_view_empty_state).visibility = View.VISIBLE
                    Toast.makeText(
                        this@SaveAll,
                        "Error loading media: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadMediaFromAutoSaveAll(mediaList: MutableList<MediaItem>, mediaType: String) {
        // Now, this function is the central point for all media loading.
        // It always tries to load from KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH.
        val possiblePaths = listOf(
            File(Environment.getExternalStorageDirectory(), KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH)
        )

        var foundDirectory = false

        for (autoSaveDir in possiblePaths) {
            Log.d(TAG, "Checking designated media directory: ${autoSaveDir.absolutePath}")

            if (autoSaveDir.exists() && autoSaveDir.isDirectory) {
                Log.d(TAG, "Found designated media directory: ${autoSaveDir.absolutePath}")
                foundDirectory = true

                try {
                    autoSaveDir.listFiles()?.let { files ->
                        Log.d(TAG, "Found ${files.size} files in designated media directory")

                        files.filter { file ->
                            file.isFile && !file.name.startsWith(".") && isValidMediaFile(file, mediaType)
                        }.forEach { file ->
                            Log.d(TAG, "Adding media file: ${file.name}")
                            mediaList.add(createMediaItem(file))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading files from ${autoSaveDir.absolutePath}: ${e.message}", e)
                }

                // If we found files in this directory, break out of the loop
                if (mediaList.isNotEmpty()) {
                    break // This break is still useful if 'possiblePaths' were to expand
                }
            }
        }

        if (!foundDirectory) {
            Log.w(TAG, "Designated media directory not found in the expected location")
        }
    }

    // Removed loadMediaFromSavedDirectory and loadMediaFromDefaultPath
    // as their functionality is now consolidated into loadMediaFromAutoSaveAll.

    private fun isValidMediaFile(file: File, type: String): Boolean {
        val extension = file.extension.lowercase()
        return when (type) {
            Constants.MEDIA_TYPE_IMAGE -> {
                listOf("jpg", "jpeg", "png", "gif", "webp", "bmp").contains(extension)
            }
            Constants.MEDIA_TYPE_VIDEO -> {
                listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp").contains(extension)
            }
            else -> false
        }
    }

    private fun createMediaItem(file: File): MediaItem {
        val extension = file.extension.lowercase()
        val type = if (listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp").contains(extension)) {
            Constants.MEDIA_TYPE_VIDEO
        } else {
            Constants.MEDIA_TYPE_IMAGE
        }

        return MediaItem(
            file = file,
            uri = file.absolutePath,
            type = type,
            lastModified = file.lastModified(),
            isSelected = false
        )
    }

    private fun addMediaItemFromDocumentFile(fileDoc: DocumentFile, mediaType: String): MediaItem? {
        // This function might still be called if deletion logic attempts SAF,
        // but for loading, it's no longer used. Keeping it for now.
        if (fileDoc.isFile && fileDoc.name?.startsWith(".") == false) {
            val fileExtension = fileDoc.name?.substringAfterLast('.', "")?.lowercase()
            val isImage = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp").contains(fileExtension)
            val isVideo = listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "3gp").contains(fileExtension)

            if ((mediaType == Constants.MEDIA_TYPE_IMAGE && isImage) ||
                (mediaType == Constants.MEDIA_TYPE_VIDEO && isVideo)) {
                return MediaItem(
                    file = getFileFromDocumentFile(fileDoc),
                    uri = fileDoc.uri.toString(),
                    type = if (isVideo) Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                    lastModified = fileDoc.lastModified(),
                    isSelected = false
                )
            }
        }
        return null
    }

    private fun getFileFromDocumentFile(documentFile: DocumentFile): File? {
        return try {
            val uri = documentFile.uri
            val scheme = uri.scheme
            if (scheme == "file") {
                return File(uri.path!!)
            } else if (scheme == "content") {
                val filePath = getRealPathFromURI(uri)
                if (filePath != null) {
                    return File(filePath)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting File from DocumentFile: ${e.message}", e)
            null
        }
    }

    @SuppressLint("Range", "UseKtx")
    private fun getRealPathFromURI(uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(this, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = Uri.withAppendedPath(
                    Uri.parse("content://downloads/public_downloads"), id
                )
                return getDataColumn(contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        val column = "_data"
        val projection = arrayOf(column)
        try {
            contentResolver.query(
                uri!!, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting data column: ${e.message}", e)
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    // This function will now always return the KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH as the "default"
    private fun getDefaultSavePath(): String {
        return try {
            File(Environment.getExternalStorageDirectory(), KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH).absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error getting default save path: ${e.message}", e)
            File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH).absolutePath // Fallback to app-specific internal storage
        }
    }

    private fun openMediaViewer(mediaItem: MediaItem, position: Int) {
        // Add your media viewer navigation logic here
        // For example:
        /*
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putExtra("media_item", mediaItem)
            putExtra("media_list", filteredMedia.toTypedArray())
            putExtra("position", position)
        }
        startActivity(intent)
        */
        Toast.makeText(this, "Opening ${mediaItem.file?.name}", Toast.LENGTH_SHORT).show()
    }

    private fun startSelectionMode(initialItem: MediaItem, position: Int) {
        actionMode = startSupportActionMode(actionModeCallback)
        adapter.isSelectionMode = true
        toggleSelection(initialItem, position)
    }

    private fun toggleSelection(mediaItem: MediaItem, position: Int) {
        val actualItemIndex = filteredMedia.indexOfFirst { it.uri == mediaItem.uri }
        if (actualItemIndex != -1) {
            val actualItem = filteredMedia[actualItemIndex]
            val newSelectionState = !actualItem.isSelected

            // Create updated item
            val updatedItem = actualItem.copy(isSelected = newSelectionState)

            // Update the selected items set
            if (newSelectionState) {
                selectedItems.add(updatedItem)
            } else {
                selectedItems.removeIf { it.uri == updatedItem.uri }
            }

            // Update the filtered media list
            val updatedList = filteredMedia.toMutableList()
            updatedList[actualItemIndex] = updatedItem
            filteredMedia = updatedList // Corrected "filteredMedia = updatedList" to "filteredList = updatedList"

            adapter.submitList(updatedList)
            adapter.notifyItemChanged(position)

            updateActionModeTitle()
            if (selectedItems.isEmpty()) {
                actionMode?.finish()
            }
        }
    }

    private fun updateActionModeTitle() {
        val count = selectedItems.size
        actionMode?.title = when (count) {
            1 -> "1 item selected"
            else -> "$count items selected"
        }
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            menuInflater.inflate(R.menu.selection_mode_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmationDialog()
                    true
                }
                R.id.action_share -> {
                    shareSelectedItems()
                    mode?.finish()
                    true
                }
                else -> false
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            adapter.isSelectionMode = false

            // Clear selection state from all items
            val clearedList = filteredMedia.map { item ->
                item.copy(isSelected = false)
            }

            // Clear the selected items set
            selectedItems.clear()

            // Update the filtered list and adapter
            filteredMedia = clearedList
            adapter.submitList(clearedList) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        val count = selectedItems.size
        val message = if (count == 1) {
            "Are you sure you want to delete this item?"
        } else {
            "Are you sure you want to delete these $count items?"
        }

        AlertDialog.Builder(this)
            .setTitle("Delete Items")
            .setMessage(message)
            .setPositiveButton("Delete") { dialog, _ ->
                deleteSelectedItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                actionMode?.finish()
            }
            .setOnCancelListener {
                actionMode?.finish()
            }
            .show()
    }

    @SuppressLint("ResourceType", "UseKtx")
    private fun deleteSelectedItems() {
        val itemsToDelete = selectedItems.toList()
        actionMode?.finish()

        lifecycleScope.launch {
            var deletedCount = 0
            val failedItems = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                itemsToDelete.forEach { mediaItem ->
                    try {
                        var deleted = false
                        // For deletion, we still need to handle content:// URIs and SAF
                        // as the files might have been created/saved via SAF originally.
                        // However, the loading logic now ensures they should all be in
                        // KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH, which would imply file URIs.
                        // But keeping this SAF deletion path is safer for backward compatibility or edge cases.
                        if (mediaItem.uri.startsWith("content://")) {
                            // This part of deletion still needs to handle potential SAF URIs,
                            // regardless of how they were loaded.
                            val folderUriString = sharedPreferences.getString(KEY_AUTO_SAVE_ALL_SAVE_FOLDER_PATH, null) // Changed this line
                            if (!folderUriString.isNullOrEmpty()) {
                                try {
                                    val treeUri = Uri.parse(folderUriString)
                                    val parentDoc = DocumentFile.fromTreeUri(this@SaveAll, treeUri)
                                    parentDoc?.listFiles()?.forEach { childDoc ->
                                        if (childDoc.uri.toString() == mediaItem.uri) {
                                            deleted = childDoc.delete()
                                            return@forEach
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error with tree URI deletion: ${e.message}", e)
                                }
                            }
                            if (!deleted) {
                                try {
                                    val docFile = DocumentFile.fromSingleUri(this@SaveAll, Uri.parse(mediaItem.uri))
                                    deleted = docFile?.delete() == true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error with direct SAF deletion: ${e.message}", e)
                                }
                            }
                            if (!deleted) {
                                try {
                                    val deletedRows = contentResolver.delete(
                                        Uri.parse(mediaItem.uri), null, null
                                    )
                                    deleted = deletedRows > 0
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error with ContentResolver deletion: ${e.message}", e)
                                }
                            }
                        } else {
                            if (mediaItem.file != null && mediaItem.file.exists()) {
                                deleted = mediaItem.file.delete()
                                if (!deleted) {
                                    deleted = Utils.deleteFile(this@SaveAll, mediaItem.file)
                                }
                            } else {
                                try {
                                    val file = File(mediaItem.uri)
                                    if (file.exists()) {
                                        deleted = file.delete()
                                        if (!deleted) {
                                            deleted = Utils.deleteFile(this@SaveAll, file)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error creating file from URI: ${e.message}", e)
                                }
                            }
                        }

                        if (deleted) {
                            deletedCount++
                            Log.d(TAG, "Successfully deleted: ${mediaItem.uri}")
                        } else {
                            failedItems.add(mediaItem.uri)
                            Log.w(TAG, "Failed to delete: ${mediaItem.uri}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception deleting item ${mediaItem.uri}: ${e.message}", e)
                        failedItems.add(mediaItem.uri)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                when {
                    deletedCount > 0 && failedItems.isEmpty() -> {
                        val message = if (deletedCount == 1) {
                            "1 item deleted successfully"
                        } else {
                            "$deletedCount items deleted successfully"
                        }
                        Toast.makeText(this@SaveAll, message, Toast.LENGTH_SHORT).show()
                    }
                    deletedCount > 0 && failedItems.isNotEmpty() -> {
                        Toast.makeText(
                            this@SaveAll,
                            "Deleted $deletedCount items. ${failedItems.size} items failed to delete.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        Toast.makeText(this@SaveAll, "Failed to delete items", Toast.LENGTH_SHORT).show()
                    }
                }
                loadMedia(currentMediaType())
            }
        }
    }

    @SuppressLint("UseKtx")
    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) return

        val fileUris = ArrayList<Parcelable>()
        var containsImage = false
        var containsVideo = false

        selectedItems.forEach { mediaItem ->
            val uri = if (mediaItem.uri.startsWith("content://")) {
                Uri.parse(mediaItem.uri)
            } else {
                try {
                    FileProvider.getUriForFile(
                        this,
                        "$packageName.fileprovider",
                        mediaItem.file!!
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating file URI: ${e.message}", e)
                    Uri.fromFile(mediaItem.file)
                }
            }

            fileUris.add(uri)

            if (mediaItem.type == Constants.MEDIA_TYPE_IMAGE) {
                containsImage = true
            } else if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) {
                containsVideo = true
            }
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, fileUris)
            type = when {
                containsImage && containsVideo -> "*/*"
                containsImage -> "image/*"
                containsVideo -> "video/*"
                else -> "*/*"
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, "Share Media"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing files: ${e.message}", e)
            Toast.makeText(this, "Error sharing files", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        if (actionMode != null) {
            actionMode?.finish()
        } else {
            super.onBackPressed()
        }
    }
}