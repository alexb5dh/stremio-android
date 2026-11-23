package com.stremio.mobile.server

import org.junit.Assert.assertEquals
import org.junit.Test

class ParseActiveInfoHashesTest {
    @Test
    fun `returns the lowercased info hashes of loaded torrents`() {
        val json = """
            {
                "CE8BB595085300D7F1EEC332A97B8D809A6BE224": {"infoHash": "ce8bb595085300d7f1eec332a97b8d809a6be224", "peers": 1},
                "0123456789abcdef0123456789abcdef01234567": null
            }
        """
        assertEquals(
            setOf("ce8bb595085300d7f1eec332a97b8d809a6be224", "0123456789abcdef0123456789abcdef01234567"),
            parseActiveInfoHashes(json),
        )
    }

    @Test
    fun `returns nothing when no torrent is loaded`() {
        assertEquals(emptySet<String>(), parseActiveInfoHashes("{}"))
    }
}
