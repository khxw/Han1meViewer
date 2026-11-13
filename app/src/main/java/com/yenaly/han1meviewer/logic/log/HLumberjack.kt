package com.yenaly.han1meviewer.logic.log

import android.util.Log
import com.yenaly.han1meviewer.BuildConfig
import com.yenaly.han1meviewer.Preferences
import com.yenaly.yenaly_libs.utils.applicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 轻量日志器：内存环形缓冲 + 私有目录落盘。
 *
 * 用于排查用户反馈的网络加载失败问题（如内置 Hosts 下首页加载失败），
 * 记录 DNS 解析结果与 HTTP 请求结果。关闭开关后停止记录，但保留已有日志；
 * 应用重启后从落盘文件恢复最近一次运行期间的日志。
 *
 * @project Han1meViewer
 * @author Yenaly Liew
 */
object HLumberjack {

    const val MAX_ENTRIES = 300

    private const val MAX_FILE_BYTES = 200 * 1024

    const val LOG_DIR = "logs"

    const val LOG_FILE_NAME = "hanime_log.txt"

    private val lock = Any()

    private val entries = ArrayDeque<LogEntry>()

    // 全局自增 id：内存与落盘恢复的条目均以此保证唯一（供列表 key 使用）
    private val idCounter = AtomicLong(0)

    private val timeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val diskTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    }

    private val lineRegex =
        Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[([VDIWEF)])\] (.*?): (.*)$""")

    data class LogEntry(
        val id: Long,
        val timeMillis: Long,
        val level: Char,
        val tag: String,
        val message: String,
    ) {
        fun formatted(): String {
            val time = requireNotNull(timeFormat.get()).format(Date(timeMillis))
            return "$time [$level] $tag: $message"
        }
    }

    fun d(tag: String, message: String) = log(Log.DEBUG, tag, message)

    fun i(tag: String, message: String) = log(Log.INFO, tag, message)

    fun w(tag: String, message: String) = log(Log.WARN, tag, message)

    fun e(tag: String, message: String) = log(Log.ERROR, tag, message)

    private fun log(priority: Int, tag: String, message: String) {
        Log.println(priority, tag, message)
        if (!Preferences.enableLogging) return
        val entry = LogEntry(idCounter.getAndIncrement(), System.currentTimeMillis(), priorityChar(priority), tag, message)
        synchronized(lock) {
            entries.addLast(entry)
            if (entries.size > MAX_ENTRIES) {
                entries.removeFirst()
            }
            appendToFile(entry)
        }
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.ERROR -> 'E'
        Log.WARN -> 'W'
        Log.INFO -> 'I'
        else -> 'D'
    }

    /** 内存日志快照（新→旧）。 */
    fun entries(): List<LogEntry> = synchronized(lock) { entries.toList().asReversed() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            logFile().delete()
        }
    }

    /** 导出为可分享的纯文本，头部附带版本信息便于开发者识别。 */
    fun exportText(): String = synchronized(lock) {
        buildString {
            appendLine("Han1meViewer v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
            appendLine("Built-in hosts: ${Preferences.useBuiltInHosts}, DoH: ${Preferences.useDoH}")
            appendLine("---")
            entries.forEach { appendLine(it.formatted()) }
        }
    }

    /** 启动时恢复上次运行期间落盘的日志。 */
    fun loadFromDisk() {
        synchronized(lock) {
            val file = logFile()
            if (!file.exists()) return
            val lines = runCatching { file.readLines() }.getOrNull() ?: return
            entries.clear()
            lines.takeLast(MAX_ENTRIES).forEach { line ->
                lineRegex.find(line)?.let { match ->
                    entries.addLast(
                        run {
                            val rawTime = match.groupValues[1]
                            val parsedTime = runCatching {
                                val year = Calendar.getInstance().get(Calendar.YEAR)
                                requireNotNull(diskTimeFormat.get()).parse("$year-$rawTime")?.time
                            }.getOrNull() ?: System.currentTimeMillis()
                            LogEntry(
                                id = idCounter.getAndIncrement(),
                                timeMillis = parsedTime,
                                level = match.groupValues[2].first(),
                                tag = match.groupValues[3],
                                message = match.groupValues[4],
                            )
                        },
                    )
                }
            }
        }
    }

    private fun logFile(): File = File(File(applicationContext.filesDir, LOG_DIR), LOG_FILE_NAME)

    private fun appendToFile(entry: LogEntry) {
        val file = logFile()
        file.parentFile?.mkdirs()
        file.appendText(entry.formatted() + "\n")
        if (file.length() > MAX_FILE_BYTES) {
            // 超限截断：仅保留文件尾部内容，保证分享的日志以最新记录为主
            val bytes = file.readBytes()
            val start = (bytes.size - MAX_FILE_BYTES / 2).coerceAtLeast(0)
            val keep = String(bytes.copyOfRange(start, bytes.size), Charsets.UTF_8)
            file.writeText(keep.substringAfter('\n'))
        }
    }
}
