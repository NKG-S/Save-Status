@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus // Ensure this package is consistent

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kezor.localsave.savestatus.databinding.ItemMediaGridBinding // Ensure this binding class exists and is in the correct package

class StatusAdapter(
    // CORRECTED: Change View to Int for position
    private val onItemClick: (MediaItem, Int) -> Unit,
    private val onItemLongClick: (MediaItem, View) -> Unit,
    private val onSelectionChanged: (MediaItem, Boolean) -> Unit // Callback for selection changes
) : ListAdapter<MediaItem, StatusAdapter.MediaViewHolder>(MediaDiffCallback()) {

    var isSelectionMode: Boolean = false
        @SuppressLint("NotifyDataSetChanged")
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
                    // CORRECTED: Pass adapterPosition instead of itemView
                    onItemClick(mediaItem, adapterPosition)
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
