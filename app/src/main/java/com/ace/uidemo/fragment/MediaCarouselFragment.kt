package com.ace.uidemo.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.MediaPagerAdapter
import com.ace.uidemo.databinding.FragmentMediaCarouselBinding
import com.ace.uidemo.model.MediaItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaCarouselFragment : Fragment() {

    companion object {
        private const val TAG = "MediaCarouselFragment"
        private const val ARG_MEDIA_ITEMS = "media_items"

        fun newInstance(mediaItems: ArrayList<MediaItem>): MediaCarouselFragment {
            val fragment = MediaCarouselFragment()
            val args = Bundle()
            args.putSerializable(ARG_MEDIA_ITEMS, mediaItems)
            fragment.arguments = args
            return fragment
        }
    }

    private var _binding: FragmentMediaCarouselBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MediaPagerAdapter
    private var autoScrollJob: Job? = null
    private var currentScrollPosition = -1
    private var isPaused = false
    private var pausedElapsedTime = 0L

    private var mediaItems: List<MediaItem> = emptyList()
    private var onCarouselCompleted: (() -> Unit)? = null
    private var onReachedEnd: (() -> Unit)? = null  // 滑动到最后一页并继续向左滑的回调
    private var onReachedStart: (() -> Unit)? = null  // 滑动到第一页并继续向右滑的回调

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("UNCHECKED_CAST")
            mediaItems = it.getSerializable(ARG_MEDIA_ITEMS) as? List<MediaItem> ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaCarouselBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewPager()
        setupIndicator()
        setupPauseButton()

        currentScrollPosition = 0
        startAutoScroll(0)
    }

    private fun setupViewPager() {
        adapter = MediaPagerAdapter(
            mediaItems = mediaItems,
            onVideoReady = { position, duration ->
                onVideoReady(position, duration)
            },
            onVideoCompleted = { position ->
                onVideoCompleted(position)
            },
            onVideoClicked = {
                togglePause()
            }
        )

        binding.viewPager.adapter = adapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        // 禁用用户输入，改为由父ViewPager控制
        binding.viewPager.isUserInputEnabled = true
    }

    private fun setupIndicator() {
        binding.indicatorView.setupWithMediaItems(mediaItems.size)
        binding.indicatorView.setCurrentPosition(0)
        scrollToCurrentIndicator()
    }

    private fun setupPauseButton() {
        binding.pauseButton.setOnClickListener {
            togglePause()
        }
    }

    private fun togglePause() {
        isPaused = !isPaused

        if (isPaused) {
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_play)
            autoScrollJob?.cancel()

            val currentPosition = binding.viewPager.currentItem
            adapter.getVideoHolder(currentPosition)?.pause()
        } else {
            binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)

            val currentPosition = binding.viewPager.currentItem
            val item = mediaItems[currentPosition]

            if (item is MediaItem.Video) {
                adapter.getVideoHolder(currentPosition)?.resume()
            }

            if (currentPosition == currentScrollPosition) {
                resumeAutoScroll(currentPosition)
            }
        }
    }

    private fun scrollToCurrentIndicator() {
        binding.indicatorView.getCurrentIndicatorView()?.let { currentView ->
            binding.indicatorScrollView.post {
                val scrollViewWidth = binding.indicatorScrollView.width
                val scrollX = binding.indicatorScrollView.scrollX
                val viewLeft = currentView.left
                val viewRight = currentView.right

                val isVisible = viewLeft >= scrollX && viewRight <= scrollX + scrollViewWidth

                if (!isVisible) {
                    val viewWidth = currentView.width
                    val targetScrollX = viewLeft - (scrollViewWidth / 2) + (viewWidth / 2)

                    binding.indicatorScrollView.smoothScrollTo(targetScrollX, 0)
                }
            }
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        private var previousPosition = -1
        private var previousPositionOffset = 0f
        private var isUserDragging = false  // 标记用户是否正在拖拽

        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            Log.d(TAG, "onPageSelected: position=$position, currentScrollPosition=$currentScrollPosition")

            if (position == currentScrollPosition) {
                Log.d(TAG, "位置未变化，跳过")
                return
            }

            autoScrollJob?.cancel()
            currentScrollPosition = position

            if (isPaused) {
                isPaused = false
                binding.pauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }

            binding.indicatorView.setCurrentPosition(position)
            scrollToCurrentIndicator()

            val item = mediaItems[position]
            when (item) {
                is MediaItem.Image -> {
                    startAutoScroll(position)
                }
                is MediaItem.Video -> {
                    if (item.duration > 0) {
                        startAutoScroll(position)
                    } else {
                        Log.d(TAG, "视频duration尚未准备好，等待onVideoReady")
                    }
                    // 如果duration还没准备好，等待onVideoReady回调
                }
            }
        }

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            super.onPageScrolled(position, positionOffset, positionOffsetPixels)

            // 只在用户手动拖拽时检测边界滑动
            if (!isUserDragging) {
                previousPositionOffset = positionOffset
                return
            }

            // 添加调试日志
            if (position == mediaItems.size - 1 || position == 0) {
                Log.d(TAG, "onPageScrolled: position=$position, offset=$positionOffset, prevOffset=$previousPositionOffset, pixels=$positionOffsetPixels")
            }

            // 检测边界滑动
            if (position == mediaItems.size - 1) {
                // 在最后一页，检测向左滑动
                if (positionOffset == 0f && previousPositionOffset == 0f && positionOffsetPixels < 0) {
                    Log.d(TAG, "检测到在最后一页尝试向左滑动")
                    isUserDragging = false  // 重置标记
                    onReachedEnd?.invoke()
                }
            } else if (position == 0) {
                // 在第一页，检测向右滑动
                if (positionOffset == 0f && previousPositionOffset == 0f && positionOffsetPixels > 0) {
                    Log.d(TAG, "检测到在第一页尝试向右滑动")
                    isUserDragging = false  // 重置标记
                    onReachedStart?.invoke()
                }
            }

            previousPosition = position
            previousPositionOffset = positionOffset
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)

            val stateName = when (state) {
                ViewPager2.SCROLL_STATE_IDLE -> "IDLE"
                ViewPager2.SCROLL_STATE_DRAGGING -> "DRAGGING"
                ViewPager2.SCROLL_STATE_SETTLING -> "SETTLING"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "onPageScrollStateChanged: state=$stateName")

            if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                isUserDragging = true
                autoScrollJob?.cancel()
                // 不要设置为-1，保持当前position，以便后续正确处理
            } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                isUserDragging = false
                // 滑动停止后，确保自动滚动正常运行
                val actualPosition = binding.viewPager.currentItem
                Log.d(TAG, "滑动停止: actualPosition=$actualPosition, currentScrollPosition=$currentScrollPosition")

                if (actualPosition != currentScrollPosition) {
                    Log.d(TAG, "位置不一致，重新同步并启动自动滚动")
                    currentScrollPosition = actualPosition
                }

                // 无论位置是否改变，都要确保自动滚动正在运行
                // 因为在DRAGGING时已经取消了autoScrollJob
                if (!isPaused && (autoScrollJob == null || !autoScrollJob!!.isActive)) {
                    Log.d(TAG, "自动滚动未运行，重新启动")
                    val item = mediaItems[actualPosition]
                    when (item) {
                        is MediaItem.Image -> {
                            startAutoScroll(actualPosition)
                        }
                        is MediaItem.Video -> {
                            if (item.duration > 0) {
                                startAutoScroll(actualPosition)
                            } else {
                                Log.d(TAG, "视频duration尚未准备好，等待onVideoReady")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startAutoScroll(position: Int) {
        autoScrollJob?.cancel()
        pausedElapsedTime = 0

        val mediaItem = mediaItems[position]
        val duration = when (mediaItem) {
            is MediaItem.Image -> 5000L
            is MediaItem.Video -> mediaItem.duration
        }

        Log.d(TAG, "startAutoScroll: position=$position, mediaType=${mediaItem::class.simpleName}, duration=$duration")

        if (duration <= 0) {
            Log.w(TAG, "duration <= 0，不启动自动滚动")
            return
        }

        autoScrollJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()

            while (isActive && !isPaused) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = ((elapsed.toFloat() / duration) * 100).coerceIn(0f, 100f)

                binding.indicatorView.updateProgress(position, progress.toInt())

                if (elapsed >= duration) {
                    Log.d(TAG, "自动滚动完成: position=$position")
                    moveToNext(position)
                    break
                }

                pausedElapsedTime = elapsed

                delay(16L)
            }
        }
    }

    private fun resumeAutoScroll(position: Int) {
        autoScrollJob?.cancel()

        val mediaItem = mediaItems[position]
        val duration = when (mediaItem) {
            is MediaItem.Image -> 5000L
            is MediaItem.Video -> mediaItem.duration
        }

        if (duration <= 0) {
            return
        }

        autoScrollJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis() - pausedElapsedTime

            while (isActive && !isPaused) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = ((elapsed.toFloat() / duration) * 100).coerceIn(0f, 100f)

                binding.indicatorView.updateProgress(position, progress.toInt())

                if (elapsed >= duration) {
                    moveToNext(position)
                    break
                }

                pausedElapsedTime = elapsed

                delay(16L)
            }
        }
    }

    private fun moveToNext(currentPosition: Int) {
        Log.d(TAG, "moveToNext called: currentPosition=$currentPosition, totalItems=${mediaItems.size}")
        if (currentPosition >= mediaItems.size - 1) {
            // 当前Tab轮播结束，通知父Activity
            Log.d(TAG, "轮播完成，触发onCarouselCompleted回调")
            onCarouselCompleted?.invoke()
        } else {
            val nextPosition = currentPosition + 1
            Log.d(TAG, "切换到下一页: $nextPosition")
            // 不要在这里设置currentScrollPosition，让onPageSelected回调来设置
            // 这样可以确保onPageSelected中的逻辑正常执行
            binding.viewPager.setCurrentItem(nextPosition, true)
        }
    }

    private fun onVideoReady(position: Int, duration: Long) {
        (mediaItems[position] as? MediaItem.Video)?.duration = duration

        if (position == binding.viewPager.currentItem && position == currentScrollPosition && autoScrollJob?.isActive != true) {
            startAutoScroll(position)
        }
    }

    private fun onVideoCompleted(position: Int) {
        // 确保只有当前显示的视频完成时才触发
        val currentItem = binding.viewPager.currentItem
        Log.d(TAG, "onVideoCompleted: position=$position, currentItem=$currentItem, currentScrollPosition=$currentScrollPosition")
        if (position == currentItem) {
            Log.d(TAG, "视频播放完成，触发moveToNext")
            moveToNext(position)
        } else {
            Log.d(TAG, "视频位置不匹配，忽略完成事件")
        }
    }

    fun setOnCarouselCompletedListener(listener: (() -> Unit)?) {
        onCarouselCompleted = listener
    }

    fun setOnReachedEndListener(listener: (() -> Unit)?) {
        onReachedEnd = listener
    }

    fun setOnReachedStartListener(listener: (() -> Unit)?) {
        onReachedStart = listener
    }

    fun resetToFirstPage() {
        binding.viewPager.setCurrentItem(0, false)
        currentScrollPosition = 0
        binding.indicatorView.setCurrentPosition(0)
        scrollToCurrentIndicator()

        // 重新开始轮播
        if (!isPaused) {
            startAutoScroll(0)
        }
    }

    fun isAtLastPage(): Boolean {
        return binding.viewPager.currentItem >= mediaItems.size - 1
    }

    fun isAtFirstPage(): Boolean {
        return binding.viewPager.currentItem == 0
    }

    fun pauseCarousel() {
        if (!isPaused) {
            togglePause()
        }
    }

    fun resumeCarousel() {
        if (isPaused) {
            togglePause()
        }
    }

    override fun onPause() {
        super.onPause()
        autoScrollJob?.cancel()
        adapter.getVideoHolder(binding.viewPager.currentItem)?.pause()
    }

    override fun onResume() {
        super.onResume()
        val currentPosition = binding.viewPager.currentItem
        adapter.getVideoHolder(currentPosition)?.resume()

        currentScrollPosition = currentPosition
        val item = mediaItems[currentPosition]
        if (item is MediaItem.Image || (item is MediaItem.Video && item.duration > 0)) {
            if (!isPaused) {
                startAutoScroll(currentPosition)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        autoScrollJob?.cancel()
        adapter.releaseAllVideos()
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        _binding = null
    }
}
