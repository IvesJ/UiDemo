package com.ace.uidemo.model

import java.io.Serializable

sealed class MediaItem : Serializable {
    abstract val id: String

    data class Image(
        override val id: String,
        val imageUrl: String
    ) : MediaItem()

    data class Video(
        override val id: String,
        val videoUrl: String,
        var duration: Long = 0L
    ) : MediaItem()
}
