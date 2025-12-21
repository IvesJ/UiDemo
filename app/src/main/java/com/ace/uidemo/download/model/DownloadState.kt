package com.ace.uidemo.download.model

sealed class DownloadState {
    object Idle : DownloadState()
    object Pending : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String, val retryCount: Int) : DownloadState()
}

data class DownloadProgress(
    val tabId: String,
    val fileName: String,
    val url: String,
    val md5: String,
    val state: DownloadState,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L
) {
    val isCompleted: Boolean
        get() = state is DownloadState.Completed

    val isFailed: Boolean
        get() = state is DownloadState.Failed

    val progressPercent: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes * 100) / totalBytes).toInt()
        } else 0
}
