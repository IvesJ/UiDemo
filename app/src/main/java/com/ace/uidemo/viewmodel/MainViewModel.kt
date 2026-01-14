package com.ace.uidemo.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ace.uidemo.download.model.DownloadProgress
import com.ace.uidemo.download.model.DownloadState
import com.ace.uidemo.model.CloudConfig
import com.ace.uidemo.model.MediaItem
import com.ace.uidemo.model.TabData
import com.ace.uidemo.model.TabDownloadState
import com.ace.uidemo.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * MainActivity的ViewModel
 * 负责管理下载状态、Tab数据、UI状态等业务逻辑
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository = DownloadRepository(application)

    // UI状态：是否显示内容（false表示显示loading）
    private val _showContent = MutableStateFlow(false)
    val showContent: StateFlow<Boolean> = _showContent.asStateFlow()

    // 所有Tab配置（从云端获取）
    private val _allTabs = MutableStateFlow<List<TabData>>(emptyList())
    val allTabs: StateFlow<List<TabData>> = _allTabs.asStateFlow()

    // 已完成下载的Tab（仅包含所有文件都下载完成的Tab）
    private val _completedTabs = MutableStateFlow<List<TabData>>(emptyList())
    val completedTabs: StateFlow<List<TabData>> = _completedTabs.asStateFlow()

    // Tab下载状态映射
    private val _tabDownloadStates = MutableStateFlow<Map<String, TabDownloadState>>(emptyMap())
    val tabDownloadStates: StateFlow<Map<String, TabDownloadState>> = _tabDownloadStates.asStateFlow()

    // 配置是否已加载
    private var isConfigLoaded = false
    private var hasShownContent = false

    // 需要显示重试对话框的Tab
    private val _showRetryDialog = MutableStateFlow<String?>(null)
    val showRetryDialog: StateFlow<String?> = _showRetryDialog.asStateFlow()

    /**
     * 绑定下载Service
     */
    fun bindDownloadService() {
        repository.bindService()
        observeServiceConnection()
    }

    /**
     * 监听Service连接状态
     */
    private fun observeServiceConnection() {
        viewModelScope.launch {
            repository.serviceConnected.collect { connected ->
                if (connected) {
                    Log.d(TAG, "Service已连接，开始监听数据")
                    observeConfig()
                    observeDownloadProgress()
                }
            }
        }
    }

    /**
     * 监听配置数据
     */
    private fun observeConfig() {
        viewModelScope.launch {
            repository.observeConfig()?.collect { configs ->
                if (configs.isNotEmpty() && !isConfigLoaded) {
                    Log.d(TAG, "收到配置: ${configs.size} 个Tab")
                    onConfigReceived(configs)
                    isConfigLoaded = true
                }
            }
        }
    }

    /**
     * 处理收到的配置数据
     */
    private fun onConfigReceived(configs: List<CloudConfig>) {
        Log.d(TAG, "========== 处理配置数据 ==========")

        // 根据配置创建所有Tabs（递归处理subTabs）
        val tabs = configs.map { config ->
            convertCloudConfigToTabData(config)
        }

        _allTabs.value = tabs

        Log.d(TAG, "创建了 ${tabs.size} 个Tab配置:")
        tabs.forEach { tab ->
            Log.d(TAG, "  - ${tab.title}: ${tab.mediaItems.size} 个文件, ${tab.subTabs.size} 个子Tab")
            tab.subTabs.forEach { subTab ->
                Log.d(TAG, "      - ${subTab.title}: ${subTab.mediaItems.size} 个文件")
            }
        }

        // 检查是否有已经完全下载好的Tab
        checkAndShowCompletedTabs()
    }

    /**
     * 递归转换 CloudConfig 到 TabData
     */
    private fun convertCloudConfigToTabData(config: CloudConfig): TabData {
        return TabData(
            id = config.tabTitle,
            title = config.tabTitle,
            mediaItems = config.filesInfo.map { fileInfo ->
                createMediaItemFromFileInfo(fileInfo)
            },
            subTabs = config.subTabs.map { subConfig ->
                convertCloudConfigToTabData(subConfig)
            }
        )
    }

    /**
     * 从FileInfo创建MediaItem
     */
    private fun createMediaItemFromFileInfo(fileInfo: com.ace.uidemo.model.FileInfo): MediaItem {
        val localFile = File(getApplication<Application>().filesDir, "media/${fileInfo.pageRes}")
        val localPath = if (localFile.exists()) {
            Log.d(TAG, "    ✓ 本地文件存在: ${fileInfo.pageRes}")
            localFile.absolutePath
        } else {
            Log.d(TAG, "    ✗ 本地文件不存在: ${fileInfo.pageRes}")
            null
        }

        return when (fileInfo.pageType) {
            "image" -> MediaItem.Image(
                id = fileInfo.pageRes,
                imageUrl = fileInfo.pageUrl,
                localPath = localPath
            )
            "video" -> MediaItem.Video(
                id = fileInfo.pageRes,
                videoUrl = fileInfo.pageUrl,
                localPath = localPath
            )
            else -> MediaItem.Image(
                id = fileInfo.pageRes,
                imageUrl = fileInfo.pageUrl,
                localPath = localPath
            )
        }
    }

    /**
     * 监听下载进度
     */
    private fun observeDownloadProgress() {
        Log.d(TAG, "开始收集下载进度Flow")
        viewModelScope.launch {
            repository.observeProgress()?.collect { progressMap ->
                Log.d(TAG, "收到进度更新: ${progressMap.size} 个文件")
                updateDownloadStates(progressMap)
                // 每次进度更新都检查是否有新的Tab完成
                checkAndShowCompletedTabs()
            }
        }
    }

    /**
     * 更新下载状态
     */
    private fun updateDownloadStates(progressMap: Map<String, DownloadProgress>) {
        val grouped = progressMap.values.groupBy { it.tabId }
        Log.d(TAG, "========== 更新下载状态 ==========")
        Log.d(TAG, "进度Map大小: ${progressMap.size}")
        Log.d(TAG, "分组后的Tab: ${grouped.keys.joinToString()}")

        // 打印每个Tab的详细进度
        grouped.forEach { (tabId, progresses) ->
            Log.d(TAG, "[$tabId] 文件数: ${progresses.size}")
            progresses.forEach { p ->
                Log.d(TAG, "  - ${p.fileName}: ${p.state}")
            }
        }

        val newStates = mutableMapOf<String, TabDownloadState>()

        // 更新allTabs的下载状态（用于判断哪些Tab已完成）
        _allTabs.value.forEach { tabData ->
            val tabProgress = grouped[tabData.title] ?: emptyList()
            Log.d(TAG, "[${tabData.title}] 匹配到 ${tabProgress.size} 个文件")

            val newState = when {
                tabProgress.isEmpty() -> {
                    Log.d(TAG, "[${tabData.title}] 状态: 未开始（无进度数据）")
                    TabDownloadState.NotStarted
                }
                tabProgress.all { it.isCompleted } -> {
                    Log.d(TAG, "[${tabData.title}] 状态: 全部完成")
                    TabDownloadState.Completed
                }
                tabProgress.any { it.isFailed } -> {
                    val error = tabProgress.first { it.isFailed }.state as DownloadState.Failed
                    Log.d(TAG, "[${tabData.title}] 状态: 失败 - ${error.error}")
                    // 触发显示重试对话框
                    _showRetryDialog.value = tabData.title
                    TabDownloadState.Failed(error.error)
                }
                else -> {
                    // 计算综合进度：已完成文件算100%，下载中的文件取其实际进度
                    val totalProgress = tabProgress.sumOf { progress ->
                        when (val state = progress.state) {
                            is DownloadState.Completed -> 100
                            is DownloadState.Downloading -> state.progress
                            else -> 0
                        }
                    }
                    val averageProgress = totalProgress / tabProgress.size

                    val completedFiles = tabProgress.count { it.isCompleted }
                    Log.d(TAG, "[${tabData.title}] 状态: 下载中 $completedFiles/${tabProgress.size} (综合进度: $averageProgress%)")
                    TabDownloadState.Downloading(averageProgress)
                }
            }

            newStates[tabData.title] = newState
            val oldState = _tabDownloadStates.value[tabData.title]
            if (oldState != newState) {
                Log.d(TAG, "[${tabData.title}] ★ 状态变化: $oldState -> $newState")
            }
        }

        _tabDownloadStates.value = newStates
        Log.d(TAG, "================================")
    }

    /**
     * 检查并显示已完成的Tab
     */
    private fun checkAndShowCompletedTabs() {
        Log.d(TAG, "========== 检查已完成的Tab ==========")

        // 找出所有文件都已下载完成的Tab（包括子Tab的文件）
        val newCompletedTabs = _allTabs.value.filter { tabData ->
            isTabFullyDownloaded(tabData)
        }

        // 更新已完成Tab列表
        val completedTabsWithLocalPaths = newCompletedTabs.map { tabData ->
            createTabDataWithLocalPaths(tabData)
        }

        _completedTabs.value = completedTabsWithLocalPaths
        Log.d(TAG, "已完成Tab数量: ${completedTabsWithLocalPaths.size}")

        // 如果有至少一个Tab完成，且还未显示内容，则显示内容
        if (completedTabsWithLocalPaths.isNotEmpty() && !hasShownContent) {
            Log.d(TAG, "首次有Tab完成，显示内容")
            _showContent.value = true
            hasShownContent = true
        }
    }

    /**
     * 检查Tab是否完全下载（包括子Tab）
     */
    private fun isTabFullyDownloaded(tabData: TabData): Boolean {
        // 检查主Tab的媒体文件
        val mainFilesExist = tabData.mediaItems.all { mediaItem ->
            val fileName = mediaItem.id
            val localFile = File(getApplication<Application>().filesDir, "media/$fileName")
            localFile.exists()
        }

        if (!mainFilesExist && tabData.mediaItems.isNotEmpty()) {
            Log.d(TAG, "  ✗ [${tabData.title}] 主文件仍有未下载")
            return false
        }

        // 检查子Tab的媒体文件
        val subTabsComplete = tabData.subTabs.all { subTab ->
            isTabFullyDownloaded(subTab)
        }

        if (!subTabsComplete && tabData.subTabs.isNotEmpty()) {
            Log.d(TAG, "  ✗ [${tabData.title}] 子Tab仍有文件未下载")
            return false
        }

        // 如果没有主文件也没有子Tab，不算完成
        if (tabData.mediaItems.isEmpty() && tabData.subTabs.isEmpty()) {
            Log.d(TAG, "  ✗ [${tabData.title}] 无内容")
            return false
        }

        Log.d(TAG, "  ✓ [${tabData.title}] 所有文件已下载完成")
        return true
    }

    /**
     * 递归创建带本地路径的TabData
     */
    private fun createTabDataWithLocalPaths(tabData: TabData): TabData {
        return TabData(
            id = tabData.id,
            title = tabData.title,
            mediaItems = tabData.mediaItems.map { mediaItem ->
                val fileName = mediaItem.id
                val localPath = File(getApplication<Application>().filesDir, "media/$fileName").absolutePath

                when (mediaItem) {
                    is MediaItem.Image -> MediaItem.Image(
                        id = mediaItem.id,
                        imageUrl = mediaItem.imageUrl,
                        localPath = localPath
                    )
                    is MediaItem.Video -> MediaItem.Video(
                        id = mediaItem.id,
                        videoUrl = mediaItem.videoUrl,
                        duration = mediaItem.duration,
                        localPath = localPath
                    )
                }
            },
            subTabs = tabData.subTabs.map { subTab ->
                createTabDataWithLocalPaths(subTab)
            }
        )
    }

    /**
     * 重试失败的下载
     */
    fun retryFailedDownloads(tabTitle: String) {
        repository.retryFailedDownloads(tabTitle)
        // 清除重试对话框状态
        _showRetryDialog.value = null
    }

    /**
     * 清除重试对话框状态
     */
    fun clearRetryDialog() {
        _showRetryDialog.value = null
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel onCleared，解绑Service")
        repository.unbindService()
    }
}
