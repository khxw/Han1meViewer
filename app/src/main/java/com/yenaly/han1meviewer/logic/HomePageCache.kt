package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.HJson
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.yenaly_libs.utils.applicationContext
import java.io.File

/**
 * 首页数据本地缓存：
 * 应用冷启动（后台被系统回收后重新打开）时先展示上次的缓存内容，
 * 避免整页转圈等待；网络刷新成功后自动替换为新数据。
 */
object HomePageCache {

    private const val FILE_NAME = "home_page_cache.json"

    private fun cacheFile(): File = File(applicationContext.filesDir, FILE_NAME)

    /**
     * 保存首页数据缓存（加载成功后调用）
     */
    fun save(page: HomePage) {
        runCatching {
            cacheFile().writeText(HJson.encodeToString(HomePage.serializer(), page))
        }.onFailure { e -> e.printStackTrace() }
    }

    /**
     * 读取缓存，不存在或解析失败（如版本升级模型变化）时返回 null
     */
    fun load(): HomePage? = runCatching {
        val file = cacheFile()
        if (!file.exists()) return null
        HJson.decodeFromString(HomePage.serializer(), file.readText())
    }.getOrElse {
        it.printStackTrace()
        null
    }

    /**
     * 清除缓存（登出、切换站点时调用，防止展示旧登录态/旧站点数据）
     */
    fun clear() {
        runCatching { cacheFile().delete() }
    }
}
