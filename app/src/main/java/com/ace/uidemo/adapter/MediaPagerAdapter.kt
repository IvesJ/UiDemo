package com.ace.uidemo.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.model.MediaItem

class MediaPagerAdapter(
    private val mediaItems: List<MediaItem>,
    private val onVideoReady: (position: Int, duration: Long) -> Unit,
    private val onVideoCompleted: (position: Int) -> Unit,
    private val onVideoClicked: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_IMAGE = 0
        private const val TYPE_VIDEO = 1
    }

    private val videoHolders = mutableMapOf<Int, VideoViewHolder>()

    override fun getItemViewType(position: Int): Int {
        return when (mediaItems[position]) {
            is MediaItem.Image -> TYPE_IMAGE
            is MediaItem.Video -> TYPE_VIDEO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_IMAGE -> ImageViewHolder.create(parent)
            TYPE_VIDEO -> VideoViewHolder.create(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ImageViewHolder -> {
                holder.bind(mediaItems[position] as MediaItem.Image)
            }
            is VideoViewHolder -> {
                holder.bind(
                    mediaItems[position] as MediaItem.Video,
                    onVideoReady,
                    onVideoCompleted,
                    onVideoClicked
                )
                videoHolders[position] = holder
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VideoViewHolder) {
            holder.release()
            // Remove from map by finding the holder
            val iterator = videoHolders.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value == holder) {
                    iterator.remove()
                    break
                }
            }
        }
    }

    override fun getItemCount(): Int = mediaItems.size

    fun getVideoHolder(position: Int): VideoViewHolder? {
        return videoHolders[position]
    }

    fun releaseAllVideos() {
        videoHolders.values.forEach { it.release() }
        videoHolders.clear()
    }
}
