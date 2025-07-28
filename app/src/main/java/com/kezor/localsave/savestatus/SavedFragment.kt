package com.kezor.localsave.savestatus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
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

    // This will hold the current list of media items displayed in the RecyclerView
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
            onItemClick = { mediaItem, position -> // This 'position' will now correctly be an Int
                if (actionMode != null) {
                    // If in selection mode, toggle item selection
                    toggleSelection(mediaItem)
                } else {
                    // Not in selection mode, navigate to MediaViewerFragment
                    val action = SavedFragmentDirections.actionNavigationSavedToMediaViewerFragment(
                        mediaItem,
                        currentMediaList.toTypedArray(),
                        position,
                        true
                    )
                    findNavController().navigate(action)
                }
            },
            onItemLongClick = { mediaItem, _ ->
                // Enter selection mode
                startSelectionMode(mediaItem)
            },
            onSelectionChanged = { mediaItem, isSelected ->
                if (isSelected) {
                    selectedItems.add(mediaItem)
                } else {
                    selectedItems.remove(mediaItem)
                }
                updateActionModeTitle()
                // If no items are selected, exit selection mode
                if (selectedItems.isEmpty() && actionMode != null) {
                    actionMode?.finish()
                }
            }
        )
        binding.recyclerViewSaved.apply {
            layoutManager = GridLayoutManager(context, 2) // 2 columns for grid layout
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
                // Reload data if the same tab is reselected
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
        savedAdapter.isSelectionMode = false // Exit selection mode on refresh
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
            val savedMediaList = mutableListOf<MediaItem>()
            val saveFolderPath = getSavedFolderPath() // Get the user-defined or default save folder

            val saveDir = File(saveFolderPath)
            if (saveDir.exists() && saveDir.isDirectory) {
                val files = saveDir.listFiles()
                files?.filter { it.isFile && !it.name.startsWith(".") &&
                        ((mediaType == Constants.MEDIA_TYPE_IMAGE && (it.extension == "jpg" || it.extension == "jpeg" || it.extension == "png")) ||
                                (mediaType == Constants.MEDIA_TYPE_VIDEO && (it.extension == "mp4" || it.extension == "avi" || it.extension == "mkv")))
                }?.map { file ->
                    savedMediaList.add(
                        MediaItem(
                            file = file,
                            uri = file.absolutePath,
                            type = if (file.extension == "mp4" || file.extension == "avi" || file.extension == "mkv") Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                            lastModified = file.lastModified()
                        )
                    )
                }
            }

            // Sort by newest first
            savedMediaList.sortByDescending { it.lastModified }

            withContext(Dispatchers.Main) {
                if (savedMediaList.isEmpty()) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                } else {
                    binding.textViewEmptyState.visibility = View.GONE
                }
                currentMediaList = savedMediaList // Update the list held by the fragment
                savedAdapter.submitList(savedMediaList)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getSavedFolderPath(): String {
        return Environment.getExternalStorageDirectory().absolutePath + Constants.KEY_SAVE_FOLDER_PATH
    }

    private fun startSelectionMode(initialItem: MediaItem) {
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
            savedAdapter.isSelectionMode = true
            selectedItems.clear()
            toggleSelection(initialItem) // Select the item that was long-pressed
        }
    }

    private fun toggleSelection(mediaItem: MediaItem) {
        mediaItem.isSelected = !mediaItem.isSelected
        if (mediaItem.isSelected) {
            selectedItems.add(mediaItem)
        } else {
            selectedItems.remove(mediaItem)
        }
        savedAdapter.notifyItemChanged(savedAdapter.currentList.indexOf(mediaItem))
        updateActionModeTitle()
        if (selectedItems.isEmpty() && actionMode != null) {
            actionMode?.finish()
        }
    }

    private fun updateActionModeTitle() {
        actionMode?.title = getString(R.string.saved_selection_mode_title, selectedItems.size)
    }

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.selection_mode_menu, menu)
            mode?.title = getString(R.string.saved_selection_mode_title, 0)
            binding.appBarLayout.visibility = View.GONE // Hide normal app bar
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false // Nothing to do here
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmationDialog()
                    mode?.finish() // Finish action mode after action
                    true
                }
                R.id.action_share -> {
                    // Implement share functionality for selected items
                    shareSelectedItems()
                    mode?.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            savedAdapter.isSelectionMode = false
            selectedItems.clear()
            binding.appBarLayout.visibility = View.VISIBLE // Show normal app bar
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog) // Use app theme for dialog
            .setTitle(getString(R.string.saved_delete_confirmation_title))
            .setMessage(getString(R.string.saved_delete_confirmation_message))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                dialog.dismiss()
                deleteSelectedItems()
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteSelectedItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            val deletedCount = selectedItems.count { Utils.deleteFile(requireContext(), it.file) }

            withContext(Dispatchers.Main) {
                if (deletedCount > 0) {
                    Utils.showToast(requireContext(), getString(R.string.saved_items_deleted, deletedCount))
                    loadMedia(currentMediaType) // Reload media after deletion
                } else {
                    Utils.showToast(requireContext(), getString(R.string.saved_delete_failed))
                }
                actionMode?.finish() // Exit selection mode
            }
        }
    }

    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) {
            Utils.showToast(requireContext(), "No items selected for sharing.")
            return
        }

        val fileUris = selectedItems.map { mediaItem ->
            // For sharing, you might need a FileProvider for security reasons,
            // especially if sharing files outside your app's private directory.
            // For simplicity, directly using Uri.fromFile for now, but be aware of limitations.
            Uri.fromFile(mediaItem.file)
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
            type = "*/*" // Or specific mime types if all are images/videos
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Statuses"))
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        actionMode?.finish() // Ensure action mode is dismissed if fragment is destroyed
    }
}
