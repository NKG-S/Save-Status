package com.kezor.localsave.savestatus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.kezor.localsave.savestatus.R
import com.kezor.localsave.savestatus.SavedFragmentDirections // Correct import for Safe Args directions

class SavedFragment : Fragment() {

    private var _binding: FragmentSavedBinding? = null
    private val binding get() = _binding!!

    private lateinit var savedAdapter: StatusAdapter
    private var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE
    private var actionMode: ActionMode? = null
    private val selectedItems = mutableSetOf<MediaItem>()

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
            onItemClick = { mediaItem, _ ->
                if (actionMode != null) {
                    toggleSelection(mediaItem)
                } else {
                    // Correct usage of SavedFragmentDirections
                    val action = SavedFragmentDirections.actionSavedToMediaViewerFragment(mediaItem, true)
                    findNavController().navigate(action)
                }
            },
            onItemLongClick = { mediaItem, _ ->
                startSelectionMode(mediaItem)
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

    private fun loadMedia(mediaType: String) {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.textViewEmptyState.visibility = View.GONE
        savedAdapter.isSelectionMode = false
        actionMode?.finish()

        lifecycleScope.launch(Dispatchers.IO) {
            val savedMediaList = mutableListOf<MediaItem>()
            val saveFolderPath = getSavedFolderPath()

            val saveDir = File(saveFolderPath)
            if (saveDir.exists() && saveDir.isDirectory) {
                val files = saveDir.listFiles()
                files?.filter { it.isFile && !it.name.startsWith(".") &&
                        ((mediaType == Constants.MEDIA_TYPE_IMAGE && (it.extension == "jpg" || it.extension == "jpeg" || it.extension == "png")) ||
                                (mediaType == Constants.MEDIA_TYPE_VIDEO && (it.extension == "mp4" || it.extension == "avi" || it.extension == "mkv")))
                }?.map { file ->
                    MediaItem(
                        file = file,
                        uri = file.absolutePath,
                        type = if (file.extension == "mp4" || file.extension == "avi" || file.extension == "mkv") Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                        lastModified = file.lastModified()
                    )
                }
            }

            savedMediaList.sortByDescending { it.lastModified }

            withContext(Dispatchers.Main) {
                if (savedMediaList.isEmpty()) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                } else {
                    binding.textViewEmptyState.visibility = View.GONE
                }
                savedAdapter.submitList(savedMediaList)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getSavedFolderPath(): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        return prefs.getString(Constants.KEY_SAVE_FOLDER_PATH, null)
            ?: (requireContext().getExternalFilesDir(null)?.absolutePath + File.separator + Constants.DEFAULT_SAVE_FOLDER_NAME)
    }

    private fun startSelectionMode(initialItem: MediaItem) {
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
            savedAdapter.isSelectionMode = true
            selectedItems.clear()
            toggleSelection(initialItem)
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
            binding.appBarLayout.visibility = View.GONE
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_delete -> {
                    showDeleteConfirmationDialog()
                    mode?.finish()
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

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            savedAdapter.isSelectionMode = false
            selectedItems.clear()
            binding.appBarLayout.visibility = View.VISIBLE
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext(), R.style.Theme_SaveStatus_AlertDialog)
            .setTitle(getString(R.string.saved_delete_confirmation_title))
            .setMessage(getString(R.string.saved_delete_confirmation_message))
            .setPositiveButton(getString(R.string.saved_delete_confirm)) { dialog, _ ->
                dialog.dismiss()
                deleteSelectedItems()
            }
            .setNegativeButton(getString(R.string.saved_delete_cancel)) { dialog, _ ->
                dialog.dismiss()
                actionMode?.finish()
            }
            .show()
    }

    private fun deleteSelectedItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            val deletedCount = selectedItems.count { Utils.deleteFile(requireContext(), it.file) }

            withContext(Dispatchers.Main) {
                if (deletedCount > 0) {
                    Utils.showToast(requireContext(), getString(R.string.saved_items_deleted, deletedCount))
                    loadMedia(currentMediaType)
                } else {
                    Utils.showToast(requireContext(), getString(R.string.saved_delete_failed))
                }
                actionMode?.finish()
            }
        }
    }

    private fun shareSelectedItems() {
        if (selectedItems.isEmpty()) {
            Utils.showToast(requireContext(), "No items selected for sharing.")
            return
        }

        val fileUris = selectedItems.map { mediaItem ->
            Uri.fromFile(mediaItem.file)
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Statuses"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        actionMode?.finish()
    }
}
