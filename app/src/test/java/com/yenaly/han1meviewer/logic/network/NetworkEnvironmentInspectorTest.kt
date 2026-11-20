package com.yenaly.han1meviewer.logic.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun mainlandCarrierNumericIsNormalized() {
        assertEquals(
            "中国移动",
            NetworkEnvironmentInspector.resolveCarrierName(null, "46000"),
        )
        assertEquals(
            "中国联通",
            NetworkEnvironmentInspector.resolveCarrierName(null, "46006"),
        )
        assertEquals(
            "中国电信",
            NetworkEnvironmentInspector.resolveCarrierName(null, "46011"),
        )
    }

    @Test
    fun unknownCarrierFallsBackToSystemName() {
        assertEquals(
            "Example Mobile",
            NetworkEnvironmentInspector.resolveCarrierName("Example Mobile", "99999"),
        )
        assertNull(NetworkEnvironmentInspector.resolveCarrierName(null, ""))
    }
}
