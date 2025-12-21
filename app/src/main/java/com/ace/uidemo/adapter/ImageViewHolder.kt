package com.ace.uidemo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.R
import com.ace.uidemo.model.MediaItem
import com.bumptech.glide.Glide
import java.io.File

class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val imageView: ImageView = itemView.findViewById(R.id.imageView)

    fun bind(image: MediaItem.Image) {
        val source = if (image.isLocal && image.localPath != null) {
            File(image.localPath)  // 本地文件
        } else {
            image.imageUrl  // 远程URL
        }

        Glide.with(itemView.context)
            .load(source)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
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
