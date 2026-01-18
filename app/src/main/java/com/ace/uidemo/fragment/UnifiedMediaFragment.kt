package com.ace.uidemo.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.R
import com.ace.uidemo.adapter.TabPagerAdapter
import com.ace.uidemo.databinding.FragmentUnifiedMediaBinding
import com.ace.uidemo.model.TabData
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * 统一的媒体展示Fragment（嵌套ViewPager2方案）
 * 外层ViewPager2用于Tab切换，内层ViewPager2用于媒体轮播
 */
class UnifiedMediaFragment : Fragment() {

    private var _binding: FragmentUnifiedMediaBinding? = null
    private val binding get() = _binding!!

    private var tabs: List<TabData> = emptyList()
    private var tabPagerAdapter: TabPagerAdapter? = null
    private var currentTabPosition = 0

    companion object {
        private const val TAG = "UnifiedMediaFragment"
        private const val ARG_TABS = "tabs"

        fun newInstance(tabs: ArrayList<TabData>): UnifiedMediaFragment {
            val fragment = UnifiedMediaFragment()
            val args = Bundle()
            args.putSerializable(ARG_TABS, tabs)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("UNCHECKED_CAST")
        tabs = (arguments?.getSerializable(ARG_TABS) as? ArrayList<TabData>) ?: emptyList()
        Log.d(TAG, "onCreate: tabs count=${tabs.size}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUnifiedMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")

        setupControls()
        setupTabViewPager()
    }

    /**
     * 设置外层ViewPager2和TabLayout
     */
    private fun setupTabViewPager() {
        if (tabs.isEmpty()) return
        Log.d(TAG, "setupTabViewPager")

        // 创建TabPagerAdapter
        tabPagerAdapter = TabPagerAdapter(
            tabs = tabs,
            lifecycleScope = lifecycleScope,
            onTabCompleted = {
                Log.d(TAG, "onTabCompleted: currentTab=$currentTabPosition")
                // Tab轮播完成，切换到下一个Tab（使用currentTabPosition更可靠）
                val nextPosition = (currentTabPosition + 1) % tabs.size
                Log.d(TAG, "Auto switching to next tab: $nextPosition")
                binding.tabViewPager.setCurrentItem(nextPosition, true)
            },
            onPauseStateChanged = { isPaused ->
                Log.d(TAG, "onPauseStateChanged: isPaused=$isPaused")
                // 暂停状态改变，更新按钮
                updatePauseButton()
            },
            onBoundaryReached = { isLeft ->
                Log.d(TAG, "onBoundaryReached: isLeft=$isLeft")
                // 处理子tab边界滑动事件
                handleBoundarySwipe(isLeft)
            }
        )
        binding.tabViewPager.adapter = tabPagerAdapter
        binding.tabViewPager.offscreenPageLimit = 1

        // 使用TabLayoutMediator自动同步TabLayout和ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.tabViewPager) { tab, position ->
            tab.text = tabs[position].title
            Log.d(TAG, "TabLayoutMediator: position=$position, title=${tabs[position].title}")
        }.attach()

        // 监听Tab切换
        binding.tabViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                Log.d(TAG, "Main tab page selected: position=$position")
                onTabChanged(position)
            }
        })
    }

    /**
     * 处理子tab边界滑动事件
     */
    private fun handleBoundarySwipe(isLeft: Boolean) {
        Log.d(TAG, "handleBoundarySwipe: isLeft=$isLeft, currentTab=$currentTabPosition, totalTabs=${tabs.size}")
        if (isLeft) {
            // 向右滑动，切换到上一个父tab
            val prevPosition = if (currentTabPosition > 0) {
                currentTabPosition - 1
            } else {
                tabs.size - 1 // 循环到最后一个tab
            }
            Log.d(TAG, "Switching to previous tab: $prevPosition")
            binding.tabViewPager.setCurrentItem(prevPosition, true)
        } else {
            // 向左滑动，切换到下一个父tab
            val nextPosition = (currentTabPosition + 1) % tabs.size
            Log.d(TAG, "Switching to next tab: $nextPosition")
            binding.tabViewPager.setCurrentItem(nextPosition, true)
        }
    }

    /**
     * Tab切换时的处理
     */
    private fun onTabChanged(newPosition: Int) {
        Log.d(TAG, "onTabChanged: from=$currentTabPosition to=$newPosition")
        if (newPosition == currentTabPosition) return

        // 暂停前一个Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.pause()
        Log.d(TAG, "Paused tab: $currentTabPosition")

        currentTabPosition = newPosition

        // 重置新Tab到第一个媒体项并开始播放
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.resetToFirst()
        Log.d(TAG, "Reset and started tab: $currentTabPosition")

        // 更新控制按钮状态
        updatePauseButton()
        updateClickableButton()
    }

    /**
     * 设置控制按钮
     */
    private fun setupControls() {
        Log.d(TAG, "setupControls")
        // 返回按钮
        binding.backButton.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            activity?.onBackPressed()
        }

        // 暂停/播放按钮
        binding.pauseButton.setOnClickListener {
            Log.d(TAG, "Pause button clicked")
            togglePause()
        }

        // 下载按钮（如果需要）
        binding.downloadButton.setOnClickListener {
            Log.d(TAG, "Download button clicked")
            // TODO: 实现下载功能
        }

        // 可点击按钮
        updateClickableButton()
    }

    /**
     * 切换暂停/播放状态
     */
    private fun togglePause() {
        Log.d(TAG, "togglePause: currentTab=$currentTabPosition")
        val currentHolder = tabPagerAdapter?.getViewHolder(currentTabPosition)
        currentHolder?.togglePause()
        updatePauseButton()
    }

    /**
     * 更新暂停按钮图标
     */
    private fun updatePauseButton() {
        val currentHolder = tabPagerAdapter?.getViewHolder(currentTabPosition)
        val isPaused = currentHolder?.isPaused() ?: false
        Log.d(TAG, "updatePauseButton: isPaused=$isPaused")
        binding.pauseButton.setImageResource(
            if (isPaused) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
    }

    /**
     * 更新可点击按钮的显示状态
     */
    private fun updateClickableButton() {
        val currentHolder = tabPagerAdapter?.getViewHolder(currentTabPosition)
        val currentMediaItem = currentHolder?.getCurrentMediaItem()

        // 假设图片可点击，视频不可点击
        val isClickable = currentMediaItem is com.ace.uidemo.model.MediaItem.Image
        Log.d(TAG, "updateClickableButton: isClickable=$isClickable")

        binding.clickableButton.visibility = if (isClickable) View.VISIBLE else View.GONE

        binding.clickableButton.setOnClickListener {
            Log.d(TAG, "Clickable button clicked")
            // TODO: 处理可点击媒体项的点击事件
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // 暂停当前Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.pause()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        // 恢复当前Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.resume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "onDestroyView")
        tabPagerAdapter?.releaseAll()
        _binding = null
    }
}
