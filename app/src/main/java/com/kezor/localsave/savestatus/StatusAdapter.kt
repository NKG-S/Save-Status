package com.kezor.localsave.savestatus
/*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.kezor.localsave.savestatus.databinding.FragmentStatusBinding
import com.kezor.localsave.savestatus.databinding.ItemMediaGridBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    private lateinit var statusAdapter: StatusAdapter
    private var currentMediaType: String = Constants.MEDIA_TYPE_IMAGE

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

        loadMedia(currentMediaType)
    }

    private fun setupRecyclerView() {
        statusAdapter = StatusAdapter(
            onItemClick = { mediaItem, _ ->
                val action = StatusFragmentDirections.actionStatusToMediaViewerFragment(
                    mediaItem = mediaItem,
                    isFromSaved = false
                )
                findNavController().navigate(action)
            },
            onItemLongClick = { _, _ -> },
            onSelectionChanged = { _, _ -> }
        )
        binding.recyclerViewStatus.apply {
            layoutManager = GridLayoutManager(context, 2)
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
            val whatsappStatusDir = File(Constants.WHATSAPP_STATUS_PATH)
            val whatsappBusinessStatusDir = File(Constants.WHATSAPP_BUSINESS_STATUS_PATH)

            fun getMediaFiles(directory: File): List<MediaItem> {
                val files = directory.listFiles() ?: return emptyList()
                return files.filter { it.isFile && !it.name.startsWith(".") &&
                        ((mediaType == Constants.MEDIA_TYPE_IMAGE && (it.extension == "jpg" || it.extension == "jpeg" || it.extension == "png")) ||
                                (mediaType == Constants.MEDIA_TYPE_VIDEO && (it.extension == "mp4" || it.extension == "avi" || it.extension == "mkv")))
                }.map { file ->
                    MediaItem(
                        file = file,
                        uri = file.absolutePath,
                        type = if (file.extension == "mp4" || file.extension == "avi" || file.extension == "mkv") Constants.MEDIA_TYPE_VIDEO else Constants.MEDIA_TYPE_IMAGE,
                        lastModified = file.lastModified()
                    )
                }.sortedByDescending { it.lastModified }
            }

            mediaList.addAll(getMediaFiles(whatsappStatusDir))
            mediaList.addAll(getMediaFiles(whatsappBusinessStatusDir))
            val distinctMediaList = mediaList.distinctBy { it.file.absolutePath }.toMutableList()

            withContext(Dispatchers.Main) {
                if (distinctMediaList.isEmpty()) {
                    binding.textViewEmptyState.visibility = View.VISIBLE
                } else {
                    binding.textViewEmptyState.visibility = View.GONE
                }
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

class StatusAdapter(
    private val onItemClick: (MediaItem, View) -> Unit,
    private val onItemLongClick: (MediaItem, View) -> Unit,
    private val onSelectionChanged: (MediaItem, Boolean) -> Unit
) : ListAdapter<MediaItem, StatusAdapter.MediaViewHolder>(MediaDiffCallback()) {

    var isSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = getItem(position)
        holder.bind(mediaItem)
    }

    inner class MediaViewHolder(private val binding: ItemMediaGridBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(mediaItem: MediaItem) {
            binding.apply {
                Glide.with(itemView.context)
                    .load(mediaItem.file)
                    .centerCrop()
                    .into(imageViewThumbnail)

                imageViewVideoIcon.visibility =
                    if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) View.VISIBLE else View.GONE
                imageViewSelectedOverlay.visibility =
                    if (isSelectionMode && mediaItem.isSelected) View.VISIBLE else View.GONE
                imageViewCheckmark.visibility =
                    if (isSelectionMode && mediaItem.isSelected) View.VISIBLE else View.GONE
                cardView.strokeWidth =
                    if (isSelectionMode && mediaItem.isSelected) 4 else 0

                itemView.setOnClickListener {
                    onItemClick(mediaItem, itemView)
                }
                itemView.setOnLongClickListener {
                    onItemLongClick(mediaItem, itemView)
                    true
                }
            }
        }
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem && oldItem.isSelected == newItem.isSelected
        }
    }
}
*/



import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kezor.localsave.savestatus.databinding.ItemMediaGridBinding // Ensure this binding class exists and is in the correct package

class StatusAdapter(
    private val onItemClick: (MediaItem, View) -> Unit,
    private val onItemLongClick: (MediaItem, View) -> Unit,
    private val onSelectionChanged: (MediaItem, Boolean) -> Unit // Callback for selection changes
) : ListAdapter<MediaItem, StatusAdapter.MediaViewHolder>(MediaDiffCallback()) {

    var isSelectionMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                // When selection mode changes, notify the adapter to rebind views
                // This is important to show/hide selection overlays/checkboxes
                notifyDataSetChanged()
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val mediaItem = getItem(position)
        holder.bind(mediaItem)
    }

    inner class MediaViewHolder(private val binding: ItemMediaGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(mediaItem: MediaItem) {
            binding.apply {
                // Load thumbnail using Glide
                Glide.with(itemView.context)
                    .load(mediaItem.file)
                    .centerCrop()
                    .into(imageViewThumbnail)

                // Show/hide video icon based on media type
                imageViewVideoIcon.visibility = if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) View.VISIBLE else View.GONE

                // Handle selection mode UI: overlay and checkmark
                imageViewSelectedOverlay.visibility = if (isSelectionMode && mediaItem.isSelected) View.VISIBLE else View.GONE
                imageViewCheckmark.visibility = if (isSelectionMode && mediaItem.isSelected) View.VISIBLE else View.GONE
                // Add a stroke to the card for selected items
                cardView.strokeWidth = if (isSelectionMode && mediaItem.isSelected) 4 else 0 // Highlight selected item visually
                cardView.strokeColor = if (isSelectionMode && mediaItem.isSelected) itemView.context.getColor(R.color.md_theme_primary) else itemView.context.getColor(android.R.color.transparent)


                // Set click listeners
                itemView.setOnClickListener {
                    onItemClick(mediaItem, itemView)
                }
                itemView.setOnLongClickListener {
                    onItemLongClick(mediaItem, itemView)
                    true // Consume the long click event
                }
            }
        }
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            // Items are the same if their unique identifiers (URIs/paths) are the same
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            // Contents are the same if all relevant properties are equal, including selection state
            return oldItem == newItem && oldItem.isSelected == newItem.isSelected
        }
    }
}
