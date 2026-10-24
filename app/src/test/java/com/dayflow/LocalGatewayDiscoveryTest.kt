package com.dayflow

import com.dayflow.data.LocalGatewayDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A gateway on the same phone is only usable if the app tries the right addresses, in the right
 * order: loopback needs no network, and a stale LAN address must never hold up the search.
 */
class LocalGatewayDiscoveryTest {

    @Test
    fun buildsHttpUrlsForEveryHostAndPort() {
        val candidates = LocalGatewayDiscovery.candidates(listOf("127.0.0.1"), listOf(20128, 8080))
        assertEquals(listOf("http://127.0.0.1:20128", "http://127.0.0.1:8080"), candidates)
    }

    @Test
    fun loopbackIsTriedBeforeTheLanAddress() {
        val ordered = LocalGatewayDiscovery.orderedCandidates(listOf("10.0.0.5"), listOf(8080))
        assertEquals(listOf("http://127.0.0.1:8080", "http://localhost:8080", "http://10.0.0.5:8080"), ordered)
        assertTrue("loopback must not come last", ordered.indexOf("http://10.0.0.5:8080") == ordered.lastIndex)
    }

    @Test
    fun duplicateHostsAndPortsAreTriedOnce() {
        val candidates = LocalGatewayDiscovery.candidates(listOf("127.0.0.1", "127.0.0.1"), listOf(8080, 8080, 8000))
        assertEquals(listOf("http://127.0.0.1:8080", "http://127.0.0.1:8000"), candidates)
    }

    @Test
    fun thePortsCoverTheServersPeopleActuallyRun() {
        assertTrue("OmniRoute's port must be probed", LocalGatewayDiscovery.COMMON_PORTS.contains(20128))
        assertTrue("a plain 8080 server must be probed", LocalGatewayDiscovery.COMMON_PORTS.contains(8080))
        assertEquals("ports are probed twice", LocalGatewayDiscovery.COMMON_PORTS.size, LocalGatewayDiscovery.COMMON_PORTS.toSet().size)
    }

    @Test
    fun probingAClosedPortIsAnAnswerNotACrash() {
        // Nothing listens on this port, so the probe must report false rather than throw.
        assertFalse(LocalGatewayDiscovery.probe("http://127.0.0.1:1", apiKey = "", timeoutMillis = 200))
    }
}
