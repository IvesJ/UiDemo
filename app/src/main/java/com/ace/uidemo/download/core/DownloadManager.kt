package com.ace.uidemo.download.core

import android.content.Context
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
            CloudConfig(
                tabTitle = "热门",
                filesInfo = listOf(
                    FileInfo(
                        pageType = "image",
                        pageRes = "image_3.jpg",
                        pageUrl = "https://picsum.photos/400/600?random=3",
                        md5 = "mock_md5_4"
                    ),
                    FileInfo(
                        pageType = "image",
                        pageRes = "image_4.jpg",
                        pageUrl = "https://picsum.photos/400/600?random=4",
                        md5 = "mock_md5_5"
                    )
                ),
                // 热门Tab包含子Tab
                subTabs = listOf(
                    CloudConfig(
                        tabTitle = "热门-子Tab1",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "sub1_image_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=10",
                                md5 = "mock_sub1_1"
                            ),
                            FileInfo(
                                pageType = "image",
                                pageRes = "sub1_image_2.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=11",
                                md5 = "mock_sub1_2"
                            )
                        )
                    ),
                    CloudConfig(
                        tabTitle = "热门-子Tab2",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "sub2_image_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=12",
                                md5 = "mock_sub2_1"
                            ),
                            FileInfo(
                                pageType = "video",
                                pageRes = "sub2_video_1.mp4",
                                pageUrl = "http://www.w3school.com.cn/i/movie.mp4",
                                md5 = "mock_sub2_2"
                            )
                        )
                    ),
                    CloudConfig(
                        tabTitle = "热门-子Tab3",
                        filesInfo = listOf(
                            FileInfo(
                                pageType = "image",
                                pageRes = "sub3_image_1.jpg",
                                pageUrl = "https://picsum.photos/400/600?random=13",
                                md5 = "mock_sub3_1"
                            )
                        )
                    )
                )
            ),
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
                        pageUrl = "https://disk.sample.cat/samples/mp4/1416529-sd_640_360_30fps.mp4",
                        md5 = "mock_md5_7"
                    )
                )
            )
        )
    }

    suspend fun startDownload() = withContext(Dispatchers.IO) {
        try {
            // 使用Mock数据替代API请求
            val configs = getMockConfig()

            // 发送配置到Flow，供UI使用
            _configFlow.value = configs

            // 准备下载任务
            val allTasks = configs.flatMap { config ->
                config.filesInfo.map { fileInfo ->
                    DownloadTask(
                        tabId = config.tabTitle,
                        fileName = fileInfo.pageRes,
                        url = fileInfo.pageUrl,
                        md5 = fileInfo.md5,
                        fileType = fileInfo.pageType
                    )
                }
            }

            // 检查本地已存在的文件
            val tasksToDownload = allTasks.filter { task ->
                !isFileExistsLocal(task.fileName)
            }

            if (tasksToDownload.isEmpty()) {
                return@withContext
            }

            // 并发下载（最多3个同时）
            tasksToDownload.chunked(maxConcurrentDownloads).forEachIndexed { _, chunk ->
                chunk.map { task ->
                    async { downloadWithRetry(task) }
                }.awaitAll()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 简化版：只检查文件是否存在，暂时跳过MD5校验
    private fun isFileExistsLocal(fileName: String): Boolean {
        val file = File(context.filesDir, "media/$fileName")
        return file.exists()
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
        var retryCount = 0
        var success = false

        while (retryCount < maxRetries && !success) {
            try {
                updateProgress(task, DownloadState.Downloading(0))

                fileDownloader.download(task.url, task.fileName) { downloaded, total ->
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    updateProgress(task, DownloadState.Downloading(progress))
                }

                val downloadedFile = File(context.filesDir, "media/${task.fileName}")
                if (!downloadedFile.exists()) {
                    throw Exception("下载完成但文件不存在")
                }

                updateProgress(task, DownloadState.Completed)
                saveToDatabase(task, DownloadState.Completed)
                success = true

            } catch (e: Exception) {
                retryCount++
                if (retryCount >= maxRetries) {
                    val failedState = DownloadState.Failed(e.message ?: "未知错误", retryCount)
                    updateProgress(task, failedState)
                    saveToDatabase(task, failedState)
                } else {
                    delay(1000L * retryCount)
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
