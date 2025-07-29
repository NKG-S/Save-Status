@file:Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
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
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.tabs.TabLayout
import com.kezor.localsave.savestatus.databinding.FragmentSavedBinding
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
    private val selectedItems = mutableSetOf<MediaItem>() // The set for tracking selected items
    private var currentMediaList: List<MediaItem> = emptyList()
    private lateinit var sharedPreferences: SharedPreferences

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
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        loadMedia(currentMediaType)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        _binding = null
    }

    private fun setupToolbar() {
        // Toolbar setup
    }

    private fun setupRecyclerView() {
        savedAdapter = StatusAdapter(
            onItemClick = { mediaItem, position ->
                if (actionMode != null) {
                    toggleSelection(mediaItem, position)
                } else {
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
                    if (actionMode == null) {
                        startSelectionMode(mediaItem, position)
                    } else {
                        toggleSelection(mediaItem, position)
                    }
                }
                true // Consume the long click
            },
            onSelectionChanged = { mediaItem, isSelected ->
                // This callback is less direct now, as toggleSelection handles the logic
                // Kept for potential future complex interactions if needed.
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
                    else -> Constants.MEDIA_TYPE_IMAGE
                }
                loadMedia(currentMediaType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                // Not needed
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {
                loadMedia(currentMediaType)
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadMedia(currentMediaType)
        }
    }

    @SuppressLint("UseKtx")
    internal fun loadMedia(mediaType: String) {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.textViewEmptyState.visibility = View.GONE
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
            val savedMediaList = mutableListOf<MediaItem>()
            try {
                val customUriString = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)

                if (!customUriString.isNullOrEmpty()) {
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
                            Log.w("SavedFragment", "Custom SAF URI invalid. Fallback to default path.")
                            loadMediaFromDefaultPath(savedMediaList, mediaType)
                        }
                    } catch (e: Exception) {
                        Log.e("SavedFragment", "Error loading from custom SAF URI: ${e.message}", e)
                        loadMediaFromDefaultPath(savedMediaList, mediaType)
                    }
                } else {
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
                        uri = file.absolutePath,
                        type = if (listOf("mp4", "avi", "mkv").contains(file.extension.lowercase()))
                            Constants.MEDIA_TYPE_VIDEO
                        else Constants.MEDIA_TYPE_IMAGE,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    private fun addMediaItemFromDocumentFile(fileDoc: DocumentFile, mediaType: String): MediaItem? {
        if (fileDoc.isFile && fileDoc.name?.startsWith(".") == false) {
            val fileExtension = fileDoc.name?.substringAfterLast('.', "")?.lowercase()
            val isImage = listOf("jpg", "jpeg", "png").contains(fileExtension)
            val isVideo = listOf("mp4", "avi", "mkv").contains(fileExtension)

            if ((mediaType == Constants.MEDIA_TYPE_IMAGE && isImage) ||
                (mediaType == Constants.MEDIA_TYPE_VIDEO && isVideo)) {
                return MediaItem(
                    file = getFileFromDocumentFile(fileDoc),
                    uri = fileDoc.uri.toString(),
                    type = if (isVideo) Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                    lastModified = fileDoc.lastModified()
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
            Log.e("SavedFragment", "Error getting File from DocumentFile: ${e.message}", e)
            null
        }
    }

    @SuppressLint("Range", "UseKtx")
    private fun getRealPathFromURI(uri: Uri): String? {
        if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
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

    @SuppressLint("UseKtx")
    private fun getSavedFolderPath(): String {
        val customUri = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
        return if (!customUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUri)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                if (docFile?.isDirectory == true) {
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

    private fun getDefaultSavePath(): String {
        return try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
        } catch (e: Exception) {
            Log.e("SavedFragment", "Error getting default save path: ${e.message}", e)
            File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                Constants.APP_SAVE_SUBDIRECTORY_NAME).absolutePath
        }
    }

    private fun getRealPathFromSAFUri(uri: Uri): String? {
        val path = uri.path
        if (path != null && path.contains("primary:")) {
            val parts = path.split(":")
            if (parts.size > 1) {
                val actualPath = parts[1]
                return "${Environment.getExternalStorageDirectory()}/$actualPath"
            }
        }
        return null
    }

    private fun startSelectionMode(initialItem: MediaItem, position: Int) {
        actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
        savedAdapter.isSelectionMode = true
        toggleSelection(initialItem, position)
        binding.appBarLayout.visibility = View.GONE
        binding.tabLayout.visibility = View.GONE
    }

    private fun toggleSelection(mediaItem: MediaItem, position: Int) {
        mediaItem.isSelected = !mediaItem.isSelected // Toggle the isSelected state
        if (mediaItem.isSelected) {
            selectedItems.add(mediaItem)
        } else {
            selectedItems.remove(mediaItem)
        }
        savedAdapter.notifyItemChanged(position) // Crucial for visual update

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
            savedAdapter.isSelectionMode = false
            selectedItems.forEach { it.isSelected = false } // Reset isSelected flag for all
            selectedItems.clear() // Clear the tracking set
            savedAdapter.notifyDataSetChanged() // Refresh RecyclerView to clear selections visually
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
                        if (mediaItem.uri.startsWith("content://")) {
                            val customUriString = sharedPreferences.getString(Constants.KEY_SAVE_FOLDER_URI, null)
                            if (!customUriString.isNullOrEmpty()) {
                                try {
                                    val treeUri = Uri.parse(customUriString)
                                    val parentDoc = DocumentFile.fromTreeUri(requireContext(), treeUri)
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
                            if (!deleted) {
                                try {
                                    val docFile = DocumentFile.fromSingleUri(requireContext(), Uri.parse(mediaItem.uri))
                                    deleted = docFile?.delete() == true
                                } catch (e: Exception) {
                                    Log.e("SavedFragment", "Error with direct SAF deletion: ${e.message}", e)
                                }
                            }
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
                        } else {
                            if (mediaItem.file != null && mediaItem.file.exists()) {
                                deleted = mediaItem.file.delete()
                                if (!deleted) {
                                    deleted = Utils.deleteFile(requireContext(), mediaItem.file)
                                }
                            } else {
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
                    mediaItem.file!!
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
                containsImage && containsVideo -> "*/*"
                containsImage -> "image/*"
                containsVideo -> "video/*"
                else -> "*/*"
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Statuses"))
    }
}