package com.yenaly.han1meviewer.logic.network

object NetworkEnvironmentInspector {

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
}
