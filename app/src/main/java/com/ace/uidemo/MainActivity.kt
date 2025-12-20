package com.ace.uidemo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.ace.uidemo.adapter.TabPagerAdapter
import com.ace.uidemo.databinding.ActivityMainBinding
import com.ace.uidemo.fragment.MediaCarouselFragment
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var tabAdapter: TabPagerAdapter
    private var currentTabPosition = 0
    private val fragmentMap = mutableMapOf<Int, MediaCarouselFragment>()
    private var isAutoSwitching = false  // 标记是否是自动切换Tab

    private val tabs = listOf(
        TabData(
            id = "tab1",
            title = "推荐",
            mediaItems = listOf(
                MediaItem.Image("1", "https://picsum.photos/800/1200?random=1"),
                MediaItem.Video("2", "https://vod.pipi.cn/fec9203cvodtransbj1251246104/bb68c7515285890807928280731/v.f42906.mp4"),
                MediaItem.Image("3", "https://picsum.photos/800/1200?random=3")
            )
        ),
        TabData(
            id = "tab2",
            title = "热门",
            mediaItems = listOf(
                MediaItem.Image("4", "https://picsum.photos/800/1200?random=4"),
                MediaItem.Image("5", "https://picsum.photos/800/1200?random=5"),
                MediaItem.Video("6", "https://media.w3.org/2010/05/sintel/trailer.mp4")
            )
        ),
        TabData(
            id = "tab3",
            title = "关注",
            mediaItems = listOf(
                MediaItem.Image("7", "https://picsum.photos/800/1200?random=7"),
                MediaItem.Image("8", "https://picsum.photos/800/1200?random=8"),
                MediaItem.Image("9", "https://picsum.photos/800/1200?random=9"),
                MediaItem.Image("10", "https://picsum.photos/800/1200?random=10")
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabViewPager()
        setupCarouselCompletedListeners()
    }

    private fun setupTabViewPager() {
        tabAdapter = TabPagerAdapter(this, tabs) { position, fragment ->
            // Fragment创建并Resume时的回调
            fragmentMap[position] = fragment
            fragment.setOnCarouselCompletedListener {
                moveToNextTab(position)
            }
        }
        binding.tabViewPager.adapter = tabAdapter

        // 关联TabLayout和ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.tabViewPager) { tab, position ->
            tab.text = tabAdapter.getTabTitle(position)
        }.attach()

        // 监听Tab切换
        binding.tabViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPosition = 0

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                // 当滑动停止时，检查是否需要重置
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val currentPos = binding.tabViewPager.currentItem
                    if (!isAutoSwitching && currentPos != previousPosition) {
                        // 用户手动切换了Tab（通过点击或滑动），重置到第一页
                        fragmentMap[currentPos]?.post {
                            it.resetToFirstPage()
                        }
                    }
                    previousPosition = currentPos
                }
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

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

    private fun setupCarouselCompletedListeners() {
        // 此方法现在不需要了，在TabPagerAdapter中处理
    }

    private fun getFragmentAt(position: Int): MediaCarouselFragment? {
        return fragmentMap[position]
    }

    private fun moveToNextTab(currentTab: Int) {
        // 只有在自动轮播完成时才切换Tab
        // 用户手动滑动会由ViewPager2的嵌套滑动机制处理
        if (currentTab == currentTabPosition && currentTab >= tabs.size - 1) {
            // 所有Tab轮播结束
            Toast.makeText(this, "所有内容已播放完毕", Toast.LENGTH_SHORT).show()
            // 可选：重新从第一个Tab开始
            isAutoSwitching = true
            binding.tabViewPager.setCurrentItem(0, true)

            // 重置第一个Tab到第一页
            fragmentMap[0]?.post {
                it.resetToFirstPage()
            }
        } else if (currentTab == currentTabPosition) {
            // 切换到下一个Tab（自动切换）
            val nextTab = currentTab + 1
            isAutoSwitching = true
            binding.tabViewPager.setCurrentItem(nextTab, true)

            // 自动切换时，新Tab从第一页开始
            fragmentMap[nextTab]?.post {
                it.resetToFirstPage()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理Fragment映射
        fragmentMap.clear()
    }
}
