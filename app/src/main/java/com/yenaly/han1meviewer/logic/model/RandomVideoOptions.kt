package com.yenaly.han1meviewer.logic.model

/**
 * 随机播放的候选来源。
 *
 * 只保留首页的三个内容分类，并且支持多选。
 */
enum class RandomVideoSource(val key: String) {
    /** 最新里番（站点「最新上傳」，即所有里番更新流） */
    LATEST_HANIME("latest_hanime"),

    /** 泡面番 */
    SHORT_EPISODE("short_episode"),

    /** 3D 作品 */
    THREE_DCG("three_dcg");

    companion object {
        /**
         * 解析单个来源 key。
         * 旧版本的 `home` 迁移到最新里番；已移除的本地来源返回 null。
         */
        fun fromKey(key: String?): RandomVideoSource? = when (key) {
            "home" -> LATEST_HANIME
            else -> entries.firstOrNull { it.key == key }
        }

        /** 解析逗号分隔的多选值，无法识别的项忽略。 */
        fun parseKeys(raw: String?): Set<RandomVideoSource> =
            raw?.split(',')
                ?.mapNotNull { fromKey(it.trim()) }
                ?.toSet()
                .orEmpty()
    }
}

/**
 * 随机播放 2.0 的用户选项。
 */
data class RandomVideoOptions(
    /** 多选来源；为空时按最新里番处理。 */
    val sources: Set<RandomVideoSource> = setOf(RandomVideoSource.LATEST_HANIME),
    val excludeWatched: Boolean = false,
    val excludeDownloaded: Boolean = false,
    val excludeFavorited: Boolean = false,
    val avoidRecent: Boolean = true,
)
