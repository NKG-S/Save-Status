@file:Suppress("DEPRECATION")

package com.kezor.localsave.savestatus

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kezor.localsave.savestatus.databinding.ItemMediaGridBinding

class StatusAdapter(
    private val onItemClick: (MediaItem, Int) -> Unit,
    private val onItemLongClick: (MediaItem, View) -> Unit,
    private val onSelectionChanged: (MediaItem, Boolean) -> Unit
) : ListAdapter<MediaItem, StatusAdapter.MediaViewHolder>(MediaDiffCallback()) {

    var isSelectionMode: Boolean = false
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            if (field != value) {
                field = value
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
                // Load thumbnail
                Glide.with(itemView.context)
                    .load(mediaItem.file)
                    .centerCrop()
                    .into(imageViewThumbnail)

                // Show play icon if video
                imageViewVideoIcon.visibility =
                    if (mediaItem.type == Constants.MEDIA_TYPE_VIDEO) View.VISIBLE else View.GONE

                // Selection visuals based on selection mode
                if (isSelectionMode) {
                    imageViewSelectedOverlay.visibility = if (mediaItem.isSelected) View.VISIBLE else View.GONE
                    imageViewCheckmark.visibility = if (mediaItem.isSelected) View.VISIBLE else View.GONE
                    cardView.strokeWidth = if (mediaItem.isSelected) 4 else 0
                    cardView.strokeColor = if (mediaItem.isSelected)
                        ContextCompat.getColor(itemView.context, R.color.md_theme_primary)
                    else
                        ContextCompat.getColor(itemView.context, android.R.color.transparent)
                } else {
                    imageViewSelectedOverlay.visibility = View.GONE
                    imageViewCheckmark.visibility = View.GONE
                    cardView.strokeWidth = 0
                    cardView.strokeColor = ContextCompat.getColor(itemView.context, android.R.color.transparent)
                }

                // Click & long click handling
                itemView.setOnClickListener {
                    val pos = absoluteAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemClick(mediaItem, pos)
                    }
                }

                itemView.setOnLongClickListener {
                    val pos = absoluteAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        onItemLongClick(mediaItem, itemView)
                        true
                    } else false
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
