package com.yenaly.han1meviewer.util

import com.yenaly.han1meviewer.logic.model.SearchOption
import com.yenaly.yenaly_libs.utils.LanguageHelper

object TagLocalizer {

    private data class TagMappings(
        val labels: Map<String, String>,
        val searchKeys: Map<String, String>,
        val variants: Map<String, Set<String>>,
    )

    private val tagOptions: List<SearchOption> by lazy {
        loadAssetAs<Map<String, List<SearchOption>>>("search_options/tags.json")
            .orEmpty()
            .values
            .flatten() + loadAssetAs<List<SearchOption>>("search_options/genre.json").orEmpty()
    }

    private var cachedLanguageTag: String? = null
    private var cachedMappings: TagMappings? = null

    private val tagMappings: TagMappings
        get() {
            val languageTag = LanguageHelper.preferredLanguage.toLanguageTag()
            val mappings = cachedMappings
            if (cachedLanguageTag == languageTag && mappings != null) return mappings
            return buildTagMappings(tagOptions).also {
                cachedLanguageTag = languageTag
                cachedMappings = it
            }
        }

    fun localizeTags(tags: List<String>): List<String> {
        if (tags.isEmpty()) return tags
        return tags.map(::localizeTag)
    }

    /** 返回当前语言下可浏览的完整标签目录（去重排序）。 */
    fun allLocalizedTags(): List<String> =
        tagOptions
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun localizeTag(tag: String): String =
        tagMappings.labels[tag.lowercase()] ?: tag

    fun resolveSearchKey(tag: String): String =
        tagMappings.searchKeys[tag.lowercase()] ?: tag

    /**
     * 返回标签对应的全部语言变体（简体/繁体中文、英文、日文及别名），
     * 未收录的标签仅返回自身；变体展开后任意两种语言文字均可互相匹配。
     */
    fun tagVariants(tag: String): Set<String> =
        tagMappings.variants[tag.lowercase()] ?: setOf(tag)

    private fun buildTagMappings(options: List<SearchOption>): TagMappings {
        val labels = mutableMapOf<String, String>()
        val searchKeys = mutableMapOf<String, String>()
        val variants = mutableMapOf<String, MutableSet<String>>()
        options.forEach { option ->
            val label = option.value.takeIf { it.isNotBlank() } ?: return@forEach
            val searchKey = option.searchKey?.takeIf { it.isNotBlank() } ?: return@forEach
            val tagVariants = listOfNotNull(
                option.searchKey,
                option.name,
                option.lang?.zhrCN,
                option.lang?.zhrTW,
                option.lang?.en,
                option.lang?.ja,
            ).toSet()
            tagVariants.forEach { rawTag ->
                // 键统一小写存储，查询小写化，实现大小写不敏感匹配
                // （如 "loli" 与 "Loli" 都能映射到同一本地化标签）
                val key = rawTag.lowercase()
                labels.putIfAbsent(key, label)
                searchKeys.putIfAbsent(key, searchKey)
                variants.getOrPut(key) { mutableSetOf() }.addAll(tagVariants)
            }
        }
        return TagMappings(
            labels = labels,
            searchKeys = searchKeys,
            variants = variants.mapValues { it.value.toSet() },
        )
    }
}
