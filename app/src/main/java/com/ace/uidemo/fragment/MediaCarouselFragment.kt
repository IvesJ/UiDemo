package com.ace.uidemo.fragment

import android.os.Bundle
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

    private var _binding: FragmentMediaCarouselBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MediaPagerAdapter
    private var autoScrollJob: Job? = null
    private var currentScrollPosition = -1
    private var isPaused = false
    private var pausedElapsedTime = 0L

    private var mediaItems: List<MediaItem> = emptyList()
    private var onCarouselCompleted: (() -> Unit)? = null
    private var onReachedEnd: (() -> Unit)? = null  // 新增：滑动到最后一页的回调
    private var onReachedStart: (() -> Unit)? = null  // 新增：滑动到第一页的回调

    companion object {
        private const val ARG_MEDIA_ITEMS = "media_items"

        fun newInstance(mediaItems: ArrayList<MediaItem>): MediaCarouselFragment {
            val fragment = MediaCarouselFragment()
            val args = Bundle()
            args.putSerializable(ARG_MEDIA_ITEMS, mediaItems)
            fragment.arguments = args
            return fragment
        }
    }

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
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            if (position == currentScrollPosition) {
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
                    }
                }
            }
        }

        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)

            if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                autoScrollJob?.cancel()
                currentScrollPosition = -1
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

        if (duration <= 0) {
            return
        }

        autoScrollJob = lifecycleScope.launch {
            val startTime = System.currentTimeMillis()

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
        if (currentPosition >= mediaItems.size - 1) {
            // 当前Tab轮播结束，通知父Activity
            onCarouselCompleted?.invoke()
        } else {
            val nextPosition = currentPosition + 1
            currentScrollPosition = -1
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
        if (position == currentScrollPosition) {
            moveToNext(position)
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
