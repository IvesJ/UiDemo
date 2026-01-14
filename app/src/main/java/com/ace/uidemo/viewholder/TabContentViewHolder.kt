package com.ace.uidemo.viewholder

import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.MediaPagerAdapter
import com.ace.uidemo.adapter.SubTabAdapter
import com.ace.uidemo.databinding.TabContentLayoutBinding
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 单个Tab的ViewHolder，管理该Tab的媒体轮播
 * 支持子Tab功能：如果TabData有subTabs，则显示左侧竖向子Tab列表
 */
class TabContentViewHolder(
    private val binding: TabContentLayoutBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit, // Tab轮播完成回调（所有子Tab都轮播完）
    private val onPauseStateChanged: (Boolean) -> Unit // 暂停状态改变回调
) : RecyclerView.ViewHolder(binding.root) {

    private var mediaPagerAdapter: MediaPagerAdapter? = null
    private var subTabAdapter: SubTabAdapter? = null
    private var currentMediaIndex = 0
    private var currentSubTabIndex = 0
    private var isPaused = false
    private var pausedElapsedTime = 0L
    private var autoScrollJob: Job? = null

    // 当前Tab的数据（包含可能的子Tab）
    private var tabData: TabData? = null
    // 当前显示的媒体项列表（可能是父Tab或子Tab的mediaItems）
    private var currentMediaItems: List<MediaItem> = emptyList()

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
                        val currentMedia = currentMediaItems.getOrNull(position)
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

    /**
     * 绑定Tab数据
     */
    fun bind(tabData: TabData) {
        this.tabData = tabData
        this.currentMediaItems = tabData.mediaItems
        this.currentMediaIndex = 0
        this.currentSubTabIndex = 0
        this.isPaused = false
        this.pausedElapsedTime = 0L

        // 检查是否有子Tab
        if (tabData.subTabs.isNotEmpty()) {
            // 有子Tab：显示左侧竖向子Tab列表
            binding.subTabRecyclerView.visibility = View.VISIBLE
            // 调整媒体内容区域的约束，让它从子Tab列表右侧开始
            (binding.scrollableHost.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).startToStart =
                android.view.View.NO_ID
            (binding.scrollableHost.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).startToEnd =
                binding.subTabRecyclerView.id

            setupSubTabs(tabData.subTabs)
        } else {
            // 无子Tab：隐藏子Tab列表
            binding.subTabRecyclerView.visibility = View.GONE
            // 恢复媒体内容区域到左边
            (binding.scrollableHost.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).startToStart =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            (binding.scrollableHost.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).startToEnd =
                android.view.View.NO_ID
        }

        // 设置媒体内容
        setupMediaContent(tabData.mediaItems)
    }

    /**
     * 设置子Tab列表
     */
    private fun setupSubTabs(subTabs: List<TabData>) {
        subTabAdapter = SubTabAdapter(subTabs) { position ->
            // 子Tab被选中
            onSubTabSelected(position)
        }
        binding.subTabRecyclerView.adapter = subTabAdapter
    }

    /**
     * 设置媒体内容
     */
    private fun setupMediaContent(mediaItems: List<MediaItem>) {
        this.currentMediaItems = mediaItems

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
     * 子Tab被选中
     */
    private fun onSubTabSelected(position: Int) {
        if (position == currentSubTabIndex) return

        // 停止当前媒体轮播
        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()
        mediaPagerAdapter?.resetAllVideos()

        currentSubTabIndex = position
        currentMediaIndex = 0

        // 获取选中子Tab的媒体内容
        val selectedSubTab = tabData?.subTabs?.get(position) ?: return

        // 更新子Tab列表的选中状态
        subTabAdapter?.setSelectedPosition(position)

        // 更新媒体内容
        currentMediaItems = selectedSubTab.mediaItems

        // 更新指示器
        binding.indicatorView.setupWithMediaItems(selectedSubTab.mediaItems.size)
        binding.indicatorView.setCurrentPosition(0)

        // 更新MediaPagerAdapter的媒体项
        mediaPagerAdapter?.updateMediaItems(selectedSubTab.mediaItems)

        binding.mediaViewPager.setCurrentItem(0, false)

        // 启动自动滚动
        if (!isPaused) {
            startAutoScroll(0)
        }
    }

    /**
     * 启动自动滚动
     */
    private fun startAutoScroll(position: Int) {
        stopAutoScroll()

        if (position < 0 || position >= currentMediaItems.size) return

        val mediaItem = currentMediaItems[position]
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
     * 如果有子Tab，则在当前子Tab的所有媒体播放完后，切换到下一个子Tab
     * 所有子Tab都播放完后，触发父级Tab切换
     */
    private fun moveToNext() {
        if (currentMediaIndex < currentMediaItems.size - 1) {
            // 当前子Tab还有下一个媒体项
            binding.mediaViewPager.setCurrentItem(currentMediaIndex + 1, true)
        } else {
            // 当前子Tab的最后一个媒体项播放完毕
            val hasSubTabs = tabData?.subTabs?.isNotEmpty() == true

            if (hasSubTabs) {
                // 有子Tab：切换到下一个子Tab
                if (currentSubTabIndex < tabData!!.subTabs.size - 1) {
                    // 还有下一个子Tab，切换到它
                    onSubTabSelected(currentSubTabIndex + 1)
                } else {
                    // 所有子Tab都播放完了，触发父级Tab切换
                    onTabCompleted()
                }
            } else {
                // 无子Tab：直接触发父级Tab切换
                onTabCompleted()
            }
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

            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
            val duration = when (mediaItem) {
                is MediaItem.Image -> 5000L
                is MediaItem.Video -> mediaItem?.duration ?: 5000L
                else -> 5000L
            }

            val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
            pausedElapsedTime = (duration * currentProgress / 100f).toLong()
        } else {
            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
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

            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
            val duration = when (mediaItem) {
                is MediaItem.Image -> 5000L
                is MediaItem.Video -> mediaItem?.duration ?: 5000L
                else -> 5000L
            }

            val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
            pausedElapsedTime = (duration * currentProgress / 100f).toLong()
        }
    }

    /**
     * 恢复轮播
     */
    fun resume() {
        if (isPaused) {
            isPaused = false
            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
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
    fun getCurrentMediaItem(): MediaItem? = currentMediaItems.getOrNull(currentMediaIndex)

    /**
     * 是否在最后一个媒体项
     */
    fun isAtLastItem(): Boolean {
        val hasSubTabs = tabData?.subTabs?.isNotEmpty() == true
        return if (hasSubTabs) {
            // 有子Tab：判断是否在最后一个子Tab的最后一个媒体项
            currentSubTabIndex == tabData!!.subTabs.size - 1 &&
                    currentMediaIndex == currentMediaItems.size - 1
        } else {
            // 无子Tab：直接判断
            currentMediaIndex == currentMediaItems.size - 1
        }
    }

    /**
     * 重置到第一个媒体项并开始播放（用于父级Tab切换时）
     */
    fun resetToFirst() {
        if (currentMediaItems.isEmpty()) return

        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()
        mediaPagerAdapter?.resetAllVideos()

        currentMediaIndex = 0
        currentSubTabIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        // 如果有子Tab，重置到第一个子Tab
        tabData?.let { data ->
            if (data.subTabs.isNotEmpty()) {
                subTabAdapter?.setSelectedPosition(0)
                currentMediaItems = data.subTabs[0].mediaItems
                mediaPagerAdapter?.updateMediaItems(data.subTabs[0].mediaItems)
                binding.indicatorView.setupWithMediaItems(data.subTabs[0].mediaItems.size)
            } else {
                currentMediaItems = data.mediaItems
                mediaPagerAdapter?.updateMediaItems(data.mediaItems)
                binding.indicatorView.setupWithMediaItems(data.mediaItems.size)
            }
        }

        binding.indicatorView.setCurrentPosition(0)
        binding.mediaViewPager.setCurrentItem(0, false)

        // 如果第一个是视频，恢复播放
        val firstMedia = currentMediaItems.firstOrNull()
        if (firstMedia is MediaItem.Video) {
            mediaPagerAdapter?.resumeVideo(0)
        }

        startAutoScroll(0)
    }
}
