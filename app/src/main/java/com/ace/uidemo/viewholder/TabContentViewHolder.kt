package com.ace.uidemo.viewholder

import android.util.Log
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
 * 支持嵌套子Tab：所有子Tab轮播完成后才触发父Tab完成回调
 */
class TabContentViewHolder(
    private val binding: TabContentLayoutBinding,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onTabCompleted: () -> Unit, // Tab轮播完成回调
    private val onPauseStateChanged: (Boolean) -> Unit // 暂停状态改变回调
) : RecyclerView.ViewHolder(binding.root) {

    companion object {
        private const val TAG = "TabContentViewHolder"
    }

    private var mediaPagerAdapter: MediaPagerAdapter? = null
    private var subTabAdapter: SubTabAdapter? = null
    private var currentMediaIndex = 0
    private var isPaused = false
    private var pausedElapsedTime = 0L
    private var autoScrollJob: Job? = null

    // 嵌套Tab相关
    private var tabData: TabData? = null
    private var subTabs: List<TabData> = emptyList()
    private var currentSubTabIndex = 0
    private var isInSubTabMode = false

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
                        val currentMedia = getCurrentMediaItemInternal()
                        if (currentMedia is MediaItem.Video) {
                            mediaPagerAdapter?.resumeVideo(position)
                        }
                        startAutoScroll(position)
                    }
                }
            })
        }
    }

    fun bind(data: TabData) {
        tabData = data
        subTabs = data.subTabs
        currentMediaIndex = 0
        currentSubTabIndex = 0
        isPaused = false
        pausedElapsedTime = 0L

        // 判断是否使用子Tab模式
        isInSubTabMode = subTabs.isNotEmpty()

        if (isInSubTabMode) {
            setupSubTabMode(data)
        } else {
            setupNormalMode(data)
        }
    }

    /**
     * 设置普通模式（无子Tab）
     */
    private fun setupNormalMode(data: TabData) {
        // 隐藏子Tab列表
        binding.subTabRecyclerView.visibility = View.GONE

        val mediaItems = data.mediaItems
        setupMediaContent(mediaItems)
    }

    /**
     * 设置子Tab模式
     */
    private fun setupSubTabMode(data: TabData) {
        // 显示子Tab列表
        binding.subTabRecyclerView.visibility = View.VISIBLE

        // 设置子Tab适配器
        subTabAdapter = SubTabAdapter(subTabs) { position ->
            onSubTabSelected(position)
        }
        binding.subTabRecyclerView.adapter = subTabAdapter

        // 加载第一个子Tab的内容
        loadSubTabContent(0)
    }

    /**
     * 子Tab选中回调
     */
    private fun onSubTabSelected(position: Int) {
        if (position == currentSubTabIndex) return

        // 停止当前轮播
        stopAutoScroll()
        mediaPagerAdapter?.pauseAllVideos()

        currentSubTabIndex = position
        currentMediaIndex = 0
        pausedElapsedTime = 0L

        // 加载新子Tab的内容
        loadSubTabContent(position)
    }

    /**
     * 加载指定子Tab的内容
     */
    private fun loadSubTabContent(subTabIndex: Int) {
        val subTab = subTabs.getOrNull(subTabIndex) ?: return

        Log.d(TAG, "加载子Tab[$subTabIndex]: ${subTab.title}, ${subTab.mediaItems.size} 个媒体项")

        val mediaItems = subTab.mediaItems
        setupMediaContent(mediaItems)

        // 重置指示器
        binding.indicatorView.setupWithMediaItems(mediaItems.size)
        binding.indicatorView.setCurrentPosition(0)
    }

    /**
     * 设置媒体内容
     */
    private fun setupMediaContent(mediaItems: List<MediaItem>) {
        // 设置指示器
        binding.indicatorView.setupWithMediaItems(mediaItems.size)
        binding.indicatorView.setCurrentPosition(0)

        // 设置媒体适配器
        mediaPagerAdapter = MediaPagerAdapter(
            mediaItems = mediaItems,
            onVideoReady = { position, duration ->
                if (position == currentMediaIndex && !isPaused) {
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
     * 获取当前媒体项（内部方法）
     */
    private fun getCurrentMediaItemInternal(): MediaItem? {
        val items = if (isInSubTabMode) {
            subTabs.getOrNull(currentSubTabIndex)?.mediaItems
        } else {
            tabData?.mediaItems
        }
        return items?.getOrNull(currentMediaIndex)
    }

    /**
     * 启动自动滚动
     */
    private fun startAutoScroll(position: Int) {
        stopAutoScroll()

        val mediaItems = if (isInSubTabMode) {
            subTabs.getOrNull(currentSubTabIndex)?.mediaItems
        } else {
            tabData?.mediaItems
        } ?: return

        if (position < 0 || position >= mediaItems.size) return

        val mediaItem = mediaItems[position]
        val duration = when (mediaItem) {
            is MediaItem.Image -> 5000L
            is MediaItem.Video -> {
                if (mediaItem.duration > 0) mediaItem.duration
                else return
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
        val mediaItems = if (isInSubTabMode) {
            subTabs.getOrNull(currentSubTabIndex)?.mediaItems
        } else {
            tabData?.mediaItems
        } ?: return

        if (currentMediaIndex < mediaItems.size - 1) {
            binding.mediaViewPager.setCurrentItem(currentMediaIndex + 1, true)
        } else {
            // 当前Tab（子Tab或主Tab）的媒体轮播完成
            if (isInSubTabMode) {
                // 子Tab模式：检查是否还有更多子Tab
                if (currentSubTabIndex < subTabs.size - 1) {
                    // 移动到下一个子Tab
                    val nextSubTabIndex = currentSubTabIndex + 1
                    Log.d(TAG, "子Tab[$currentSubTabIndex]完成，切换到子Tab[$nextSubTabIndex]")
                    subTabAdapter?.let { adapter ->
                        // 更新子Tab列表选中状态
                        adapter.resetSelection()
                    }
                    onSubTabSelected(nextSubTabIndex)
                } else {
                    // 所有子Tab完成，通知父Tab完成
                    Log.d(TAG, "所有子Tab完成，触发父Tab完成回调")
                    subTabAdapter?.resetSelection()
                    onTabCompleted()
                }
            } else {
                // 普通模式：直接通知完成
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

            val currentMedia = getCurrentMediaItemInternal()
            if (currentMedia != null) {
                val duration = when (currentMedia) {
                    is MediaItem.Image -> 5000L
                    is MediaItem.Video -> currentMedia.duration
                }
                val currentProgress = binding.indicatorView.getCurrentProgress(currentMediaIndex)
                pausedElapsedTime = (duration * currentProgress / 100f).toLong()
            }
        } else {
            val currentMedia = getCurrentMediaItemInternal()
            if (currentMedia is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(currentMediaIndex)
            }
            startAutoScroll(currentMediaIndex)
        }

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

            val currentMedia = getCurrentMediaItemInternal()
            if (currentMedia != null) {
                val duration = when (currentMedia) {
                    is MediaItem.Image -> 5000L
                    is MediaItem.Video -> currentMedia.duration
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
            val currentMedia = getCurrentMediaItemInternal()
            if (currentMedia is MediaItem.Video) {
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
    fun getCurrentMediaItem(): MediaItem? = getCurrentMediaItemInternal()

    /**
     * 是否在最后一个媒体项
     */
    fun isAtLastItem(): Boolean {
        val mediaItems = if (isInSubTabMode) {
            subTabs.getOrNull(currentSubTabIndex)?.mediaItems
        } else {
            tabData?.mediaItems
        } ?: return false
        return currentMediaIndex == mediaItems.size - 1
    }

    /**
     * 重置到第一个媒体项并开始播放（用于Tab切换时）
     */
    fun resetToFirst() {
        if (isInSubTabMode && subTabs.isNotEmpty()) {
            // 子Tab模式：重置到第一个子Tab
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()
            mediaPagerAdapter?.resetAllVideos()

            currentSubTabIndex = 0
            currentMediaIndex = 0
            isPaused = false
            pausedElapsedTime = 0L

            // 重置子Tab列表选中状态
            subTabAdapter?.resetSelection()

            // 加载第一个子Tab
            loadSubTabContent(0)
        } else if (tabData != null && tabData!!.mediaItems.isNotEmpty()) {
            // 普通模式
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()
            mediaPagerAdapter?.resetAllVideos()

            currentMediaIndex = 0
            isPaused = false
            pausedElapsedTime = 0L

            binding.indicatorView.setCurrentPosition(0)
            binding.mediaViewPager.setCurrentItem(0, false)

            val firstMedia = tabData!!.mediaItems.firstOrNull()
            if (firstMedia is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(0)
            }

            startAutoScroll(0)
        }
    }
}
