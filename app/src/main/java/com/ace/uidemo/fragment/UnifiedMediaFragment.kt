package com.ace.uidemo.fragment

import android.os.Bundle
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

        setupControls()
        setupTabViewPager()
    }

    /**
     * 设置外层ViewPager2和TabLayout
     */
    private fun setupTabViewPager() {
        if (tabs.isEmpty()) return

        // 创建TabPagerAdapter
        tabPagerAdapter = TabPagerAdapter(
            tabs = tabs,
            lifecycleScope = lifecycleScope,
            onTabCompleted = {
                // Tab轮播完成，切换到下一个Tab（使用currentTabPosition更可靠）
                val nextPosition = (currentTabPosition + 1) % tabs.size
                binding.tabViewPager.setCurrentItem(nextPosition, true)
            },
            onPauseStateChanged = { isPaused ->
                // 暂停状态改变，更新按钮
                updatePauseButton()
            }
        )
        binding.tabViewPager.adapter = tabPagerAdapter
        binding.tabViewPager.offscreenPageLimit = 1

        // 使用TabLayoutMediator自动同步TabLayout和ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.tabViewPager) { tab, position ->
            tab.text = tabs[position].title
        }.attach()

        // 监听Tab切换
        binding.tabViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                onTabChanged(position)
            }
        })
    }

    /**
     * Tab切换时的处理
     */
    private fun onTabChanged(newPosition: Int) {
        if (newPosition == currentTabPosition) return

        // 暂停前一个Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.pause()

        currentTabPosition = newPosition

        // 重置新Tab到第一个媒体项并开始播放
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.resetToFirst()

        // 更新控制按钮状态
        updatePauseButton()
        updateClickableButton()
    }

    /**
     * 设置控制按钮
     */
    private fun setupControls() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            activity?.onBackPressed()
        }

        // 暂停/播放按钮
        binding.pauseButton.setOnClickListener {
            togglePause()
        }

        // 下载按钮（如果需要）
        binding.downloadButton.setOnClickListener {
            // TODO: 实现下载功能
        }

        // 可点击按钮
        updateClickableButton()
    }

    /**
     * 切换暂停/播放状态
     */
    private fun togglePause() {
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

        binding.clickableButton.visibility = if (isClickable) View.VISIBLE else View.GONE

        binding.clickableButton.setOnClickListener {
            // TODO: 处理可点击媒体项的点击事件
        }
    }

    override fun onPause() {
        super.onPause()
        // 暂停当前Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.pause()
    }

    override fun onResume() {
        super.onResume()
        // 恢复当前Tab的轮播
        tabPagerAdapter?.getViewHolder(currentTabPosition)?.resume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tabPagerAdapter?.releaseAll()
        _binding = null
    }
}
