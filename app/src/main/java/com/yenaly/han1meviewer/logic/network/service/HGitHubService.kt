package com.yenaly.han1meviewer.logic.network.service

import com.yenaly.han1meviewer.logic.model.github.Release
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/09/09 009 20:04
 */
interface HGitHubService {
    @GET("releases/latest")
    suspend fun getLatestVersion(): Release

    /**
     * 通过 GitHub Contents API 直接获取仓库根目录的 announcements.json 原文。
     *
     * 相比 raw.githubusercontent.com，走 api.github.com（应用已用固定 IP 的
     * [GitHubDns][com.yenaly.han1meviewer.logic.network.GitHubDns] 可靠连通，检查更新即走此通道），
     * 在受限网络下更稳。Accept: application/vnd.github.raw 让响应体即为文件原文。
     */
    @Headers("Accept: application/vnd.github.raw")
    @GET("contents/announcements.json")
    suspend fun getAnnouncements(
        @Query("ref") ref: String,
    ): Response<ResponseBody>

    /**
     * Typical request
     */
    @GET
    @Streaming
    suspend fun request(
        @Url url: String,
    ): Response<ResponseBody>
}
