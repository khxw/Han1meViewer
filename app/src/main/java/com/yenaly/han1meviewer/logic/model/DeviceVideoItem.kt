package com.yenaly.han1meviewer.logic.model

data class DeviceVideoItem(
    val id: Long,
    val contentUri: String,
    val title: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folderName: String?,
    val dateAddedSeconds: Long,
)
