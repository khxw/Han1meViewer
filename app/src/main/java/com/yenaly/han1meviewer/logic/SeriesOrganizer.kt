package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.logic.entity.FavoriteEntity
import com.yenaly.han1meviewer.logic.entity.WatchHistoryEntity
import com.yenaly.han1meviewer.logic.entity.download.HanimeDownloadEntity
import com.yenaly.han1meviewer.logic.model.LocalSeries
import com.yenaly.han1meviewer.logic.model.LocalSeriesEpisode
import com.yenaly.han1meviewer.logic.model.SeriesCandidate
import java.text.Normalizer
import java.util.Locale

/**
 * 本地系列标题解析与归并。
 *
 * 只处理明确包含集数的标题，并只在至少两集拥有同一归一化标题时生成系列，
 * 避免把普通单视频误识别成系列。
 */
object SeriesOrganizer {

    private val numberToken = """(?:[0-9０-９]+|[零〇一二三四五六七八九十百]+)"""
    private val markerRegexes = listOf(
        Regex("""第\s*($numberToken)\s*[話话集]"""),
        Regex("""(?i)(?<![A-Za-z])(?:EP|EPISODE)\s*[.:_-]?\s*(\d{1,4})(?![0-9])"""),
        Regex(
            """(?i)(?<![A-Za-z])(?:PART|VOL(?:UME)?)\s*[.:_-]?\s*(\d{1,4})(?![0-9])""",
        ),
        Regex("""(?i)(?<![A-Za-z])#\s*(\d{1,4})(?![0-9])"""),
        Regex("""(?:^|[\s\-_/|—–])(\d{1,3})\s*$"""),
    )
    private val leadingTagRegex = Regex("""^\s*[\[【（(][^\]】）)]{1,16}[\]】）)]\s*""")
    private val qualifierRegex = Regex(
        """(?i)(?:中文字幕|中字|無修正|无修正|字幕|HD|FHD|4K)""",
    )
    private val trailingSeparatorRegex = Regex("""[\s\-–—_|:：·・]+$""")
    private val normalizedPunctuationRegex = Regex("""[\s\p{P}\p{S}]+""")

    internal data class ParsedTitle(
        val key: String,
        val displayTitle: String,
        val episode: Int,
    )

    fun buildCandidates(
        favorites: List<FavoriteEntity>,
        history: List<WatchHistoryEntity>,
        downloads: List<HanimeDownloadEntity>,
    ): List<SeriesCandidate> {
        val byCode = linkedMapOf<String, SeriesCandidate>()

        favorites.forEach { favorite ->
            byCode[favorite.videoCode] = merge(
                byCode[favorite.videoCode],
                SeriesCandidate(
                    videoCode = favorite.videoCode,
                    title = favorite.title,
                    coverUrl = favorite.coverUrl,
                    timestamp = favorite.savedAt,
                    isFavorite = true,
                ),
            )
        }

        history.forEach { item ->
            byCode[item.videoCode] = merge(
                byCode[item.videoCode],
                SeriesCandidate(
                    videoCode = item.videoCode,
                    title = item.title,
                    coverUrl = item.coverUrl,
                    timestamp = item.watchDate,
                    progress = item.progress,
                    duration = item.duration,
                    isCompleted = item.isCompleted,
                ),
            )
        }

        downloads.groupBy { it.videoCode }.forEach { (videoCode, entries) ->
            val best = entries.maxWithOrNull(
                compareBy<HanimeDownloadEntity> { qualityScore(it.quality) }
                    .thenBy { it.addDate },
            ) ?: return@forEach
            byCode[videoCode] = merge(
                byCode[videoCode],
                SeriesCandidate(
                    videoCode = best.videoCode,
                    title = best.title,
                    coverUrl = best.coverUrl,
                    timestamp = best.addDate,
                    duration = best.length,
                    isDownloaded = true,
                    localUri = best.videoUri,
                ),
            )
        }

        return byCode.values.toList()
    }

    fun organize(candidates: List<SeriesCandidate>): List<LocalSeries> {
        val parsed = candidates.mapNotNull { candidate ->
            parseTitle(candidate.title)?.let { parsed -> parsed to candidate }
        }

        return parsed.groupBy { it.first.key }
            .mapNotNull { (key, entries) ->
                val distinctEntries = entries.distinctBy { it.second.videoCode }
                if (distinctEntries.size < 2) return@mapNotNull null

                // 前篇/中篇/后篇中的“中篇”和“后篇”解析值都为 2；
                // 两者同时存在时把后篇提升为第 3 集，避免第三集被当作重复项丢弃。
                val hasMiddle = distinctEntries.any {
                    it.first.episode == 2 && it.second.title.containsMiddleMarker()
                }
                val hasEnd = distinctEntries.any {
                    it.first.episode == 2 && it.second.title.containsEndMarker()
                }
                val normalizedEntries = if (hasMiddle && hasEnd) {
                    distinctEntries.map { (parsed, candidate) ->
                        if (parsed.episode == 2 && candidate.title.containsEndMarker()) {
                            parsed.copy(episode = 3) to candidate
                        } else {
                            parsed to candidate
                        }
                    }
                } else {
                    distinctEntries
                }

                val episodeEntries = normalizedEntries
                    .groupBy { it.first.episode }
                    .mapNotNull { (_, duplicates) ->
                        duplicates.maxWithOrNull(episodePriority()) ?: return@mapNotNull null
                    }
                    .sortedBy { it.first.episode }

                if (episodeEntries.size < 2) return@mapNotNull null

                val episodes = episodeEntries.map { (parsedTitle, candidate) ->
                    LocalSeriesEpisode(
                        videoCode = candidate.videoCode,
                        title = candidate.title,
                        coverUrl = candidate.coverUrl,
                        episode = parsedTitle.episode,
                        progress = candidate.progress,
                        duration = candidate.duration,
                        isCompleted = candidate.isCompleted,
                        isFavorite = candidate.isFavorite,
                        isDownloaded = candidate.isDownloaded,
                        localUri = candidate.localUri,
                    )
                }

                val minEpisode = episodes.first().episode
                val maxEpisode = episodes.last().episode
                val missingEpisodes = if (maxEpisode - minEpisode <= 200) {
                    (minEpisode..maxEpisode).filter { episode ->
                        episodes.none { it.episode == episode }
                    }
                } else {
                    emptyList()
                }

                val latest = distinctEntries.maxByOrNull { it.second.timestamp }?.second
                LocalSeries(
                    id = key,
                    title = latest?.let { parseTitle(it.title)?.displayTitle }.orEmpty()
                        .ifBlank { latest?.title.orEmpty() },
                    coverUrl = latest?.coverUrl.orEmpty()
                        .ifBlank { episodes.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty() },
                    episodes = episodes,
                    missingEpisodes = missingEpisodes,
                    latestTimestamp = distinctEntries.maxOfOrNull { it.second.timestamp } ?: 0L,
                )
            }
            .sortedByDescending { it.latestTimestamp }
    }

    private fun merge(base: SeriesCandidate?, candidate: SeriesCandidate): SeriesCandidate {
        if (base == null) return candidate
        return base.copy(
            title = candidate.title.ifBlank { base.title },
            coverUrl = candidate.coverUrl.ifBlank { base.coverUrl },
            timestamp = maxOf(base.timestamp, candidate.timestamp),
            progress = maxOf(base.progress, candidate.progress),
            duration = candidate.duration.takeIf { it > 0L } ?: base.duration,
            isCompleted = base.isCompleted || candidate.isCompleted,
            isFavorite = base.isFavorite || candidate.isFavorite,
            isDownloaded = base.isDownloaded || candidate.isDownloaded,
            localUri = candidate.localUri ?: base.localUri,
        )
    }

    private fun String.containsMiddleMarker(): Boolean =
        contains("中編") || contains("中篇") || contains("中集")

    private fun String.containsEndMarker(): Boolean =
        contains("後編") || contains("後篇") || contains("后篇") ||
            contains("下集") || contains("下篇")

    private fun episodePriority(): Comparator<Pair<ParsedTitle, SeriesCandidate>> =
        compareByDescending<Pair<ParsedTitle, SeriesCandidate>> {
            it.second.isCompleted || it.second.progress > 0L
        }
            .thenByDescending { it.second.isDownloaded }
            .thenByDescending { it.second.isFavorite }
            .thenByDescending { it.second.timestamp }

    internal fun parseTitle(rawTitle: String): ParsedTitle? {
        val raw = rawTitle.trim()
        if (raw.isBlank()) return null

        var markerRange: IntRange? = null
        var episode: Int? = null

        for (regex in markerRegexes) {
            val match = regex.find(raw) ?: continue
            episode = parseEpisodeNumber(match.groupValues.getOrNull(1)).takeIf { it > 0 }
            if (episode != null) {
                markerRange = match.range
                break
            }
        }

        if (episode == null) {
            val special = when {
                raw.contains("前編") -> "前編" to 1
                raw.contains("前篇") -> "前篇" to 1
                raw.contains("上集") -> "上集" to 1
                raw.contains("上篇") -> "上篇" to 1
                raw.contains("中編") -> "中編" to 2
                raw.contains("中篇") -> "中篇" to 2
                raw.contains("中集") -> "中集" to 2
                raw.contains("後編") -> "後編" to 2
                raw.contains("後篇") -> "後篇" to 2
                raw.contains("后篇") -> "后篇" to 2
                raw.contains("下集") -> "下集" to 2
                raw.contains("下篇") -> "下篇" to 2
                else -> null
            }
            if (special != null) {
                episode = special.second
                val start = raw.indexOf(special.first)
                markerRange = start until start + special.first.length
            }
        }

        val resolvedEpisode = episode ?: return null
        val displayTitle = cleanDisplayTitle(
            markerRange?.let { raw.removeRange(it) } ?: raw,
        )
        if (displayTitle.isBlank()) return null

        val key = normalize(displayTitle)
        if (key.isBlank()) return null

        return ParsedTitle(
            key = key,
            displayTitle = displayTitle,
            episode = resolvedEpisode,
        )
    }

    private fun cleanDisplayTitle(raw: String): String {
        var value = leadingTagRegex.replace(raw, "")
        value = qualifierRegex.replace(value, " ")
        value = value.replace(Regex("""^\s*[-–—_|:：·・]+\s*"""), "")
        value = trailingSeparatorRegex.replace(value, "")
        return value.replace(Regex("""\s+"""), " ").trim()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(normalizedPunctuationRegex, "")

    private fun parseEpisodeNumber(raw: String?): Int {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return -1
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        normalized.toIntOrNull()?.let { return it }
        return parseChineseNumber(normalized) ?: -1
    }

    private fun parseChineseNumber(value: String): Int? {
        if (value == "百" || value == "一百") return 100
        val tenIndex = value.indexOf('十')
        if (tenIndex >= 0) {
            val tensPart = value.substring(0, tenIndex)
            val onesPart = value.substring(tenIndex + 1)
            val tens = if (tensPart.isEmpty()) 1 else parseChineseDigits(tensPart) ?: return null
            val ones = if (onesPart.isEmpty()) 0 else parseChineseDigits(onesPart) ?: return null
            return tens * 10 + ones
        }
        return parseChineseDigits(value)
    }

    private fun parseChineseDigits(value: String): Int? {
        if (value.isEmpty()) return 0
        var result = 0
        value.forEach { char ->
            val digit = when (char) {
                '零', '〇' -> 0
                '一' -> 1
                '二' -> 2
                '三' -> 3
                '四' -> 4
                '五' -> 5
                '六' -> 6
                '七' -> 7
                '八' -> 8
                '九' -> 9
                else -> return null
            }
            result = result * 10 + digit
        }
        return result
    }

    private fun qualityScore(quality: String): Int {
        val normalized = quality.lowercase(Locale.ROOT)
        return when {
            "2160" in normalized || "4k" in normalized -> 4
            "1080" in normalized -> 3
            "720" in normalized -> 2
            "480" in normalized -> 1
            else -> 0
        }
    }
}
