package com.yenaly.han1meviewer.ui.viewmodel

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.NetworkRepo
import com.yenaly.han1meviewer.logic.dao.HistoryDatabase
import com.yenaly.han1meviewer.logic.dao.DownloadDatabase
import com.yenaly.han1meviewer.logic.log.HLumberjack
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.NetworkEnvironmentInspector
import com.yenaly.han1meviewer.logic.network.ServiceCreator
import com.yenaly.han1meviewer.logic.model.HomePage
import com.yenaly.han1meviewer.logic.state.WebsiteState
import com.yenaly.han1meviewer.logic.state.VideoLoadingState
import com.yenaly.yenaly_libs.utils.applicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

enum class NetworkCheckStatus { PENDING, RUNNING, PASS, WARN, FAIL, SKIPPED }

data class NetworkCheckItem(
    val id: String,
    val titleRes: Int,
    val status: NetworkCheckStatus = NetworkCheckStatus.PENDING,
    val detail: String? = null,
    val metric: String? = null,
)

data class NetworkDiagnosticsUiState(
    val items: List<NetworkCheckItem> = emptyList(),
    val isRunning: Boolean = false,
    val started: Boolean = false,
    val conclusion: String? = null,
    val suggestion: String? = null,
)

/**
 * 网络诊断：按顺序执行网络/热点环境、DNS、主站、内置 Hosts IP、DoH、视频 CDN 六项检查，
 * 每项独立超时、互不中断，全部结束后给出结论与建议。
 */
class NetworkDiagnosticsViewModel : ViewModel() {

    private companion object {
        const val ID_NETWORK_ENVIRONMENT = "network_environment"
        const val ID_DNS_SYSTEM = "dns_system"
        const val ID_MAIN_SITE = "main_site"
        const val ID_HOSTS_IPS = "hosts_ips"
        const val ID_DNS_DOH = "dns_doh"
        const val ID_VIDEO_CDN = "video_cdn"
    }

    private val _uiState = MutableStateFlow(NetworkDiagnosticsUiState())
    val uiState = _uiState.asStateFlow()

    fun runDiagnostics() {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.value = NetworkDiagnosticsUiState(
                items = initialItems(),
                isRunning = true,
                started = true,
            )

            val host = Preferences.baseUrl.toHttpUrlOrNull()?.host
            if (host.isNullOrBlank()) {
                finish(
                    conclusion = getString(R.string.network_diagnostics_bad_url),
                    suggestion = null,
                )
                return@launch
            }

            var dnsOk = false
            var mainOk = false
            var hostsOk = false
            var hostsTotal = 0
            var hostsReachable = 0
            var cdnStatus: NetworkCheckStatus = NetworkCheckStatus.SKIPPED
            var hotspotSuspected = false
            var networkAvailable = false
            var networkValidated = false
            var vpnActive = false
            var dohOk = false
            var mainHttpCode: Int? = null

            // 0. 网络类型与 Wi-Fi/热点特征
            updateItem(ID_NETWORK_ENVIRONMENT, NetworkCheckStatus.RUNNING)
            val environment = inspectNetworkEnvironment()
            applyItem(environment.item)
            hotspotSuspected = environment.hotspotSuspected
            networkAvailable = environment.networkAvailable
            networkValidated = environment.networkValidated
            vpnActive = environment.vpnActive

            // 1. 系统 DNS
            updateItem(ID_DNS_SYSTEM, NetworkCheckStatus.RUNNING)
            val dnsResult = checkSystemDns(host)
            applyItem(dnsResult)
            dnsOk = dnsResult.status == NetworkCheckStatus.PASS

            // 2. 主站连通
            updateItem(ID_MAIN_SITE, NetworkCheckStatus.RUNNING)
            val mainResult = checkMainSite()
            applyItem(mainResult.item)
            mainOk = mainResult.item.status == NetworkCheckStatus.PASS
            mainHttpCode = mainResult.httpCode

            // 3. 内置 Hosts IP 延迟
            updateItem(ID_HOSTS_IPS, NetworkCheckStatus.RUNNING)
            val hostsResult = checkHostsIps(host)
            applyItem(hostsResult.first)
            hostsTotal = hostsResult.second
            hostsReachable = hostsResult.third
            hostsOk = hostsTotal > 0 && hostsReachable > 0

            // 4. DoH
            updateItem(ID_DNS_DOH, NetworkCheckStatus.RUNNING)
            val dohResult = checkDoh(host)
            applyItem(dohResult)
            dohOk = dohResult.status == NetworkCheckStatus.PASS

            // 5. 视频 CDN 实测
            updateItem(ID_VIDEO_CDN, NetworkCheckStatus.RUNNING)
            val cdnResult = checkVideoCdn()
            applyItem(cdnResult.first)
            cdnStatus = cdnResult.first.status

            // 结论
            val (conclusion, suggestion) = buildConclusion(
                dnsOk = dnsOk,
                mainOk = mainOk,
                hostsOk = hostsOk,
                hostsTotal = hostsTotal,
                hostsReachable = hostsReachable,
                cdnStatus = cdnStatus,
                hotspotSuspected = hotspotSuspected,
                networkAvailable = networkAvailable,
                networkValidated = networkValidated,
                vpnActive = vpnActive,
                dohOk = dohOk,
                mainHttpCode = mainHttpCode,
            )
            finish(conclusion = conclusion, suggestion = suggestion)
        }
    }

    // ---------- 各项检查 ----------

    private fun inspectNetworkEnvironment(): NetworkEnvironmentResult {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return offlineNetworkResult()
        val network = runCatching { manager.activeNetwork }.getOrNull()
            ?: return offlineNetworkResult()
        val capabilities = runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
            ?: return offlineNetworkResult()
        val linkProperties = runCatching { manager.getLinkProperties(network) }.getOrNull()

        val transportLabels = buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                add(getString(R.string.network_diagnostics_transport_wifi))
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                add(getString(R.string.network_diagnostics_transport_cellular))
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                add(getString(R.string.network_diagnostics_transport_ethernet))
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                add(getString(R.string.network_diagnostics_transport_vpn))
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                add(getString(R.string.network_diagnostics_transport_bluetooth))
            }
        }.ifEmpty {
            listOf(getString(R.string.network_diagnostics_transport_other))
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = runCatching { manager.isActiveNetworkMetered }.getOrDefault(false)
        val vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val gateway = if (isWifi) linkProperties?.defaultGateway() else null
        val dnsServers = if (isWifi) {
            linkProperties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        } else {
            emptyList()
        }
        val likelyHotspot = isWifi &&
            NetworkEnvironmentInspector.isLikelyHotspot(gateway, dnsServers)
        val environmentItem = item(
            id = ID_NETWORK_ENVIRONMENT,
            status = when {
                !hasInternet -> NetworkCheckStatus.FAIL
                likelyHotspot || !validated -> NetworkCheckStatus.WARN
                else -> NetworkCheckStatus.PASS
            },
            detail = buildString {
                append(transportLabels.joinToString(" + "))
                appendLine()
                append(
                    getString(
                        if (validated) {
                            R.string.network_diagnostics_network_validated
                        } else {
                            R.string.network_diagnostics_network_not_validated
                        }
                    )
                )
                append(" · ")
                append(
                    getString(
                        if (metered) {
                            R.string.network_diagnostics_network_metered
                        } else {
                            R.string.network_diagnostics_network_unmetered
                        }
                    )
                )
                appendLine()
                if (!isWifi) {
                    append(getString(R.string.network_diagnostics_hotspot_not_wifi))
                } else {
                    append(
                        getString(
                            if (likelyHotspot) {
                                R.string.network_diagnostics_hotspot_suspected
                            } else {
                                R.string.network_diagnostics_hotspot_router
                            }
                        )
                    )
                }
                gateway?.takeIf { it.isNotBlank() }?.let {
                    appendLine()
                    append(getString(R.string.network_diagnostics_hotspot_gateway, it))
                }
                dnsServers.takeIf { it.isNotEmpty() }?.let {
                    appendLine()
                    append(
                        getString(
                            R.string.network_diagnostics_hotspot_dns,
                            it.take(3).joinToString(" · "),
                        )
                    )
                }
                if (likelyHotspot) {
                    appendLine()
                    append(getString(R.string.network_diagnostics_hotspot_advice))
                }
            },
        )
        return NetworkEnvironmentResult(
            item = environmentItem,
            hotspotSuspected = likelyHotspot,
            networkAvailable = hasInternet,
            networkValidated = validated,
            vpnActive = vpnActive,
        )
    }

    private fun offlineNetworkResult(): NetworkEnvironmentResult = NetworkEnvironmentResult(
        item = item(
            id = ID_NETWORK_ENVIRONMENT,
            status = NetworkCheckStatus.FAIL,
            detail = getString(R.string.network_diagnostics_transport_none),
        ),
        hotspotSuspected = false,
        networkAvailable = false,
        networkValidated = false,
        vpnActive = false,
    )

    private fun LinkProperties.defaultGateway(): String? =
        routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress

    private data class NetworkEnvironmentResult(
        val item: NetworkCheckItem,
        val hotspotSuspected: Boolean,
        val networkAvailable: Boolean,
        val networkValidated: Boolean,
        val vpnActive: Boolean,
    )

    private suspend fun checkSystemDns(host: String): NetworkCheckItem {
        val start = System.currentTimeMillis()
        val ips = withTimeoutOrNull(6_000) {
            withContext(Dispatchers.IO) {
                runCatching { Dns.SYSTEM.lookup(host) }.getOrNull()
            }
        }
        val cost = System.currentTimeMillis() - start
        if (ips.isNullOrEmpty()) {
            return item(
                id = ID_DNS_SYSTEM,
                status = NetworkCheckStatus.FAIL,
                detail = getString(R.string.network_diagnostics_dns_failed),
                metric = "${cost}ms",
            )
        }
        val addresses = ips.mapNotNull { it.hostAddress }
        // 只识别"明确不可能是合法解析结果"的地址：默认认为解析正常。
        // 不再用"是否属于 Cloudflare 网段"判断，避免站点更换 CDN 或域名本身不在
        // Cloudflare 时被误报成 DNS 污染。
        val polluted = addresses.filter { isPollutedAddress(it) }
        val status = when {
            polluted.isEmpty() -> NetworkCheckStatus.PASS
            polluted.size == addresses.size -> NetworkCheckStatus.FAIL
            else -> NetworkCheckStatus.WARN
        }
        return item(
            id = ID_DNS_SYSTEM,
            status = status,
            detail = buildString {
                append(addresses.take(4).joinToString(" · "))
                if (polluted.isNotEmpty()) {
                    append("\n")
                    append(getString(R.string.network_diagnostics_dns_polluted))
                }
            },
            metric = "${cost}ms",
        )
    }

    private suspend fun checkMainSite(): MainSiteCheckResult {
        val start = System.currentTimeMillis()
        val code = withTimeoutOrNull(10_000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(Preferences.baseUrl).get().build()
                    ServiceCreator.hClient.newCall(request).execute().use { it.code }
                }.getOrNull()
            }
        }
        val cost = System.currentTimeMillis() - start
        if (code == null) {
            return MainSiteCheckResult(
                item = item(
                    id = ID_MAIN_SITE,
                    status = NetworkCheckStatus.FAIL,
                    detail = getString(R.string.network_diagnostics_connect_failed),
                    metric = "${cost}ms",
                ),
                httpCode = null,
            )
        }
        val ok = code in 200..399
        return MainSiteCheckResult(
            item = item(
                id = ID_MAIN_SITE,
                status = if (ok) NetworkCheckStatus.PASS else NetworkCheckStatus.FAIL,
                detail = "HTTP $code",
                metric = "${cost}ms",
            ),
            httpCode = code,
        )
    }

    private data class MainSiteCheckResult(
        val item: NetworkCheckItem,
        val httpCode: Int?,
    )

    /** @return 三元组：检查项、IP 总数、可达 IP 数 */
    private suspend fun checkHostsIps(host: String): Triple<NetworkCheckItem, Int, Int> {
        val enabled = Preferences.useBuiltInHosts
        if (!enabled) {
            return Triple(
                item(
                    id = ID_HOSTS_IPS,
                    status = NetworkCheckStatus.SKIPPED,
                    detail = getString(R.string.network_diagnostics_hosts_disabled),
                ),
                0,
                0,
            )
        }
        val ips = withContext(Dispatchers.IO) {
            runCatching { HDns().getCDNList(host) }.getOrDefault(emptyList())
        }
        if (ips.isEmpty()) {
            return Triple(
                item(
                    id = ID_HOSTS_IPS,
                    status = NetworkCheckStatus.FAIL,
                    detail = getString(R.string.network_diagnostics_hosts_empty),
                ),
                0,
                0,
            )
        }
        // 必须在 IO 线程执行：Android 禁止主线程做网络操作，
        // 否则 Socket.connect 会抛 NetworkOnMainThreadException 并被误判成"全部超时"。
        val delays = withContext(Dispatchers.IO) {
            ips.map { ip -> ip to measureTcpDelay(ip) }
        }
        val reachable = delays.filter { it.second >= 0 }
        val detail = delays.take(6).joinToString("\n") { (ip, delay) ->
            if (delay >= 0) "$ip · ${delay}ms" else "$ip · ${getString(R.string.network_diagnostics_timeout)}"
        }
        val status = when {
            reachable.isEmpty() -> NetworkCheckStatus.FAIL
            reachable.size < ips.size -> NetworkCheckStatus.WARN
            else -> NetworkCheckStatus.PASS
        }
        return Triple(
            item(
                id = ID_HOSTS_IPS,
                status = status,
                detail = detail,
                metric = "${reachable.size}/${ips.size}",
            ),
            ips.size,
            reachable.size,
        )
    }

    private suspend fun checkDoh(host: String): NetworkCheckItem {
        if (!Preferences.useDoH) {
            return item(
                id = ID_DNS_DOH,
                status = NetworkCheckStatus.SKIPPED,
                detail = getString(R.string.network_diagnostics_doh_disabled),
            )
        }
        val start = System.currentTimeMillis()
        val ips = withTimeoutOrNull(10_000) {
            withContext(Dispatchers.IO) {
                runCatching { HDns().lookupByDoHOnly(host) }.getOrNull()
            }
        }
        val cost = System.currentTimeMillis() - start
        if (ips.isNullOrEmpty()) {
            return item(
                id = ID_DNS_DOH,
                status = NetworkCheckStatus.FAIL,
                detail = getString(R.string.network_diagnostics_doh_failed),
                metric = "${cost}ms",
            )
        }
        return item(
            id = ID_DNS_DOH,
            status = NetworkCheckStatus.PASS,
            detail = ips.mapNotNull { it.hostAddress }.take(3).joinToString(" · "),
            metric = "${cost}ms",
        )
    }

    /** 视频 CDN 实测：判定"线路是否可达"，状态码只作为业务层参考。 */
    private suspend fun checkVideoCdn(): Pair<NetworkCheckItem, String?> {
        val sample = resolveSampleUrl()
        if (sample.url.isNullOrBlank()) {
            return item(
                id = ID_VIDEO_CDN,
                status = NetworkCheckStatus.SKIPPED,
                detail = getString(R.string.network_diagnostics_cdn_no_sample),
            ) to null
        }
        val videoUrl = sample.url
        val cdnHost = videoUrl.toHttpUrlOrNull()?.host.orEmpty()
        val start = System.currentTimeMillis()
        val probe = withTimeoutOrNull(12_000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder()
                        .url(videoUrl)
                        .header("Range", "bytes=0-1")
                        .get()
                        .build()
                    ServiceCreator.hClient.newCall(request).execute().use { it.code }
                }.getOrNull()
            }
        }
        val cost = System.currentTimeMillis() - start

        // 关键判定：只要收到任何 HTTP 响应（含 4xx/5xx），就说明 CDN 线路本身是通的，
        // 状态码只反映业务层（签名过期、来源校验等）。只有完全无响应才是线路不通。
        if (probe == null) {
            return item(
                id = ID_VIDEO_CDN,
                status = NetworkCheckStatus.FAIL,
                detail = buildString {
                    if (cdnHost.isNotBlank()) appendLine(cdnHost)
                    append(getString(R.string.network_diagnostics_cdn_unreachable))
                    append("\n")
                    append(
                        getString(
                            R.string.network_diagnostics_cdn_sample_from,
                            sample.sourceLabel,
                        ),
                    )
                },
                metric = "${cost}ms",
            ) to videoUrl
        }
        val accepted = probe in 200..399
        return item(
            id = ID_VIDEO_CDN,
            status = if (accepted) NetworkCheckStatus.PASS else NetworkCheckStatus.WARN,
            detail = buildString {
                if (cdnHost.isNotBlank()) appendLine(cdnHost)
                append("HTTP $probe")
                if (!accepted) {
                    append(" · ")
                    append(getString(R.string.network_diagnostics_cdn_rejected_business))
                }
                append("\n")
                append(
                    getString(
                        R.string.network_diagnostics_cdn_sample_from,
                        sample.sourceLabel,
                    ),
                )
            },
            metric = "${cost}ms",
        ) to videoUrl
    }

    private data class CdnSample(val url: String?, val sourceLabel: String)

    /**
     * 三层兜底取样本：
     * 1. 下载记录里的 videoUrl —— 本地就有真实 CDN 地址，主站不可达时仍可测；
     * 2. 观看历史 → 实时解析 watch 页；
     * 3. 都没有则返回空，由调用方提示缺少样本。
     */
    private suspend fun resolveSampleUrl(): CdnSample {
        // ① 首页随便取一个视频 code 再解析：拿到的地址与播放器同源，
        //    最贴近真实播放链路，因此放在最优先。
        val onlineCode = withContext(Dispatchers.IO) {
            runCatching {
                val state = NetworkRepo.getHomePage().first()
                // NetworkRepo.getHomePage() 返回的是 WebsiteState，不是 PageLoadingState；
                // 原来这里强制转成 PageLoadingState.Success，永远失败，第一层样本从未生效。
                (state as? WebsiteState.Success<HomePage>)?.info
                    ?.latestHanime?.firstOrNull()?.videoCode
            }.getOrNull()
        }
        val onlineUrl = parseWatchPage(onlineCode)
        if (!onlineUrl.isNullOrBlank()) {
            return CdnSample(
                onlineUrl,
                getString(R.string.network_diagnostics_cdn_sample_online),
            )
        }

        // ② 观看历史里的视频 code → 实时解析 watch 页
        val historyCode = withContext(Dispatchers.IO) { recentVideoCode() }
        val historyUrl = parseWatchPage(historyCode)
        if (!historyUrl.isNullOrBlank()) {
            return CdnSample(
                historyUrl,
                getString(R.string.network_diagnostics_cdn_sample_history),
            )
        }

        // ③ 下载记录兜底：本地数据不需要联网，主站不可达时仍能测出 CDN 线路是否可达；
        //    注意其中的地址可能已过期，但"线路可达性"判定不受影响（有响应即视为可达）。
        val downloadUrl = withContext(Dispatchers.IO) {
            runCatching {
                DownloadDatabase.instance.hanimeDownloadDao.getAll()
                    .firstOrNull { it.videoUrl.isNotBlank() }
                    ?.videoUrl
            }.getOrNull()
        }
        if (!downloadUrl.isNullOrBlank()) {
            return CdnSample(
                downloadUrl,
                getString(R.string.network_diagnostics_cdn_sample_download),
            )
        }

        return CdnSample(null, "")
    }

    /** 解析 watch 页拿到真实视频地址；主站不可达时返回 null。 */
    private suspend fun parseWatchPage(videoCode: String?): String? {
        if (videoCode.isNullOrBlank()) return null
        return withTimeoutOrNull(15_000) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val state = NetworkRepo.getHanimeVideo(videoCode).first()
                    (state as? VideoLoadingState.Success)?.info
                        ?.videoUrls?.values?.firstOrNull()?.link
                }.getOrNull()
            }
        }
    }

    // ---------- 工具 ----------

    private suspend fun recentVideoCode(): String? = runCatching {
        val recent = HistoryDatabase.instance.watchHistory.getRecentWatches(1)
        recent.firstOrNull()?.videoCode
            ?: HistoryDatabase.instance.watchHistory.getAll().firstOrNull()?.videoCode
    }.getOrNull()

    private fun measureTcpDelay(ip: String, port: Int = 443, timeoutMs: Int = 2_000): Int {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            (System.currentTimeMillis() - start).toInt()
        } catch (t: Throwable) {
            // 记录异常：此前静默吞掉 NetworkOnMainThreadException，导致"全部超时"难以定位
            HLumberjack.w("NETDIAG", "tcp probe failed for $ip:$port - ${t.message}")
            -1
        }
    }

    /**
     * 是否是"明确不可能为合法解析结果"的地址。
     * 只覆盖投毒/劫持的典型特征，其余一律视为正常，避免误报。
     */
    private fun isPollutedAddress(address: String): Boolean {
        val inet = runCatching { InetAddress.getByName(address) }.getOrNull() ?: return true
        if (inet.isLoopbackAddress || inet.isAnyLocalAddress || inet.isSiteLocalAddress) return true
        if (inet !is Inet4Address) return false
        val parts = address.split('.')
        if (parts.size < 2) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        // GFW DNS 投毒最常用的假 IP 池（Facebook 网段）
        return (a == 31 && b == 13) || (a == 69 && b == 171) || (a == 157 && b == 240)
    }

    private data class Conclusion(val text: String, val suggestion: String?)

    private fun buildConclusion(
        dnsOk: Boolean,
        mainOk: Boolean,
        hostsOk: Boolean,
        hostsTotal: Int,
        hostsReachable: Int,
        cdnStatus: NetworkCheckStatus,
        hotspotSuspected: Boolean,
        networkAvailable: Boolean,
        networkValidated: Boolean,
        vpnActive: Boolean,
        dohOk: Boolean,
        mainHttpCode: Int?,
    ): Conclusion {
        val g = { res: Int -> getString(res) }
        return when {
            !networkAvailable -> Conclusion(
                g(R.string.network_diagnostics_conclusion_offline),
                g(R.string.network_diagnostics_suggest_connect_network),
            )
            !mainOk && (mainHttpCode == 403 || mainHttpCode == 429) -> Conclusion(
                getString(R.string.network_diagnostics_conclusion_main_rate_limited, mainHttpCode),
                g(R.string.network_diagnostics_suggest_main_rate_limited),
            )
            !mainOk && mainHttpCode != null && mainHttpCode in 500..599 -> Conclusion(
                getString(R.string.network_diagnostics_conclusion_main_server_error, mainHttpCode),
                g(R.string.network_diagnostics_suggest_main_server_error),
            )
            !mainOk && mainHttpCode != null -> Conclusion(
                getString(R.string.network_diagnostics_conclusion_main_http_error, mainHttpCode),
                g(R.string.network_diagnostics_suggest_switch_domain),
            )
            hotspotSuspected && !mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_hotspot),
                g(R.string.network_diagnostics_suggest_hotspot),
            )
            vpnActive && !mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_vpn_blocked),
                g(R.string.network_diagnostics_suggest_vpn),
            )
            !networkValidated && !mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_network_unvalidated),
                g(R.string.network_diagnostics_suggest_network_unvalidated),
            )
            !mainOk && hostsTotal > 0 && hostsReachable == 0 -> Conclusion(
                g(R.string.network_diagnostics_conclusion_hosts_dead),
                g(R.string.network_diagnostics_suggest_renew_hosts),
            )
            !mainOk && hostsOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_hosts_tls_blocked),
                g(R.string.network_diagnostics_suggest_tls_blocked),
            )
            !mainOk && dohOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_doh_tls_blocked),
                g(R.string.network_diagnostics_suggest_tls_blocked),
            )
            !dnsOk && !mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_dns_broken),
                g(R.string.network_diagnostics_suggest_check_network),
            )
            // DNS 能解析、但主站连不上：地址查得到却走不通，典型的 IP 封锁或 SNI 阻断。
            // 这个组合此前会掉进兜底分支，误报成"结果不完整，建议换域名"。
            // 建议分两级：还没开内置 Hosts 就先让用户开它；已经开了仍然不通，才建议走代理。
            dnsOk && !mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_main_blocked),
                // 依据"内置 Hosts 开关是否打开"而不是 hostsTotal：
                // Hosts 已开启但 IP 列表取空（解析异常）时 hostsTotal 也是 0，
                // 用它会错误地提示"先开启内置 Hosts"。
                if (Preferences.useBuiltInHosts) {
                    g(R.string.network_diagnostics_suggest_proxy)
                } else {
                    g(R.string.network_diagnostics_suggest_enable_hosts)
                },
            )
            mainOk && cdnStatus == NetworkCheckStatus.FAIL -> Conclusion(
                g(R.string.network_diagnostics_conclusion_cdn_blocked),
                g(R.string.network_diagnostics_suggest_proxy),
            )
            mainOk && cdnStatus == NetworkCheckStatus.WARN -> Conclusion(
                g(R.string.network_diagnostics_conclusion_cdn_rejected),
                g(R.string.network_diagnostics_suggest_switch_domain),
            )
            mainOk && cdnStatus == NetworkCheckStatus.PASS -> Conclusion(
                g(R.string.network_diagnostics_conclusion_all_good),
                null,
            )
            mainOk && cdnStatus == NetworkCheckStatus.SKIPPED -> Conclusion(
                g(R.string.network_diagnostics_conclusion_cdn_no_sample),
                g(R.string.network_diagnostics_suggest_play_then_retry),
            )
            mainOk -> Conclusion(
                g(R.string.network_diagnostics_conclusion_main_only),
                g(R.string.network_diagnostics_suggest_proxy_if_video_fails),
            )
            else -> Conclusion(
                g(R.string.network_diagnostics_conclusion_partial),
                g(R.string.network_diagnostics_suggest_switch_domain),
            )
        }
    }

    // ---------- 状态更新 ----------

    private fun initialItems() = listOf(
        NetworkCheckItem(
            ID_NETWORK_ENVIRONMENT,
            R.string.network_diagnostics_check_network_environment,
        ),
        NetworkCheckItem(ID_DNS_SYSTEM, R.string.network_diagnostics_check_dns),
        NetworkCheckItem(ID_MAIN_SITE, R.string.network_diagnostics_check_main),
        NetworkCheckItem(ID_HOSTS_IPS, R.string.network_diagnostics_check_hosts),
        NetworkCheckItem(ID_DNS_DOH, R.string.network_diagnostics_check_doh),
        NetworkCheckItem(ID_VIDEO_CDN, R.string.network_diagnostics_check_cdn),
    )

    private fun item(
        id: String,
        status: NetworkCheckStatus,
        detail: String? = null,
        metric: String? = null,
    ) = NetworkCheckItem(id = id, titleRes = 0, status = status, detail = detail, metric = metric)

    private fun applyItem(result: NetworkCheckItem) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    // 检查项的 id 与 titleRes 绑定的资源 id 一致，直接按资源 id 定位
                    if (item.id == result.id) item.copy(
                        status = result.status,
                        detail = result.detail,
                        metric = result.metric,
                    ) else item
                },
            )
        }
    }

    private fun updateItem(id: String, status: NetworkCheckStatus) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == id) it.copy(status = status) else it })
        }
    }

    private fun finish(conclusion: String, suggestion: String?) {
        _uiState.update {
            it.copy(isRunning = false, conclusion = conclusion, suggestion = suggestion)
        }
    }

    private fun getString(resId: Int): String = applicationContext.getString(resId)

    private fun getString(resId: Int, vararg args: Any): String =
        applicationContext.getString(resId, *args)
}
