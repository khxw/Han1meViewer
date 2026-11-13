package com.yenaly.han1meviewer.logic.model

/**
 * 应用内自定义动画的显示策略。
 */
enum class UiMotionMode(val key: String) {
    FOLLOW_SYSTEM("follow_system"),
    FULL("full"),
    REDUCED("reduced");

    companion object {
        fun fromKey(key: String?): UiMotionMode =
            entries.firstOrNull { it.key == key } ?: FOLLOW_SYSTEM
    }
}
