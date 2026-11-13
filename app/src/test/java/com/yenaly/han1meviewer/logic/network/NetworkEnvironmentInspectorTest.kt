package com.yenaly.han1meviewer.logic.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkEnvironmentInspectorTest {

    @Test
    fun commonPhoneHotspotAddressesAreDetected() {
        assertTrue(NetworkEnvironmentInspector.isLikelyHotspot("192.168.43.1", emptyList()))
        assertTrue(NetworkEnvironmentInspector.isLikelyHotspot("172.20.10.1", emptyList()))
        assertTrue(NetworkEnvironmentInspector.isLikelyHotspot("192.168.137.1", emptyList()))
        assertTrue(
            NetworkEnvironmentInspector.isLikelyHotspot(
                gateway = null,
                dnsServers = listOf("172.20.10.1"),
            )
        )
    }

    @Test
    fun ordinaryRouterAddressesAreNotTreatedAsHotspots() {
        assertFalse(NetworkEnvironmentInspector.isLikelyHotspot("192.168.0.1", emptyList()))
        assertFalse(NetworkEnvironmentInspector.isLikelyHotspot("192.168.1.1", emptyList()))
        assertFalse(NetworkEnvironmentInspector.isLikelyHotspot("10.0.0.1", emptyList()))
    }
}
