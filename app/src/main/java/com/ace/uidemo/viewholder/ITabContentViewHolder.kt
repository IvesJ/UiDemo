package com.ace.uidemo.viewholder

import com.ace.uidemo.model.MediaItem

/**
 * Tab内容ViewHolder的通用接口
 * 用于统一 TabContentViewHolder 和 TabWithSubTabsViewHolder 的操作
 */
interface ITabContentViewHolder {
    fun togglePause()
    fun pause()
    fun resume()
    fun release()
    fun isPaused(): Boolean
    fun getCurrentMediaItem(): MediaItem?
    fun isAtLastItem(): Boolean
    fun resetToFirst()
}
