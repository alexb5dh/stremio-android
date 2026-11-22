package com.stremio.mobile.core

import com.stremio.core.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamsHistoryTest {
    private fun item(metaId: String, videoId: String) = """
        [
            {"metaId":"$metaId","videoId":"$videoId"},
            {
                "stream":{"infoHash":"0123456789abcdef0123456789abcdef01234567","fileIdx":1,"name":"Addon 1080p"},
                "type":"series","metaId":"$metaId","videoId":"$videoId",
                "metaTransportUrl":"https://v3-cinemeta.strem.io/manifest.json",
                "streamTransportUrl":"https://addon.example/manifest.json",
                "state":null,"_mtime":"2026-11-01T10:00:00Z"
            }
        ]
    """

    private val bucket = """
        {"uid":"user","items":[${item("tt1", "tt1:1:1")},${item("tt1", "tt1:1:2")},${item("tt2", "tt2")}]}
    """

    private class FakeStorage(var value: String?) : Storage {
        override fun get(key: String): Storage.Result<String?> =
            Storage.Result.Ok(value.takeIf { key == StreamsHistory.STORAGE_KEY })

        override fun set(key: String, value: String?): Storage.Result<Unit> = Storage.Result.Ok(Unit)
    }

    @Test
    fun `parses video ids per meta id`() {
        assertEquals(
            mapOf("tt1" to setOf("tt1:1:1", "tt1:1:2"), "tt2" to setOf("tt2")),
            StreamsHistory.parse(bucket),
        )
    }

    @Test
    fun `treats missing or malformed data as empty`() {
        assertEquals(emptyMap<String, Set<String>>(), StreamsHistory.parse(null))
        assertEquals(emptyMap<String, Set<String>>(), StreamsHistory.parse("""{"uid":null,"items":[]}"""))
        assertEquals(emptyMap<String, Set<String>>(), StreamsHistory.parse("not json"))
    }

    @Test
    fun `contains matches a video or any video of the meta item`() {
        val history = StreamsHistory(FakeStorage(bucket))

        assertTrue(history.contains("tt1", "tt1:1:2"))
        assertFalse(history.contains("tt1", "tt1:1:3"))
        assertTrue(history.contains("tt1", null))
        assertFalse(history.contains("tt3", null))
    }

    @Test
    fun `contains sees storage updates`() {
        val storage = FakeStorage(null)
        val history = StreamsHistory(storage)
        assertFalse(history.contains("tt2", "tt2"))

        storage.value = bucket
        assertTrue(history.contains("tt2", "tt2"))
    }
}
