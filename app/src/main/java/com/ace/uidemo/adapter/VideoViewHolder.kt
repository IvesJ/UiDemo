package com.ace.uidemo.adapter

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.ace.uidemo.R
import com.ace.uidemo.model.MediaItem

class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val textureView: TextureView = itemView.findViewById(R.id.textureView)
    private val loadingIndicator: ProgressBar = itemView.findViewById(R.id.loadingIndicator)
    private var mediaPlayer: MediaPlayer? = null
    private var currentVideo: MediaItem.Video? = null

    fun bind(
        video: MediaItem.Video,
        onVideoReady: (position: Int, duration: Long) -> Unit,
        onVideoCompleted: (position: Int) -> Unit,
        onVideoClicked: () -> Unit
    ) {
        currentVideo = video
        loadingIndicator.visibility = View.VISIBLE

        // 设置点击监听
        itemView.setOnClickListener {
            onVideoClicked()
        }

        if (textureView.isAvailable) {
            initializeMediaPlayer(video, textureView.surfaceTexture!!, onVideoReady, onVideoCompleted)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    initializeMediaPlayer(video, surface, onVideoReady, onVideoCompleted)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    release()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun initializeMediaPlayer(
        video: MediaItem.Video,
        surface: SurfaceTexture,
        onVideoReady: (Int, Long) -> Unit,
        onVideoCompleted: (Int) -> Unit
    ) {
        try {
            release()

            val videoSource = if (video.isLocal && video.localPath != null) {
                video.localPath  // 本地文件路径
            } else {
                video.videoUrl  // 远程URL
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(videoSource)
                setSurface(Surface(surface))

                setOnPreparedListener { mp ->
                    loadingIndicator.visibility = View.GONE
                    val duration = mp.duration.toLong()
                    video.duration = duration
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onVideoReady(adapterPosition, duration)
                    }
                    mp.start()
                }

                setOnCompletionListener {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        onVideoCompleted(adapterPosition)
                    }
                }

                setOnErrorListener { _, what, extra ->
                    loadingIndicator.visibility = View.GONE
                    true
                }

                isLooping = false
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            loadingIndicator.visibility = View.GONE
        }
    }

    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    companion object {
        fun create(parent: ViewGroup): VideoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_video, parent, false)
            return VideoViewHolder(view)
        }
    }
}
