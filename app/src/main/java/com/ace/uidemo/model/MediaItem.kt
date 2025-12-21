package com.ace.uidemo.model

import java.io.Serializable

sealed class MediaItem : Serializable {
    abstract val id: String
    abstract val localPath: String?
    abstract val isLocal: Boolean

    data class Image(
        override val id: String,
        val imageUrl: String,
        override val localPath: String? = null
    ) : MediaItem() {
        override val isLocal: Boolean
            get() = !localPath.isNullOrEmpty()
    }

    data class Video(
        override val id: String,
        val videoUrl: String,
        var duration: Long = 0L,
        override val localPath: String? = null
    ) : MediaItem() {
        override val isLocal: Boolean
            get() = !localPath.isNullOrEmpty()
    }
}
