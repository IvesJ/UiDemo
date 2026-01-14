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
        // 根据配置创建所有Tabs（但先不显示）
        val tabs = configs.map { config ->
            createTabDataFromConfig(config)
        }

        _allTabs.value = tabs

        Log.d(TAG, "创建了 ${tabs.size} 个Tab配置:")
        tabs.forEach { tab ->
            val totalFiles = tab.mediaItems.size + tab.subTabs.sumOf { it.mediaItems.size }
            Log.d(TAG, "  - ${tab.title}: ${tab.mediaItems.size} 个主文件, ${tab.subTabs.size} 个子Tab (共 $totalFiles 个文件)")
        }

        // 检查是否有已经完全下载好的Tab
        checkAndShowCompletedTabs()
    }

    /**
     * 从CloudConfig创建TabData（递归处理子Tab）
     */
    private fun createTabDataFromConfig(config: CloudConfig): TabData {
        return TabData(
            id = config.tabTitle,
            title = config.tabTitle,
            mediaItems = config.filesInfo.map { fileInfo ->
                createMediaItemFromFileInfo(fileInfo)
            },
            subTabs = config.subTabs.map { subConfig ->
                createTabDataFromConfig(subConfig)
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
        viewModelScope.launch {
            repository.observeProgress()?.collect { progressMap ->
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

        val newStates = mutableMapOf<String, TabDownloadState>()

        // 更新allTabs的下载状态（用于判断哪些Tab已完成）
        _allTabs.value.forEach { tabData ->
            val tabProgress = grouped[tabData.title] ?: emptyList()

            val newState = when {
                tabProgress.isEmpty() -> {
                    TabDownloadState.NotStarted
                }
                tabProgress.all { it.isCompleted } -> {
                    TabDownloadState.Completed
                }
                tabProgress.any { it.isFailed } -> {
                    val error = tabProgress.first { it.isFailed }.state as DownloadState.Failed
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
                    TabDownloadState.Downloading(averageProgress)
                }
            }

            newStates[tabData.title] = newState
            val oldState = _tabDownloadStates.value[tabData.title]
            if (oldState != newState) {
                Log.d(TAG, "[${tabData.title}] 状态变化: $oldState -> $newState")
            }
        }

        _tabDownloadStates.value = newStates
    }

    /**
     * 检查并显示已完成的Tab
     * 直接显示所有Tab（不等待下载完成），用于测试轮播效果
     */
    private fun checkAndShowCompletedTabs() {
        // 直接显示所有Tab（使用URL作为本地路径）
        val allTabsWithPaths = _allTabs.value.map { tabData ->
            createTabDataWithPaths(tabData, useLocalPath = false)
        }

        if (allTabsWithPaths.isNotEmpty() && allTabsWithPaths != _completedTabs.value) {
            _completedTabs.value = allTabsWithPaths
            Log.d(TAG, "显示所有Tab: ${allTabsWithPaths.size}")

            if (!hasShownContent) {
                _showContent.value = true
                hasShownContent = true
            }
        }
    }

    /**
     * 创建带路径的TabData
     */
    private fun createTabDataWithPaths(tabData: TabData, useLocalPath: Boolean): TabData {
        val mediaItems = tabData.mediaItems.map { mediaItem ->
            val path = if (useLocalPath) {
                File(getApplication<Application>().filesDir, "media/${mediaItem.id}").absolutePath
            } else {
                // 使用远程URL
                when (mediaItem) {
                    is MediaItem.Image -> mediaItem.imageUrl
                    is MediaItem.Video -> mediaItem.videoUrl
                }
            }
            when (mediaItem) {
                is MediaItem.Image -> MediaItem.Image(
                    id = mediaItem.id,
                    imageUrl = mediaItem.imageUrl,
                    localPath = path
                )
                is MediaItem.Video -> MediaItem.Video(
                    id = mediaItem.id,
                    videoUrl = mediaItem.videoUrl,
                    duration = mediaItem.duration,
                    localPath = path
                )
            }
        }

        return TabData(
            id = tabData.id,
            title = tabData.title,
            mediaItems = mediaItems,
            subTabs = tabData.subTabs.map { subTab ->
                createTabDataWithPaths(subTab, useLocalPath)
            }
        )
    }

    /**
     * 检查Tab的所有文件是否都存在
     */
    private fun checkTabFilesExist(tabData: TabData): Boolean {
        // 检查主媒体文件
        val mainFilesExist = tabData.mediaItems.all { mediaItem ->
            val fileName = when (mediaItem) {
                is MediaItem.Image -> mediaItem.id
                is MediaItem.Video -> mediaItem.id
            }
            val localFile = File(getApplication<Application>().filesDir, "media/$fileName")
            localFile.exists()
        }

        if (!mainFilesExist) {
            return false
        }

        // 递归检查子Tab
        val subTabsExist = tabData.subTabs.all { subTab ->
            checkTabFilesExist(subTab)
        }

        return mainFilesExist && subTabsExist
    }

    /**
     * 创建已完成的TabData（递归处理子Tab和本地路径）
     */
    private fun createCompletedTabData(tabData: TabData): TabData {
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
                createCompletedTabData(subTab)
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
