@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.FileProvider
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
    private var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE
    private var actionMode: ActionMode? = null
    private val selectedItems = mutableSetOf<MediaItem>()
    private var currentMediaList: List<MediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupTabLayout()
        setupSwipeRefresh()
        loadMedia(currentMediaType)
    }

    private fun setupRecyclerView() {
        savedAdapter = StatusAdapter(
            onItemClick = { mediaItem, position ->
                if (actionMode != null) {
                    // When an item is clicked in selection mode, we toggle its state.
                    // The adapter will then call onSelectionChanged.
                    toggleSelection(mediaItem, position)
                } else {
                    // Otherwise, we navigate to the viewer.
                    val action = SavedFragmentDirections.actionNavigationSavedToMediaViewerFragment(
                        mediaItem,
                        currentMediaList.toTypedArray(),
                        position,
                        true // isFromSavedSection
                    )
                    findNavController().navigate(action)
                }
            },
            onItemLongClick = { mediaItem, view ->
                // Get the adapter position from the long-pressed view.
                val position = binding.recyclerViewSaved.getChildAdapterPosition(view)

                if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                    if (actionMode == null) {
                        startSelectionMode(mediaItem, position)
                    }
                }
            },
            // CORRECTED: The required onSelectionChanged parameter is now included.
            onSelectionChanged = { mediaItem, isSelected ->
                // This callback syncs the fragment's list of selected items
                // with the adapter's state.
                if (isSelected) {
                    selectedItems.add(mediaItem)
                } else {
                    selectedItems.remove(mediaItem)
                }
                updateActionModeTitle()

                // If no items remain selected, exit action mode.
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

    private fun loadMedia(mediaType: String) {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.textViewEmptyState.visibility = View.GONE
        // Ensure any existing action mode is finished before loading new data
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
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
        }
    }

    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val customPath = prefs.getString(Constants.KEY_SAVE_FOLDER_PATH, null)

        return if (!customPath.isNullOrEmpty()) {
            customPath
        } else {
            // Default to the app's primary external media directory
            requireContext().externalMediaDirs.firstOrNull()?.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME
                ?: (requireContext().getExternalFilesDir(null)?.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
                ?: (requireContext().filesDir.absolutePath + File.separator + Constants.APP_SAVE_SUBDIRECTORY_NAME)
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
            // Reset the isSelected flag for all items to false
            selectedItems.forEach { it.isSelected = false }
            selectedItems.clear()
            // Notify the adapter to remove the selection visuals from all items
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

    private fun deleteSelectedItems() {
        val itemsToDelete = selectedItems.toList() // Create a copy to avoid modification issues
        actionMode?.finish() // Finish action mode immediately

        lifecycleScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                deletedCount = itemsToDelete.count { Utils.deleteFile(requireContext(), it.file) }
            }
            // Switch back to the Main thread for UI updates
            if (deletedCount > 0) {
                Utils.showToast(requireContext(), getString(R.string.saved_items_deleted, deletedCount))
                loadMedia(currentMediaType) // Reload media to reflect deletions
            } else {
                Utils.showToast(requireContext(), getString(R.string.saved_delete_failed))
            }
        }
    }

    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) {
            return
        }

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