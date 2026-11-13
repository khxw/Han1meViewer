package com.yenaly.han1meviewer.logic.model

/**
 * 在授权目录中发现、但尚未写入下载数据库的视频。
 */
data class DiscoveredHanimeDownload(
    val videoCode: String,
    val title: String,
    val coverUrl: String,
    val coverUri: String?,
    val videoUri: String,
    val quality: String,
    val videoUrl: String?,
)

/**
 * 本地媒体库自动发现结果。
 */
data class LocalMediaDiscoveryResult(
    val newItems: List<DiscoveredHanimeDownload> = emptyList(),
    val movedItems: List<DiscoveredHanimeDownload> = emptyList(),
    val missingVideoCodes: List<String> = emptyList(),
    val skippedCount: Int = 0,
) {
    val pendingCount: Int
        get() = newItems.size + movedItems.size

    val hasPendingChanges: Boolean
        get() = pendingCount > 0
}
