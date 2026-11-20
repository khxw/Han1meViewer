package com.yenaly.han1meviewer.logic.network

import com.yenaly.han1meviewer.GITHUB_UPDATE_MIRROR
import com.yenaly.han1meviewer.GITHUB_UPDATE_MIRROR_COMPAT
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
        val mirrorUrl = GITHUB_UPDATE_MIRRORS.last() +
            "https://github.com/khxw/Han1meViewer/releases/download/v2.1.151/app.apk"
        assertEquals(mirrorUrl, mirrorUrl.toGitHubUpdateMirrorUrl())
    }

    @Test
    fun mirrorEnabledUsesAllMirrorsThenGithubFallback() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.156/app.apk"

        assertEquals(
            GITHUB_UPDATE_MIRRORS.map { it + direct } + direct,
            buildUpdateDownloadCandidates(direct, useMirror = true),
        )
    }

    @Test
    fun mirrorDisabledUsesOnlyGithub() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.153/app.apk"
        val mirror = GITHUB_UPDATE_MIRRORS.last() + direct

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

    @Test
    fun everyMirrorPrefixIsNormalizedToGithub() {
        val direct = "https://github.com/khxw/Han1meViewer/releases/download/v2.1.171/app.apk"

        GITHUB_UPDATE_MIRRORS.forEach { prefix ->
            assertEquals(
                listOf(direct),
                buildUpdateDownloadCandidates(prefix + direct, useMirror = false),
            )
        }
    }
}
