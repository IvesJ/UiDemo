package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.R
import com.ace.uidemo.model.MediaItem
import com.bumptech.glide.Glide

class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.imageView)

    fun bind(image: MediaItem.Image) {
        Glide.with(itemView.context)
            .load(image.imageUrl)
            .centerCrop()
            .into(imageView)
    }

    companion object {
        fun create(parent: ViewGroup): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ImageViewHolder(view)
        }
    }
}
