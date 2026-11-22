package com.stremio.mobile.core

import com.stremio.core.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamsHistoryTest {
    private fun item(metaId: String, videoId: String, source: String = """"infoHash":"0123456789ABCDEF0123456789abcdef01234567","fileIdx":1""") = """
        [
            {"metaId":"$metaId","videoId":"$videoId"},
            {
                "stream":{$source,"name":"Addon 1080p","behaviorHints":{"bingeGroup":"addon|1080p"}},
                "type":"series","metaId":"$metaId","videoId":"$videoId",
                "metaTransportUrl":"https://v3-cinemeta.strem.io/manifest.json",
                "streamTransportUrl":"https://addon.example/manifest.json",
                "state":null,"_mtime":"2026-11-01T10:00:00Z"
            }
        ]
    """

    private val torrentKey = "torrent:0123456789abcdef0123456789abcdef01234567:1"

    private val bucket = """
        {"uid":"user","items":[
            ${item("tt1", "tt1:1:1")},
            ${item("tt1", "tt1:1:2", """"url":"https://cdn.example/video.mp4"""")},
            ${item("tt2", "tt2", """"rarUrls":[{"url":"https://cdn.example/a.rar"}],"fileIdx":null""")}
        ]}
    """

    private class FakeStorage(var value: String?) : Storage {
        override fun get(key: String): Storage.Result<String?> =
            Storage.Result.Ok(value.takeIf { key == StreamsHistory.STORAGE_KEY })

        override fun set(key: String, value: String?): Storage.Result<Unit> = Storage.Result.Ok(Unit)
    }

    @Test
    fun `parses the played stream's source per meta and video id`() {
        assertEquals(
            mapOf(
                "tt1" to mapOf(
                    "tt1:1:1" to PlayedStream(torrentKey),
                    "tt1:1:2" to PlayedStream("url:https://cdn.example/video.mp4"),
                ),
                "tt2" to mapOf("tt2" to PlayedStream(null)),
            ),
            StreamsHistory.parse(bucket),
        )
    }

    @Test
    fun `reads a missing torrent file index as null, not 0`() {
        val entries = StreamsHistory.parse(
            """{"uid":null,"items":[${item("tt3", "tt3", """"infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":null""")}]}""",
        )
        assertEquals(PlayedStream("torrent:0123456789abcdef0123456789abcdef01234567:"), entries["tt3"]?.get("tt3"))
    }

    @Test
    fun `skips malformed entries and keeps the rest`() {
        val entries = StreamsHistory.parse("""{"uid":null,"items":[[{"metaId":"tt9"}],"junk",${item("tt1", "tt1:1:1")}]}""")
        assertEquals(mapOf("tt1" to mapOf("tt1:1:1" to PlayedStream(torrentKey))), entries)
    }

    @Test
    fun `treats missing or malformed data as empty`() {
        assertEquals(emptyMap<String, Any>(), StreamsHistory.parse(null))
        assertEquals(emptyMap<String, Any>(), StreamsHistory.parse("""{"uid":null,"items":[]}"""))
        assertEquals(emptyMap<String, Any>(), StreamsHistory.parse("not json"))
    }

    @Test
    fun `contains matches a video or any video of the meta item`() {
        val history = StreamsHistory(FakeStorage(bucket))

        assertTrue(history.contains("tt1", "tt1:1:2"))
        assertFalse(history.contains("tt1", "tt1:1:3"))
        assertTrue(history.contains("tt1", null))
        assertFalse(history.contains("tt3", null))
        assertEquals(PlayedStream(torrentKey), history.playedStream("tt1", "tt1:1:1"))
        assertNull(history.playedStream("tt1", "tt1:1:3"))
    }

    @Test
    fun `sees storage updates`() {
        val storage = FakeStorage(null)
        val history = StreamsHistory(storage)
        assertFalse(history.contains("tt2", "tt2"))

        storage.value = bucket
        assertTrue(history.contains("tt2", "tt2"))
    }
}
