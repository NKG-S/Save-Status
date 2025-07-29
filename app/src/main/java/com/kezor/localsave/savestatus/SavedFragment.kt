@file:Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

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
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.kezor.localsave.savestatus.databinding.FragmentSavedBinding
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.DataSource
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SavedFragment : Fragment() {

    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!
    private lateinit var savedAdapter: StatusAdapter
    internal var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE
    private var actionMode: ActionMode? = null
    private val selectedItems = mutableSetOf<MediaItem>()
    private var currentMediaList: List<MediaItem> = emptyList()
    private lateinit var sharedPreferences: SharedPreferences

    // Listener for changes in the shared preferences, specifically the save folder URI
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Constants.KEY_SAVE_FOLDER_URI) {
            loadMedia(currentMediaType)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        setupToolbar()
        setupRecyclerView()
        setupTabLayout()
        setupSwipeRefresh()
        loadMedia(currentMediaType)
    }

    override fun onResume() {
        super.onResume()
        // Register the preference listener to react to save folder changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        // Refresh data when fragment resumes in case location changed while inactive
        loadMedia(currentMediaType)
    }

    override fun onPause() {
        super.onPause()
        // Unregister the preference listener to prevent memory leaks
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Finish action mode if it's active to clean up UI
        actionMode?.finish()
        _binding = null
    }

    private fun setupToolbar() {
        // You can set up the toolbar title here if needed, or it can be done in XML
        // binding.topAppBar.title = getString(R.string.title_saved)
    }

    private fun setupRecyclerView() {
        savedAdapter = StatusAdapter(
            onItemClick = { mediaItem, position ->
                if (actionMode != null) {
                    toggleSelection(mediaItem, position)
                } else {
                    // Navigate to MediaViewerFragment when an item is clicked
                    val action = SavedFragmentDirections.actionNavigationSavedToMediaViewerFragment(
                        mediaItem,
                        currentMediaList.toTypedArray(),
                        position,
                        true
                    )
                    findNavController().navigate(action)
                }
            },
            onItemLongClick = { mediaItem, view ->
                val position = binding.recyclerViewSaved.getChildAdapterPosition(view)
                if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    // Start selection mode on long press
                    if (actionMode == null) {
                        startSelectionMode(mediaItem, position)
                    } else {
                        toggleSelection(mediaItem, position)
                    }
                }
            },
            onSelectionChanged = { mediaItem, isSelected ->
                if (isSelected) {
                    selectedItems.add(mediaItem)
                } else {
                    selectedItems.remove(mediaItem)
                }
                updateActionModeTitle()

                // Finish action mode if no items are selected
                if (selectedItems.isEmpty()) {
                    actionMode?.finish()
                }
            }
        )

        binding.recyclerViewSaved.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = savedAdapter
        }
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMediaType = when (tab?.position) {
                    0 -> Constants.MEDIA_TYPE_IMAGE
                    1 -> Constants.MEDIA_TYPE_VIDEO
                    else -> Constants.MEDIA_TYPE_IMAGE // Default to images
                }
                loadMedia(currentMediaType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Not needed for this implementation
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Reload media if the same tab is re-selected
                loadMedia(currentMediaType)
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMedia(currentMediaType)
        }
    }

    /**
     * Loads media items (images or videos) from the saved folder.
     * Handles both direct file access and Storage Access Framework (SAF) URIs.
     */
    @SuppressLint("UseKtx")
    internal fun loadMedia(mediaType: String) {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.textViewEmptyState.visibility = View.GONE
        actionMode?.finish() // Exit selection mode when refreshing content

        lifecycleScope.launch(Dispatchers.IO) {
            val savedMediaList = mutableListOf<MediaItem>()
            try {
                val customUriString = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)

                if (!customUriString.isNullOrEmpty()) {
                    // Attempt to load from custom SAF URI
                    try {
                        val uri = Uri.parse(customUriString)
                        val folderDoc = DocumentFile.fromTreeUri(requireContext(), uri)
                        if (folderDoc != null && folderDoc.exists() && folderDoc.isDirectory) {
                            folderDoc.listFiles().forEach { fileDoc ->
                                addMediaItemFromDocumentFile(fileDoc, mediaType)?.let {
                                    savedMediaList.add(it)
                                }
                            }
                        } else {
                            Log.w("SavedFragment", "Custom SAF URI is invalid or inaccessible. Falling back to default path.")
                            // Fallback to default if SAF URI is problematic
                            loadMediaFromDefaultPath(savedMediaList, mediaType)
                        }
                    } catch (e: Exception) {
                        Log.e("SavedFragment", "Error loading from custom SAF URI: ${e.message}", e)
                        // Fallback to default if there's an error with SAF URI
                        loadMediaFromDefaultPath(savedMediaList, mediaType)
                    }
                } else {
                    // Load from default path if no custom URI is set
                    loadMediaFromDefaultPath(savedMediaList, mediaType)
                }

                savedMediaList.sortByDescending { it.lastModified }

                withContext(Dispatchers.Main) {
                    binding.textViewEmptyState.visibility =
                        if (savedMediaList.isEmpty()) View.VISIBLE else View.GONE
                    currentMediaList = savedMediaList
                    savedAdapter.submitList(savedMediaList)
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                Log.e("SavedFragment", "Error loading media: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                    Utils.showToast(requireContext(), "Error loading media: ${e.message}")
                }
            }
        }
    }

    /**
     * Helper function to load media from the default save path.
     */
    private fun loadMediaFromDefaultPath(mediaList: MutableList<MediaItem>, mediaType: String) {
        val saveDir = File(getDefaultSavePath())
        if (saveDir.exists() && saveDir.isDirectory) {
            saveDir.listFiles()?.filter { file ->
                file.isFile && !file.name.startsWith(".") &&
                        ((mediaType == Constants.MEDIA_TYPE_IMAGE &&
                                listOf("jpg", "jpeg", "png").contains(file.extension.lowercase())) ||
                                (mediaType == Constants.MEDIA_TYPE_VIDEO &&
                                        listOf("mp4", "avi", "mkv").contains(file.extension.lowercase())))
            }?.forEach { file ->
                mediaList.add(
                    MediaItem(
                        file = file,
                        uri = file.absolutePath, // For direct files, uri is the absolute path
                        type = if (listOf("mp4", "avi", "mkv").contains(file.extension.lowercase()))
                            Constants.MEDIA_TYPE_VIDEO
                        else Constants.MEDIA_TYPE_IMAGE,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    /**
     * Creates a MediaItem from a DocumentFile, filtering by media type.
     */
    private fun addMediaItemFromDocumentFile(fileDoc: DocumentFile, mediaType: String): MediaItem? {
        if (fileDoc.isFile && fileDoc.name?.startsWith(".") == false) {
            val fileExtension = fileDoc.name?.substringAfterLast('.', "")?.lowercase()
            val isImage = listOf("jpg", "jpeg", "png").contains(fileExtension)
            val isVideo = listOf("mp4", "avi", "mkv").contains(fileExtension)

            if ((mediaType == Constants.MEDIA_TYPE_IMAGE && isImage) ||
                (mediaType == Constants.MEDIA_TYPE_VIDEO && isVideo)) {
                return MediaItem(
                    file = getFileFromDocumentFile(fileDoc) ?: return null, // Attempt to get a File object
                    uri = fileDoc.uri.toString(),
                    type = if (isVideo) Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                    lastModified = fileDoc.lastModified()
                )
            }
        }
        return null
    }

    /**
     * Attempts to convert a DocumentFile to a java.io.File.
     * This is inherently tricky with SAF and might return null if direct file path is not accessible.
     */
    private fun getFileFromDocumentFile(documentFile: DocumentFile): File? {
        // For SAF URIs, getting a direct File path is often not possible directly from the URI.
        // We'll try to resolve it using MediaStore for common cases or fall back to an indirect approach.
        // For actual file operations with SAF, you should use documentFile.uri and ContentResolver.
        // This function is primarily for situations where a 'File' object is strictly required,
        // but it's important to understand its limitations with SAF.
        return try {
            val uri = documentFile.uri
            val scheme = uri.scheme
            if (scheme == "file") {
                return File(uri.path!!)
            } else if (scheme == "content") {
                // Attempt to get the real path from content URI, often works for local files managed by MediaStore
                val filePath = getRealPathFromURI(uri)
                if (filePath != null) {
                    return File(filePath)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("SavedFragment", "Error getting File from DocumentFile: ${e.message}", e)
            null
        }
    }

    @SuppressLint("Range", "UseKtx")
    private fun getRealPathFromURI(uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
            // Handle primary external storage provider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return "${Environment.getExternalStorageDirectory()}/${split[1]}"
                }
            }
            // Handle downloads provider
            else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = Uri.withAppendedPath(
                    Uri.parse("content://downloads/public_downloads"), id
                )
                return getDataColumn(contentUri, null, null)
            }
            // Handle MediaStore documents
            else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":").toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(contentUri, selection, selectionArgs)
            }
        }
        // MediaStore (and general)
        else if ("content".equals(uri.scheme, ignoreCase = true)) {
            // Return the remote content if it's not local
            return if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(uri, null, null)
        }
        // File
        else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        val column = "_data"
        val projection = arrayOf(column)
        try {
            requireContext().contentResolver.query(
                uri!!, projection, selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("SavedFragment", "Error getting data column: ${e.message}", e)
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

    /**
     * Determines and returns the save folder path.
     * Prioritizes custom URI set by the user; otherwise, returns the default path.
     */
    @SuppressLint("UseKtx")
    private fun getSavedFolderPath(): String {
        val customUri = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)

        return if (!customUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUri)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                if (docFile?.isDirectory == true) {
                    // This path is for display/internal logic. For actual file operations with SAF,
                    // you would continue to use the URI. This attempts to get a 'real' path.
                    getRealPathFromSAFUri(uri) ?: getDefaultSavePath()
                } else {
                    Log.w("SavedFragment", "Invalid custom SAF URI. Falling back to default path.")
                    getDefaultSavePath()
                }
            } catch (e: Exception) {
                Log.e("SavedFragment", "Error parsing custom URI: ${e.message}", e)
                getDefaultSavePath()
            }
        } else {
            getDefaultSavePath()
        }
    }

    /**
     * Fallback function for the default save path.
     */
    private fun getDefaultSavePath(): String {
        return try {
            // Standard Downloads directory within external public storage
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
        } catch (e: Exception) {
            Log.e("SavedFragment", "Error getting default save path: ${e.message}", e)
            // Fallback to app-specific external files directory if public directory is inaccessible
            File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                Constants.APP_SAVE_SUBDIRECTORY_NAME).absolutePath
        }
    }

    /**
     * Attempts to get a readable file path from a SAF tree URI.
     * This is complex and might not always return a direct file path, especially for non-local content.
     * It's more of a best-effort for display or specific legacy file operations.
     */
    private fun getRealPathFromSAFUri(uri: Uri): String? {
        // For tree URIs, direct path resolution is not straightforward or always possible.
        // SAF is designed to work with content URIs and ContentResolver, not direct File paths.
        // However, if the URI points to a known public directory, we might construct a path.
        // This is a common pain point with SAF.
        // A robust solution for showing files from SAF generally involves displaying them
        // using their URIs and not trying to convert to File paths for access.

        // Example (simplified and limited): If your app saves to a specific sub-folder
        // within Downloads picked by SAF, you might be able to derive it.
        // This is highly dependent on how the URI is structured by the document provider.
        val path = uri.path
        if (path != null && path.contains("primary:")) {
            val parts = path.split(":")
            if (parts.size > 1) {
                val actualPath = parts[1]
                return "${Environment.getExternalStorageDirectory()}/$actualPath"
            }
        }
        // Add more specific handling for other known document providers if needed
        return null
    }

    private fun startSelectionMode(initialItem: MediaItem, position: Int) {
        actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
        savedAdapter.isSelectionMode = true
        toggleSelection(initialItem, position)
        // Hide the regular app bar when action mode starts
        binding.appBarLayout.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
    }

    private fun toggleSelection(mediaItem: MediaItem, position: Int) {
        mediaItem.isSelected = !mediaItem.isSelected
        if (mediaItem.isSelected) {
            selectedItems.add(mediaItem)
        } else {
            selectedItems.remove(mediaItem)
        }
        savedAdapter.notifyItemChanged(position)
        updateActionModeTitle()

        if (selectedItems.isEmpty()) {
            actionMode?.finish()
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
            mode?.menuInflater?.inflate(R.menu.selection_mode_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // You can hide/show menu items based on selection here
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
                    mode?.finish() // Finish action mode after sharing
                    true
                }
                else -> false
            }
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            savedAdapter.isSelectionMode = false
            selectedItems.forEach { it.isSelected = false } // Deselect all items
            selectedItems.clear()
            savedAdapter.notifyDataSetChanged() // Refresh RecyclerView to clear selections
            // Show the regular app bar when action mode ends
            binding.appBarLayout.visibility = View.VISIBLE
            binding.tabLayout.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmationDialog() {
        val count = selectedItems.size
        val message = if (count == 1) {
            "Are you sure you want to delete this item?"
        } else {
            "Are you sure you want to delete these $count items?"
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle("Delete Items")
            .setMessage(message)
            .setPositiveButton("Delete") { dialog, _ ->
                deleteSelectedItems()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                actionMode?.finish() // End action mode if cancelled
            }
            .setOnCancelListener {
                actionMode?.finish() // End action mode if dialog is dismissed by back button
            }
            .show()
    }

    @SuppressLint("ResourceType", "UseKtx")
    private fun deleteSelectedItems() {
        val itemsToDelete = selectedItems.toList()
        actionMode?.finish() // Exit action mode immediately

        lifecycleScope.launch {
            var deletedCount = 0
            val failedItems = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                itemsToDelete.forEach { mediaItem ->
                    try {
                        var deleted = false

                        // Handle SAF URIs (content://)
                        if (mediaItem.uri.startsWith("content://")) {
                            // Try to get the parent folder URI for proper SAF deletion
                            val customUriString = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
                            if (!customUriString.isNullOrEmpty()) {
                                try {
                                    val treeUri = Uri.parse(customUriString)
                                    val parentDoc = DocumentFile.fromTreeUri(requireContext(), treeUri)

                                    // Find the specific file in the parent directory
                                    parentDoc?.listFiles()?.forEach { childDoc ->
                                        if (childDoc.uri.toString() == mediaItem.uri) {
                                            deleted = childDoc.delete()
                                            return@forEach
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SavedFragment", "Error with tree URI deletion: ${e.message}", e)
                                }
                            }

                            // Fallback: try direct DocumentFile deletion
                            if (!deleted) {
                                try {
                                    val docFile = DocumentFile.fromSingleUri(requireContext(), Uri.parse(mediaItem.uri))
                                    deleted = docFile?.delete() == true
                                } catch (e: Exception) {
                                    Log.e("SavedFragment", "Error with direct SAF deletion: ${e.message}", e)
                                }
                            }

                            // Last resort: try ContentResolver deletion
                            if (!deleted) {
                                try {
                                    val deletedRows = requireContext().contentResolver.delete(
                                        Uri.parse(mediaItem.uri),
                                        null,
                                        null
                                    )
                                    deleted = deletedRows > 0
                                } catch (e: Exception) {
                                    Log.e("SavedFragment", "Error with ContentResolver deletion: ${e.message}", e)
                                }
                            }
                        }
                        // Handle direct file paths
                        else {
                            if (mediaItem.file != null && mediaItem.file.exists()) {
                                deleted = mediaItem.file.delete()

                                // Fallback using Utils.deleteFile if direct deletion fails
                                if (!deleted) {
                                    deleted = Utils.deleteFile(requireContext(), mediaItem.file)
                                }
                            } else {
                                // Try to create File object from URI string if file is null
                                try {
                                    val file = File(mediaItem.uri)
                                    if (file.exists()) {
                                        deleted = file.delete()
                                        if (!deleted) {
                                            deleted = Utils.deleteFile(requireContext(), file)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SavedFragment", "Error creating file from URI: ${e.message}", e)
                                }
                            }
                        }

                        if (deleted) {
                            deletedCount++
                            Log.d("SavedFragment", "Successfully deleted: ${mediaItem.uri}")
                        } else {
                            failedItems.add(mediaItem.uri)
                            Log.w("SavedFragment", "Failed to delete: ${mediaItem.uri}")
                        }

                    } catch (e: Exception) {
                        Log.e("SavedFragment", "Exception deleting item ${mediaItem.uri}: ${e.message}", e)
                        failedItems.add(mediaItem.uri)
                    }
                }
            }

            // Show appropriate message based on results
            withContext(Dispatchers.Main) {
                when {
                    deletedCount > 0 && failedItems.isEmpty() -> {
                        val message = if (deletedCount == 1) {
                            "1 item deleted successfully"
                        } else {
                            "$deletedCount items deleted successfully"
                        }
                        Utils.showToast(requireContext(), message)
                    }
                    deletedCount > 0 && failedItems.isNotEmpty() -> {
                        Utils.showToast(
                            requireContext(),
                            "Deleted $deletedCount items. ${failedItems.size} items failed to delete."
                        )
                    }
                    else -> {
                        Utils.showToast(requireContext(), "Failed to delete items")
                    }
                }

                // Always reload media after deletion attempt to refresh the list
                loadMedia(currentMediaType)
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
                FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    mediaItem.file
                )
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
                containsImage && containsVideo -> "*/*" // Both images and videos
                containsImage -> "image/*"
                containsVideo -> "video/*"
                else -> "*/*" // Fallback
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Statuses"))
    }
}