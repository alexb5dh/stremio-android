package com.stremio.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class HostRedactionRegexTest {
    private fun redact(host: String, text: String) = hostRedactionRegex(host).replace(text, "[HOST]")

    @Test
    fun `redacts the whole host only`() {
        assertEquals("connect to [HOST]:11470", redact("pc", "connect to pc:11470"))
        assertEquals("pcm and pc.lan and my-pc", redact("pc", "pcm and pc.lan and my-pc"))
    }

    @Test
    fun `matches before a sentence-ending dot and ignores case`() {
        assertEquals("Failed to reach [HOST].", redact("pc.example.ts.net", "Failed to reach PC.example.ts.net."))
    }

    @Test
    fun `matches dotted hosts literally`() {
        assertEquals("[HOST]/settings", redact("192.168.1.10", "192.168.1.10/settings"))
        assertEquals("192x168x1x10 and 192.168.1.100", redact("192.168.1.10", "192x168x1x10 and 192.168.1.100"))
    }

    @Test
    fun `strips IPv6 brackets`() {
        assertEquals("http://[[HOST]]:11470", redact("[fd12::1]", "http://[fd12::1]:11470"))
    }
}
