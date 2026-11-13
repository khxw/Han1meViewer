@file:Suppress("UnstableApiUsage")

import org.gradle.api.Project
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * @author Yenaly Liew
 * @time 2023/11/25 025 17:55
 */
object Config {

    val Project.isRelease: Boolean
        get() = gradle.startParameter.taskNames.any { it.contains("Release") }

    object Version {

        const val DEBUG = "debug"
        const val RELEASE = "release"
//        const val CI = "ci"

        /**
         * 创建版本号
         *
         * @return 版本号和版本名
         *
         *         Example:
         *         release: 0.1.2
         *         debug (fixed): debug+1
         */
        fun Project.createVersion(
            major: Int,
            minor: Int,
            patch: Int,
            versionCodePatch: Int = patch,
        ): Pair<Int, String> {
            val source = this.source
            val versionCode: Int
            val versionName: String
            when (source) {
                DEBUG -> {
                    versionCode = 1
                    versionName = "$DEBUG+$versionCode"
                }

                else -> {
                    // 日期保证跨天递增，versionCodePatch 保证同一日期内严格递增。
                    // 显示版本与 versionCode 后缀可独立，避免语义化版本降号时被 Android 判定为降级。
                    // 例：2026-09-13 + versionCodePatch 65 -> 260913065。
                    versionCode = LocalDateTime.now(Clock.systemUTC()).format(
                        DateTimeFormatter.ofPattern("yyMMdd")
                    ).toInt() * 1000 + versionCodePatch
                    versionName = "${major}.${minor}.${patch}"
                }
            }
            return versionCode to versionName
        }

        /**
         * 版本来源，用于区分不同的版本
         *
         * @return ci 或 release 或 debug
         */

        val Project.source: String
            get() = System.getenv("HA1_VERSION_SOURCE") ?: kotlin.run {
                return if (isRelease) RELEASE else DEBUG
            }
    }

    val Project.lastCommitSha: String
        get() = providers.exec {
            commandLine = "git rev-parse --short=7 HEAD".split(' ')
        }.standardOutput.asText.get().trim()

    val thisYear: Int
        get() = LocalDateTime.now(Clock.system(ZoneId.of("UTC+8"))).year
}