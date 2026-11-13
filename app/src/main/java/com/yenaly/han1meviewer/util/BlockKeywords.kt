package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.Preferences

/**
 * 判断标题是否命中屏蔽关键词。
 *
 * 仅在开启标题关键词屏蔽且列表非空时生效，
 * 大小写不敏感的包含匹配（如 "NTR" 可命中 "ntr"、"Ntr"）。
 */
fun isTitleBlocked(title: String?): Boolean {
    if (!Preferences.blockKeywordsEnabled) return false
    if (title.isNullOrBlank()) return false
    val keywords = Preferences.blockedKeywords
    if (keywords.isEmpty()) return false
    val lowerTitle = title.lowercase()
    return keywords.any { keyword ->
        keyword.isNotBlank() && lowerTitle.contains(keyword.lowercase())
    }
}

/**
 * 判断评论内容是否命中屏蔽关键词。
 *
 * 仅在开启评论关键词屏蔽且列表非空时生效，
 * 大小写不敏感的包含匹配，命中的评论在评论区直接隐藏。
 */
fun isCommentBlocked(content: String?): Boolean =
    isCommentBlocked(
        content = content,
        enabled = Preferences.commentKeywordsEnabled,
        keywords = Preferences.blockedCommentKeywords,
    )

fun isCommentBlocked(
    content: String?,
    enabled: Boolean,
    keywords: Set<String>,
): Boolean {
    if (!enabled) return false
    if (content.isNullOrBlank()) return false
    if (keywords.isEmpty()) return false
    val lowerContent = content.lowercase()
    return keywords.any { keyword ->
        keyword.isNotBlank() && lowerContent.contains(keyword.lowercase())
    }
}
