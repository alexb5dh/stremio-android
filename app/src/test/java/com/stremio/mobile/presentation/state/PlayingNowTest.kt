package com.stremio.mobile.presentation.state

import com.stremio.core.types.resource.Stream
import com.stremio.mobile.core.CoreStream
import com.stremio.mobile.core.StreamFixtures.request
import com.stremio.mobile.core.StreamFixtures.torrent
import com.stremio.mobile.core.StreamFixtures.url
import com.stremio.mobile.data.model.StreamOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingNowTest {
    private val hash = "ce8bb595085300d7f1eec332a97b8d809a6be224"

    private fun option(stream: Stream) = StreamOption(
        key = "0",
        name = "1080p",
        description = null,
        addonTitle = "Addon",
        quality = null,
        core = CoreStream(stream, request("https://a.example/manifest.json"), metaRequest = null, addonTitle = "Addon"),
    )

    @Test
    fun `reads a torrent's info hash in lowercase`() {
        assertEquals(hash, option(torrent(hash.uppercase(), fileIdx = 0)).infoHash)
        assertNull(option(url("https://cdn.example/a.mp4")).infoHash)
    }

    @Test
    fun `marks only torrents the server has loaded`() {
        val state = StreamsUiState(playingInfoHashes = setOf(hash))

        assertTrue(state.isPlayingNow(option(torrent(hash.uppercase(), fileIdx = null))))
        assertFalse(state.isPlayingNow(option(torrent("0123456789abcdef0123456789abcdef01234567", fileIdx = null))))
        assertFalse(state.isPlayingNow(option(url("https://cdn.example/a.mp4"))))
        assertFalse(StreamsUiState().isPlayingNow(option(torrent(hash, fileIdx = null))))
    }
}
