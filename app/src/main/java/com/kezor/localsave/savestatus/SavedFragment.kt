@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SavedFragment : Fragment() {

    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!
    private lateinit var savedAdapter: StatusAdapter
    var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE
    private var actionMode: ActionMode? = null
    private val selectedItems = mutableSetOf<MediaItem>()
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
        setupRecyclerView()
        setupTabLayout()
        setupSwipeRefresh()
        loadMedia(currentMediaType)
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
        // Refresh data when fragment resumes in case location changed while inactive
        loadMedia(currentMediaType)
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
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

                if (selectedItems.isEmpty() && actionMode != null) {
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
                    else -> Constants.MEDIA_TYPE_IMAGE
                }
                loadMedia(currentMediaType)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
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

    fun loadMedia(mediaType: String) {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.textViewEmptyState.visibility = View.GONE
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val savedMediaList = mutableListOf<MediaItem>()
                val saveFolderPath = getSavedFolderPath()
                val saveDir = File(saveFolderPath)

                if (saveDir.exists() && saveDir.isDirectory) {
                    val files = saveDir.listFiles()
                    files?.filter { file ->
                        file.isFile && !file.name.startsWith(".") &&
                                ((mediaType == Constants.MEDIA_TYPE_IMAGE &&
                                        listOf("jpg", "jpeg", "png").contains(file.extension.lowercase())) ||
                                        (mediaType == Constants.MEDIA_TYPE_VIDEO &&
                                                listOf("mp4", "avi", "mkv").contains(file.extension.lowercase())))
                    }?.forEach { file ->
                        savedMediaList.add(
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

                savedMediaList.sortByDescending { it.lastModified }

                withContext(Dispatchers.Main) {
                    binding.textViewEmptyState.visibility =
                        if (savedMediaList.isEmpty()) View.VISIBLE else View.GONE
                    currentMediaList = savedMediaList
                    savedAdapter.submitList(savedMediaList)
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(requireContext(), "Error loading media: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val customUri = prefs.getString(Constants.KEY_SAVE_FOLDER_URI, null)

        return if (!customUri.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(customUri)
                val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
                docFile?.let {
                    // For SAF URI, we need to get the actual path
                    if (docFile.isDirectory) {
                        // Try to get the actual path for SAF URI
                        val path = getPathFromSAFUri(uri)
                        path ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
                    } else {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
                    }
                } ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
            } catch (e: Exception) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
            }
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
        }
    }

    @SuppressLint("Range")
    private fun getPathFromSAFUri(uri: Uri): String? {
        return try {
            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayName = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME))
                    val path = it.getString(it.getColumnIndex(MediaStore.MediaColumns.DATA))
                    path ?: displayName?.let { name ->
                        File(Environment.getExternalStorageDirectory(), name).absolutePath
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun startSelectionMode(initialItem: MediaItem, position: Int) {
        actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
        savedAdapter.isSelectionMode = true
        toggleSelection(initialItem, position)
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

    @SuppressLint("StringFormatInvalid")
    private fun updateActionModeTitle() {
        actionMode?.title = getString(R.string.saved_selection_mode_title, selectedItems.size)
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.selection_mode_menu, menu)
            binding.appBarLayout.visibility = View.GONE
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmationDialog()
                    return true
                }
                R.id.action_share -> {
                    shareSelectedItems()
                    mode?.finish()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            savedAdapter.isSelectionMode = false
            selectedItems.forEach { it.isSelected = false }
            selectedItems.clear()
            savedAdapter.notifyDataSetChanged()
            binding.appBarLayout.visibility = View.VISIBLE
        }
    }

    @SuppressLint("StringFormatInvalid")
    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle(getString(R.string.saved_delete_confirmation_title))
            .setMessage(getString(R.string.saved_delete_confirmation_message, selectedItems.size))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                deleteSelectedItems()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
                actionMode?.finish()
            }
            .setOnCancelListener {
                actionMode?.finish()
            }
            .show()
    }

    @SuppressLint("StringFormatInvalid")
    private fun deleteSelectedItems() {
        val itemsToDelete = selectedItems.toList()
        actionMode?.finish()

        lifecycleScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                deletedCount = itemsToDelete.count { Utils.deleteFile(requireContext(), it.file) }
            }
            if (deletedCount > 0) {
                Utils.showToast(requireContext(), getString(R.string.saved_items_deleted, deletedCount))
                loadMedia(currentMediaType)
            } else {
                Utils.showToast(requireContext(), getString(R.string.saved_delete_failed))
            }
        }
    }

    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) return

        val fileUris = selectedItems.map { mediaItem ->
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                mediaItem.file
            )
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Parcelable>(fileUris))
            type = if (selectedItems.all { it.type == Constants.MEDIA_TYPE_VIDEO }) "video/*"
            else if (selectedItems.all { it.type == Constants.MEDIA_TYPE_IMAGE }) "image/*"
            else "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Statuses"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        _binding = null
    }
}