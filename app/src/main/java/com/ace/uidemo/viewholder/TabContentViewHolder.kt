package com.ace.uidemo.viewholder

import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.MediaPagerAdapter
import com.ace.uidemo.databinding.TabContentLayoutBinding
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 单个Tab的ViewHolder，管理该Tab的媒体轮播
 */
class TabContentViewHolder(
    private val binding: TabContentLayoutBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit, // Tab轮播完成回调
    private val onPauseStateChanged: (Boolean) -> Unit // 暂停状态改变回调
) : RecyclerView.ViewHolder(binding.root) {

    private var mediaPagerAdapter: MediaPagerAdapter? = null
    private var currentMediaIndex = 0
    private var isPaused = false
    private var pausedElapsedTime = 0L
    private var autoScrollJob: Job? = null
    private var mediaItems: List<MediaItem> = emptyList()

    init {
        setupMediaViewPager()
    }

    private fun setupMediaViewPager() {
        binding.mediaViewPager.apply {
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentMediaIndex = position

                    // 停止之前的自动滚动
                    stopAutoScroll()

                    // 重置所有视频
                    mediaPagerAdapter?.pauseAllVideos()
                    mediaPagerAdapter?.resetAllVideos()

                    // 更新指示器
                    binding.indicatorView.setCurrentPosition(position)

                    // 启动新的自动滚动，并恢复当前视频播放
                    if (!isPaused) {
                        val currentMedia = mediaItems.getOrNull(position)
                        if (currentMedia is MediaItem.Video) {
                            // 恢复当前视频播放
                            mediaPagerAdapter?.resumeVideo(position)
                        }
                        startAutoScroll(position)
                    }
                }
            })
        }
    }

    fun bind(tabData: TabData) {
        mediaItems = tabData.mediaItems
        currentMediaIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        // 先设置指示器
        binding.indicatorView.setupWithMediaItems(mediaItems.size)
        binding.indicatorView.setCurrentPosition(0)

        // 设置媒体适配器
        mediaPagerAdapter = MediaPagerAdapter(
            mediaItems = mediaItems,
            onVideoReady = { position, duration ->
                if (position == currentMediaIndex && !isPaused) {
                    // 启动当前视频播放
                    mediaPagerAdapter?.resumeVideo(position)
                    startAutoScroll(position)
                }
            },
            onVideoCompleted = { position ->
                if (position == currentMediaIndex) {
                    moveToNext()
                }
            },
            onVideoClicked = {
                togglePause()
            }
        )

        binding.mediaViewPager.adapter = mediaPagerAdapter
        binding.mediaViewPager.setCurrentItem(0, false)

        // 启动自动滚动
        startAutoScroll(0)
    }

    /**
     * 启动自动滚动
     */
    private fun startAutoScroll(position: Int) {
        stopAutoScroll()

        if (position < 0 || position >= mediaItems.size) return

        val mediaItem = mediaItems[position]
        val duration = when (mediaItem) {
            is MediaItem.Image -> 5000L
            is MediaItem.Video -> {
                if (mediaItem.duration > 0) {
                    mediaItem.duration
                } else {
                    return
                }
            }
        }

        val startTime = if (pausedElapsedTime > 0) pausedElapsedTime else 0L
        pausedElapsedTime = 0L

        autoScrollJob = lifecycleScope.launch {
            var elapsed = startTime

            while (isActive && !isPaused && currentMediaIndex == position) {
                val progress = ((elapsed.toFloat() / duration) * 100).coerceIn(0f, 100f)
                binding.indicatorView.updateProgress(position, progress.toInt())

                if (elapsed >= duration) {
                    moveToNext()
                    break
                }

                delay(16L)
                elapsed += 16L
            }
        }
    }

    /**
     * 停止自动滚动
     */
    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /**
     * 移动到下一个媒体项
     */
    private fun moveToNext() {
        if (currentMediaIndex < mediaItems.size - 1) {
            binding.mediaViewPager.setCurrentItem(currentMediaIndex + 1, true)
        } else {
            // 到达最后一项，通知Fragment切换到下一个Tab
            onTabCompleted()
        }
    }

    /**
     * 切换暂停/播放
     */
    fun togglePause() {
        isPaused = !isPaused

        if (isPaused) {
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()

            val mediaItem = mediaItems[currentMediaIndex]
            val duration = when (mediaItem) {
                is MediaItem.Image -> 5000L
                is MediaItem.Video -> mediaItem.duration
            }

            val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
            pausedElapsedTime = (duration * currentProgress / 100f).toLong()
        } else {
            val mediaItem = mediaItems[currentMediaIndex]
            if (mediaItem is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(currentMediaIndex)
            }
            startAutoScroll(currentMediaIndex)
        }

        // 通知Fragment更新暂停按钮状态
        onPauseStateChanged(isPaused)
    }

    /**
     * 暂停轮播
     */
    fun pause() {
        if (!isPaused) {
            isPaused = true
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()

            val mediaItem = mediaItems.getOrNull(currentMediaIndex)
            if (mediaItem != null) {
                val duration = when (mediaItem) {
                    is MediaItem.Image -> 5000L
                    is MediaItem.Video -> mediaItem.duration
                }
                val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
                pausedElapsedTime = (duration * currentProgress / 100f).toLong()
            }
        }
    }

    /**
     * 恢复轮播
     */
    fun resume() {
        if (isPaused) {
            isPaused = false
            val mediaItem = mediaItems.getOrNull(currentMediaIndex)
            if (mediaItem is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(currentMediaIndex)
            }
            startAutoScroll(currentMediaIndex)
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopAutoScroll()
        mediaPagerAdapter?.releaseAllVideos()
    }

    /**
     * 获取暂停状态
     */
    fun isPaused(): Boolean = isPaused

    /**
     * 获取当前媒体项
     */
    fun getCurrentMediaItem(): MediaItem? = mediaItems.getOrNull(currentMediaIndex)

    /**
     * 是否在最后一个媒体项
     */
    fun isAtLastItem(): Boolean = currentMediaIndex == mediaItems.size - 1

    /**
     * 重置到第一个媒体项并开始播放（用于Tab切换时）
     */
    fun resetToFirst() {
        if (mediaItems.isEmpty()) return

        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()
        mediaPagerAdapter?.resetAllVideos()

        currentMediaIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        binding.indicatorView.setCurrentPosition(0)
        binding.mediaViewPager.setCurrentItem(0, false)

        // 如果第一个是视频，恢复播放
        val firstMedia = mediaItems.firstOrNull()
        if (firstMedia is MediaItem.Video) {
            mediaPagerAdapter?.resumeVideo(0)
        }

        startAutoScroll(0)
    }
}
