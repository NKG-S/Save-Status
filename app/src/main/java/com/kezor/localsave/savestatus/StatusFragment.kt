package com.kezor.localsave.savestatus // Standardized package name

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.kezor.localsave.savestatus.databinding.FragmentStatusBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    private lateinit var statusAdapter: StatusAdapter
    private var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE

    // This will hold the current list of media items displayed in the RecyclerView
    private var currentMediaList: List<MediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTabLayout()
        setupSwipeRefresh()

        // Load initial data
        loadMedia(currentMediaType)
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(
            onItemClick = { mediaItem, position -> // This 'position' will now correctly be an Int
                // Navigate to MediaViewerFragment
                val action = StatusFragmentDirections.actionNavigationStatusToMediaViewerFragment(
                    mediaItem,
                    currentMediaList.toTypedArray(),
                    position,
                    false
                )
                findNavController().navigate(action)
            },
            onItemLongClick = { mediaItem, _ ->
                // No specific action for long click in StatusFragment, but adapter needs it
                // Haptic feedback is handled in adapter
            },
            onSelectionChanged = { _, _ ->
                // No selection mode in StatusFragment
            }
        )
        binding.recyclerViewStatus.apply {
            layoutManager = GridLayoutManager(context, 2) // 2 columns for grid layout
            adapter = statusAdapter
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

        lifecycleScope.launch(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            // Paths for WhatsApp and WhatsApp Business statuses
            val whatsappStatusDir = File(Constants.WHATSAPP_STATUS_PATH)
            val whatsappBusinessStatusDir = File(Constants.WHATSAPP_BUSINESS_STATUS_PATH)

            Log.d("StatusFragment", "WhatsApp Status Dir: ${whatsappStatusDir.absolutePath}, Exists: ${whatsappStatusDir.exists()}")
            Log.d("StatusFragment", "WhatsApp Business Status Dir: ${whatsappBusinessStatusDir.absolutePath}, Exists: ${whatsappBusinessStatusDir.exists()}")


            // Function to get files from a directory
            fun getMediaFiles(directory: File): List<MediaItem> {
                val files = directory.listFiles()
                if (files == null) {
                    Log.w("StatusFragment", "Directory ${directory.absolutePath} is null or not accessible.")
                    return emptyList()
                }
                Log.d("StatusFragment", "Found ${files.size} files in ${directory.absolutePath}")

                return files.filter { it.isFile &&
                        ((mediaType == Constants.MEDIA_TYPE_IMAGE && (it.extension == "jpg" || it.extension == "jpeg" || it.extension == "png")) ||
                                (mediaType == Constants.MEDIA_TYPE_VIDEO && (it.extension == "mp4" || it.extension == "avi" || it.extension == "mkv")))
                }.map { file ->
                    MediaItem(
                        file = file,
                        uri = file.absolutePath,
                        type = if (file.extension == "mp4" || file.extension == "avi" || file.extension == "mkv") Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                        lastModified = file.lastModified()
                    )
                }.sortedByDescending { it.lastModified } // Sort by newest first
            }

            // Load media from both WhatsApp and WhatsApp Business
            mediaList.addAll(getMediaFiles(whatsappStatusDir))
            mediaList.addAll(getMediaFiles(whatsappBusinessStatusDir))

            // Remove duplicates if any (based on absolute path)
            val distinctMediaList = mediaList.distinctBy { it.file?.absolutePath }.toMutableList()

            Log.d("StatusFragment", "Total distinct media items found: ${distinctMediaList.size}")

            withContext(Dispatchers.Main) {
                if (distinctMediaList.isEmpty()) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                } else {
                    binding.textViewEmptyState.visibility = View.GONE
                }
                currentMediaList = distinctMediaList // Update the list held by the fragment
                statusAdapter.submitList(distinctMediaList)
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
