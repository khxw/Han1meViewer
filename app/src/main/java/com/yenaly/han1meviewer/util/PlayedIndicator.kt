package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.DatabaseRepo
import com.yenaly.han1meviewer.logic.model.HanimeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 为视频列表标记"已看"状态，供卡片"已看"角标使用。
 *
 * 仅在设置中开启"已看角标"时查询观看历史；列表为空时直接返回。
 * 返回的新列表中 watched 字段为 true 表示对应视频已看过。
 */
suspend fun markWatchedList(items: List<HanimeInfo>): List<HanimeInfo> {
    if (!Preferences.showPlayedIndicator || items.isEmpty()) return items
    val codes = items.map { it.videoCode }
    val watchedCodes = withContext(Dispatchers.IO) {
        DatabaseRepo.WatchHistory.getWatched(codes).toSet()
    }
    return items.map { item ->
        item.copy(watched = watchedCodes.contains(item.videoCode))
    }
}
