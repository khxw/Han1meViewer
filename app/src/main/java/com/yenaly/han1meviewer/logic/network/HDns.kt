package com.yenaly.han1meviewer.logic.network

import android.util.Log
import com.yenaly.han1meviewer.HanimeConstants.HANIME_HOSTNAME
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.log.HLumberjack
import okhttp3.Dns
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2024/03/10 010 17:01
 */
class HDns(
    /**
     * true 时不走内建 Hosts 的硬编码 IP，直接交给 DoH / 系统解析。
     *
     * 漫画等「非当前主域名」的镜像域名并不一定在 Cloudflare 上，
     * 强行用 CF IP 配该域名的 SNI 会被服务端重置连接（Connection reset）。
     */
    private val ignoreBuiltInHosts: Boolean = false,
    /**
     * 内建 Hosts 开启时 DoH 是关闭的；为 true 时仍强制走 DoH 解析。
     *
     * 漫画域名（如 hanimeone.me）的系统 DNS 会被投毒，而内建 Hosts 里那组
     * Cloudflare IP 对部分域名并不适用，因此需要绕开两者、用 DoH 拿真实 IP。
     */
    private val forceDohWhenDisabled: Boolean = false,
) : Dns {

    private data class DohRuntimeConfig(
        val url: String,
        val bootstrapIps: List<String>,
        val timeoutSeconds: Int,
    )

    @Volatile
    private var cachedDohConfig: DohRuntimeConfig? = null

    @Volatile
    private var cachedDohDns: Dns? = null

    companion object {

        private val cloudFlareIps = listOf(
            "172.64.229.154", "162.159.0.1", "108.162.192.1", "172.64.33.1", "104.19.0.1",
            "2606:4700:3035::ac43:bb8d", "2606:4700:3030::6815:746", "2606:4700:3030::6815:714"
        )

        private val getchuIps = listOf("210.155.150.166", "210.155.150.145")

        /**
         * 漫画相关域名的已知可用 IP。
         *
         * 背景：这些域名（尤其 hanimeone.me）在部分网络下系统 DNS 被投毒、DoH 也被干扰，
         * 解析拿不到任何可用地址；这里写死一份经过实测的地址作为兜底，
         * 让请求至少能走到 TCP/TLS 阶段（再由 ClientHello 分片去绕 SNI 识别）。
         *
         * 2026-10-12 通过 DoH（doh.pub）实测得到。IP 可能随时间变化。
         */
        private val comicStaticIps = mapOf(
            "hanimeone.me" to listOf("104.21.43.14", "172.67.215.214"),
            "i.nhentai.net" to listOf("213.152.165.53", "213.152.165.54", "77.247.178.1"),
            "t1.nhentai.net" to listOf("104.244.43.182"),
            "t2.nhentai.net" to listOf("213.152.165.54"),
        )

        private const val GETCHU_HOSTNAME = "www.getchu.com"

        /**
         * 添加DNS
         */
        private operator fun MutableMap<String, List<InetAddress>>.set(
            host: String, ips: List<String>,
        ) {
            this[host] = ips.map {
                InetAddress.getByAddress(host, InetAddress.getByName(it).address)
            }
        }

        /**
         * 解析自定义 IP 列表，逗号分隔
         */
        fun parseCustomIps(raw: String): List<String> {
            return raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        /**
         * 验证自定义 IP 列表格式是否有效
         * @return 无效 IP 的错误信息列表，为空表示全部有效
         */
        fun validateCustomHosts(raw: String): List<String> {
            val errors = mutableListOf<String>()
            if (raw.isBlank()) return errors
            val ips = parseCustomIps(raw)
            if (ips.isEmpty()) {
                errors.add("No IP addresses entered")
                return errors
            }
            ips.forEach { ip ->
                if (!isValidIpAddress(ip)) {
                    errors.add("Invalid IP address: \"$ip\"")
                }
            }
            return errors
        }

        private fun isValidIpAddress(ip: String): Boolean {
            return runCatching {
                val addr = InetAddress.getByName(ip)
                addr.hostAddress == ip || addr.hostAddress == ip.removePrefix("[")
                    .removeSuffix("]")
            }.getOrDefault(false)
        }

        /**
         * 通用污染黑名单：回环/未指定地址，以及 GFW 投毒最常见的假 IP 池（Facebook 网段），
         * 均不可能是合法解析结果。
         */
        private fun isPollutedAddress(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress) return true
            val host = address.hostAddress ?: return false
            if (address !is Inet4Address) return false
            val parts = host.split('.')
            if (parts.size < 2) return false
            val a = parts[0].toIntOrNull() ?: return false
            val b = parts[1].toIntOrNull() ?: return false
            // Facebook 网段：GFW DNS 投毒最常见的假 IP 池
            return (a == 31 && b == 13) || (a == 69 && b == 171) || (a == 157 && b == 240)
        }

        /**
         * hanime1 系列域名托管于 Cloudflare，权威解析结果必在 Cloudflare 网段内；
         * 其余网段（含 IPv6）均为 DNS 投毒结果，予以剔除。
         */
        private fun isCloudflareAddress(address: InetAddress): Boolean {
            val host = address.hostAddress ?: return false
            if (address is Inet6Address) {
                // Cloudflare IPv6 网段 2606:4700::/32
                return host.lowercase().startsWith("2606:4700:")
            }
            if (address !is Inet4Address) return false
            val parts = host.split('.')
            if (parts.size < 3) return false
            val a = parts[0].toIntOrNull() ?: return false
            val b = parts[1].toIntOrNull() ?: return false
            val c = parts[2].toIntOrNull() ?: return false
            return when (a) {
                104 -> b in 16..31                    // 104.16.0.0/12
                172 -> b in 64..71                    // 172.64.0.0/13
                162 -> b == 158 || b == 159           // 162.158.0.0/15
                173 -> b == 245 && c in 48..63        // 173.245.48.0/20
                188 -> b == 114 && c in 96..111       // 188.114.96.0/20
                190 -> b == 93 && c in 240..255       // 190.93.240.0/20
                197 -> b == 234 && c in 240..243      // 197.234.240.0/22
                198 -> b == 41 && c in 128..255       // 198.41.128.0/17
                103 -> (b == 21 && c in 244..247) ||  // 103.21.244.0/22
                    (b == 22 && c in 200..203) ||     // 103.22.200.0/22
                    (b == 31 && c in 4..7)            // 103.31.4.0/22
                141 -> b == 101 && c in 64..127       // 141.101.64.0/18
                108 -> b == 162 && c in 192..255      // 108.162.192.0/18
                else -> false
            }
        }

        /**
         * 过滤 DNS 解析结果中的污染 IP：
         * 通用黑名单（回环/未指定/Facebook 网段）+ hanime1 系列域名专属的 Cloudflare 网段白名单。
         * 被过滤时记录日志，便于在应用日志中确认污染情况。
         */
        private fun filterPollutedIps(
            hostname: String, addresses: List<InetAddress>,
        ): List<InetAddress> {
            val filtered = addresses.filter { addr ->
                !isPollutedAddress(addr) &&
                    (!HANIME_HOSTNAME.contains(hostname) || isCloudflareAddress(addr))
            }
            if (filtered.size != addresses.size) {
                HLumberjack.w(
                    "DNS",
                    "polluted IPs filtered: $hostname -> ${addresses.map { it.hostAddress }} " +
                        "(keep ${filtered.map { it.hostAddress }})"
                )
            }
            return filtered
        }
    }

    /**
     * IPv4 优先排序：移动网络下 IPv6 出境链路通常不可达，
     * 避免 OkHttp 优先尝试 IPv6 导致连接超时（日志中常见的 15s 卡顿/失败）。
     */
    private fun List<InetAddress>.sortIpv4First(): List<InetAddress> {
        return partition { it is Inet4Address }.let { (v4, v6) -> v4 + v6 }
    }

    private data class CachedDnsResult(
        val addresses: List<InetAddress>,
        val cachedAt: Long,
    )

    /** 正常解析结果缓存时长（弱网下避免每个请求都走 DoH 网络往返） */
    private val dnsCache = ConcurrentHashMap<String, CachedDnsResult>()

    private val dnsCacheTtlMillis = 120_000L

    /** 解析失败（含全部污染）结果的负缓存时长，避免弱网下反复重试拖慢所有请求 */
    private val dnsNegativeTtlMillis = 30_000L

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname == GETCHU_HOSTNAME) {
            HLumberjack.i("DNS", "built-in getchu: $hostname -> $getchuIps")
            return getchuIps.map {
                InetAddress.getByAddress(hostname, InetAddress.getByName(it).address)
            }
        }

        if (!ignoreBuiltInHosts && Preferences.useBuiltInHosts && HANIME_HOSTNAME.contains(hostname)) {
            val customIps = resolveCustomIps()
            if (!customIps.isNullOrEmpty()) {
                HLumberjack.i("DNS", "built-in hosts (custom): $hostname -> $customIps")
                return customIps.map {
                    InetAddress.getByAddress(hostname, InetAddress.getByName(it).address)
                }
            }
            HLumberjack.i("DNS", "built-in hosts (cloudflare): $hostname -> $cloudFlareIps")
            return cloudFlareIps.map {
                InetAddress.getByAddress(hostname, InetAddress.getByName(it).address)
            }
        }

        return resolveWithCache(hostname)
    }

    /**
     * 带缓存的解析入口：命中有效缓存直接返回（正缓存 120s、失败负缓存 30s），
     * 未命中才发起网络查询（DoH/系统），结果写入缓存。
     * 弱网下避免每个请求都重复走 DNS 网络往返。
     */
    private fun resolveWithCache(hostname: String): List<InetAddress> {
        // 漫画（forceDohWhenDisabled）不走缓存：一次 DoH 失败会被负缓存 30 秒，
        // 用户重启前会一直看到同一条旧错误。
        if (forceDohWhenDisabled) {
            return lookupFromNetwork(hostname)
        }
        val now = System.currentTimeMillis()
        dnsCache[hostname]?.let { cached ->
            val ttl = if (cached.addresses.isEmpty()) dnsNegativeTtlMillis else dnsCacheTtlMillis
            if (now - cached.cachedAt < ttl) {
                if (cached.addresses.isEmpty()) {
                    throw UnknownHostException("cached: all DNS results for $hostname are polluted")
                }
                return cached.addresses
            }
        }
        return try {
            val addresses = lookupFromNetwork(hostname)
            dnsCache[hostname] = CachedDnsResult(addresses, now)
            addresses
        } catch (e: Exception) {
            dnsCache[hostname] = CachedDnsResult(emptyList(), now)
            throw e
        }
    }

    private fun lookupFromNetwork(hostname: String): List<InetAddress> {
        val dohUrl = DohConfig.resolveUrl()
        if (!dohUrl.isNullOrBlank()) {
            return runCatching { lookupByDoH(dohUrl, hostname) }
                .getOrElse {
                    HLumberjack.w("DNS", "DoH lookup failed for $hostname: ${it.message}, fallback to system")
                    Log.w("DOH", "lookup failed for $hostname: ${it.message}")
                    systemLookup(hostname)
                }
        }

        if (forceDohWhenDisabled) {
            // 内建 Hosts 开启时 DoH 关闭；漫画域名被投毒，这里把所有公共 DoH 预设的结果合并，
            // 交给 OkHttp 逐个 IP 试连（连不通会自动换下一个 Route）。
            val merged = LinkedHashMap<String, InetAddress>()
            // 1) 先用已知可用 IP 打底（DNS 全线不可用时的唯一依靠）
            comicStaticIps[hostname.lowercase()]?.forEach { ip ->
                runCatching {
                    InetAddress.getByAddress(hostname, InetAddress.getByName(ip).address)
                }.getOrNull()?.let { merged[ip] = it }
            }
            val dohFailures = mutableListOf<String>()
            DohConfig.presets.forEach { preset ->
                runCatching { lookupByDoH(preset.url, hostname) }
                    .onSuccess { addresses ->
                        addresses.forEach { address ->
                            merged.putIfAbsent(address.hostAddress.orEmpty(), address)
                        }
                    }
                    .onFailure { failure ->
                        dohFailures += "${preset.key}: ${failure.message}"
                    }
            }
            if (merged.isNotEmpty()) {
                HLumberjack.i("DNS", "comic DoH merged: $hostname -> ${merged.keys}")
                return merged.values.toList()
            }
            val systemResult = runCatching { systemLookup(hostname) }
            return systemResult.getOrElse { systemFailure ->
                throw UnknownHostException(
                    "comic DNS failed for $hostname | DoH: ${dohFailures.joinToString("; ")} | system: ${systemFailure.message}"
                )
            }
        }

        return systemLookup(hostname)
    }

    private fun systemLookup(hostname: String): List<InetAddress> {
        return runCatching {
            val results = filterPollutedIps(hostname, Dns.SYSTEM.lookup(hostname))
            if (results.isEmpty()) {
                throw UnknownHostException("all DNS results for $hostname are polluted")
            }
            results.sortIpv4First()
        }.getOrElse {
            HLumberjack.e("DNS", "system lookup failed for $hostname: ${it.message}")
            throw it
        }.also {
            HLumberjack.i("DNS", "system: $hostname -> ${it.map { a -> a.hostAddress }}")
        }
    }

    private fun lookupByDoH(dohUrl: String, hostname: String): List<InetAddress> {
        val config = DohRuntimeConfig(
            url = dohUrl,
            bootstrapIps = DohConfig.bootstrapIps(),
            timeoutSeconds = DohConfig.timeoutSeconds(),
        )
        val dns = getOrCreateDohDns(config)
        val results = filterPollutedIps(hostname, dns.lookup(hostname))
        if (results.isEmpty()) {
            throw UnknownHostException("all DoH results for $hostname are polluted")
        }
        return results.sortIpv4First().also {
            Log.i("DOH", it.toString())
            HLumberjack.i("DNS", "DoH($dohUrl): $hostname -> ${it.map { a -> a.hostAddress }}")
        }
    }

    fun lookupByDoHOnly(hostname: String): List<InetAddress> {
        val dohUrl = DohConfig.resolveUrl() ?: error("DoH is disabled")
        return lookupByDoH(dohUrl, hostname)
    }

    private fun getOrCreateDohDns(config: DohRuntimeConfig): Dns {
        val currentDns = cachedDohDns
        if (currentDns != null && cachedDohConfig == config) return currentDns

        synchronized(this) {
            val dnsAgain = cachedDohDns
            if (dnsAgain != null && cachedDohConfig == config) return dnsAgain

            val client = OkHttpClient.Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
            val bootstrapHosts = config.bootstrapIps.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()
            }
            val dnsBuilder = DnsOverHttps.Builder()
                .client(client)
                .url(config.url.toHttpUrl())
                .includeIPv6(true)
                .post(false)
                .resolvePrivateAddresses(true)
                .resolvePublicAddresses(true)
            if (bootstrapHosts.isNotEmpty()) {
                dnsBuilder.bootstrapDnsHosts(bootstrapHosts)
            }
            val dns = dnsBuilder.build()

            cachedDohConfig = config
            cachedDohDns = dns
            return dns
        }
    }

    fun getCDNList(host: String): List<String> {
        if (host == GETCHU_HOSTNAME) {
            HLumberjack.i("DNS", "CDN list (getchu): $host -> $getchuIps")
            return getchuIps.distinct()
        }

        if (Preferences.useBuiltInHosts && HANIME_HOSTNAME.contains(host)) {
            val customIps = resolveCustomIps()
            if (!customIps.isNullOrEmpty()) {
                HLumberjack.i("DNS", "CDN list (custom): $host -> $customIps")
                return customIps.distinct()
            }
            HLumberjack.i("DNS", "CDN list (cloudflare): $host -> $cloudFlareIps")
            return cloudFlareIps.distinct()
        }

        return runCatching {
            Dns.SYSTEM.lookup(host).let { filterPollutedIps(host, it) }
                .map { it.hostAddress }.distinct()
        }.getOrElse {
            it.printStackTrace()
            HLumberjack.w("DNS", "CDN list system lookup failed for $host: ${it.message}")
            emptyList()
        }
    }

    @Volatile
    private var cachedCustomIps: List<String>? = null

    @Volatile
    private var cachedCustomIpsRaw: String? = null

    private fun resolveCustomIps(): List<String>? {
        val raw = Preferences.customHostsData
        if (raw.isBlank()) return null
        if (raw == cachedCustomIpsRaw && cachedCustomIps != null) {
            return cachedCustomIps
        }
        val result = parseCustomIps(raw)
        cachedCustomIpsRaw = raw
        cachedCustomIps = result
        return result
    }

}
