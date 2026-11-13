package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.GITHUB_UPDATE_MIRROR
import org.junit.Assert.assertEquals
import org.junit.Test

class HUpdaterMirrorTest {

    @Test
    fun githubReleaseUrlUsesMirror() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.151/app.apk"

        assertEquals(GITHUB_UPDATE_MIRROR + direct, direct.toGitHubUpdateMirrorUrl())
    }

    @Test
    fun nonGithubUrlIsNotRewritten() {
        val apiUrl = "https://api.github.com/repos/khxw/Han1meViewer/releases/latest"
        assertEquals(apiUrl, apiUrl.toGitHubUpdateMirrorUrl())
    }

    @Test
    fun existingMirrorUrlIsNotDoublePrefixed() {
        val mirrorUrl = GITHUB_UPDATE_MIRROR +
            "https://github.com/khxw/Han1meViewer/releases/download/v2.1.151/app.apk"
        assertEquals(mirrorUrl, mirrorUrl.toGitHubUpdateMirrorUrl())
    }

    @Test
    fun mirrorEnabledUsesMirrorThenGithubFallback() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.153/app.apk"

        assertEquals(
            listOf(GITHUB_UPDATE_MIRROR + direct, direct),
            buildUpdateDownloadCandidates(direct, useMirror = true),
        )
    }

    @Test
    fun mirrorDisabledUsesOnlyGithub() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.153/app.apk"
        val mirror = GITHUB_UPDATE_MIRROR + direct

        assertEquals(
            listOf(direct),
            buildUpdateDownloadCandidates(direct, useMirror = false),
        )
        assertEquals(
            listOf(direct),
            buildUpdateDownloadCandidates(mirror, useMirror = false),
        )
    }

    @Test
    fun nonGithubUrlIsNotDuplicatedWhenMirrorEnabled() {
        val apiUrl = "https://api.github.com/repos/khxw/Han1meViewer/releases/latest"

        assertEquals(
            listOf(apiUrl),
            buildUpdateDownloadCandidates(apiUrl, useMirror = true),
        )
    }
}
