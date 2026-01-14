package com.ace.uidemo.viewholder

import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.MediaPagerAdapter
import com.ace.uidemo.adapter.SubTabAdapter
import com.ace.uidemo.databinding.TabContentWithSubtabsLayoutBinding
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 带子Tab的ViewHolder，管理子Tab轮播和媒体轮播
 * 逻辑：子Tab的所有媒体轮播完成后，切换到下一个子Tab
 *       所有子Tab轮播完成后，通知父级Tab切换
 */
class TabWithSubTabsViewHolder(
    private val binding: TabContentWithSubtabsLayoutBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit,
    private val onPauseStateChanged: (Boolean) -> Unit
) : RecyclerView.ViewHolder(binding.root), ITabContentViewHolder {

    private var subTabAdapter: SubTabAdapter? = null
    private var mediaPagerAdapter: MediaPagerAdapter? = null
    private var subTabs: List<TabData> = emptyList()
    private var currentSubTabIndex = 0
    private var currentMediaIndex = 0
    private var isPaused = false
    private var pausedElapsedTime = 0L
    private var autoScrollJob: Job? = null

    // 当前子Tab的媒体项
    private var currentMediaItems: List<MediaItem> = emptyList()

    init {
        setupSubTabViewPager()
    }

    private fun setupSubTabViewPager() {
        // 设置子Tab内容的ViewPager2
        binding.subTabViewPager.apply {
            offscreenPageLimit = 1
            // 禁用用户滑动，只允许代码控制
            isUserInputEnabled = false

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    currentMediaIndex = position

                    stopAutoScroll()
                    mediaPagerAdapter?.pauseAllVideos()
                    mediaPagerAdapter?.resetAllVideos()

                    binding.indicatorView.setCurrentPosition(position)

                    if (!isPaused) {
                        val currentMedia = currentMediaItems.getOrNull(position)
                        if (currentMedia is MediaItem.Video) {
                            mediaPagerAdapter?.resumeVideo(position)
                        }
                        startAutoScroll(position)
                    }
                }
            })
        }
    }

    fun bind(tabData: TabData) {
        subTabs = tabData.subTabs
        currentSubTabIndex = 0
        currentMediaIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        // 设置左侧子Tab列表
        setupSubTabList()

        // 绑定第一个子Tab的内容
        bindSubTabContent(0)
    }

    private fun setupSubTabList() {
        subTabAdapter = SubTabAdapter(subTabs) { position ->
            // 用户点击子Tab时的处理
            if (position != currentSubTabIndex) {
                switchToSubTab(position)
            }
        }

        binding.subTabRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
            adapter = subTabAdapter
        }
    }

    /**
     * 切换到指定的子Tab
     */
    private fun switchToSubTab(position: Int) {
        if (position < 0 || position >= subTabs.size) return

        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()
        mediaPagerAdapter?.resetAllVideos()

        currentSubTabIndex = position
        currentMediaIndex = 0
        pausedElapsedTime = 0L

        // 更新左侧选中状态
        subTabAdapter?.setSelectedPosition(position)

        // 绑定新子Tab的内容
        bindSubTabContent(position)
    }

    /**
     * 绑定子Tab的媒体内容
     */
    private fun bindSubTabContent(subTabIndex: Int) {
        val subTab = subTabs.getOrNull(subTabIndex) ?: return
        currentMediaItems = subTab.mediaItems

        // 设置指示器
        binding.indicatorView.setupWithMediaItems(currentMediaItems.size)
        binding.indicatorView.setCurrentPosition(0)

        // 设置媒体适配器
        mediaPagerAdapter = MediaPagerAdapter(
            mediaItems = currentMediaItems,
            onVideoReady = { position, duration ->
                if (position == currentMediaIndex && !isPaused) {
                    mediaPagerAdapter?.resumeVideo(position)
                    startAutoScroll(position)
                }
            },
            onVideoCompleted = { position ->
                if (position == currentMediaIndex) {
                    moveToNextMedia()
                }
            },
            onVideoClicked = {
                togglePause()
            }
        )

        binding.subTabViewPager.adapter = mediaPagerAdapter
        binding.subTabViewPager.setCurrentItem(0, false)

        // 开始自动轮播
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
                    moveToNextMedia()
                    break
                }

                delay(16L)
                elapsed += 16L
            }
        }
    }

    private fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    /**
     * 移动到下一个媒体项
     */
    private fun moveToNextMedia() {
        if (currentMediaIndex < currentMediaItems.size - 1) {
            // 还有下一个媒体，切换到下一个
            binding.subTabViewPager.setCurrentItem(currentMediaIndex + 1, true)
        } else {
            // 当前子Tab的所有媒体播放完成，切换到下一个子Tab
            moveToNextSubTab()
        }
    }

    /**
     * 移动到下一个子Tab
     */
    private fun moveToNextSubTab() {
        if (currentSubTabIndex < subTabs.size - 1) {
            // 还有下一个子Tab
            switchToSubTab(currentSubTabIndex + 1)
        } else {
            // 所有子Tab都轮播完成，通知父级Tab完成
            onTabCompleted()
        }
    }

    /**
     * 切换暂停/播放
     */
    override fun togglePause() {
        isPaused = !isPaused

        if (isPaused) {
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()

            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
            if (mediaItem != null) {
                val duration = when (mediaItem) {
                    is MediaItem.Image -> 5000L
                    is MediaItem.Video -> mediaItem.duration
                }
                val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
                pausedElapsedTime = (duration * currentProgress / 100f).toLong()
            }
        } else {
            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
            if (mediaItem is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(currentMediaIndex)
            }
            startAutoScroll(currentMediaIndex)
        }

        onPauseStateChanged(isPaused)
    }

    /**
     * 暂停轮播
     */
    override fun pause() {
        if (!isPaused) {
            isPaused = true
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()

            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
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
    override fun resume() {
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
    override fun release() {
        stopAutoScroll()
        mediaPagerAdapter?.releaseAllVideos()
    }

    /**
     * 获取暂停状态
     */
    override fun isPaused(): Boolean = isPaused

    /**
     * 获取当前媒体项
     */
    override fun getCurrentMediaItem(): MediaItem? = currentMediaItems.getOrNull(currentMediaIndex)

    /**
     * 是否在最后一项（子Tab的最后一个媒体且是最后一个子Tab）
     */
    override fun isAtLastItem(): Boolean {
        return currentSubTabIndex == subTabs.size - 1 &&
                currentMediaIndex == currentMediaItems.size - 1
    }

    /**
     * 重置到第一个子Tab的第一个媒体项并开始播放
     */
    override fun resetToFirst() {
        if (subTabs.isEmpty()) return

        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()
        mediaPagerAdapter?.resetAllVideos()

        currentSubTabIndex = 0
        currentMediaIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        // 重置左侧子Tab选中状态
        subTabAdapter?.setSelectedPosition(0)

        // 重新绑定第一个子Tab的内容
        bindSubTabContent(0)
    }
}
