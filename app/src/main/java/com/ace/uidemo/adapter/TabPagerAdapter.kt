package com.ace.uidemo.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.ace.uidemo.fragment.MediaCarouselFragment
import com.ace.uidemo.model.TabData

class TabPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val tabs: List<TabData>,
    private val onFragmentCreated: (Int, MediaCarouselFragment) -> Unit
) : FragmentStateAdapter(fragmentActivity) {

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment {
        val tabData = tabs[position]
        val fragment = MediaCarouselFragment.newInstance(ArrayList(tabData.mediaItems))

        // 通知Fragment已创建
        fragment.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                onFragmentCreated(position, fragment)
            }
        })

        return fragment
    }

    fun getTabTitle(position: Int): String {
        return if (position in tabs.indices) {
            tabs[position].title
        } else {
            ""
        }
    }
}
