package com.ace.uidemo.adapter

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.databinding.ItemSubTabContentBinding
import com.ace.uidemo.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 子Tab切换的适配器，每个item包含一个子tab的所有媒体内容
 */
class SubTabSwitchAdapter(
    private val subTabs: List<TabData>,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onSubTabCompleted: (Int) -> Unit,
    private val onAllSubTabsCompleted: () -> Unit,
    private val onPauseStateChanged: (Boolean) -> Unit,
    private val onSwitchToPrevious: (Int) -> Unit // 切换到上一个子tab的回调
) : RecyclerView.Adapter<SubTabSwitchAdapter.SubTabViewHolder>() {

    companion object {
        private const val TAG = "SubTabSwitchAdapter"
    }

    private val viewHolders = mutableMapOf<Int, SubTabViewHolder>()
    private var currentPosition = 0
    private var isPaused = false

    // 使用新的状态管理
    private val state = NestedViewPagerState()

    init {
        // 初始化状态
        state.addStateChangeListener { newState ->
            Log.d(TAG, "State changed: $newState")
            currentPosition = newState.currentSubTabIndex
            isPaused = newState.isPaused
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubTabViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val binding = ItemSubTabContentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubTabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubTabViewHolder, position: Int) {
        Log.d(TAG, "onBindViewHolder: position=$position, subTab=${subTabs[position].title}")
        viewHolders[position] = holder

        holder.bind(
            subTab = subTabs[position],
            position = position,
            lifecycleScope = lifecycleScope,
            onCompleted = {
                Log.d(TAG, "SubTab completed callback triggered from ViewHolder at position $position")
                // 使用当前adapter追踪的position，而不是闭包中的position或ViewHolder存储的position
                Log.d(TAG, "Using current adapter position: $currentPosition")
                handleSubTabCompleted(currentPosition)
            },
            onPauseStateChanged = onPauseStateChanged,
            getCurrentPosition = { currentPosition },
            onSwitchToPrevious = {
                Log.d(TAG, "Switch to previous callback triggered from ViewHolder at position $position")
                onSwitchToPrevious(currentPosition)
            }
        )
    }

    /**
     * 处理单个子tab完成事件
     */
    private fun handleSubTabCompleted(position: Int) {
        Log.d(TAG, "handleSubTabCompleted: position=$position, totalSubTabs=${subTabs.size}, currentPosition=$currentPosition")

        // 确保position是当前活动的subtab位置
        if (position != currentPosition) {
            Log.w(TAG, "Position mismatch: expected=$currentPosition, received=$position, ignoring")
            return
        }

        if (position < subTabs.size - 1) {
            // 还有下一个子tab，自动切换
            Log.d(TAG, "Switching to next subtab: ${position + 1}")
            onSubTabCompleted(position)
        } else {
            // 所有子tab都完成了
            Log.d(TAG, "All subtabs completed")
            onAllSubTabsCompleted()
        }
    }

    override fun getItemCount(): Int = subTabs.size

    override fun onViewRecycled(holder: SubTabViewHolder) {
        super.onViewRecycled(holder)
        val position = holder.adapterPosition
        if (position != RecyclerView.NO_POSITION) {
            Log.d(TAG, "onViewRecycled: position=$position")
            holder.release()
            viewHolders.remove(position)
        }
    }

    /**
     * 切换到指定的子Tab
     */
    fun switchToSubTab(position: Int) {
        Log.d(TAG, "switchToSubTab: from=$currentPosition to=$position")
        if (position < 0 || position >= subTabs.size) {
            Log.w(TAG, "Invalid position: $position, subTabsSize=${subTabs.size}")
            return
        }

        if (position == currentPosition) {
            Log.d(TAG, "Already at position $position, no switch needed")
            return
        }

        // 暂停之前的子Tab
        viewHolders[currentPosition]?.pause()

        currentPosition = position

        // 如果不是暂停状态，开始新子Tab (暂时注释掉自动轮播)
        // if (!isPaused) {
        //     viewHolders[currentPosition]?.resume()
        // }

        Log.d(TAG, "Switched to subtab $currentPosition successfully")
    }

    /**
     * 暂停所有子Tab
     */
    fun pauseAll() {
        Log.d(TAG, "pauseAll")
        isPaused = true
        viewHolders.values.forEach { it.pause() }
    }

    /**
     * 恢复当前子Tab
     */
    fun resumeCurrent() {
        Log.d(TAG, "resumeCurrent: currentPosition=$currentPosition")
        isPaused = false
        // 暂时注释掉自动轮播
        // viewHolders[currentPosition]?.resume()
    }

    /**
     * 释放所有资源
     */
    fun releaseAll() {
        Log.d(TAG, "releaseAll")
        viewHolders.values.forEach { it.release() }
        viewHolders.clear()
    }

    /**
     * 获取当前媒体项
     */
    fun getCurrentMediaItem(): MediaItem? {
        return viewHolders[currentPosition]?.getCurrentMediaItem()
    }

    /**
     * 重置currentPosition到0
     * 在resetToFirst时调用，确保位置状态同步
     */
    fun resetCurrentPosition() {
        Log.d(TAG, "resetCurrentPosition: from=$currentPosition to=0")
        currentPosition = 0
    }

    /**
     * 设置currentPosition
     * 在子tab手动切换时调用，确保位置状态同步
     */
    fun setCurrentPosition(position: Int) {
        Log.d(TAG, "setCurrentPosition: from=$currentPosition to=$position")
        currentPosition = position
    }

    /**
     * 是否暂停状态
     */
    fun isPaused(): Boolean = isPaused

    /**
     * 子Tab内容的ViewHolder
     */
    class SubTabViewHolder(
        private val binding: ItemSubTabContentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        companion object {
            private const val TAG = "SubTabViewHolder"
        }

        private var mediaPagerAdapter: MediaPagerAdapter? = null
        private var currentMediaItems: List<MediaItem> = emptyList()
        private var currentMediaIndex = 0
        private var isPaused = false
        private var pausedElapsedTime = 0L
        private var autoScrollJob: Job? = null
        private var onCompleted: (() -> Unit)? = null
        private var onSwitchToPrevious: (() -> Unit)? = null // 切换到上一个子tab的回调
        private var lifecycleScope: LifecycleCoroutineScope? = null
        private var myPosition = -1 // 存储当前ViewHolder的真实位置
        private var lastCompletionTime = 0L // 防止重复触发完成事件

        fun bind(
            subTab: TabData,
            position: Int,
            lifecycleScope: LifecycleCoroutineScope,
            onCompleted: () -> Unit,
            onPauseStateChanged: (Boolean) -> Unit,
            getCurrentPosition: () -> Int,
            onSwitchToPrevious: () -> Unit // 新增：切换到上一个子tab的回调
        ) {
            Log.d(TAG, "bind: subTab=${subTab.title}, mediaCount=${subTab.mediaItems.size}, position=$position")
            this.lifecycleScope = lifecycleScope
            this.onCompleted = onCompleted
            this.onSwitchToPrevious = onSwitchToPrevious // 保存回调
            this.myPosition = position // 存储真实位置
            currentMediaItems = subTab.mediaItems
            currentMediaIndex = 0
            isPaused = false
            pausedElapsedTime = 0L

            setupMediaViewPager(onPauseStateChanged, getCurrentPosition)
            setupIndicator()
        }

        private fun setupMediaViewPager(onPauseStateChanged: (Boolean) -> Unit, getCurrentPosition: () -> Int) {
            Log.d(TAG, "setupMediaViewPager")
            binding.mediaViewPager.apply {
                offscreenPageLimit = 1

                // 重要：媒体内容的ViewPager2应该消费边界事件，不传递给父级
                consumeBoundaryEvents = true

                // 设置媒体边界滑动回调 - 只处理子tab切换
                onBoundaryReached = { isLeft ->
                    val currentAdapterPosition = getCurrentPosition()
                    val currentTime = System.currentTimeMillis()
                    Log.d(TAG, "Media boundary reached: isLeft=$isLeft, currentIndex=$currentMediaIndex, totalMedia=${currentMediaItems.size}, myPosition=$myPosition, currentAdapterPosition=$currentAdapterPosition")

                    // 只有在没有被防抖且是当前活动ViewHolder时才处理
                    if (currentTime - lastCompletionTime >= NestedPagerConfig.DEBOUNCE_TIME_MS && myPosition == currentAdapterPosition) {
                        // 媒体滑动到边界时的处理
                        if (!isLeft && currentMediaIndex == currentMediaItems.size - 1) {
                            // 向左滑动到最后一个媒体，切换到下一个子tab
                            Log.d(TAG, "Last media reached, request switch to next subtab from position $myPosition")
                            lastCompletionTime = currentTime
                            onCompleted?.invoke()
                        } else if (isLeft && currentMediaIndex == 0) {
                            // 向右滑动到第一个媒体，切换到上一个子tab
                            Log.d(TAG, "First media reached, request switch to previous subtab from position $myPosition")
                            lastCompletionTime = currentTime
                            onSwitchToPrevious?.invoke()
                        }
                        // 注意：媒体内容边界只处理子tab切换，父tab切换由子tab边界处理
                    } else {
                        if (currentTime - lastCompletionTime < NestedPagerConfig.DEBOUNCE_TIME_MS) {
                            Log.d(TAG, "Debouncing: ignoring boundary event (too soon after last completion)")
                        } else {
                            Log.d(TAG, "Boundary reached from non-active ViewHolder (myPosition=$myPosition, currentAdapterPosition=$currentAdapterPosition), ignoring")
                        }
                    }
                }

                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        Log.d(TAG, "Media page selected: position=$position")
                        currentMediaIndex = position
                        updateUI(position)

                        // 暂时注释掉自动轮播
                        // if (!isPaused) {
                        //     startAutoScroll(position)
                        // }
                    }
                })
            }

            // 设置媒体适配器
            mediaPagerAdapter = MediaPagerAdapter(
                mediaItems = currentMediaItems,
                onVideoReady = { position, duration ->
                    Log.d(TAG, "Video ready: position=$position, duration=$duration")
                    // 暂时注释掉自动播放
                    // if (position == currentMediaIndex && !isPaused) {
                    //     mediaPagerAdapter?.resumeVideo(position)
                    //     startAutoScroll(position)
                    // }
                },
                onVideoCompleted = { position ->
                    Log.d(TAG, "Video completed: position=$position")
                    // 暂时注释掉自动切换
                    // if (position == currentMediaIndex) {
                    //     moveToNextMedia()
                    // }
                },
                onVideoClicked = {
                    Log.d(TAG, "Video clicked")
                    togglePause()
                    onPauseStateChanged(isPaused)
                }
            )

            binding.mediaViewPager.adapter = mediaPagerAdapter
            binding.mediaViewPager.setCurrentItem(0, false)

            Log.d(TAG, "Media adapter set with ${currentMediaItems.size} items")

            // 暂时注释掉自动轮播
            // if (!isPaused) {
            //     startAutoScroll(0)
            // }
        }

        private fun setupIndicator() {
            Log.d(TAG, "setupIndicator: mediaCount=${currentMediaItems.size}")
            binding.indicatorView.setupWithMediaItems(currentMediaItems.size)
            binding.indicatorView.setCurrentPosition(0)
        }

        private fun updateUI(position: Int) {
            Log.d(TAG, "updateUI: position=$position")
            stopAutoScroll()
            mediaPagerAdapter?.pauseAllVideos()
            mediaPagerAdapter?.resetAllVideos()
            binding.indicatorView.setCurrentPosition(position)

            // 暂时注释掉自动播放
            // if (!isPaused) {
            //     val currentMedia = currentMediaItems.getOrNull(position)
            //     if (currentMedia is MediaItem.Video) {
            //         mediaPagerAdapter?.resumeVideo(position)
            //     }
            // }
        }

        private fun startAutoScroll(position: Int) {
            // 暂时注释掉自动滚动功能
            Log.d(TAG, "startAutoScroll: position=$position (DISABLED)")
            return

            /*
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

            autoScrollJob = lifecycleScope?.launch {
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
            */
        }

        private fun stopAutoScroll() {
            Log.d(TAG, "stopAutoScroll")
            autoScrollJob?.cancel()
            autoScrollJob = null
        }

        private fun moveToNextMedia() {
            Log.d(TAG, "moveToNextMedia: currentIndex=$currentMediaIndex, totalMedia=${currentMediaItems.size}")
            if (currentMediaIndex < currentMediaItems.size - 1) {
                // 还有下一个媒体，切换到下一个
                binding.mediaViewPager.setCurrentItem(currentMediaIndex + 1, true)
            } else {
                // 当前子Tab的所有媒体播放完成，通过自动轮播切换子tab
                Log.d(TAG, "All media completed, requesting subtab switch")
                onCompleted?.invoke()
            }
        }

        fun togglePause() {
            Log.d(TAG, "togglePause: isPaused=$isPaused")
            isPaused = !isPaused

            if (isPaused) {
                pause()
            } else {
                resume()
            }
        }

        fun pause() {
            Log.d(TAG, "pause")
            if (!isPaused) {
                isPaused = true
            }
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
                Log.d(TAG, "pause: savedProgress=$pausedElapsedTime")
            }
        }

        fun resume() {
            Log.d(TAG, "resume")
            if (isPaused) {
                isPaused = false
            }
            val mediaItem = currentMediaItems.getOrNull(currentMediaIndex)
            if (mediaItem is MediaItem.Video) {
                mediaPagerAdapter?.resumeVideo(currentMediaIndex)
            }
            // 暂时注释掉自动轮播
            // startAutoScroll(currentMediaIndex)
        }

        fun release() {
            Log.d(TAG, "release")
            stopAutoScroll()
            mediaPagerAdapter?.releaseAllVideos()
        }

        fun getCurrentMediaItem(): MediaItem? {
            return currentMediaItems.getOrNull(currentMediaIndex)
        }

        /**
         * 获取当前ViewHolder的真实位置
         */
        fun getRealPosition(): Int {
            return myPosition
        }
    }
}