package com.yenaly.han1meviewer.util

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * 在 MP4 末尾追加一个标准 free box，用于携带 info.json 和封面。
 *
 * 该 box 对播放器透明，不修改视频轨道，也不重新编码。
 * 读取时从文件末尾定位，因此单个视频文件即可恢复下载元数据与封面。
 */
object EmbeddedVideoMetadata {
    private val boxType = "free".toByteArray(Charsets.US_ASCII)
    private val magic = "H1MMETA1".toByteArray(Charsets.US_ASCII)
    private val footerMagic = "H1MEND01".toByteArray(Charsets.US_ASCII)
    private const val version = 1
    private const val fixedSize = 8 + 8 + 4 + 4 + 4 + 16

    data class Payload(
        val infoJson: String,
        val coverBytes: ByteArray?,
    )

    fun write(
        context: Context,
        file: File?,
        uri: Uri?,
        infoJson: ByteArray,
        coverBytes: ByteArray?,
    ): Boolean {
        val box = encodeBox(infoJson, coverBytes) ?: return false
        if (uri == null && file != null) {
            return writeToFile(file, box)
        }
        return runCatching {
            val target = uri ?: file?.toUri() ?: return false
            val existingStart = readTrailerStart(context, target)
            context.contentResolver.openFileDescriptor(target, "rw")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).channel.use { channel ->
                    val start = existingStart ?: channel.size()
                    if (existingStart != null) {
                        channel.truncate(existingStart)
                    }
                    channel.position(start)
                    writeFully(channel, ByteBuffer.wrap(box))
                    channel.force(true)
                }
            } != null
        }.getOrDefault(false)
    }

    internal fun writeToFile(file: File, infoJson: ByteArray, coverBytes: ByteArray?): Boolean {
        val box = encodeBox(infoJson, coverBytes) ?: return false
        return writeToFile(file, box)
    }

    internal fun readFromFile(file: File): Payload? =
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                this@EmbeddedVideoMetadata.readPayload(raf.channel)
            }
        }.getOrNull()

    private fun writeToFile(file: File, box: ByteArray): Boolean =
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                val channel = raf.channel
                val start = findTrailerStart(channel)
                if (start != null) {
                    channel.truncate(start)
                    channel.position(start)
                } else {
                    channel.position(channel.size())
                }
                writeFully(channel, ByteBuffer.wrap(box))
                channel.force(true)
            }
            true
        }.getOrDefault(false)

    fun read(context: Context, uri: Uri): Payload? =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { readPayload(it) }
            }
        }.getOrNull()

    fun cacheCover(context: Context, videoCode: String, bytes: ByteArray): String? =
        runCatching {
            val folder = File(context.filesDir, "embedded_covers").apply { mkdirs() }
            val file = File(folder, "$videoCode.img")
            FileOutputStream(file).use { it.write(bytes) }
            file.toUri().toString()
        }.getOrNull()

    internal fun encodeBox(infoJson: ByteArray, coverBytes: ByteArray?): ByteArray? {
        val cover = coverBytes ?: ByteArray(0)
        val total = fixedSize + infoJson.size + cover.size
        if (total <= 0) return null
        return ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(total)
            put(boxType)
            put(magic)
            putInt(version)
            putInt(infoJson.size)
            putInt(cover.size)
            put(infoJson)
            put(cover)
            putLong(total.toLong())
            put(footerMagic)
        }.array()
    }

    private fun readPayload(channel: FileChannel): Payload? {
        val start = findTrailerStart(channel) ?: return null
        val boxSize = (channel.size() - start).toInt()
        if (boxSize < fixedSize) return null

        val buffer = ByteBuffer.allocate(boxSize)
        if (readFully(channel, buffer, start) != boxSize) return null
        return decodeBox(buffer.array())
    }

    internal fun decodeBox(box: ByteArray): Payload? {
        if (box.size < fixedSize) return null
        val buffer = ByteBuffer.wrap(box).order(ByteOrder.BIG_ENDIAN)
        if (buffer.int != box.size) return null
        val type = ByteArray(4).also(buffer::get)
        if (!type.contentEquals(boxType)) return null
        val fileMagic = ByteArray(magic.size).also(buffer::get)
        if (!fileMagic.contentEquals(magic)) return null
        if (buffer.int != version) return null

        val infoSize = buffer.int
        val coverSize = buffer.int
        if (infoSize < 0 || coverSize < 0 || infoSize + coverSize + fixedSize != box.size) {
            return null
        }

        val info = ByteArray(infoSize).also(buffer::get)
        val cover = ByteArray(coverSize).also(buffer::get)
        return Payload(
            infoJson = info.toString(Charsets.UTF_8),
            coverBytes = cover.takeIf { it.isNotEmpty() },
        )
    }

    private fun findTrailerStart(channel: FileChannel): Long? {
        val fileSize = channel.size()
        if (fileSize < fixedSize) return null
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        if (readFully(channel, buffer, fileSize - 16) != 16) return null
        buffer.flip()
        val boxSize = buffer.long
        val footer = ByteArray(footerMagic.size).also(buffer::get)
        if (!footer.contentEquals(footerMagic) || boxSize < fixedSize || boxSize > fileSize) {
            return null
        }
        return fileSize - boxSize
    }

    private fun readTrailerStart(context: Context, uri: Uri): Long? =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use(::findTrailerStart)
            }
        }.getOrNull()

    private fun readFully(channel: FileChannel, buffer: ByteBuffer, position: Long): Int {
        var offset = position
        var read = 0
        while (buffer.hasRemaining()) {
            val count = channel.read(buffer, offset)
            if (count <= 0) break
            read += count
            offset += count
        }
        return read
    }

    private fun writeFully(channel: FileChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            channel.write(buffer)
        }
    }
}