package com.yenaly.han1meviewer.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceVideoScannerTest {

    @Test
    fun appDownloadPathsAreExcludedFromDeviceVideos() {
        assertTrue(
            "Download/MyVideos/hanime_download/65225/video.mp4".isHanimeDownloadPath()
        )
        assertTrue(
            "Download/MyVideos/HanimeDownload/65225/video.mp4".isHanimeDownloadPath()
        )
        assertTrue(
            "/storage/emulated/0/Download/HANIME_DOWNLOAD/65225/video.mp4".isHanimeDownloadPath()
        )
    }

    @Test
    fun unrelatedPhoneVideosAreNotExcluded() {
        assertFalse("DCIM/Camera/VID_20261031.mp4".isHanimeDownloadPath())
        assertFalse("Movies/Travel/clip.mp4".isHanimeDownloadPath())
    }
}
