package com.yenaly.han1meviewer.logic.network

object NetworkEnvironmentInspector {

    private val mainlandCarrierNames = mapOf(
        "46000" to "中国移动",
        "46002" to "中国移动",
        "46004" to "中国移动",
        "46007" to "中国移动",
        "46008" to "中国移动",
        "46001" to "中国联通",
        "46006" to "中国联通",
        "46009" to "中国联通",
        "46003" to "中国电信",
        "46005" to "中国电信",
        "46011" to "中国电信",
        "46015" to "中国广电",
    )

    private val commonHotspotGateways = setOf(
        "192.168.42.1",
        "192.168.43.1",
        "192.168.49.1",
        "192.168.137.1",
        "192.168.232.1",
        "172.20.10.1",
    )

    /**
     * Android does not expose a public "currently connected to a hotspot" capability.
     * Known Android/iPhone/Windows tethering gateway and DNS addresses are therefore
     * treated only as a hotspot signal, never as certain identification.
     */
    fun isLikelyHotspot(gateway: String?, dnsServers: List<String>): Boolean {
        return buildList {
            gateway?.takeIf { it.isNotBlank() }?.let(::add)
            dnsServers.filterTo(this) { it.isNotBlank() }
        }.any { address ->
            address in commonHotspotGateways ||
                address.startsWith("172.20.10.") ||
                address.startsWith("192.168.43.") ||
                address.startsWith("192.168.49.") ||
                address.startsWith("192.168.137.") ||
                address.startsWith("192.168.232.")
        }
    }

    fun resolveCarrierName(operatorName: String?, operatorNumeric: String?): String? {
        val mapped = operatorNumeric
            ?.trim()
            ?.take(5)
            ?.let(mainlandCarrierNames::get)
        return mapped ?: operatorName?.trim()?.takeIf { it.isNotEmpty() }
    }
}
