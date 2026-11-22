package com.stremio.mobile.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsLocalNetworkUrlTest {
    @Test
    fun `private and link-local addresses are local`() {
        assertTrue(isLocalNetworkUrl("http://192.168.1.10:11470"))
        assertTrue(isLocalNetworkUrl("http://10.0.0.5:11470"))
        assertTrue(isLocalNetworkUrl("http://172.20.1.1:11470"))
        assertTrue(isLocalNetworkUrl("http://169.254.3.4:11470"))
        assertTrue(isLocalNetworkUrl("http://[fd12:3456::1]:11470"))
        assertTrue(isLocalNetworkUrl("http://[fe80::1]:11470"))
    }

    @Test
    fun `local host names are local`() {
        assertTrue(isLocalNetworkUrl("http://mypc:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.local:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.lan:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.home:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.home.arpa:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.internal:11470"))
        assertTrue(isLocalNetworkUrl("http://mypc.fritz.box:11470"))
    }

    @Test
    fun `public, loopback and Tailscale addresses are not local`() {
        assertFalse(isLocalNetworkUrl("http://172.32.0.1:11470"))
        assertFalse(isLocalNetworkUrl("http://8.8.8.8:11470"))
        assertFalse(isLocalNetworkUrl("http://100.101.102.103:11470"))
        assertFalse(isLocalNetworkUrl("http://[fd7a:115c:a1e0::1]:11470"))
        assertFalse(isLocalNetworkUrl("http://mypc.tail1234.ts.net:11470"))
        assertFalse(isLocalNetworkUrl("https://stremio.example.com"))
        assertFalse(isLocalNetworkUrl("http://localhost:11470"))
    }
}
