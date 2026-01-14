package com.ace.uidemo.download.core

import android.content.Context
import android.util.Log
import com.ace.uidemo.download.database.DownloadDatabase
import com.ace.uidemo.download.database.DownloadEntity
import com.ace.uidemo.download.model.DownloadProgress
import com.ace.uidemo.download.model.DownloadState
import com.ace.uidemo.model.CloudConfig
import com.ace.uidemo.model.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File

class DownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"
    }

    private val database = DownloadDatabase.getInstance(context)
    private val downloadDao = database.downloadDao()
    private val fileDownloader = FileDownloader(context)

    private val _progressFlow = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progressFlow: StateFlow<Map<String, DownloadProgress>> = _progressFlow

    private val _configFlow = MutableStateFlow<List<CloudConfig>>(emptyList())
    val configFlow: StateFlow<List<CloudConfig>> = _configFlow

    private val maxConcurrentDownloads = 3
    private val maxRetries = 3

    // Mock数据 - 用于测试
    private fun getMockConfig(): List<CloudConfig> {
        return listOf(
            // 第一个Tab：推荐（无子Tab）
            CloudConfig(
                tabTitle = "推荐",
                filesInfo = listOf(
                    FileInfo(
                        pageType = "image",
                        pageRes = "image_1.jpg",
                        pageUrl = "https://picsum.photos/400/600?random=1",
                        md5 = "mock_md5_1"
                    ),
                    FileInfo(
                        pageType = "image",
                        pageRes = "image_2.jpg",
                        pageUrl = "https://picsum.photos/400/600?random=2",
                        md5 = "mock_md5_2"
                    ),
                    FileInfo(
                        pageType = "video",
                        pageRes = "video_1.mp4",
                        pageUrl = "http://www.w3school.com.cn/i/movie.mp4",
                        md5 = "mock_md5_3"
                    )
                )
            ),
            // 第二个Tab：热门（带子Tab）
            CloudConfig(
                tabTitle = "热门",
                filesInfo = emptyList(),
                subTabs = listOf(
                    CloudConfig(
                        tabTitle = "今日热门",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_today_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=10",
                                md5 = "mock_md5_10"
                            ),
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_today_2.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=11",
                                md5 = "mock_md5_11"
                            )
                        )
                    ),
                    CloudConfig(
                        tabTitle = "本周热门",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_week_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=12",
                                md5 = "mock_md5_12"
                            ),
                            FileInfo(
                                pageType = "video",
                                pageRes = "hot_week_video.mp4",
                                pageUrl = "http://www.w3school.com.cn/i/movie.mp4",
                                md5 = "mock_md5_13"
                            )
                        )
                    ),
                    CloudConfig(
                        tabTitle = "本月热门",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_month_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=14",
                                md5 = "mock_md5_14"
                            ),
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_month_2.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=15",
                                md5 = "mock_md5_15"
                            ),
                            FileInfo(
                                pageType = "image",
                                pageRes = "hot_month_3.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=16",
                                md5 = "mock_md5_16"
                            )
                        )
                    )
                )
            ),
            // 第三个Tab：关注（无子Tab）
            CloudConfig(
                tabTitle = "关注",
                filesInfo = listOf(
                    FileInfo(
                        pageType = "image",
                        pageRes = "image_5.jpg",
                        pageUrl = "https://picsum.photos/400/600?random=5",
                        md5 = "mock_md5_6"
                    ),
                    FileInfo(
                        pageType = "video",
                        pageRes = "video_2.mp4",
                        pageUrl = "http://www.w3school.com.cn/i/movie.mp4",
                        md5 = "mock_md5_7"
                    )
                )
            )
        )
    }

    suspend fun startDownload() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "========== 开始下载流程 ==========")

            // 使用Mock数据替代API请求
            val configs = getMockConfig()
            Log.d(TAG, "获取到 ${configs.size} 个Tab的配置")

            // 发送配置到Flow，供UI使用
            _configFlow.value = configs
            Log.d(TAG, "配置已发送到configFlow")

            // 2. 准备下载任务（包括子Tab中的文件）
            val allTasks = mutableListOf<DownloadTask>()

            configs.forEach { config ->
                // 添加主Tab的文件
                config.filesInfo.forEach { fileInfo ->
                    allTasks.add(
                        DownloadTask(
                            tabId = config.tabTitle,
                            fileName = fileInfo.pageRes,
                            url = fileInfo.pageUrl,
                            md5 = fileInfo.md5,
                            fileType = fileInfo.pageType
                        )
                    )
                }

                // 添加子Tab的文件
                config.subTabs.forEach { subTab ->
                    subTab.filesInfo.forEach { fileInfo ->
                        allTasks.add(
                            DownloadTask(
                                tabId = "${config.tabTitle}/${subTab.tabTitle}",
                                fileName = fileInfo.pageRes,
                                url = fileInfo.pageUrl,
                                md5 = fileInfo.md5,
                                fileType = fileInfo.pageType
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "准备下载任务总数: ${allTasks.size}")
            allTasks.forEach { task ->
                Log.d(TAG, "  - [${task.tabId}] ${task.fileName} (${task.fileType})")
            }

            // 3. 检查本地已存在的文件（暂时跳过MD5校验）
            val tasksToDownload = allTasks.filter { task ->
                val exists = isFileExistsLocal(task.fileName)
                if (exists) {
                    Log.d(TAG, "文件已存在，跳过下载: ${task.fileName}")
                }
                !exists
            }
            Log.d(TAG, "需要下载的文件数: ${tasksToDownload.size}")

            if (tasksToDownload.isEmpty()) {
                Log.d(TAG, "所有文件已存在，无需下载")
                return@withContext
            }

            // 4. 并发下载（最多3个同时）
            Log.d(TAG, "开始并发下载 (最大并发数: $maxConcurrentDownloads)")
            tasksToDownload.chunked(maxConcurrentDownloads).forEachIndexed { batchIndex, chunk ->
                Log.d(TAG, "下载批次 ${batchIndex + 1}/${(tasksToDownload.size + maxConcurrentDownloads - 1) / maxConcurrentDownloads}, 包含 ${chunk.size} 个文件")
                chunk.map { task ->
                    async { downloadWithRetry(task) }
                }.awaitAll()
                Log.d(TAG, "批次 ${batchIndex + 1} 完成")
            }

            Log.d(TAG, "========== 下载流程完成 ==========")

        } catch (e: Exception) {
            Log.e(TAG, "下载流程异常", e)
            e.printStackTrace()
        }
    }

    // 简化版：只检查文件是否存在，暂时跳过MD5校验
    private fun isFileExistsLocal(fileName: String): Boolean {
        val file = File(context.filesDir, "media/$fileName")
        val exists = file.exists()
        Log.d(TAG, "检查文件: $fileName -> 存在=${exists}, 路径=${file.absolutePath}")
        if (exists) {
            Log.d(TAG, "  文件大小: ${file.length()} bytes")
        }
        return exists
    }

    // 原来的MD5校验方法（暂时不用）
    private fun isFileValidLocal(fileName: String, expectedMd5: String): Boolean {
        val file = File(context.filesDir, "media/$fileName")
        if (!file.exists()) return false

        // 暂时跳过MD5校验，直接返回true
        return true

        // 取消注释以启用MD5校验
        // val actualMd5 = MD5Util.calculateMD5(file)
        // return actualMd5.equals(expectedMd5, ignoreCase = true)
    }

    private suspend fun downloadWithRetry(task: DownloadTask) {
        Log.d(TAG, "---------- 开始下载任务 ----------")
        Log.d(TAG, "文件名: ${task.fileName}")
        Log.d(TAG, "URL: ${task.url}")
        Log.d(TAG, "Tab: ${task.tabId}")

        var retryCount = 0
        var success = false

        while (retryCount < maxRetries && !success) {
            try {
                if (retryCount > 0) {
                    Log.w(TAG, "[${task.fileName}] 第 ${retryCount} 次重试")
                }

                updateProgress(task, DownloadState.Downloading(0))
                Log.d(TAG, "[${task.fileName}] 开始下载...")

                fileDownloader.download(task.url, task.fileName) { downloaded, total ->
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    updateProgress(task, DownloadState.Downloading(progress))

                    // 每20%打印一次进度
                    if (progress % 20 == 0 && downloaded > 0) {
                        Log.d(TAG, "[${task.fileName}] 下载进度: $progress% ($downloaded/$total bytes)")
                    }
                }

                // MD5校验
                val downloadedFile = File(context.filesDir, "media/${task.fileName}")
                if (!downloadedFile.exists()) {
                    throw Exception("下载完成但文件不存在")
                }

                Log.d(TAG, "[${task.fileName}] 下载完成，文件大小: ${downloadedFile.length()} bytes")

                // MD5校验（暂时跳过）
                // val actualMd5 = MD5Util.calculateMD5(downloadedFile)
                // Log.d(TAG, "[${task.fileName}] MD5计算完成: $actualMd5")
                //
                // if (actualMd5.equals(task.md5, ignoreCase = true)) {
                //     Log.i(TAG, "[${task.fileName}] ✓ 下载成功且MD5校验通过")
                //     updateProgress(task, DownloadState.Completed)
                //     saveToDatabase(task, DownloadState.Completed)
                //     success = true
                // } else {
                //     Log.w(TAG, "[${task.fileName}] MD5校验失败 - 期望: ${task.md5}, 实际: $actualMd5")
                //     throw Exception("MD5校验失败")
                // }

                // 跳过MD5校验，直接标记为成功
                Log.i(TAG, "[${task.fileName}] ✓ 下载成功（已跳过MD5校验）")
                updateProgress(task, DownloadState.Completed)
                saveToDatabase(task, DownloadState.Completed)
                success = true

            } catch (e: Exception) {
                retryCount++
                Log.e(TAG, "[${task.fileName}] 下载失败 (尝试 $retryCount/$maxRetries): ${e.message}", e)

                if (retryCount >= maxRetries) {
                    val failedState = DownloadState.Failed(e.message ?: "未知错误", retryCount)
                    Log.e(TAG, "[${task.fileName}] ✗ 达到最大重试次数，标记为失败")
                    updateProgress(task, failedState)
                    saveToDatabase(task, failedState)
                } else {
                    val delayMs = 1000L * retryCount
                    Log.w(TAG, "[${task.fileName}] 将在 ${delayMs}ms 后重试...")
                    delay(delayMs)  // 递增延迟
                }
            }
        }
    }

    private fun updateProgress(task: DownloadTask, state: DownloadState) {
        val key = "${task.tabId}_${task.fileName}"
        val progress = DownloadProgress(
            tabId = task.tabId,
            fileName = task.fileName,
            url = task.url,
            md5 = task.md5,
            state = state
        )

        val currentMap = _progressFlow.value.toMutableMap()
        currentMap[key] = progress
        _progressFlow.value = currentMap
    }

    private suspend fun saveToDatabase(task: DownloadTask, state: DownloadState) {
        val entity = DownloadEntity(
            id = "${task.tabId}_${task.fileName}",
            tabId = task.tabId,
            fileName = task.fileName,
            url = task.url,
            md5 = task.md5,
            state = state.toString(),
            timestamp = System.currentTimeMillis()
        )
        downloadDao.insert(entity)
    }

    suspend fun retryTab(tabId: String) = withContext(Dispatchers.IO) {
        val failedTasks = _progressFlow.value.values
            .filter { it.tabId == tabId && it.isFailed }

        failedTasks.forEach { progress ->
            val task = DownloadTask(
                tabId = progress.tabId,
                fileName = progress.fileName,
                url = progress.url,
                md5 = progress.md5,
                fileType = if (progress.fileName.endsWith(".mp4")) "video" else "image"
            )
            downloadWithRetry(task)
        }
    }

    fun cancelAll() {
        fileDownloader.cancelAll()
    }
}

data class DownloadTask(
    val tabId: String,
    val fileName: String,
    val url: String,
    val md5: String,
    val fileType: String
)
