package com.ace.uidemo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.TabPagerAdapter
import com.ace.uidemo.databinding.ActivityMainBinding
import com.ace.uidemo.fragment.MediaCarouselFragment
import com.ace.uidemo.viewmodel.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var tabAdapter: TabPagerAdapter? = null
    private var currentTabPosition = 0
    private val fragmentMap = mutableMapOf<Int, MediaCarouselFragment>()
    private var isAutoSwitching = false  // 标记是否是自动切换Tab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "========== MainActivity onCreate ==========")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 显示loading状态
        showLoading()

        // 启动并绑定Service
        viewModel.bindDownloadService()

        // 观察ViewModel状态
        observeViewModel()
    }

    /**
     * 观察ViewModel的状态变化
     */
    private fun observeViewModel() {
        // 观察是否显示内容
        lifecycleScope.launch {
            viewModel.showContent.collect { showContent ->
                if (showContent) {
                    showContent()
                } else {
                    showLoading()
                }
            }
        }

        // 观察已完成的Tab列表
        lifecycleScope.launch {
            viewModel.completedTabs.collect { completedTabs ->
                if (completedTabs.isNotEmpty()) {
                    Log.d(TAG, "收到已完成Tab列表更新: ${completedTabs.size} 个Tab")
                    updateTabViewPager(completedTabs)
                }
            }
        }

        // 观察重试对话框
        lifecycleScope.launch {
            viewModel.showRetryDialog.collect { tabTitle ->
                tabTitle?.let {
                    showRetryDialog(it)
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingLayout.visibility = View.VISIBLE
        binding.contentLayout.visibility = View.GONE
        Log.d(TAG, "显示Loading状态")
    }

    private fun showContent() {
        binding.loadingLayout.visibility = View.GONE
        binding.contentLayout.visibility = View.VISIBLE
        Log.d(TAG, "显示内容视图")
    }

    private fun updateTabViewPager(completedTabs: List<com.ace.uidemo.model.TabData>) {
        Log.d(TAG, "更新ViewPager，当前完成Tab数: ${completedTabs.size}")

        // 如果是首次设置，需要初始化ViewPager
        if (tabAdapter == null) {
            setupTabViewPager(completedTabs)
        } else {
            // 更新现有adapter
            tabAdapter = TabPagerAdapter(this, completedTabs) { position, fragment ->
                fragmentMap[position] = fragment

                // 设置轮播完成回调
                fragment.setOnCarouselCompletedListener {
                    moveToNextTab(position, completedTabs.size)
                }

                // 设置边界滑动回调
                fragment.setOnReachedEndListener {
                    if (position == completedTabs.size - 1) {
                        Log.d(TAG, "最后一个Tab向左滑动，切换到第一个Tab")
                        isAutoSwitching = true
                        binding.tabViewPager.setCurrentItem(0, true)
                        fragmentMap[0]?.post {
                            it.resetToFirstPage()
                        }
                    }
                }

                fragment.setOnReachedStartListener {
                    if (position == 0) {
                        Log.d(TAG, "第一个Tab向右滑动，切换到最后一个Tab")
                        val lastTabIndex = completedTabs.size - 1
                        isAutoSwitching = true
                        binding.tabViewPager.setCurrentItem(lastTabIndex, true)
                        fragmentMap[lastTabIndex]?.post {
                            it.resetToFirstPage()
                        }
                    }
                }
            }
            binding.tabViewPager.adapter = tabAdapter

            // 重新关联TabLayout
            TabLayoutMediator(binding.tabLayout, binding.tabViewPager) { tab, position ->
                tab.text = tabAdapter?.getTabTitle(position) ?: ""
            }.attach()
        }
    }

    private fun setupTabViewPager(completedTabs: List<com.ace.uidemo.model.TabData>) {
        if (completedTabs.isEmpty()) {
            Log.w(TAG, "completedTabs为空，跳过setupTabViewPager")
            return
        }

        Log.d(TAG, "初始化TabViewPager，${completedTabs.size} 个已完成Tab")
        tabAdapter = TabPagerAdapter(this, completedTabs) { position, fragment ->
            // Fragment创建并Resume时的回调
            Log.d(TAG, "Fragment创建回调: position=$position")
            fragmentMap[position] = fragment

            // 设置轮播完成回调
            fragment.setOnCarouselCompletedListener {
                Log.d(TAG, "收到Fragment轮播完成回调: position=$position")
                moveToNextTab(position, completedTabs.size)
            }

            // 设置边界滑动回调
            fragment.setOnReachedEndListener {
                Log.d(TAG, "收到Fragment边界滑动回调(向左): position=$position")
                // 如果是最后一个Tab，切换到第一个Tab
                if (position == completedTabs.size - 1) {
                    Log.d(TAG, "最后一个Tab向左滑动，切换到第一个Tab")
                    isAutoSwitching = true
                    binding.tabViewPager.setCurrentItem(0, true)
                    fragmentMap[0]?.post {
                        it.resetToFirstPage()
                    }
                }
            }

            fragment.setOnReachedStartListener {
                Log.d(TAG, "收到Fragment边界滑动回调(向右): position=$position")
                // 如果是第一个Tab，切换到最后一个Tab
                if (position == 0) {
                    Log.d(TAG, "第一个Tab向右滑动，切换到最后一个Tab")
                    val lastTabIndex = completedTabs.size - 1
                    isAutoSwitching = true
                    binding.tabViewPager.setCurrentItem(lastTabIndex, true)
                    fragmentMap[lastTabIndex]?.post {
                        it.resetToFirstPage()
                    }
                }
            }
        }
        binding.tabViewPager.adapter = tabAdapter

        // 关联TabLayout和ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.tabViewPager) { tab, position ->
            tab.text = tabAdapter?.getTabTitle(position) ?: ""
        }.attach()

        // 监听Tab切换
        binding.tabViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPosition = 0
            private var previousPositionOffset = 0f
            private var isUserDragging = false  // 标记用户是否正在拖拽

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels)

                // 只在用户手动拖拽时检测边界滑动
                if (!isUserDragging) {
                    previousPositionOffset = positionOffset
                    return
                }

                // 添加调试日志
                if (position == completedTabs.size - 1 || position == 0) {
                    Log.d(TAG, "Tab onPageScrolled: position=$position, offset=$positionOffset, prevOffset=$previousPositionOffset, isDragging=$isUserDragging")
                }

                // 检测边界滑动（只在用户手动拖拽时）
                if (position == completedTabs.size - 1) {
                    // 在最后一个Tab，检测向左滑动（尝试到下一个Tab但没有）
                    // offset 应该保持为 0，因为没有下一个Tab
                    if (positionOffset == 0f && previousPositionOffset == 0f && positionOffsetPixels < 0) {
                        // 检测到用户尝试向左滑动
                        Log.d(TAG, "检测到在最后一个Tab尝试向左滑动，切换到第一个Tab")
                        isAutoSwitching = true
                        isUserDragging = false  // 重置标记
                        binding.tabViewPager.post {
                            binding.tabViewPager.setCurrentItem(0, true)
                            fragmentMap[0]?.post {
                                it.resetToFirstPage()
                            }
                        }
                    }
                } else if (position == 0) {
                    // 在第一个Tab，检测向右滑动（尝试到上一个Tab但没有）
                    if (positionOffset == 0f && previousPositionOffset == 0f && positionOffsetPixels > 0) {
                        // 检测到用户尝试向右滑动
                        Log.d(TAG, "检测到在第一个Tab尝试向右滑动，切换到最后一个Tab")
                        val lastTabIndex = completedTabs.size - 1
                        isAutoSwitching = true
                        isUserDragging = false  // 重置标记
                        binding.tabViewPager.post {
                            binding.tabViewPager.setCurrentItem(lastTabIndex, true)
                            fragmentMap[lastTabIndex]?.post {
                                it.resetToFirstPage()
                            }
                        }
                    }
                }

                previousPositionOffset = positionOffset
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                // 标记用户是否正在拖拽
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        isUserDragging = true
                        Log.d(TAG, "Tab开始拖拽")
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        isUserDragging = false
                        val currentPos = binding.tabViewPager.currentItem
                        Log.d(TAG, "Tab滑动停止: currentPos=$currentPos, previousPos=$previousPosition, isAutoSwitching=$isAutoSwitching")
                        if (!isAutoSwitching && currentPos != previousPosition) {
                            // 用户手动切换了Tab（通过点击或滑动），重置到第一页
                            Log.d(TAG, "用户手动切换Tab，重置到第一页")
                            fragmentMap[currentPos]?.post {
                                it.resetToFirstPage()
                            }
                        }
                        previousPosition = currentPos
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                Log.d(TAG, "Tab页面选中: position=$position, isAutoSwitching=$isAutoSwitching")
                // 重置自动切换标记
                isAutoSwitching = false
                currentTabPosition = position
            }
        })
    }

    private fun MediaCarouselFragment.post(action: (MediaCarouselFragment) -> Unit) {
        view?.post {
            action(this)
        }
    }

    private fun moveToNextTab(currentTab: Int, totalTabs: Int) {
        Log.d(TAG, "moveToNextTab: currentTab=$currentTab, totalTabs=$totalTabs, currentTabPosition=$currentTabPosition")
        // 只有在自动轮播完成时才切换Tab
        // 用户手动滑动会由ViewPager2的嵌套滑动机制处理
        if (currentTab == currentTabPosition && currentTab >= totalTabs - 1) {
            // 所有Tab轮播结束
            Log.d(TAG, "所有Tab轮播结束，重新从第一个Tab开始")
            Toast.makeText(this, "所有内容已播放完毕", Toast.LENGTH_SHORT).show()
            // 可选：重新从第一个Tab开始
            isAutoSwitching = true
            binding.tabViewPager.setCurrentItem(0, true)

            // 重置第一个Tab到第一页
            fragmentMap[0]?.post {
                Log.d(TAG, "重置第一个Tab到第一页")
                it.resetToFirstPage()
            }
        } else if (currentTab == currentTabPosition) {
            // 切换到下一个Tab（自动切换）
            val nextTab = currentTab + 1
            Log.d(TAG, "切换到下一个Tab: $nextTab")
            isAutoSwitching = true
            binding.tabViewPager.setCurrentItem(nextTab, true)

            // 自动切换时，新Tab从第一页开始
            fragmentMap[nextTab]?.post {
                Log.d(TAG, "重置Tab $nextTab 到第一页")
                it.resetToFirstPage()
            }
        } else {
            Log.d(TAG, "currentTab($currentTab) != currentTabPosition($currentTabPosition)，忽略")
        }
    }

    private fun showRetryDialog(tabTitle: String) {
        AlertDialog.Builder(this)
            .setTitle("下载失败")
            .setMessage("Tab \"$tabTitle\" 的部分文件下载失败，是否重试？")
            .setPositiveButton("重试") { _, _ ->
                viewModel.retryFailedDownloads(tabTitle)
            }
            .setNegativeButton("取消") { _, _ ->
                viewModel.clearRetryDialog()
            }
            .setOnCancelListener {
                viewModel.clearRetryDialog()
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "========== MainActivity onDestroy ==========")

        // 清理Fragment映射
        fragmentMap.clear()
        Log.d(TAG, "Fragment映射已清理")
    }
}
