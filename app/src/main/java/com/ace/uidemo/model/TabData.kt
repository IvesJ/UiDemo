package com.ace.uidemo.model

data class TabData(
    val id: String,
    val title: String,
    val mediaItems: List<MediaItem>,
    val downloadState: TabDownloadState = TabDownloadState.NotStarted
)

sealed class TabDownloadState {
    object NotStarted : TabDownloadState()
    data class Downloading(val progress: Int) : TabDownloadState()
    object Completed : TabDownloadState()
    data class Failed(val errorMessage: String) : TabDownloadState()
}
