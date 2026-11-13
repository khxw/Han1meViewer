package com.yenaly.han1meviewer.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmbeddedVideoMetadataTest {
    @Test
    fun roundTripsInfoAndCover() {
        val info = """{"title":"测试"}""".toByteArray()
        val cover = byteArrayOf(1, 2, 3, 4)

        val box = EmbeddedVideoMetadata.encodeBox(info, cover)!!
        val payload = EmbeddedVideoMetadata.decodeBox(box)!!

        assertEquals("""{"title":"测试"}""", payload.infoJson)
        assertArrayEquals(cover, payload.coverBytes)
    }

    @Test
    fun roundTripsThroughRealFile() {
        val file = File.createTempFile("embedded-video", ".mp4").apply {
            writeBytes(ByteArray(32))
        }
        try {
            val info = """{"title":"文件测试"}""".toByteArray()
            val cover = byteArrayOf(9, 8, 7)
            assertTrue(EmbeddedVideoMetadata.writeToFile(file, info, cover))

            val payload = EmbeddedVideoMetadata.readFromFile(file)!!
            assertEquals("""{"title":"文件测试"}""", payload.infoJson)
            assertArrayEquals(cover, payload.coverBytes)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsInvalidBox() {
        assertNull(EmbeddedVideoMetadata.decodeBox(byteArrayOf(0, 1, 2)))
    }
}
